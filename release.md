# 🎓 CICS QR Attendance Control v8.0.0

The **"Validation & Reliability"** Fresh Launch introduces strict Student ID verification, a powerful absence detection module, and a modernized synchronization engine for professional-grade attendance management.

---

## 🆕 What's New in v8.0.0

### 🛡️ Strict Student ID Validation
- **Format Enforcement**: All scans now require the `YY-NNNNN` format (e.g., `24-06281`).
- **Instant Feedback**: The scanner provides immediate alerts for malformed codes, allowing for instant retries.

### 📋 Absent Checker Module
- **Global Matching**: Automatically identifies missing students by checking StudentIDs across all sections for the day—perfect for irregular students.
- **Masterlist Management**: Simple CSV import/export with overwrite protection and semestral wipe support.

### 🔄 Robust Synchronization
- **High-Resiliency Engine**: Replaced transactions with a "Fire and Forget" merge strategy to fix syncing on slow or intermittent networks.
- **Smart Merging**: Multi-device support ensures scans from different phones never overwrite each other.

### 🚀 Smart Tools & UI
- **Auto-Updates**: Check for new app versions directly from the menu and download from GitHub with one tap.
- **Admin Optimization**: Dashboard now defaults to Today's records for instant monitoring.
- **Interactive Messaging**: Real-time admin alerts via Firebase In-App Messaging.

---

## ✨ Core Features
- 📷 QR & RFID/NFC Scanning (Strictly Validated)
- 📁 Offline-First Local Storage (Zero Data Loss)
- ☁️ Robust Cloud Sync
- 📤 Professional CSV Reporting (ID-First Format)

---

_Developed with ❤️ by Jeylo Baoit._
