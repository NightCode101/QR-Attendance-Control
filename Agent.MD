# Agent.MD - QR Attendance Control

## Project Overview
**QR Attendance Control** is an Android application designed to track student attendance efficiently using QR codes and RFID (NFC) technology. It integrates deeply with Firebase for backend services, providing a robust, scalable, and offline-capable solution.

## Tech Stack
- **Language**: Java (Android)
- **Build System**: Gradle (Kotlin DSL)
- **Database**: 
  - **Local**: SQLite (via `SQLiteOpenHelper`)
  - **Cloud**: Firebase Firestore
- **Authentication**: Firebase Auth & Custom Access Code System
- **Backend Logic**: Firebase Cloud Functions (Node.js/JavaScript)
- **Hardware Integration**:
  - **Camera**: CameraX + Google ML Kit (Barcode Scanning)
  - **NFC**: Android NFC APIs (NDEF reading)
- **Other Services**:
  - **Remote Config**: Dynamic section lists and feature flags
  - **Analytics**: Firebase Analytics
  - **Cloud Messaging**: Push notifications
  - **Ads**: AdMob
  - **Charts**: MPAndroidChart (Feature in development)

## Core Components

### 1. Access Control
The app is gatekept by `AccessCodeActivity`. Users must enter a valid access code which is verified against Firestore via Firebase Functions (`AccessControlManager`). This ensures only authorized devices can use the app.

### 2. Attendance Recording
- **QR Scanning**: `CustomScanActivity` handles QR code capture. It expects data in the format `studentID|studentName`.
- **RFID Scanning**: `RFIDScanActivity` provides a continuous scanning interface for NFC tags (NDEF format).
- **Validation**: `MainActivity` and `AttendanceDBHelper` perform validation to prevent double-scanning or conflicting records for the same day/session.

### 3. Data Flow
1. **Capture**: Scan QR/RFID data.
2. **Local Store**: Save immediately to local SQLite (`attendance_records` table).
3. **Sync**: `NetworkChangeReceiver` triggers `syncUnsyncedRecords` which uploads local data to Firestore.
4. **Cloud Store**: Firestore collection `attendance_records` stores the authoritative copy.

### 4. Admin Panel
- **Access**: Restricted via `LoginActivity`.
- **Functionality**: `AdminActivity` allows viewing all records, filtering by section/date/name, deleting records, and exporting to CSV.

## Key Files & Packages
- `MainActivity.java`: Central dashboard and primary scan handler.
- `AccessControlManager.java`: Handles app access logic and Firebase Function calls.
- `AttendanceDBHelper.java`: Manages the local SQLite attendance database.
- `ConfigHelper.java`: Helper for Firebase Remote Config (e.g., fetching section lists).
- `CustomScanActivity.java`: CameraX + ML Kit implementation.
- `functions/index.js`: Backend logic for access code management.

## Database Schema (Firestore)
- `attendance_records/{docId}`:
  - `studentID`: String
  - `name`: String
  - `date`: String (YYYY-MM-DD)
  - `section`: String
  - `time_in_am`: String (or "-")
  - `time_out_am`: String (or "-")
  - `time_in_pm`: String (or "-")
  - `time_out_pm`: String (or "-")
- `access_codes/{code}`:
  - `status`: "active" | "revoked"
  - `expiresAt`: Timestamp
  - `maxDevices`: Number
  - `activations/`: Subcollection of device IDs

## Development Guidelines
- **Offline First**: Always write to the local database first. Synchronization should be handled as a background/secondary task.
- **Normalization**: Use the normalization methods in `AttendanceDBHelper` and `MainActivity` to ensure data consistency between QR and NFC inputs.
- **Permissions**: Ensure Camera and NFC permissions are handled gracefully.
- **Security**: Sensitive operations (like revoking codes) require admin privileges enforced by Firebase Functions.

## Future Enhancements (Roadmap)
- [ ] Completion of `GraphActivity` for data visualization.
- [ ] Migration to Firebase Data Connect for complex relational queries.
- [ ] Genkit integration for AI-powered insights or automated reporting.
