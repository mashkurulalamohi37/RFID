-- =====================================================================
-- RollTrack PDA: proposed DB fix for Load / Unload
-- NOT YET RUN. Review with the backend team first. Run on the DEV DB only.
--
-- Order: STEP 0 (read-only checks) -> STEP 1 (backup) -> STEP 2 (new helper,
-- touches nothing that exists) -> STEP 3 (test the helper) -> STEP 4 (point the
-- two existing procedures at the helper) -> STEP 5 (only if the ORDS handler
-- rewrites replies) -> STEP 6 (verify / rollback).
--
-- PART B at the end covers Tag Issue (sub-contract-issue / req-scan). Its SQL is
-- NOT written yet: it needs the answers from the PART B checks first.
-- =====================================================================


-- ---------------------------------------------------------------------
-- STEP 0 - READ-ONLY CHECKS (safe, change nothing). Do ALL of these first.
-- ---------------------------------------------------------------------

-- 0a. Which DB schema is behind the URL alias "snym_erp"?
--     Run while connected as IDEAERP. If a row shows PATTERN = snym_erp, IDEAERP is the right schema.
SELECT parsing_schema, pattern, status FROM user_ords_schemas;

-- 0b. Which code does each PDA endpoint run?
--     Read the SOURCE of 'load' and 'unload' and answer:
--       - Does it pass P_RESPONSE straight back to the caller?            -> STEP 5 not needed
--       - Does it build its own text (e.g. 'SUCCESS', COUNT || ' item(s)'),
--         run TO_NUMBER(P_RESPONSE), or CASE on fixed values like '-1'?  -> STEP 5 IS needed,
--         otherwise the new reply text never reaches the PDA (or makes the handler fail).
SELECT t.uri_template, h.method, h.source
FROM   user_ords_templates t JOIN user_ords_handlers h ON h.template_id = t.id
WHERE  t.uri_template IN ('load', 'unload', 'sub-contract-issue', 'ref-weight', 'req-scan', 'pda-login');

-- 0c. Are our 2 test tags registered as rolls? (Load only works for tags that are
--     already tagged to a roll with production qty: REF_ID_FLG = 1, RF_PROD_QTY not null)
--     Also note the value LOAD_FLG / UNLOAD_FLG has once set (expected: 1). If it is
--     something else (e.g. 'Y'), change c_flag_done in STEP 2.
SELECT rf_no, roll_no, ref_id_flg, rf_prod_qty, load_flg, unload_flg
FROM   tex_wo_tube_dtls_rf
WHERE  rf_no IN ('E2806A960000402D1EB05D2F', 'E2806A960000502D1EB05952');

-- 0d. Oracle version. STEP 2 uses JSON_OBJECT_T / JSON_ARRAY_T, which need 12.2 or later.
SELECT product, version FROM product_component_version;

-- 0e. Exact parameter list of the existing work-order procedures.
--     STEP 2 assumes: 6 parameters, (1) the RF tag, (2) something we pass NULL for,
--     (3)-(6) OUT values. Check:
--       - Is POSITION 1 the RF tag?  If not, STEP 2 must change.
--       - What is POSITION 2? If it is a user / employee ID, set v_arg2 := v_user_id in
--         STEP 2 so the ERP records who scanned. If it is something else (work order,
--         date...), tell the backend team - passing NULL may make every save fail.
SELECT object_name, position, argument_name, data_type, in_out
FROM   user_arguments
WHERE  object_name IN ('LOADING_WORK_ORDER_PROS', 'UNLOADING_WORK_ORDER_PROS')
AND    package_name IS NULL
ORDER  BY object_name, position;

