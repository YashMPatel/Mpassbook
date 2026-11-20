# Mpassbook

Daily expense and friends ledger built with Capacitor for Android.

## Features

- Track income and expenses by category, description, and date
- Track money given to and received from friends
- Review the overall balance and friends' outstanding amounts
- Filter expense reports by date range and select a display currency
- Export reports to PDF and save or restore JSON backups using Android's file picker
- Store transactions, friend records, and currency settings locally on the device

## Build the Android APK

Requirements: Node.js, Android SDK, and JDK 17 or 21.

```powershell
npm ci
npx cap sync android
Set-Location android
.\gradlew.bat assembleDebug
```

The debug APK is created at `android/app/build/outputs/apk/debug/app-debug.apk`.
