# 🎓 CICS QR Attendance Control v8.1.0

The **"Optimized Management & UI"** Update introduces automated history management, a cleaner expandable "About" experience, and drastic performance improvements for the Absent Checker.

---

## 🆕 What's New in v8.1.0

### 📋 Smart Attendance History
- **Today's Focus**: Automatically filters the history list to show only today's records by default.
- **Alphabetical Sorting**: All attendance logs are now sorted alphabetically by student name.
- **ID Visibility**: Student ID numbers are now displayed prominently above names in each card.

### 📋 Absent Checker Optimization
- **⚡ Quota Saver Technology**: Attendance records are cached locally for the selected date. Switching sections costs **Zero additional Firestore reads**.
- **🔍 Instant Search**: Real-time name and ID search bar within the Absent Checker.
- **🕒 Session Filtering**: View absentees for **AM**, **PM**, or **Both**.
- **✏️ Masterlist Editing**: Long-press to edit student Name, ID, or Section directly from the list.

### ✨ Redesigned About Screen
- **Expandable Sections**: Changelog and Testers sections are now partially collapsed (3 lines) to keep the screen neat. Tap to expand for full details.
- **Visual Improvements**: Integrated a new circular photo style for the developer profile.

---

## 🆕 What's New in v8.0.0

### 🛡️ Strict Student ID Validation
- **Format Enforcement**: All scans now require the `YY-NNNNN` format (e.g., `24-06281`).
- **Instant Feedback**: The scanner provides immediate alerts for malformed codes.

### 🔄 Robust Synchronization
- **High-Resiliency Engine**: "Fire and Forget" merge strategy ensures every scan is safely delivered.

---

## ✨ Core Features
- 📷 QR & RFID/NFC Scanning (Strict Validation)
- 📋 Optimized Absent Checker & Masterlist Tools
- 📁 Zero-Data-Loss Local Storage
- ☁️ High-Resiliency Firestore Sync

---

_Developed with ❤️ by Jeylo Baoit._