-- 0f. Source of the work-order procedures. Look for:
--       - COMMIT inside them. If present, each tag is saved the moment it is processed and
--         nothing can be rolled back afterwards. STEP 2 still reports exactly which tags
--         were saved, so the reply stays truthful - just be aware there is no "all or nothing".
--       - Which checks they do (tagged? produced? already loaded?) and what text they put
--         in the OUT parameters. STEP 2 does NOT trust that text for success - it re-reads
--         LOAD_FLG / UNLOAD_FLG - but the text is shown to the PDA user when a tag fails.
SELECT name, line, text
FROM   user_source
WHERE  name IN ('LOADING_WORK_ORDER_PROS', 'UNLOADING_WORK_ORDER_PROS')
ORDER  BY name, line;

-- 0g. Parameter list of the two procedures we will replace in STEP 4.
--     Expected: P_DATA IN CLOB, P_RESPONSE OUT VARCHAR2. If different, adjust STEP 4 to match
--     exactly, or the ORDS handler will stop compiling.
SELECT object_name, position, argument_name, data_type, in_out
FROM   user_arguments
WHERE  object_name IN ('TAGGING_RFID_TO_LOAD', 'TAGGING_RFID_TO_UNLOAD')
AND    package_name IS NULL
ORDER  BY object_name, position;


-- ---------------------------------------------------------------------
-- STEP 1 - BACKUP: in Toad, save the current source of BOTH procedures
--          (TAGGING_RFID_TO_LOAD, TAGGING_RFID_TO_UNLOAD) and of the 'load' / 'unload'
--          ORDS handlers (the SOURCE column from 0b) to files.
--          (Schema Browser -> Procedures -> right-click -> Create Script).
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- STEP 2 - NEW HELPER PROCEDURE (new name - does not change anything that exists).
--          If it fails to compile, nothing in production is affected: fix and retry.
--          Before compiling, set the two marked values from checks 0c and 0e.
-- ---------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE IDEAERP.PDA_RFID_BULK_FLAG(
    P_DATA     IN  CLOB,
    P_MODE     IN  VARCHAR2,          -- 'LOAD' or 'UNLOAD'
    P_RESPONSE OUT VARCHAR2
) AS
    -- >>> CONFIRM BEFORE COMPILING <<<
    c_flag_done CONSTANT VARCHAR2(10) := '1';  -- LOAD_FLG / UNLOAD_FLG value once set (check 0c)
    -- v_arg2 (below): 2nd argument of *_WORK_ORDER_PROS (check 0e). Set to v_user_id if it is a user ID.

    c_max_resp  CONSTANT PLS_INTEGER := 3900;  -- keep reply inside a VARCHAR2(4000) bind

    TYPE t_seen IS TABLE OF BOOLEAN INDEX BY VARCHAR2(100);
    v_seen    t_seen;
    v_obj     JSON_OBJECT_T;
    v_items   JSON_ARRAY_T;
    v_user_id NUMBER;
    v_arg2    VARCHAR2(100);
    v_tag     VARCHAR2(100);
    v_out     VARCHAR2(4000);
    v_out2    VARCHAR2(4000);
    v_out3    VARCHAR2(4000);
    v_out4    VARCHAR2(4000);
    v_rows    NUMBER;
    v_flag    VARCHAR2(10);
    v_ok      PLS_INTEGER := 0;
    v_bad     PLS_INTEGER := 0;
    v_dup     PLS_INTEGER := 0;
    v_reasons VARCHAR2(32767);
    v_verb    VARCHAR2(20);
    v_err     VARCHAR2(400);

    PROCEDURE add_reason(p_tag IN VARCHAR2, p_why IN VARCHAR2) IS
    BEGIN
        v_bad := v_bad + 1;
        IF NVL(LENGTH(v_reasons), 0) < 3000 THEN
            v_reasons := v_reasons || '; ' || p_tag || ' ' || SUBSTR(p_why, 1, 120);
        END IF;
    END add_reason;

    -- Reads how many rows the tag has and whether the flag for this mode is already set.
    PROCEDURE read_flag(p_tag IN VARCHAR2) IS
    BEGIN
        SELECT COUNT(*),
               MAX(CASE WHEN P_MODE = 'LOAD' THEN TO_CHAR(load_flg) ELSE TO_CHAR(unload_flg) END)
        INTO   v_rows, v_flag
        FROM   tex_wo_tube_dtls_rf
        WHERE  rf_no = p_tag;
    END read_flag;

    PROCEDURE undo_tag IS
    BEGIN
        ROLLBACK TO SAVEPOINT pda_tag;
    EXCEPTION
        WHEN OTHERS THEN NULL;  -- savepoint gone because the called procedure committed
    END undo_tag;
