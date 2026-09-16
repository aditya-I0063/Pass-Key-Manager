# Privacy Policy — PassKey: Password Manager

_Last updated: 2026-09-16_

## Summary

Your vault stays on your device. PassKey has no account, no sync and no server of its own. The
app does, however, bundle Google Firebase and Google Play libraries that send diagnostic and
usage data to Google. This document says exactly what does and does not leave your phone.

## What we never collect

PassKey does not collect, transmit or store the passwords, usernames, notes or any other entries
you save. They exist only in an encrypted database on your device.

We cannot read your entries and we cannot recover them for you. There is no server-side copy.

## What the app does send

PassKey includes the following third-party components, all provided by Google:

| Component | What it sends |
|---|---|
| Firebase Crashlytics | Crash stack traces, device model, OS version, app version, a Crashlytics installation identifier |
| Firebase Analytics | App usage events, session and screen information, device and installation identifiers, and an advertising ID (`AD_ID`) |
| Firebase Performance Monitoring | Startup, screen rendering and trace timings |
| Google Play in-app updates | Communicates with the Play Store to check for and install updates |

Because of these components the app requests the `INTERNET` permission, along with
`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `AD_ID` and the Play AdServices attribution permissions.
None of these components has access to your vault contents.

Data handled by these services is processed by Google under
[Google's privacy policy](https://policies.google.com/privacy).

## Autofill

If you make PassKey your autofill service, Android shows it the structure of the form you are
filling - the field types and the app package or web address it belongs to - so that it can
offer matching entries. That happens entirely on the device: nothing about the form, the site or
the entries offered is transmitted, logged or stored.

While the vault is locked, PassKey offers a single "unlock" suggestion and no entry names, so the
suggestion list drawn over another app never reveals which accounts you hold. Saving a login that
autofill captured only works while the vault is unlocked; otherwise the app says so rather than
keeping the credential anywhere.

Autofill is off until you turn it on in Android's settings, and it can be turned off there again
at any time.

## Backups

When you export a backup, the file is encrypted with AES-256-GCM using a key derived from a
password you choose, and written to the location you pick through the system file picker.

That file is then yours to manage. Anyone who obtains **both** the file and its password can read
it. We keep no copy and cannot recover the password for you.

## Device backup

PassKey sets `android:allowBackup="false"` and excludes its data from both Android cloud backup
and device-to-device transfer, so your vault is not uploaded to your Google account.

## Permissions

| Permission | Why |
|---|---|
| `USE_BIOMETRIC` | Unlocking the vault |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` | Required by the Firebase and Google Play libraries above |
| `AD_ID`, AdServices permissions | Merged in by Firebase Analytics |

## Children

PassKey is not directed at children and does not knowingly collect information from them.

## Changes

This policy may be updated. Material changes will be noted in the app's release notes and in this
file's history.

## Contact

yrkkh.cclub@gmail.com
