# MemPass 🔐 — The Ultimate Secure Vault for Android

**MemPass** is a professional-grade, open-source password manager and secure document vault for Android. Built with a "Security-First" philosophy, it ensures that your most sensitive data never leaves your control.

---

## 🚀 Key Features

### 🛡️ Uncompromising Security
- **Argon2id KDF**: Industry-standard Key Derivation Function (`Iterations: 2`, `Memory: 16MB`, `Parallelism: 1`) to protect against brute-force attacks.
- **Military-Grade Encryption**: Uses **AES-256-GCM** for all stored data, providing both high-level encryption and data integrity verification.
- **Hardware-Backed Protection**: Integration with **Android Keystore** to wrap master keys, ensuring they are protected by the device's Secure Element/TEE.
- **Biometric Authentication**: Seamless access using Fingerprint or Face Unlock via `BiometricPrompt`.
- **Automatic Protection**: 
  - **Auto-Lock on Screen Off**: Instantly clears encryption keys from memory when the screen is turned off.
  - **Background Grace Period**: Configurable 5-minute grace period before the vault auto-locks in the background.
  - **Exponential Lockout**: Failed attempts lead to increasing lockout durations to prevent brute-force (up to 24 hours).

### 📁 Vault Management
- **Password Manager**: Securely store accounts with support for Usernames, Passwords, Notes, and **TOTP (2FA)** secrets.
- **Document Vault & OCR**: Scan physical documents or IDs. Built-in **ML Kit Text Recognition** allows you to search through scanned documents.
- **Secure Notes**: A private space for sensitive text information with a self-destruct feature.
- **Vault Health Dashboard**: Automatically identifies weak, reused, or compromised passwords.

### ☁️ Backup & Automation
- **Encrypted Google Drive Sync**: Automatic or manual backups to your own Drive account. Data is encrypted *before* it leaves the device.
- **Smart Workers**:
  - `DriveBackupWorker`: Daily automated backups.
  - `NoteSelfDestructWorker`: Hourly check to wipe expired sensitive notes.
  - `DocumentExpiryWorker`: Daily check for expiring IDs/Documents.
  - `SyncReminderWorker`: Notifies you if you haven't backed up recently.

---

## 🛠️ Technical Architecture

MemPass is built using modern Android development practices:

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Clean Architecture principles
- **Dependency Injection**: Hilt (Dagger)
- **Database**: Room Persistence Library with encrypted data handling
- **Concurrency**: Kotlin Coroutines & Flow
- **Background Processing**: WorkManager
- **Security**: Argon2Kt, AES-GCM, Android Keystore

---

## 📦 Project Structure

```text
app/
├── src/main/java/com/example/mempass/
│   ├── common/          # Global Constants & Utils
│   ├── di/              # Hilt Modules
│   ├── repository/      # Data layer & Room DAOs
│   ├── ui/              # Compose UI, Themes, & NavGraph
│   ├── workers/         # WorkManager Implementations
│   └── VaultManager.kt  # Core Vault State Management
└── build.gradle.kts     # Dependency & Build Configuration
```

---

## 📥 Getting Started

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or newer
- JDK 17+
- Android Device/Emulator (API 26+)

### Build Instructions
1. Clone the repo: `git clone https://github.com/your-username/mempass.git`
2. Open in Android Studio.
3. Add your `google-services.json` in the `app/` folder (required for Google Drive sync).
4. Sync Gradle and hit **Run**.

---

## 🛡️ Security Disclaimer
This app is provided "as is". While it uses industry-standard encryption, users are responsible for remembering their **Master PIN** and **24-character Recovery Key**. If both are lost, the data is **permanently irrecoverable**.

## 📄 License
Licensed under the **MIT License**. You are free to use, modify, and distribute this software. See [LICENSE](LICENSE) for details.

---
*Created with ❤️ by Anshu. Dedicated to digital privacy.*
