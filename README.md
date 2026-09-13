# Auto Sukli for Mobile User

Auto Sukli displays movable denomination targets and uses an Android Accessibility Service to tap the targets in sequence. The current denominations are **₱50, ₱20, ₱10, ₱5, and ₱1**, followed by the check target.

## Download the APK from GitHub Actions

1. Open the repository on GitHub: <https://github.com/Aizen807/Auto-Sukli-For-Mobile-User>.
2. Tap **Actions**.
3. Open the newest successful **Build APK** workflow run. A successful run has a green check mark.
4. Scroll to **Artifacts** and download **auto-sukli-debug-apk**.
5. Extract the downloaded ZIP file. Inside it is an `.apk` file.
6. Open the APK on your Android phone and allow installation from that source if Android asks.

The APK is not currently published under **Releases**. It is stored as a workflow artifact. Artifacts may expire according to GitHub's retention settings, so download a copy when needed.

## Build the APK locally

Install Android Studio, Android SDK Platform 34, and Java 17. Then open a terminal in the repository folder and run:

```bash
./gradlew assembleDebug
```

On Windows, run:

```bat
gradlew.bat assembleDebug
```

The generated debug APK will be here:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A debug APK is suitable for testing on your own phone. It is not a Play Store release APK and is not signed with a production signing key.

## First-time phone setup

1. Install and open the APK.
2. Tap **Enable Accessibility Service**, select **Auto Sukli**, and enable it.
3. Return to the app and tap **Show Targets**.
4. Allow the app to display over other apps.
5. Move the denomination targets to the positions where the change buttons appear.
6. Enter an amount such as `35` and tap **Give Change**.

The app will tap `20`, `10`, `5`, and then the check target for ₱35. Keep the target positions aligned with the buttons in the payment or cashier app.

## Important safety note

Accessibility access allows the app to perform screen gestures. Enable it only for this app and disable it in Android Settings when you are finished testing. The overlay and Accessibility permissions are required for the automatic tapping feature.

## Current limitations

- The supported denominations are fixed at ₱50, ₱20, ₱10, ₱5, and ₱1.
- The app currently creates a debug APK through GitHub Actions.
- A production release build still needs a private signing key and release configuration.
- Test the tap positions carefully before using the app for real transactions.