BEGIN
    IF P_MODE IS NULL OR P_MODE NOT IN ('LOAD', 'UNLOAD') THEN
        P_RESPONSE := '-9: invalid mode ' || P_MODE;
        RETURN;
    END IF;
    v_verb := CASE P_MODE WHEN 'LOAD' THEN 'loaded' ELSE 'unloaded' END;

    IF P_DATA IS NULL OR DBMS_LOB.GETLENGTH(P_DATA) = 0 THEN
        P_RESPONSE := '0';
        RETURN;
    END IF;

    -- Parse the body once (CLOB-safe, no 4000-char limit on the tag list).
    BEGIN
        v_obj := JSON_OBJECT_T.PARSE(P_DATA);
    EXCEPTION
        WHEN OTHERS THEN
            P_RESPONSE := '-9: request body is not valid JSON';
            RETURN;
    END;

    v_user_id := JSON_VALUE(P_DATA, '$.USER_ID' RETURNING NUMBER NULL ON ERROR);
    IF v_user_id IS NULL THEN
        P_RESPONSE := '-1';
        RETURN;
    END IF;
    v_arg2 := NULL;  -- <<< set to v_user_id if check 0e shows argument 2 is a user ID

    IF NOT v_obj.HAS('ITEMS') OR NOT v_obj.GET('ITEMS').IS_ARRAY() THEN
        P_RESPONSE := '-3';
        RETURN;
    END IF;
    v_items := v_obj.GET_ARRAY('ITEMS');
    IF v_items.GET_SIZE() = 0 THEN
        P_RESPONSE := '-3';
        RETURN;
    END IF;

    FOR i IN 0 .. v_items.GET_SIZE() - 1 LOOP
        -- Same form the PDA sends: trimmed, upper-case hex.
        v_tag := SUBSTR(UPPER(TRIM(REPLACE(v_items.GET(i).TO_STRING(), '"', ''))), 1, 100);

        IF v_tag IS NULL THEN
            add_reason('(blank)', 'empty tag');
        ELSIF v_seen.EXISTS(v_tag) THEN
            v_dup := v_dup + 1;               -- same tag twice in one batch: count it once
        ELSE
            v_seen(v_tag) := TRUE;
            read_flag(v_tag);
            IF v_rows = 0 THEN
                add_reason(v_tag, 'not registered as a roll');
            ELSIF v_flag = c_flag_done THEN
                add_reason(v_tag, 'already ' || v_verb);
            ELSE
                -- One tag failing must not cancel the others.
                BEGIN
                    SAVEPOINT pda_tag;
                    v_out := NULL; v_out2 := NULL; v_out3 := NULL; v_out4 := NULL;
                    IF P_MODE = 'LOAD' THEN
                        LOADING_WORK_ORDER_PROS(v_tag, v_arg2, v_out, v_out2, v_out3, v_out4);
                    ELSE
                        UNLOADING_WORK_ORDER_PROS(v_tag, v_arg2, v_out, v_out2, v_out3, v_out4);
                    END IF;

                    -- Success = the flag is really set in the table, not whatever text came back.
                    read_flag(v_tag);
                    IF v_flag = c_flag_done THEN
                        v_ok := v_ok + 1;
                    ELSE
                        undo_tag;
                        add_reason(v_tag, 'not ' || v_verb || ': '
                                   || COALESCE(v_out4, v_out3, v_out2, v_out, 'no message from ERP'));
                    END IF;
                EXCEPTION
                    WHEN OTHERS THEN
                        v_err := SUBSTR(SQLERRM, 1, 150);
                        undo_tag;
                        add_reason(v_tag, 'error: ' || v_err);
                END;
            END IF;
        END IF;
    END LOOP;

    COMMIT;

    -- Starts with "<n> loaded" / "<n> unloaded" - the PDA reads "0 loaded..." as "nothing saved".
    P_RESPONSE := SUBSTR(
           v_ok || ' ' || v_verb || ', ' || v_bad || ' rejected'
        || CASE WHEN v_dup > 0 THEN ', ' || v_dup || ' duplicate(s) ignored' END
        || CASE WHEN v_bad > 0 THEN ' -' || SUBSTR(v_reasons, 2) END,
        1, c_max_resp);
