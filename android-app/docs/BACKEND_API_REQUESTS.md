# Backend API requests (for the ERP / ORDS team)

The app works today with the six endpoints in `PDA.postman_collection.json`. This document lists
what is **missing** or **needs changing**, in the same ORDS style (`/ords/<schema>/api/<name>`).

The app already calls the proposed endpoints below. Until they exist it shows
"not available on the ERP yet" (HTTP 404 → handled), so they can be added one at a time.

## 0. Conventions the app understands

Replies are read leniently (case-insensitive keys, `_` ignored, ORDS `{"items":[...]}` wrapper OK),
but please follow this shape so nothing is guessed:

```json
{ "STATUS": "S", "MESSAGE": "43 accepted", "...": "..." }      // success
{ "STATUS": "E", "MESSAGE": "Tag already used" }                // business error (HTTP 200 or 4xx)
```

**Bulk replies — per-item results (strongly requested).** Without them, one bad tag fails the
whole batch on the tablet:

```json
{
  "STATUS": "S",
  "MESSAGE": "2 accepted, 1 rejected",
  "RESULTS": [
    { "ITEM": "2029875230", "STATUS": "S" },
    { "ITEM": "2029967302", "STATUS": "E", "CODE": "RFID_ALREADY_USED", "MESSAGE": "Used on BPKWL-13/IR25060011" }
  ]
}
```

**Tag format.** `ITEMS[]` contains tag numbers in the format chosen in app Settings. Default:
**10-digit decimal exactly as the USB reader types it** (UID little-endian, e.g. `2029875230`),
because the existing prototype captured tags that way. Tell us which format the ERP stores
(open question 9) and we switch with one setting.

**Duplicate protection.** Every write body includes `"CLIENT_TXN_ID": "<uuid>"`.
Please store it (unique) and, if the same id arrives again, return the first reply without writing
again. The tablet resends the same id after a lost reply.

**Users.** Do not trust `USER_ID` from the request body; derive it from the login session
(token/cookie) when available.

## 1. Changes to existing endpoints

| Endpoint | Change requested | Why |
|---|---|---|
| `POST snym_erp/api/pda-login` | Accept `USER_NAME`/`USER_PASSWORD` in a JSON body; reply `{STATUS, USER_ID, FULL_NAME, ROLES}`; HTTPS | Password in the URL ends up in server logs |
| `POST snym_erp/api/req-scan` | Reply `{REF_ID, REF_CODE_NO, SUPPLIER_NAME, PROGRAM_NO, WO_NO, ITEM_NAME, COLOR, TOTAL_TAG, ISSUED_TAG, BALANCE_TAG}` | Tag Issue screen shows required / issued / balance |
| `POST xact_erp/api/sub-contract-issue` | Confirm schema (`xact_erp` vs `snym_erp`). Accept optional `TAG_COUNT` (count-only issue, `ITEMS` empty), `CLIENT_TXN_ID`; per-item `RESULTS` | Handwritten notes: tag count mandatory, exact tags optional |
| `PUT snym_erp/api/ref-weight` | Add a bulk variant `POST snym_erp/api/ref-weight-bulk {USER_ID, PROGRAM_NO, CLIENT_TXN_ID, ITEMS:[{ITEM, WEIGHT}]}` returning `RESULTS` with `ROLL_NO` | Today the tablet makes one call per roll |
| `POST snym_erp/api/load` | Accept `VEHICLE_NO`, `CHALLAN_NO`, `SUPPLIER`, `CLIENT_TXN_ID`; reply `LOADING_REF` + per-item `RESULTS` | Unloading needs a loading reference to reconcile against |
| `POST snym_erp/api/unload` | Accept `LOADING_REF`, `CLIENT_TXN_ID`; reply per-item `RESULTS`, `MISSING[]`, `EXTRA[]`, and `REGISTER[]` grouped by Supplier / Program / WO with roll count and kg | Handwritten notes Step 5: "show in a register of BS by Supplier, Prog, WO" |

## 2. New endpoints

### `GET snym_erp/api/work-orders?USER_ID=&Q=`
Work-order list as in the existing prototype screen.
```json
{ "items": [ { "WO_NO": "WO-W261000014", "PROGRAM_NO": "TEX-PRG-26100136",
  "UNIT": "BLUE PLANET KNIT COMPOSITE LTD.", "LOCATION": "Barotopa, Rathura, Mawna, Sreepur, Gazipur", "SUPPLIER": "..." } ] }
```

### `GET snym_erp/api/program?USER_ID=&CODE=`
`CODE` = program no, WO no, or the program/job-card QR content. Step 2 context.
```json
{ "STATUS": "S", "PROGRAM_NO": "TEX-PRG-26100136", "WO_NO": "WO-W261000014",
  "SUPPLIER": "ABC Knitting", "SUPERVISOR": "Rahim",
  "TAGS": [ { "ITEM": "2029875230", "STATUS": "ISSUED", "ISSUED_AT": "2026-10-01T10:00:00+06:00" } ] }
```
`TAGS` = tags issued to this program in Step 1, **oldest first** (the app uses this order as the FIFO hint).
Status `ISSUED` = not yet weighed.

### `GET snym_erp/api/loading-expected?USER_ID=&LOADING_REF=`
```json
{ "STATUS": "S", "items": [ { "ITEM": "2029875230", "ROLL_NO": "RL-260900001" } ] }
```

### `POST snym_erp/api/store-delivery`
```json
{ "USER_ID": "1", "STORE_CODE": "MAIN", "TUBE_TAG": "2029967302", "CLIENT_TXN_ID": "uuid",
  "ITEMS": ["2029875230", "2029875231"] }
```
Must reject rolls not yet through ERP Step 6 with `CODE: "NOT_READY_FOR_STORE"`.
Reply per-item `RESULTS` plus `TUBE_REF`, roll count, total kg.

### `GET snym_erp/api/trace?USER_ID=&CODE=`
`CODE` = tag, roll no, final roll QR, or tube ref.
```json
{ "STATUS": "S", "ITEM": "2029875230", "ROLL_NO": "RL-260900001", "STATUS_CODE": "GATE_RECEIVED",
  "WEIGHT": 24.5, "QR_TOKEN": "BSR:92837465", "PROGRAM_NO": "...", "SUPPLIER": "...",
  "EVENTS": [ { "TIME": "2026-10-01 10:00", "STEP": "Tag issued", "DETAIL": "BPKWL-13/IR25060017" } ] }
```

## 3. Server rules (from the spec, sections 3.2 and 10.1)

- Check the roll's current state on **every** write: ISSUED → PRODUCED → LOADED → GATE_RECEIVED →
  READY_FOR_STORE (ERP Step 6) → DELIVERED_TO_STORE. Never trust the tablet for this.
- Unique constraint on the active tag number. Row-level locking on request balance.
- One transaction per request; set-based inserts, no commit inside loops.
- Never return SQL errors or stack traces in `MESSAGE`.

## 4. ERP Step 6 page (QC + heat press + QR)

The USB reader on the ERP PC types the same 10-digit number as on the tablet. The ERP page must
store / look up tags in the **same format** as the app sends (section 0), or the roll's RFID and
its new QR will not link.
