# RollTrack — Factory RFID Roll Tracking Application

Industrial Android application built for **Blue Planet Knit Composite Ltd / EXACT IDEA**, optimized specifically for rugged industrial handheld terminals including the **Seuic CRUISE GeP**.

Tracks fabric and yarn rolls end-to-end across:
- **Step 1: Tag Issue** (Requisition RFID card tap + roll RFID tagging)
- **Step 2: Production** (Program / Job-card RFID tap + roll weight capture)
- **Step 4: Loading** (Vehicle / Challan RFID tap + loading manifest verification)
- **Step 5: Unloading** (Vehicle / Gate-pass RFID tap + live 3-way reconciliation: Matched / Missing / Extra)
- **Step 7: Store Delivery** & **Trace Roll** (Instant roll history by tapping any tag)

---

## 1. Device Hardware Profile (Target Configuration)

Configured and integrated specifically for the factory's target industrial terminal:
- **Device Model**: `Model CRUISE GeP`
- **Device ID**: `C16260702213`
- **Serial Number**: `547317bce70fa31c`
- **Hardware Version**: `110`
- **Function Model**: `2.0M8-16.0-4/64-X3PC(HD)GC13/5`
- **Operating System**: Android 11 / 12 with Seuic ScanService

### Supported Scanner Inputs:
1. **Direct Native NFC Reader Mode**: `NfcAdapter.enableReaderMode` with `FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK | FLAG_READER_NO_PLATFORM_SOUNDS` (no Android system dialog popup).
2. **Seuic CRUISE GeP Broadcast Receiver**: Captures hardware physical scan button triggers via `com.android.server.scannerservice.broadcast`, `com.seuic.scanner.action.RESULT`, and `com.seuic.scanservice`.
3. **USB Desktop / Benchtop Wedge**: Captures external 13.56 MHz / 125 kHz USB keyboard emulation readers with automatic keystroke aggregation.

---

## 2. Workflows: Complete RFID Integration (QR Code Replaced)

| Workflow | Old QR Code Method | New RFID Workflow | ERP Endpoint |
|---|---|---|---|
| **Step 1: Tag Issue** | Camera QR scan of requisition sheet | **Tap Requisition RFID Card** or enter request number | `POST snym_erp/api/req-scan`<br>`POST xact_erp/api/sub-contract-issue` |
| **Step 2: Production** | Camera QR scan of work order sheet | **Tap Program / Job Card RFID** or select work order | `GET snym_erp/api/program`<br>`PUT snym_erp/api/ref-weight` |
| **Step 4: Loading** | Manual vehicle entry | **Tap Vehicle / Challan RFID Card** to assign vehicle | `POST snym_erp/api/load` |
| **Step 5: Unloading** | Camera QR scan of loading manifest | **Tap Vehicle / Gate Pass RFID Tag** for live reconciliation | `GET snym_erp/api/loading-expected`<br>`POST snym_erp/api/unload` |

---

## 3. Oracle ORDS Backend Integration (`PDA.postman_collection.json`)

All queries and payloads strictly match the factory's production Oracle ORDS service:
- **Base URL**: `http://118.179.223.40:7007/ords/`
- **Authentication**: `snym_erp/api/pda-login` with URL-safe credential encoding (handles passwords with `#` like `sumon##1031`).
- **Requisition Scan**: `snym_erp/api/req-scan` with URL-encoded reference codes (e.g. `BPKWL-13/IR25060017`).
- **Sub-Contract Issue**: `xact_erp/api/sub-contract-issue` under `xact_erp` schema with batch JSON payload.
- **Roll Weights**: `snym_erp/api/ref-weight` saving individual or batch roll weights.
- **Loading**: `snym_erp/api/load` issuing `LOADING_REF` for vehicle trip.
- **Unloading Manifest & Submit**: `snym_erp/api/loading-expected` and `snym_erp/api/unload`.

---

## 4. Building & Running the Project

### Prerequisites:
- JDK 17 or JDK 21 (installer available in `E:\Main Downlaods\OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.msi`)
- Android Studio Ladybug / Meerkat or Android SDK 36

### Build Commands:
```bash
# Day-to-day debug build
./gradlew assembleDevDebug

# Release build with ProGuard & optimizations
./gradlew assembleDevRelease

# Run unit tests
./gradlew testDevDebugUnitTest
```

### Pre-built APKs:
Pre-built binaries are ready in the `release/` directory:
- `release/RollTrack-1.0.0-dev-release.apk`
- `release/RollTrack-release.apk`
