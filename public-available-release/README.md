# NEXO – Public Available Release

Central distribution folder for verified, signed public Android APK releases of **NEXO** by **RK Animation Studios**.

---

## Latest Release Information

- **Latest Version:** `v0.1` (Pending first signed GitHub Release)
- **Latest Build Status:** `AWAITING FIRST SIGNED RELEASE` ([Build Details](./BUILD_STATUS.md))
- **Application ID:** `com.aistudio.nexo.btr`
- **Release Binary Names:**
  - Latest Public APK: `NEXO-Public-Latest.apk`
  - Versioned Archive APK: `NEXO-v<version>-Public.apk` (e.g., `NEXO-v0.1.0-Public.apk`)

> Once a GitHub Release (e.g. `v0.1.0`) is published with signing secrets configured, GitHub Actions automatically verifies the signed release APK, attaches both `NEXO-v<version>-Public.apk` and `NEXO-Public-Latest.apk` to the GitHub Release, and updates this folder and direct download links below.

---

## Direct Download Links

- **Latest Public APK (`NEXO-Public-Latest.apk`):**
  - `https://github.com/<owner>/<repo>/releases/latest/download/NEXO-Public-Latest.apk` *(automatically populated with your repository URL on first release run)*
- **Release Notes:** [RELEASE_NOTES.md](./RELEASE_NOTES.md)
- **Version Metadata:** [version.json](./version.json)

---

## Supported Device & Runtime Requirements

Derived directly from `app/build.gradle.kts` and `AndroidManifest.xml`:

- **Minimum Supported Android Version:** Android 7.0 Nougat (`minSdk = 24`)
- **Target / Compiled Android SDK:** Android 16 (`targetSdk = 36`, `compileSdk = 36`)
- **Orientation:** Landscape Only (`sensorLandscape`)
- **Connectivity:** Online-only (active internet connection required)
- **Initial Launch Regions:** Netherlands (`NL`) & Indonesia (`ID`)

---

## How to Download & Install the APK

1. **Download** `NEXO-Public-Latest.apk` from the direct GitHub Releases link above (or from this folder once populated by the release workflow).
2. **Enable APK Installation on Android:**
   - Open the downloaded `.apk` file from your browser downloads or Android Files app.
   - If prompted, tap **Settings** and enable **Allow from this source** for your browser or file manager.
3. **Install & Launch:**
   - Tap **Install**, then launch **NEXO** with an active internet connection.

---

## Preserved Release History

| Release Tag | Version | APK Filename | Size | Download |
| :--- | :--- | :--- | :--- | :--- |
| *Pending* | `0.1` (`1`) | `NEXO-Public-Latest.apk` | — | *Awaiting first published GitHub Release* |
