# CICS QR Attendance Control - AI Workspace Instructions

**Project**: QR-based attendance system for educational institutions  
**Language**: Java (Android)  
**Build System**: Gradle (Kotlin DSL)  
**Minimum SDK**: Android 23 | **Target SDK**: 34 | **Compile SDK**: 35

---

## Quick Reference

### Build & Run Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (with ProGuard)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run connected device tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

### Project Structure
- **`app/src/main/java/cics/csup/qrattendancecontrol/`** – All Java source code
- **`app/src/main/res/`** – Resources (layouts, drawables, strings, etc.)
- **`app/src/main/AndroidManifest.xml`** – App manifest
- **`gradle/libs.versions.toml`** – Centralized dependency versions
- **`app/build.gradle.kts`** – App-level Gradle configuration

---

## Architecture Overview

### Design Pattern: Activity-Based with Local-First Sync

1. **Offline-First Sync Model**
   - All attendance records are saved immediately to **SQLite** (`AttendanceDBHelper`)
   - When internet is available, records auto-sync to **Firebase Firestore**
   - `NetworkChangeReceiver` monitors connectivity and triggers sync
   - Red/Green indicators in History show sync status

2. **Core Application Flow**
   ```
   MainActivity (Menu)
   ├── CustomScanActivity (QR Scanner)
   ├── RFIDScanActivity (NFC/RFID Scanner) [NEW v6.0]
   ├── HistoryActivity (View/Export/Search)
   ├── AdminActivity (Dashboard + Auth)
   ├── GraphActivity (Analytics)
   ├── LoginActivity (Firebase Auth)
   └── AboutActivity (Info)
   ```

3. **Key Components**

   | Component | Purpose |
   |-----------|---------|
   | **AttendanceRecord** | Data model for a single attendance entry |
   | **AttendanceDBHelper** | SQLite CRUD operations |
   | **AdminCacheDBHelper** | Caches admin data for quick lookups |
   | **AttendanceAdapter** | RecyclerView adapter for history display |
   | **ConfigHelper** | Manages local config + Firebase Remote Config |
   | **AnalyticsManager** | Computes statistics for graphs |
   | **CustomScanActivity** | CameraX + ML Kit QR code reader |
   | **RFIDScanActivity** | NFC tag detection for ID cards |
   | **MyFirebaseMessagingService** | FCM push notifications |

---

## Database Schema

### SQLite Tables

**`attendance` table** (AttendanceDBHelper)
```sql
CREATE TABLE attendance (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id TEXT NOT NULL,
    student_name TEXT NOT NULL,
    section TEXT NOT NULL,
    time_slot TEXT NOT NULL,  -- "Time In AM", "Time In PM", "Time Out AM", "Time Out PM"
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    sync_status INTEGER DEFAULT 0  -- 0=local, 1=synced
);
```

**`admin_cache` table** (AdminCacheDBHelper)
- Caches Firestore data locally for faster queries on admin panel

---

## Firebase Configuration

### Required Services
- **Firebase Authentication** – UID whitelist-based admin login
- **Firestore** – Cloud storage for synced attendance records
- **Remote Config** – Dynamic section list management (JSON format: `{"sections": ["1A", "1B", "2A"]}`)
- **Cloud Messaging (FCM)** – Push notifications for sync events

### Authentication Flow
1. User taps "Admin Panel" on MainActivity
2. Firebase Auth dialog presents login screen
3. UID is verified against whitelisted admins in Firestore
4. On success: access AdminActivity dashboard

### Remote Config Pattern
```json
{
  "sections_list": "1A,1B,2A,2B,3A,3B,4A,4B,5A,5B"
}
```
Changes take effect after app restart.

---

## Scanning Logic & ID Format

### QR Code Format
```
ID_NUMBER|STUDENT_NAME
Example: 2024001|Juan Dela Cruz
```

### NFC/RFID Format (v6.0+)
- Card UID or encoded payload parsed to extract student ID
- Normalized against QR format to prevent duplicate records

### Smart Duplicate Prevention
- Check for duplicate entries within 5 seconds of the same student+section+time_slot
- Prevents accidental multiple scans

---

## Development Conventions

### Code Style
- **Java 17** – Target compatibility for modern features (records, sealed classes possible)
- **Package Structure**: Single package `cics.csup.qrattendancecontrol.*`
- **Naming**: CamelCase for classes, camelCase for methods/variables
- **Comments**: Document non-obvious logic, especially sync/database operations

