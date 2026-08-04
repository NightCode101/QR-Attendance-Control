# 🎓 CICS QR Attendance Control v8.2.0

The **"Stability & Data Integrity"** Update introduces server-side protection for attendance records and critical crash fixes for administrative tools.

---

## 🆕 What's New in v8.2.0

### 🛡️ First-Scan Protection (Zero-Read Cost)
- **Data Preservation**: Implemented server-side security rules that prevent existing attendance data from being overwritten. The **first scan** to reach the cloud is now permanent and cannot be replaced by subsequent scans for the same time slot.
- **Quota Optimized**: This protection is enforced at the database level, meaning it consumes **Zero extra Firestore Reads**, staying well within your daily quota limits.

### 📋 Absent Checker Stability
- **Crash Fixes**: Optimized the long-press editing flow in the Absent Checker. Fixed issues where the app would close unexpectedly when updating or deleting student information.
- **Improved Responsiveness**: Enhanced state management to ensure smooth transitions during cloud synchronization.

### 🛡️ Flexible Student ID Validation (v8.1.1)
- **Broader Support**: Officially supports `XX-XXXXX`, `XX-XXXX`, `XXX-XXXXX`, and `XXX-XXXX` formats for all scanning generations.

---

## 🆕 What's New in v8.1.0

### 📋 Absent Checker Optimization
- **⚡ Quota Saver Technology**: Attendance records are cached locally for the selected date. Switching sections or searching costs **Zero additional Firestore reads**.
- **🔍 Instant Search**: Real-time name and ID search bar within the Absent Checker.

---

## ✨ Core Features
- 📷 QR & RFID/NFC Scanning (Flexible Validation)
- 📋 Optimized Absent Checker & Masterlist Tools
- 📁 Zero-Data-Loss Local Storage
- ☁️ High-Resiliency Firestore Sync (Protected Updates)

---

_Developed with ❤️ by Jeylo Baoit._
