# RFID Roll Tracking App — Implementation Plan

Prepared 2026-10-04 from the only project inputs available:

| Source | What it tells us |
|---|---|
| `RFID_QR_PDA_Technical_Design_Specification.docx` | Target design: 7-step flow, identity model, API standards, offline sync, tests |
| `PDA.postman_collection.json` | The **real, existing** ERP endpoints (Oracle ORDS) |
| Handwritten notes (2 photos) | The client's actual requirements per step |
| 3 videos + 3 screenshots | The **real hardware**, tags, and an existing prototype app |

Where these sources disagree, this plan follows the order: **hardware reality > handwritten notes > existing API > spec**.
Every place where that changes the spec is marked **[DEVIATION]**.

---

## 1. What we are building (one paragraph)

An Android app for a rugged factory tablet that tracks fabric/yarn rolls through a knit-composite factory
(Blue Planet Knit Composite Ltd, per the prototype). Each roll carries an RFID tag. Operators scan QR codes
(request / program / work order) and RFID tags at each step, the app collects the scans in a local list,
and sends them in bulk to the existing Oracle ERP via its ORDS REST API. The ERP is the source of truth.
Step 3 (outside QC) is dropped; Step 6 (QC + heat press + QR sticker) is done in the ERP, not the app.

---

## 2. Facts established from the videos (these override the spec)

### 2.1 Hardware is NFC, not UHF **[DEVIATION — biggest one]**
- Device: 8" rugged Android tablet with a **built-in NFC reader** (the window with the contactless icon).
- Tags: yellow round disc tags, **NXP MIFARE Classic 1K, ISO 14443-3A (13.56 MHz HF)**.
  NFC Tools shows: serial `1E:70:FD:78`, ATQA `0x0004`, SAK `0x08`, technologies `NfcA, MifareClassic, NdefFormatable`.
- A second reader: USB desktop card reader ("Windows" label) plugged into the tablet. It behaves as a
  **keyboard** — it types a **10-digit decimal number** + Enter into whatever field has focus.

Consequences:
- **Read range is ~2–4 cm, one tag at a time.** There is no "batch read 150 tags at once" like UHF.
  In this plan, *"batch scan" = tap tags one after another, the app accumulates a list, then one bulk submit.*
- Loading/unloading 150 rolls = 150 taps (~2–3 s each ≈ 5–8 min per vehicle). Acceptable for go-live;
  if too slow, upgrade path is UHF hardware behind the same `TagReader` interface (§5.2) — no screen rewrite.
- We only need the tag **UID**. We never write to tag memory (avoids MIFARE Classic compatibility problems —
  NFC Tools shows "Mifare Classic 1k – Not Supported" for memory access on this device, but UID read works).

### 2.2 The same tag shows different numbers on different readers
- Built-in NFC gives the UID as bytes → `1E70FD78` (hex).
- USB reader types decimal. `0x1E70FD78` read little-endian = `2029875230`; the USB reader's typed values in
  the video look like `2029967302`, `2029967230` (same format, different tags).