EXCEPTION
    WHEN OTHERS THEN
        v_err := SUBSTR(SQLERRM, 1, 200);
        ROLLBACK;
        P_RESPONSE := '-9: ' || v_err;       -- the real DB error instead of a bare -1
END PDA_RFID_BULK_FLAG;
/

-- Must say VALID. If INVALID: SELECT * FROM user_errors WHERE name = 'PDA_RFID_BULK_FLAG';
SELECT object_name, status FROM user_objects WHERE object_name = 'PDA_RFID_BULK_FLAG';


-- ---------------------------------------------------------------------
-- STEP 3 - TEST THE HELPER DIRECTLY (DEV DB). Nothing the PDA uses has changed yet.
--          This really marks the test rolls, so run the LOAD test, then the UNLOAD test.
-- ---------------------------------------------------------------------

SET SERVEROUTPUT ON

-- Expected: "1 loaded, 1 rejected, 1 duplicate(s) ignored - NOT_A_REAL_TAG not registered as a roll"
-- (or "0 loaded ... not loaded: <ERP message>" if the test tag is not produced yet - see 0c)
DECLARE v VARCHAR2(4000);
BEGIN
    PDA_RFID_BULK_FLAG(
        '{"USER_ID":1,"ITEMS":["E2806A960000402D1EB05D2F","e2806a960000402d1eb05d2f","NOT_A_REAL_TAG"]}',
        'LOAD', v);
    DBMS_OUTPUT.PUT_LINE(v);
END;
/

-- Running the same LOAD again must now say: "0 loaded, 1 rejected - E2806A... already loaded"
-- Then the UNLOAD test - expected: "1 unloaded, 0 rejected"
DECLARE v VARCHAR2(4000);
BEGIN
    PDA_RFID_BULK_FLAG('{"USER_ID":1,"ITEMS":["E2806A960000402D1EB05D2F"]}', 'UNLOAD', v);
    DBMS_OUTPUT.PUT_LINE(v);
END;
/

-- Error paths - expected: '-1', '-3', '-9: request body is not valid JSON'
DECLARE v VARCHAR2(4000);
BEGIN
    PDA_RFID_BULK_FLAG('{"ITEMS":["X"]}', 'LOAD', v);       DBMS_OUTPUT.PUT_LINE(v);
    PDA_RFID_BULK_FLAG('{"USER_ID":1}', 'LOAD', v);         DBMS_OUTPUT.PUT_LINE(v);
    PDA_RFID_BULK_FLAG('not json', 'LOAD', v);              DBMS_OUTPUT.PUT_LINE(v);
END;
/

-- Re-run 0c: LOAD_FLG / UNLOAD_FLG must match what the test output said.


-- ---------------------------------------------------------------------
-- STEP 4 - SWITCH THE PDA ENDPOINTS: same names and parameters as today (check 0g),
--          so the ORDS handlers keep working unchanged.
-- ---------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE IDEAERP.TAGGING_RFID_TO_LOAD(
    P_DATA     IN  CLOB,
    P_RESPONSE OUT VARCHAR2
) AS
BEGIN
    PDA_RFID_BULK_FLAG(P_DATA, 'LOAD', P_RESPONSE);