### Imports & Dependencies
Key libraries in `gradle/libs.versions.toml`:
- `AndroidX Core`, `AppCompat`, `Material Design`
- `CameraX` for video capture
- `Google ML Kit Vision` for QR decoding
- `Firebase Suite` (Auth, Firestore, Remote Config, Messaging)
- `MPAndroidChart` for graph visualization

### ProGuard Rules
- Configured in `app/proguard-rules.pro`
- Keeps Firebase classes, custom models, and reflection-heavy classes

### Manifest Permissions
```xml
<!-- Required in AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.NFC" /> <!-- For RFID/NFC -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

---

## Common Development Tasks

### Adding a New Section to the Admin Panel
1. Update Firebase Remote Config with new section in `sections_list`
2. App fetches config on startup via `ConfigHelper.fetchSections()`
3. Spinner is populated dynamically

### Fixing Sync Issues
- Check `NetworkChangeReceiver` – listens for connectivity
- Verify `AttendanceDBHelper.syncLocalRecords()` is called when online
- Inspect Firestore rules – must allow authenticated writes for synced records
- Log level `Log.d()` throughout network operations for debugging

### Adding New Activity
1. Create class extending `AppCompatActivity`
2. Create layout XML in `app/src/main/res/layout/`
3. Register in `AndroidManifest.xml`
4. Start from existing activity using `startActivity(new Intent(...))`

### Exporting CSV
- Uses Storage Access Framework (SAF) for file picker
- `HistoryActivity` compiles CSV using `AttendanceAdapter` data
- One row per record with headers: `Student ID, Name, Section, Time, Timestamp`

---

## Common Pitfalls & Solutions

| Issue | Cause | Solution |
|-------|-------|----------|
| Duplicate records after scanning | Multiple scans within 5 sec | Check `CustomScanActivity.handleScanResult()` duplicate guard |
| Sync not working | No internet or Firebase rules too strict | Verify `NetworkChangeReceiver`, check Firestore security rules |
| QR not decoding | Poor lighting, invalid format | Validate format: `ID\|NAME`, adjust camera exposure in `CustomScanActivity` |
| Admin login fails | UID not whitelisted | Add UID to Firestore admins collection |
| App crash on NFC intent | Manifest missing NFC permission | Add `<uses-permission android:name="android.permission.NFC" />` |
| ProGuard minification errors | Reflection targets obfuscated | Add `-keep` rules in `proguard-rules.pro` |

---

## Release Build Process

1. **Increment version** in `app/build.gradle.kts`:
   - `versionCode` (integer, unique per release)
   - `versionName` (semantic: "6.1", etc.)

2. **Run release build**:
   ```bash
   ./gradlew assembleRelease
   ```
   - Output: `CICS_QR_Attendance_Control_<version>.apk`
   - ProGuard minification + code shrinking enabled

3. **Test on actual device** before publishing

4. **Sign APK** with keystore (if distributing outside Play Store)

---

## Testing

### Unit Tests
- Located in `app/src/test/`
- Run: `./gradlew test`

### Instrumented Tests (Device/Emulator)
- Located in `app/src/androidTest/`
- Run: `./gradlew connectedAndroidTest`

### Manual Testing Checklist
- [ ] QR scan captures and saves correctly
- [ ] NFC card read recognizes student ID
- [ ] History shows Red (local) and Green (synced) dots correctly
- [ ] Offline mode: records save to SQLite
- [ ] Online sync: records upload to Firestore
- [ ] CSV export generates valid file
- [ ] Admin login validates whitelist
- [ ] Graph analytics calculate correctly

---

## Resources & Documentation

- **README**: [README.md](../../README.md) – End-user and admin guides
- **Gradle Docs**: https://gradle.org/
- **Android Developers**: https://developer.android.com/
- **Firebase Docs**: https://firebase.google.com/docs
- **CameraX Guide**: https://developer.android.com/training/camerax
- **ML Kit**: https://developers.google.com/ml-kit/vision/barcode-scanning

---

## Important Notes for AI Assistants

1. **Respect local-first design**: Always ensure offline functionality is maintained
2. **Firebase permissions**: Ask user to verify Firestore rules allow authenticated operations
3. **RFID vs QR**: Both use same normalization logic – keep ID parsing consistent
4. **Test on API 23+**: The minimum SDK version is 23; avoid newer APIs without fallbacks
5. **ProGuard**: When adding new dependencies, may need corresponding keep rules
6. **Git workflow**: `_upstream_ref/` is a reference branch – main work is in root `app/`

---

Generated: 2026-04-17  
Last Updated: v6.1
