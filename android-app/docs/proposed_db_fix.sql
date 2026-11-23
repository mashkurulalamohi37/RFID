-- =====================================================================
-- RollTrack PDA: proposed DB fix for Load / Unload
-- NOT YET RUN. Review with the backend team first. Run on the DEV DB only.
-- =====================================================================

-- ---------------------------------------------------------------------
-- STEP 0 - READ-ONLY CHECKS (safe, change nothing)
-- ---------------------------------------------------------------------

-- 0a. Which DB schema is behind the URL alias "snym_erp"?
--     Run while connected as IDEAERP. If a row shows PATTERN = snym_erp, IDEAERP is the right schema.
SELECT parsing_schema, pattern, status FROM user_ords_schemas;

-- 0b. Which code does each PDA endpoint run? (look for TAGGING_RFID_TO_LOAD / _UNLOAD in SOURCE)
SELECT t.uri_template, h.method, h.source
FROM   user_ords_templates t JOIN user_ords_handlers h ON h.template_id = t.id
WHERE  t.uri_template IN ('load', 'unload', 'sub-contract-issue', 'ref-weight', 'req-scan', 'pda-login');

-- 0c. Are our 2 test tags registered as rolls? (Load only works for tags that are
--     already tagged to a roll with production qty: REF_ID_FLG = 1, RF_PROD_QTY not null)
SELECT rf_no, roll_no, ref_id_flg, rf_prod_qty, load_flg, unload_flg
FROM   tex_wo_tube_dtls_rf
WHERE  rf_no IN ('E2806A960000402D1EB05D2F', 'E2806A960000502D1EB05952');

-- ---------------------------------------------------------------------
-- STEP 1 - BACKUP: in Toad, save the current source of both procedures first
--          (Schema Browser -> Procedures -> right-click -> Create Script).
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- STEP 2 - THE FIX: same name, same parameters, same input JSON.
--          Each tag goes through the EXISTING working procedure.
-- ---------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE IDEAERP.TAGGING_RFID_TO_LOAD(
    P_DATA     IN  CLOB,
    P_RESPONSE OUT VARCHAR2
) AS
    v_user_id  NUMBER;
    v_items    JSON_ARRAY_T;
    v_tag      VARCHAR2(100);
    v_out      VARCHAR2(4000);
    v_out2     VARCHAR2(4000);
    v_out3     VARCHAR2(4000);
    v_out4     VARCHAR2(4000);
    v_ok       NUMBER := 0;
    v_bad      NUMBER := 0;
    v_bad_list VARCHAR2(400);
BEGIN
    IF P_DATA IS NULL THEN P_RESPONSE := '0'; RETURN; END IF;
    v_user_id := JSON_VALUE(P_DATA, '$.USER_ID');
    IF v_user_id IS NULL THEN P_RESPONSE := '-1'; RETURN; END IF;
    v_items := JSON_ARRAY_T.PARSE(JSON_QUERY(P_DATA, '$.ITEMS'));
    IF v_items IS NULL THEN P_RESPONSE := '-3'; RETURN; END IF;

    FOR i IN 0 .. v_items.GET_SIZE() - 1 LOOP
        v_tag := REPLACE(v_items.GET(i).TO_STRING(), '"', '');
        LOADING_WORK_ORDER_PROS(v_tag, NULL, v_out, v_out2, v_out3, v_out4);
        IF v_out4 = 'Successful' THEN
            v_ok := v_ok + 1;
        ELSE
            v_bad := v_bad + 1;
            v_bad_list := SUBSTR(v_bad_list || ' ' || v_tag, 1, 400);
        END IF;
    END LOOP;

    COMMIT;
    P_RESPONSE := v_ok || ' loaded, ' || v_bad || ' rejected'
               || CASE WHEN v_bad > 0 THEN ' (not tagged/produced or already loaded):' || v_bad_list END;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        P_RESPONSE := '-9: ' || SUBSTR(SQLERRM, 1, 200);
END TAGGING_RFID_TO_LOAD;
/

CREATE OR REPLACE PROCEDURE IDEAERP.TAGGING_RFID_TO_UNLOAD(
    P_DATA     IN  CLOB,
    P_RESPONSE OUT VARCHAR2
) AS
    v_user_id  NUMBER;
    v_items    JSON_ARRAY_T;
    v_tag      VARCHAR2(100);
    v_out      VARCHAR2(4000);
    v_out2     VARCHAR2(4000);
    v_out3     VARCHAR2(4000);
    v_out4     VARCHAR2(4000);
    v_ok       NUMBER := 0;
    v_bad      NUMBER := 0;
    v_bad_list VARCHAR2(400);
BEGIN
    IF P_DATA IS NULL THEN P_RESPONSE := '0'; RETURN; END IF;
    v_user_id := JSON_VALUE(P_DATA, '$.USER_ID');
    IF v_user_id IS NULL THEN P_RESPONSE := '-1'; RETURN; END IF;
    v_items := JSON_ARRAY_T.PARSE(JSON_QUERY(P_DATA, '$.ITEMS'));
    IF v_items IS NULL THEN P_RESPONSE := '-3'; RETURN; END IF;

    FOR i IN 0 .. v_items.GET_SIZE() - 1 LOOP
        v_tag := REPLACE(v_items.GET(i).TO_STRING(), '"', '');
        UNLOADING_WORK_ORDER_PROS(v_tag, NULL, v_out, v_out2, v_out3, v_out4);
        IF v_out4 = 'Successful' THEN
            v_ok := v_ok + 1;
        ELSE
            v_bad := v_bad + 1;
            v_bad_list := SUBSTR(v_bad_list || ' ' || v_tag, 1, 400);
        END IF;
    END LOOP;

    COMMIT;
    P_RESPONSE := v_ok || ' unloaded, ' || v_bad || ' rejected'
               || CASE WHEN v_bad > 0 THEN ' (not loaded or already unloaded):' || v_bad_list END;
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        P_RESPONSE := '-9: ' || SUBSTR(SQLERRM, 1, 200);
END TAGGING_RFID_TO_UNLOAD;
/

-- ---------------------------------------------------------------------
-- STEP 3 - VERIFY after scanning on the PDA: re-run query 0c and check LOAD_FLG / UNLOAD_FLG = 1.
-- ROLLBACK PLAN: re-run the backup scripts from STEP 1.
-- ---------------------------------------------------------------------