END TAGGING_RFID_TO_LOAD;
/

CREATE OR REPLACE PROCEDURE IDEAERP.TAGGING_RFID_TO_UNLOAD(
    P_DATA     IN  CLOB,
    P_RESPONSE OUT VARCHAR2
) AS
BEGIN
    PDA_RFID_BULK_FLAG(P_DATA, 'UNLOAD', P_RESPONSE);
END TAGGING_RFID_TO_UNLOAD;
/


-- ---------------------------------------------------------------------
-- STEP 5 - ONLY IF check 0b showed the 'load' / 'unload' handler rewrites the reply.
--          The handler must hand P_RESPONSE to the PDA unchanged. A handler shape the
--          PDA app understands (adapt to the existing handler; keep its method and URL):
--
--   DECLARE
--       v_resp VARCHAR2(4000);
--   BEGIN
--       TAGGING_RFID_TO_LOAD(:body_text, v_resp);          -- _UNLOAD for 'unload'
--       OWA_UTIL.MIME_HEADER('application/json', TRUE);
--       HTP.P(JSON_OBJECT(
--           'STATUS'     VALUE CASE WHEN v_resp = '0' OR v_resp LIKE '-%' OR v_resp LIKE '0 %'
--                                   THEN 'E' ELSE 'S' END,
--           'P_RESPONSE' VALUE v_resp));
--   END;
--
--   Notes: read :body_text only once (ORDS empties it after the first read).
--          Do NOT TO_NUMBER(v_resp) - the reply is now text like "2 loaded, 0 rejected".
-- ---------------------------------------------------------------------


-- ---------------------------------------------------------------------
-- STEP 6 - VERIFY after scanning on the PDA: re-run query 0c and check LOAD_FLG / UNLOAD_FLG.
--          The PDA should show e.g. "2 loaded, 0 rejected" and Diagnostics -> API log should
--          show the verdict OK.
-- ROLLBACK PLAN: re-run the backup scripts from STEP 1 (procedures and, if changed, handlers).
--                PDA_RFID_BULK_FLAG can stay - nothing uses it once the originals are restored.
-- ---------------------------------------------------------------------


-- =====================================================================
-- PART B - TAG ISSUE (sub-contract-issue / req-scan). SQL NOT WRITTEN YET.
-- The tables that hold requisitions and issued rolls are not known to the app team,
-- so a fix cannot be written safely. Run these read-only checks and send the output
-- back, or have the backend team apply the same pattern as STEP 2.
-- =====================================================================

-- B1. Current source of the issue procedure (expected: counts tags, saves nothing).
SELECT line, text FROM user_source WHERE name = 'TAGGING_RFID_TO_ROLL' ORDER BY line;

-- B2. Is there already a working "issue roll to sub-contract" procedure, like
--     LOADING_WORK_ORDER_PROS is for loading? Candidates by name:
SELECT object_name, object_type, status
FROM   user_objects
WHERE  object_type IN ('PROCEDURE', 'PACKAGE')
AND    (object_name LIKE '%ISSUE%' OR object_name LIKE '%SUB%CONT%' OR object_name LIKE '%REQ%')
ORDER  BY object_name;

-- B3. Handler source for 'sub-contract-issue' and 'req-scan' is in the 0b output.
--     For req-scan, find where NO_OF_ITEMS = 10 is set and which table holds the
--     requisition lines (REF_CODE_NO, e.g. 'BPKWL-13/IR25060017').

-- What the fix must do once those are known:
--   sub-contract-issue: for each tag, call the working issue procedure (or do the UPDATE),
--     verify it in the table, and reply "<n> issued, <m> rejected - <tag> <reason>; ..."
--     exactly like STEP 2 (the PDA already understands that reply).
--   req-scan: return the real REQUIRED / ISSUED / BALANCE counts for REF_CODE_NO instead
--     of the hard-coded NO_OF_ITEMS = 10.
