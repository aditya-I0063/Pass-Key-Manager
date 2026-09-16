<h1 align="center">PassKey - Password Manager</h1>

<p><img src="https://user-images.githubusercontent.com/63164037/187455306-6f717189-daf8-4906-a038-6907bd6b53fb.png"/></p>

<p float="left">
  <img src="https://user-images.githubusercontent.com/63164037/187455514-9a27baad-cff3-4983-b335-e9ec3870c0b6.png" width="19%"/>
  <img src="https://user-images.githubusercontent.com/63164037/187455529-6e092a62-f17a-4134-91fe-31680b196110.png" width="19%"/>
  <img src="https://user-images.githubusercontent.com/63164037/187455544-df969b5a-222e-450d-8513-adf82afa6eae.png" width="19%"/>
  <img src="https://user-images.githubusercontent.com/63164037/187455575-8db975c4-6e6a-4f6e-9569-1bb8dda77a19.png" width="19%"/>
  <img src="https://user-images.githubusercontent.com/63164037/187455589-8174b818-a6ef-4881-bd91-84b5b4e3017c.png" width="19%"/>
</p>

<p align="center">
  <img src="https://user-images.githubusercontent.com/63164037/187455605-b4c64912-f026-4598-aae2-8b829ce6f915.png" width="25%"/>
  <img src="https://user-images.githubusercontent.com/63164037/187455627-aa90e9c3-7317-4427-9ba8-f211568bd2e6.png" width="25%"/>
</p>

## 📜 Description:

Tired of remembering multiple passwords or annoyed of forgetting them?

Pass Key app helps you to store all your logins, passwords, and other private information safe and
secure in an encrypted database.

PassKey - Password Manager does not store your credentials on servers so your passwords are in your
hands, no need to worry.

I created this app being inspired by a
design -> https://www.figma.com/community/file/1116675775484733517

## 🤩 Features:

- Kotlin & Jetpack Compose, Material 3.
- Clean architecture with Kotlin Flows and Dagger-Hilt.
- Encrypted database (Room + SQLCipher).
- Biometric / device-credential unlock, with automatic re-lock on background and idle.
- Screenshot blocking, including dialogs and bottom sheets.
- Clipboard entries marked sensitive and cleared automatically.
- Password generator backed by SecureRandom.
- Vault analysis for weak and reused passwords.
- Password-protected encrypted backup and restore, written wherever you choose.
- Excluded from Android cloud backup and device-to-device transfer.
- Drag & drop reordering, swipe to delete, category separation.
- Dark mode and 17 languages.
- Open source.

## 🔐 Security model:

- The vault is a Room database encrypted with SQLCipher, stored only on the device.
- It is unlocked with device biometrics or the screen lock, and re-locks automatically after a
  configurable idle period.
- Backups are encrypted with AES-256-GCM under a key derived from a password you choose
  (PBKDF2-HMAC-SHA512), written through the system file picker.
- The app performs no network requests of its own and has no server or account.

> **Known limitation, being addressed in 5.7.0:** the database key is currently a single
> build-time constant shared by every install, so the database is encrypted against someone who
> obtains the file alone, and not against someone who also has the APK. 5.7.0 replaces it with a
> per-install random key wrapped by an Android Keystore key and unlocked via a biometric
> `CryptoObject`, with a password-based recovery slot.

## 🌐 Network and privacy:

The app itself makes no network calls, but it bundles Google Firebase (Crashlytics, Analytics,
Performance Monitoring) and Google Play in-app updates. These require the `INTERNET` permission
and send crash diagnostics, usage events, performance traces and device/installation identifiers
— including an advertising ID — to Google. They never have access to vault contents. See
[Privacy Policy.md](Privacy%20Policy.md).


## 🌎 Released Android Application:

https://play.google.com/store/apps/details?id=com.bhardwaj.passkey

## 📽 Sample Demo:

https://user-images.githubusercontent.com/63164037/187469596-29dd8287-50d8-4f6f-98a7-b5bbec1b8fd0.mp4

## 🧪 Steps to Build locally:

- Open Android Studio IDE.
- Clone with `git clone https://github.com/aditya-190/Pass-Key-Manager`.
- Select whether to run on Android Emulator or Physical Device connected with USB.

## 💥 How to Contribute?

[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)
[![Open Source Love svg2](https://badges.frapsoft.com/os/v2/open-source.svg?v=103)](https://github.com/ellerbrock/open-source-badges/)

- Take a look at the Existing [Issues](https://github.com/aditya-190/Pass-Key-Manager/issues) or
  create your own Issues!
- Wait for the Issue to be assigned to you after which you can start working on it.
- Fork the Repo and create a Branch for any Issue that you are working upon.
- Create a Pull Request which will be promptly reviewed and suggestions would be added to improve
  it.
- Add Screenshots to help me know what this Code is all about.

## 👦 Developed By:

<h2 align="center">Aditya Bhardwaj</h2>
<p align="center">
  <a href="https://github.com/aditya-190"><img src="https://avatars.githubusercontent.com/u/63164037?v=4" width=150px height=150px /></a> 

<p align="center">
  <a target="_blank"href="https://www.linkedin.com/in/adi-bhardwaj/"><img src="https://img.shields.io/badge/linkedin-%230077B5.svg?&style=for-the-badge&logo=linkedin&logoColor=white" /></a>&nbsp;&nbsp;&nbsp;
  <a href="mailto:aadi.bbhardwaj@gmail.com?subject=Hello%20Aditya,%20From%20Github"><img src="https://img.shields.io/badge/gmail-%23D14836.svg?&style=for-the-badge&logo=gmail&logoColor=white" /></a>
