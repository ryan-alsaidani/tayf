# طيف · Tayf

Offline-first document scanner for Android (Kotlin + Jetpack Compose). Arabic is the primary language (RTL), with English and French.

## Before the first build
1. Put the launcher icon at `app/src/main/res/drawable/ic_launcher_foreground.png`.
2. Run `bash tools/fetch_assets.sh` once (downloads `ara`/`eng` OCR models and the Noto Naskh Arabic font into `app/src/main/assets`). The GitHub workflow also runs it automatically when files are missing.

## GitHub Actions secrets (optional, for a release-signed APK)
`KEYSTORE_BASE64` (base64 of the .jks file), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Without them the APK is signed with the debug key.

## Versions
AGP 8.9.1 · Gradle 8.11.1 · Kotlin 2.1.0 · KSP 2.1.0-1.0.29 · compileSdk/targetSdk 36 · minSdk 26 · JDK 17.
