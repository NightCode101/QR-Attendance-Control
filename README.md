<p align="center">
  <img src="banner.png" alt="CICS QR Attendance Control Banner" />
</p>

<h1 align="center">CICS QR Attendance Control</h1>

<p align="center">
  A high-performance, offline-first Android application designed to streamline student attendance using QR technology, real-time analytics, and high-resiliency cloud synchronization.
</p>

<p align="center">
  <a href="https://github.com/NightCode101/QR_Attendance_Control/releases/latest">
    <img src="https://img.shields.io/badge/Download-APK-blue.svg" alt="Download APK">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/platform-Android-green.svg" alt="Platform">
  </a>
  <a href="mailto:jeylodigitals@gmail.com">
    <img src="https://img.shields.io/badge/contact-email-orange.svg" alt="Contact">
  </a>
</p>

---

## 🚀 Key v8.1.0 Updates

### 📋 Smart Attendance Management
- **Automated Filtering:** Attendance history defaults to **Today's** records, sorted alphabetically for efficiency.
- **Quota Saver Technology:** Local caching ensures switching sections or searching in Absent Checker costs **Zero additional Firestore reads**.
- **Enhanced Visibility:** Student IDs are now displayed prominently above names in the attendance list.

### 🛡️ Professional ID Validation
- **Strict Format Enforcement:** All QR and RFID scans require the `YY-NNNNN` format (e.g., `24-06281`).
- **Instant Rejection:** Invalid codes are detected instantly in the camera view.

### 🔄 High-Resiliency Sync Engine
- **Robust Merging:** `SetOptions.merge()` strategy ensures data is saved locally and synced automatically even on slow or unstable networks.

### ✨ Redesigned About Screen
- **Expandable Sections:** Changelog and Testers are now expandable for a cleaner user experience.
- **Visual Identity:** Professional developer profile with a modern layout.

---

## ✨ Core Features

### 📱 For Attendance Taking
- **Dual Scanning Modes:** QR codes or NFC/RFID cards.
- **Instant Offline Storage:** Records save to local SQLite immediately.
- **Cloud Synchronization:** Automatic background upload to Firestore with status indicators.

### 📊 For Administrators
- **Absent Checker:** Masterlist comparison (CSV import) with global ID matching and session filtering.
- **Professional Exports:** Generate attendance and absent reports in CSV format.
- **App Update Checker:** Built-in GitHub integration to check for new releases.

---

## 🧰 Tech Stack

 Component | Technology |
-----------|-----------|
 **Language** | Java 17 (Android SDK) |
 **Database** | SQLite (Local Cache) + Firebase Firestore (Cloud Sync) |
 **Scanning** | CameraX + Google ML Kit (QR), Android NFC APIs (RFID) |
 **Build System** | Gradle (Kotlin DSL) with ProGuard minification |

---

## 📧 Contact

**Jeylo Baoit** 
📬 [jeylodigitals@gmail.com](mailto:jeylodigitals@gmail.com)  
🌐 [Facebook Profile](https://fb.com/stc.primo)

---

_Developed with ❤️ for academic efficiency and data integrity._
