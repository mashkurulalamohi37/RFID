# RollTrack — Factory RFID Roll Tracking System

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--36)-green.svg)](https://developer.android.com)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-blue.svg)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Offline--First-orange.svg)](https://developer.android.com/topic/architecture)
[![Target Terminal](https://img.shields.io/badge/Device-Seuic%20CRUISE%20GeP-purple.svg)](http://www.seuic.com)

**RollTrack** is an industrial Android application designed for **Blue Planet Knit Composite Ltd / EXACT IDEA** to track fabric and yarn rolls end-to-end across factory operations.

The application operates on rugged handheld PDA terminals (primarily the **Seuic CRUISE GeP**) as well as standard Android devices. It replaces legacy paper-and-QR-code workflows with **100% direct RFID tag and card scanning**, connecting seamlessly with an on-premise **Oracle ORDS** backend.

---

## 📋 Table of Contents
1. [Key Features](#-key-features)
2. [Target Hardware Specifications](#-target-hardware-specifications)
3. [RFID Scanner Engine](#-rfid-scanner-engine)
4. [Factory Workflows (QR Code $\rightarrow$ RFID)](#-factory-workflows-qr-code--rfid)
5. [Oracle ORDS API Integration](#-oracle-ords-api-integration)
6. [Offline-First Architecture](#-offline-first-architecture)
7. [Repository Structure](#-repository-structure)
8. [Setup & Build Instructions](#-setup--build-instructions)
9. [Demo Mode (Testing Without Live ERP)](#-demo-mode-testing-without-live-erp)

---

## ✨ Key Features

- **100% RFID-Driven Workflows**: Replaced camera QR code scanning across Tag Issue, Production, and Loading/Unloading.
- **Hardware-Integrated Scanner Engine**: Direct capture via native Android NFC Reader Mode, Seuic PDA hardware trigger broadcasts, and benchtop USB RFID readers.
- **Live 3-Way Reconciliation**: Real-time matching during unloading (*Matched*, *Missing*, and *Extra* rolls).
- **Offline-First & Resilient**: Local Room database caching with automated background sync queue. No scans are lost during network dropouts.
- **Bi-directional Tag Normalization**: Converts seamlessly between Little-Endian Decimal (`2029967390`) and Big-Endian Hex (`7901EF78`).
- **One-Handed Industrial UI**: Designed specifically for factory floor operators with high-contrast Material 3 components, audio beeps, and haptic vibration feedback.

---

## 📱 Target Hardware Specifications

Configured and optimized for the factory's target rugged terminal:

| Specification | Hardware Value |
|---|---|
| **Device Model** | Model CRUISE GeP |
| **Serial Number** | `547317bce70fa31c` |
| **Hardware Version** | `110` |
| **Device ID** | `C16260702213` |
| **Function Model** | `2.0M8-16.0-4/64-X3PC(HD)GC13/5` |
| **Operating System** | Android 11 / 12 (Rugged Enterprise) |

---

## 📡 RFID Scanner Engine

The app unifies three distinct hardware scanning channels through a centralized `ScanHub`:

```
                       ┌───────────────────────────────┐
                       │       Industrial Sources      │
                       └──────────────┬────────────────┘
                                      │
     ┌────────────────────────────────┼────────────────────────────────┐
     ▼                                ▼                                ▼
[Native NFC]                 [Seuic Broadcast]                 [USB Wedge Reader]
• Android Reader Mode         • Physical PDA scan keys          • Benchtop / OTG reader
• FLAG_READER_NFC_A           • com.android.server.             • Keyboard emulation
• OS popup suppressed           scannerservice.broadcast        • <50ms keystroke batch
     │                                │                                │
     └────────────────────────────────┼────────────────────────────────┘
                                      ▼
                        ┌───────────────────────────┐
                        │    TagIdNormalizer.kt     │
                        │ Hex (BE) <-> Decimal (LE) │
                        └─────────────┬─────────────┘
                                      ▼
                        ┌───────────────────────────┐
                        │      ScanSession / UI     │
                        │   Audio & Haptic Feedback │
                        └───────────────────────────┘
```

1. **Native NFC Reader Mode (`MainActivity.kt`)**:  
   Runs in foreground with `FLAG_READER_NFC_A | FLAG_READER_SKIP_NDEF_CHECK | FLAG_READER_NO_PLATFORM_SOUNDS` to suppress Android's default "New tag collected" system dialog.
2. **Seuic CRUISE GeP Broadcast Receiver (`PdaScannerReceiver.kt`)**:  
   Intercepts physical scan button triggers via `com.android.server.scannerservice.broadcast`, supporting both String and byte array payload decodings (`scannerdata`, `barcode`, `se_barcode`).
3. **USB Keyboard Wedge (`KeyboardWedge.kt`)**:  
   Captures keystrokes from external desktop USB RFID readers with a 50ms batching threshold.
4. **UID Normalizer (`TagIdNormalizer.kt`)**:  
   Standardizes raw values into Canonical Hex UID and Little-Endian Decimal format expected by Oracle ORDS.

---

## 🏭 Factory Workflows (QR Code $\rightarrow$ RFID)

| Workflow | Old QR Code Workflow | New RFID-First Workflow | ERP REST Endpoint |
|---|---|---|---|
| **Step 1: Tag Issue** | Camera QR scan of requisition sheet | **Tap Requisition RFID Card** to query requisition details; tap roll RFID tags to assign in batch. | `POST snym_erp/api/req-scan`<br>`POST xact_erp/api/sub-contract-issue` |
| **Step 2: Production** | Camera QR scan of work order sheet | **Tap Program / Job-Card RFID** to load program context; tap roll tags to enter and sync weight. | `GET snym_erp/api/program`<br>`PUT snym_erp/api/ref-weight` |
| **Step 4: Loading** | Manual vehicle entry | **Tap Vehicle / Challan RFID Card** to assign vehicle; scan roll tags to build dispatch manifest. | `POST snym_erp/api/load` |
| **Step 5: Unloading** | Camera QR scan of gate pass | **Tap Vehicle / Gate Pass RFID Tag** to pull expected manifest; scan unloaded rolls for **live 3-way reconciliation**. | `GET snym_erp/api/loading-expected`<br>`POST snym_erp/api/unload` |
| **Step 7: Store Delivery** | Manual paperwork | Scan store tube tag + roll tags to confirm warehouse arrival. | `POST snym_erp/api/store-delivery` |
| **Trace Roll** | N/A | **Tap any physical tag** at any time to instantly view its complete historical lifecycle. | `GET snym_erp/api/trace` |

---

## 🌐 Oracle ORDS API Integration

Tested and aligned with the factory's Postman collection (`PDA.postman_collection.json`):

- **Base URL**: `http://118.179.223.40:7007/ords/`
- **Authentication**: `POST snym_erp/api/pda-login` with UTF-8 URL-encoded credentials (handles `#` in passwords like `sumon##1031`).
- **Reference Lookups**: Slashes in codes (e.g. `BPKWL-13/IR25060017`) are properly escaped.
- **Sub-Contract Issue**: Transacts under `xact_erp/api/sub-contract-issue` schema with client transaction idempotency tokens (`CLIENT_TXN_ID`).
- **Network Security**: Configured in `network_security_config.xml` to allow cleartext HTTP traffic to the factory's private IP (`118.179.223.40:7007`).

---

## 🗄️ Offline-First Architecture

```
[ Operator Scan ]
       │
       ▼
[ Room Database ] ──► (Instant UI feedback & local storage)
       │
   [ Network? ]
     ├── Yes ──► [ POST to Oracle ORDS ] ──► [ Mark Synchronized ]
     └── No  ──► [ Pending Queue ] ───────► [ Auto Sync on Reconnect ]
```

- Scans are written to local SQLite Room tables immediately before network dispatch.
- If connectivity drops, items transition to `PendingSubmitEntity` and sync automatically once the device reconnects to Wi-Fi.

---

## 📂 Repository Structure

```
RFID/
├── android-app/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── res/
│   │   │   │   ├── drawable/ (ic_launcher.xml, ic_logo.xml)
│   │   │   │   ├── values/ (strings, themes)
│   │   │   │   └── xml/ (network_security_config.xml)
│   │   │   └── java/com/rfid/rolltrack/
│   │   │       ├── config/        # Device specs & persistent preferences
│   │   │       ├── data/local/    # Room database, DAOs, & entities
│   │   │       ├── data/remote/   # OrdsErpApi, DemoErpApi, HttpTransport
│   │   │       ├── scanner/       # PdaScannerReceiver, KeyboardWedge, TagIdNormalizer
│   │   │       └── ui/            # Jetpack Compose screens (Login, Home, Steps 1-7)
│   │   └── build.gradle.kts
│   ├── docs/                      # Technical specs & operator guides
│   ├── gradle/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── PDA.postman_collection.json    # Complete Oracle ORDS Postman Collection
├── IMPLEMENTATION_PLAN.md         # Implementation blueprint
├── RFID_QR_PDA_Technical_Design_Specification.docx
└── README.md
```

---

## 🚀 Setup & Build Instructions

### Prerequisites
- **JDK 17** or **JDK 21**
- **Android Studio Ladybug (2024.2+)** or newer
- Android SDK Platform **API 36** (minSdkVersion: 26)

### Build Commands
```bash
cd android-app

# Build debug APK
./gradlew assembleDevDebug

# Build optimized release APK
./gradlew assembleDevRelease

# Run unit test suite
./gradlew testDevDebugUnitTest
```

### Installing on Device via ADB
```bash
adb devices
adb install -r -d -g release/RollTrack-1.0.0-dev-release.apk
```

---

## 🧪 Demo Mode (Testing Without Live ERP)

If you are testing outside the factory Wi-Fi network without access to `http://118.179.223.40:7007`:
1. On the **Login Screen**, tap **"Use Demo Mode (Offline Testing)"**.
2. Or tap the **⚙ Server** button at the top right to toggle **Demo Mode**.
3. The app will load mock data allowing you to test all RFID scan actions, roll tagging, and reconciliation offline.

---

## 📄 License & Ownership
Proprietary software developed for **Blue Planet Knit Composite Ltd / EXACT IDEA**. All rights reserved.
