<p align="center">
  <img src="banner.png" alt="CICS QR Attendance Control Banner" />
</p>

<h1 align="center">CICS QR Attendance Control</h1>

<p align="center">
  A simple, offline-ready Android app that records student attendance by scanning QR codes.
</p>

<p align="center">
  <a href="https://github.com/NightCode101/QR_Attendance_Control/releases/latest">
    <img src="https://img.shields.io/badge/Download-APK-blue.svg" alt="Download APK">
  </a>
  <a href="https://github.com/NightCode101/QR_Attendance_Control/releases/latest">
    <img src="https://img.shields.io/badge/status-stable-brightgreen.svg" alt="App Status">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/platform-Android-blue.svg" alt="Platform">
  </a>
  <a href="mailto:baoitjerialle01@gmail.com">
    <img src="https://img.shields.io/badge/contact-email-orange.svg" alt="Contact">
  </a>
</p>

---

## 📱 Features

### 🔐 Admin Panel
- Secure Firebase email/password login
- Multiple admin support via UID checking
- "Remember Me" login toggle
- Access-controlled AdminActivity

### 🧑‍💼 Attendance Management
- QR code scanner for student check-in/out
- Time-in and time-out detection logic
- View and search records by section
- Long-press delete with undo option
- Firebase Firestore sync (admin only)

### 💾 Storage and Export
- Offline-first: all records saved in local SQLite
- Scoped Storage-based CSV export per section
- Automatic file naming with section and date

### 🖌 UI/UX
- Alphabetically sorted section buttons
- Color indicators for selected/unselected buttons
- Custom light theme and branding palette
- Material UI components and spacing

---

## 🧰 Built With

- **Java** (Android SDK)
- **SQLite** (Local Database)
- **Firebase Auth & Firestore** (Admin & Sync)
- **ZXing** (QR Scanner via external intent)
- **Storage Access Framework** (File export)

---

## 📦 APK Download

Click below to grab the latest version:

👉 [**Download APK from Releases**](https://github.com/NightCode101/QR_Attendance_Control/releases/latest)

---

## 🖼 Screenshots

| Main Menu | Attendance History |
|-----------|--------------------|
| ![Main Menu](UI_Main.jpg) | ![History](UI_Attendance_List.jpg) |
| Login Menu | Admin Panel |
|-----------|--------------------|
| ![Main Menu](UI_Login.jpg) | ![History](UI_Admin_Panel.jpg) |

---

## 📧 Contact

For bugs, questions, or feedback:

**Jeylo Baoit**  
📬 [baoitjerialle01@gmail.com](mailto:baoitjerialle01@gmail.com)  
🌐 [Facebook Profile](https://fb.com/jeylo.tangaro)

---

## 📝 License

This project is intended for academic and educational use.  
Please ask permission if you plan to use this in commercial or institutional settings.

---

## 🙌 Contributions

Pull requests and suggestions are welcome.  
Help improve the system by opening an issue or forking the project.