- The number printed on the disc (`0511573368`) is a third format (looks like big-endian decimal, but it does
  not exactly match this tag's UID — must be verified).

**Rule:** the app normalises every read to one **canonical tag ID = uppercase hex UID, big-endian, no separators**
(e.g. `1E70FD78`) before de-duplication or sending to the server. Phase 0 must confirm the conversion with
5 real tags read on both readers.

### 2.3 The system "New tag collected" popup
The Google screenshots are the team researching why Android shows its own NFC popup instead of passing the
tag to the app. **Fix:** use `NfcAdapter.enableReaderMode()` while a scan screen is in the foreground
(flags: `FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK | FLAG_READER_NO_PLATFORM_SOUNDS`), disable it in
`onPause`. No manifest intent filters are needed for in-app scanning.

### 2.4 A prototype app already exists
Screens seen in the video:
1. **Work Order list** — search box "Search Work Order No"; cards like `WO-W261000014 (TEX-PRG-26100136)`,
   Unit `BLUE PLANET KNIT COMPOSITE LTD.`, Location `Barotopa, Rathura, Mawna, Sreepur, Gazipur`.
2. **Tagging screen** — header "Work Order No `WO-W261000002 (TEX-PRG-26100015)`", field
   "RFID Scan: Scan / Enter Code", counter "RFID Tagged Successfully — Tagged: 1 — Remaining / Total".

So the WO ↔ Program link already exists in the ERP (`WO-…` + `TEX-PRG-…`), and a WO-list endpoint exists
that is **not** in the Postman collection. Reuse the prototype's look; rebuild the internals per this plan.

### 2.5 Heat-press risk
Step 6 heat-presses the rolls. The yellow tags must survive the heat press temperature, or be attached
after/away from the pressed area. **Verify with a physical test in Phase 0** — if tags die, the whole Step 7 flow breaks.

---

## 3. Final scope (merging notes + spec)

| Step | Where | What the operator does | Notes from handwritten page |
|---|---|---|---|
| 1. Tag issue | App | Scan request QR (REF_CODE_NO) → see required / issued / **balance** tag count → tap tags → submit | **Mandatory:** total tag count, supplier, issue/program. **Optional:** which exact tags. → App supports both: "Count only" and "Scan tags" modes **[DEVIATION]** |
| 2. Subcontract production | App (tablet) | Pick Work Order / scan Program QR → tap a roll's tag → enter weight → next; submit | Supervisor does production entry + tagging. Weight + RFID saved against the QR, **FIFO** (oldest pending first) |
| 3. Outside QC | — | Not built | "No need" |
| 4. Loading | App | Start loading → tap every roll's tag (pallet by pallet) → review → bulk submit | "Allow multi scan, store temp in tab, send in bulk by API, keep all data in interface" |
| 5. Unloading | App | Select loading/vehicle → tap all tags → bulk submit → see register | Server builds a **register by Supplier, Program, WO** (this *is* the "SOP segregation" the spec left open) |
| 6. QC + heat press + QR | **ERP only** | ERP page: scan roll tag (USB reader on PC) → QC entry → print QR sticker | "Interface will be in ERP". The USB keyboard reader works on the ERP PC too |
| 7. Store delivery | App | Tap rolls → group into a **tube** → tap/scan the tube's own QR/RFID tag → submit | "Same as now" + **"Tube QR / RFID tag → new work"**: tubes get their own physical tag **[DEVIATION]** |
| — | App | Login, home, trace a roll, pending-sync list | From spec |

---

## 4. API plan

### 4.1 Decision: build on the existing ORDS API now, standardise later
The `/api/v1/...` endpoints in the spec do not exist. The ORDS endpoints do. The app is built against ORDS
through a repository layer, so moving to `/api/v1` later is a change in one module, not in screens.

### 4.2 Mapping

| App action | Existing endpoint | Status | Ask backend for |
|---|---|---|---|
| Login | `POST snym_erp/api/pda-login?USER_NAME&USER_PASSWORD` | Exists | Move credentials to JSON body; return a token + user's roles/unit |
| Work Order list | (used by prototype, not in Postman) | Exists? | Add to Postman with a sample response |
| Step 1 lookup | `POST snym_erp/api/req-scan?USER_ID&REF_CODE_NO` | Exists | Sample response: required / issued / balance, supplier, program, `REF_ID` |
| Step 1 save | `POST xact_erp/api/sub-contract-issue` `{REF_ID, USER_ID, ITEMS[]}` | Exists | Confirm `xact_erp` vs `snym_erp` (different schema — probably a mistake); a count-only variant |
| Step 2 lookup | — | **Missing** | Program/WO → supplier, supervisor, eligible tags, pending rolls (FIFO order) |
| Step 2 weight | `PUT snym_erp/api/ref-weight?USER_ID&ITEM&WEIGHT` | Exists (one roll per call) | Bulk version `{USER_ID, ITEMS:[{ITEM, WEIGHT}]}` |
| Step 4 load | `POST snym_erp/api/load` `{USER_ID, ITEMS[]}` | Exists | Vehicle / challan / supplier fields; return a loading ID |
| Step 5 expected list | — | **Missing** | Expected tags for a loading ID (needed for matched/missing/extra) |
| Step 5 unload | `POST snym_erp/api/unload` `{USER_ID, ITEMS[]}` | Exists | Loading ID field; return the register (by Supplier/Program/WO) + missing/extra |
| Step 7 delivery | — | **Missing** | `{USER_ID, STORE_ID, TUBE_TAG, ITEMS[]}` |
| Trace | — | **Missing** | Tag/QR → roll and its history |

### 4.3 Rules for every call (app side, even before backend improves)
- `ITEMS[]` always contains **canonical tag IDs** (§2.2).
- Every bulk submit carries a client-generated `CLIENT_TXN_ID` (UUID). Until the server de-duplicates on it,
  the app prevents double-submits itself: button disabled while sending; on timeout the batch is marked
  "unknown — check before resending" instead of resending automatically.
- Expect per-item results. If the server only returns one overall status, treat the batch as all-or-nothing
  and ask backend for per-item results.
- Base URL + schema (`snym_erp` / `xact_erp`) come from build config, never hard-coded in screens.

### 4.4 Security items (fix before production)
- Traffic is **plain HTTP to a public IP** (`118.179.223.40:7007`). Required: HTTPS, or VPN / factory LAN only.
  For development, allow cleartext for that host only via `network_security_config.xml`.
- The login sends the password in the URL (ends up in server logs). Move it to the request body.
- The Postman collection contains a real user's password → **change that password** and strip it from shared files.
- Server must not trust `USER_ID` sent by the app; derive the user from the login session/token.

---

## 5. Android app design

### 5.1 Stack
| Area | Choice | Why |
|---|---|---|
| Language / UI | **Kotlin + Jetpack Compose (Material 3)** | Spec choice; native NFC reader mode needs no plugin |
| Architecture | MVVM, one feature package per step | Spec §4 |
| Network | Retrofit + OkHttp + kotlinx.serialization | Standard |
| Local storage | **Room** | Scans must survive crash / Wi-Fi loss (spec §11) |
| Background retry | WorkManager | Spec §11 |
| Secure prefs | DataStore + Android Keystore | Token storage |
| QR scanning | CameraX + ML Kit Barcode Scanning; manual entry fallback | Tablet has a camera; no QR scan engine seen |
| DI | Hilt | Keeps scanners/repositories swappable |

> If the existing prototype is Flutter (Flutter is installed on the dev machine), the same design applies with
> `nfc_manager`, `drift`/`sqflite`, `workmanager`, `mobile_scanner`. Pick one stack in Phase 0 and don't mix.

### 5.2 Tag input — one interface, three sources
```kotlin
interface TagReader { val reads: Flow<TagRead> }           // TagRead(canonicalId, source, timestamp)

class NfcReaderModeSource   : TagReader   // built-in NFC, enableReaderMode(), UID bytes -> hex
class KeyboardWedgeSource   : TagReader   // USB reader: catches 10-digit + Enter, decimal -> canonical hex
class (future) UhfSdkSource : TagReader   // only if hardware is upgraded
```
- Screens merge all sources; they never know which reader was used.
- `TagIdNormalizer` is a single unit-tested function (hex ↔ decimal, byte order, trimming).
- Every successful read: beep + vibrate + row added at top. Duplicate: different short tone, row flashes.

### 5.3 Package layout
```
app/
  core/      network/  database/  security/  ui-components/  config/
  scanner/   TagReader, NfcReaderModeSource, KeyboardWedgeSource, QrCameraScanner, TagIdNormalizer
  feature/   auth/  home/  tagissue/  production/  loading/  unloading/  storedelivery/  trace/  sync/
```

### 5.4 Local database (Room)
| Table | Columns |
|---|---|
| `scan_session` | id, type (ISSUE/PRODUCTION/LOAD/UNLOAD/DELIVERY), header ref (REF_CODE_NO / WO / loading ID / tube tag), user, started_at, status |
| `scan_item` | session_id, tag_id (unique per session), weight (nullable), scanned_at, state (LOCAL / SENDING / SAVED / FAILED), error |
| `pending_submit` | client_txn_id, endpoint, payload JSON, attempts, last_error, next_try_at |

A tap is written to Room **before** the UI shows it, so killing the app loses nothing.

### 5.5 Screens
| Screen | Content |
|---|---|
| Login | Username, password, server status; remembers username only |
| Home | Big tiles: Tag Issue, Production, Loading, Unloading, Store Delivery, Trace; badge with unsent count; online/offline indicator |
| Tag Issue (1) | Scan request QR → Supplier / Program / Required / Issued / **Balance** → mode toggle *Count only* / *Scan tags* → big counter "12 / 95" → Submit |
| Production (2) | WO list (reuse prototype) or scan Program QR → header card → tap tag → weight dialog with numeric keypad auto-opens → row added → FIFO hint for next pending roll → Submit |
| Loading (4) | Vehicle no., challan ref, supplier → tap tags → counter + list → Review → Submit |
| Unloading (5) | Pick loading ID / vehicle → Expected count → tap tags → live Matched / Missing / Extra (if expected list available) → Submit → register by Supplier / Program / WO |
| Store Delivery (7) | Store → tap rolls → **tap/scan tube tag** → tube summary (rolls, total kg) → Submit; repeat per tube |
| Trace | Tap tag or scan QR → roll no., weight, current step, history |
| Pending | Unsent / failed batches with reason; Retry; never auto-deleted on logout |

UX rules (from spec §5 + wireframes): large counters readable at arm's length, full-width green / amber / red
result cards (no toasts), sticky Submit button, after submit remove only accepted rows, failed rows stay with reason.

---

## 6. Phased delivery

Estimates assume 1–2 developers and backend help on the missing endpoints. Adjust to your team.

### Phase 0 — Verify before coding (Week 1)
- [ ] Call every Postman endpoint with test data and **save the real responses** as examples in the collection.
- [ ] Read 5 tags on both the built-in NFC and the USB reader; confirm the canonical ID conversion (§2.2).
- [ ] Heat-press a tag; confirm it still reads (§2.5).
- [ ] Time 50 consecutive taps → realistic seconds per roll for loading/unloading.
- [ ] Get answers to the open questions in §8.
- [ ] Decide stack (Kotlin vs existing prototype tech) and get the prototype source code.
- **Exit:** response samples saved, ID rule written, tag survives heat, questions answered.

### Phase 1 — Foundation (Weeks 2–3)
- Project skeleton, Hilt, Room, Retrofit, build flavours (dev / UAT / prod base URLs).
- `TagReader` with NFC reader mode + USB wedge + normaliser (unit tests).
- QR camera scanner + manual entry.
- Login, Home, online/offline banner, Pending screen shell.
- **Exit:** log in, tap a tag on either reader, see the same canonical ID in a test screen.

### Phase 2 — Step 1 and Step 2 (Weeks 4–5)
- Tag Issue: `req-scan` lookup, count-only and scan-tags modes, balance guard, `sub-contract-issue` submit.
- Production: WO list, tag → weight rows, FIFO hint, `ref-weight` submit (loop per row until bulk endpoint exists).
- **Exit:** a request can be tagged and its rolls weighed end-to-end on UAT data.

### Phase 3 — Step 4 and Step 5 (Weeks 6–7)
- Loading session with header fields, tap accumulation, `load` submit.
- Unloading: expected list (when backend ready), live reconciliation, `unload` submit, register view.
- **Exit:** load 30 tags, unload 28 + 1 stranger → app shows 28 matched / 2 missing / 1 extra.

### Phase 4 — Step 7, Trace, ERP Step 6 support (Weeks 8–9)
- Store delivery with tube tags (depends on backend endpoint + tube tag decision).
- Trace screen.
- Support the ERP team: Step 6 page uses the USB reader on the PC with the **same canonical ID rule**.
- **Exit:** a roll can be traced from issue to store delivery by its tag and by its QR sticker.

### Phase 5 — Hardening and rollout (Week 10)
- Offline tests (Wi-Fi off mid-batch, app killed, reboot), double-tap submit, 300-tag batch performance.
- HTTPS / VPN in place, password moved out of URL.
- Release APK signing, install on all tablets, optional kiosk mode (MDM or Android lock-task).
- Short operator guide (1 page per step, with photos).
- **Exit:** all tests in §7 pass on the real tablet with UAT ERP.

---

## 7. Acceptance tests (adapted from spec §16 to NFC hardware)

| # | Scenario | Expected |
|---|---|---|
| 1 | Same tag read on built-in NFC and USB reader | Identical canonical ID |
| 2 | Tap the same tag twice | Counted once; duplicate tone |
| 3 | Tap a tag while system NFC popup would normally appear | No system popup; app receives the tag |
| 4 | Step 1 scan more tags than balance | Blocked at balance |
| 5 | Step 1 count-only mode | Count submitted without tag list |
| 6 | Step 2 row without weight / weight ≤ 0 | Cannot submit |
| 7 | Tag already used elsewhere | That row rejected, others saved |
| 8 | Wi-Fi off after 100 taps, app killed, reopened | All 100 still there; submit works when online |
| 9 | Double-tap Submit | One transaction on server |
| 10 | Unload with 2 missing + 1 extra | Exact counts and lists shown |
| 11 | Store delivery before ERP Step 6 done | Rejected "QC/QR not completed" |
| 12 | Trace by tag and by QR sticker | Same roll, full history |
| 13 | Tag after heat press | Still readable |
| 14 | 300 taps in one session | No lag in list or counter |

---

## 8. Open questions (need answers in Phase 0)

1. Sample responses for each existing endpoint (none are saved in Postman).
2. Is `sub-contract-issue` really under `xact_erp` while everything else is `snym_erp`?
3. Step 1: is the count-only mode enough, or must every tag be scanned at issue?
4. Step 2: what does "FIFO against the QR code" mean exactly — the oldest issued tag must be used first, or the oldest pending roll weighed first?
5. Step 2: is weight typed by hand, or read from a digital scale (Bluetooth/USB)?
6. Step 4: does "pallet by scan" mean pallets have their own tag/ID?
7. Step 7: tube tag — is it a QR sticker, an NFC tag, or both? Who creates it?
8. Are tags reused after store delivery? If yes, when is a tag released?
9. Which tag ID format does the ERP already store (hex / decimal / printed number)?
10. Who builds the missing endpoints, and by when?

---

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Tag-ID format mismatch between readers / ERP | Same roll looks like two rolls | One `TagIdNormalizer`, verified in Phase 0, used by app **and** ERP |
| Tags fail in heat press | Step 7 impossible | Test in Phase 0; change tag type or position |
| One-by-one NFC is too slow for loading | Operator pushback | Measure in Phase 0; UHF upgrade path behind `TagReader` |
| Missing backend endpoints | Steps 2, 5, 7 blocked | Request in week 1; build screens against mock responses meanwhile |
| Plain-HTTP public endpoint | Credentials and data exposed | HTTPS/VPN before production; rotate leaked password |
| Server trusts app-sent `USER_ID` / state | Wrong or forged records | Server-side session + state checks (spec §3.2) |
