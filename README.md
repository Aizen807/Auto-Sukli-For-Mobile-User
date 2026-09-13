# Auto Sukli for Mobile User

Auto Sukli is an Android Fare Matrix calculator for the Diesel N' Steel route setup. Choose the route, pickup, drop-off, passenger type, passenger count, and payment amount. The app calculates the fare and change, then uses the configured Accessibility Service to tap the denomination targets automatically.

## Fare rules included

The current Fare Matrix data includes:

- **Balagtas ↔ Bulakan:** Bagumbayan/San Jose, Matungao, Panginay Guiguinto, Panginay Balagtas, Wawa
- **Guiguinto ↔ Bulakan:** Bagumbayan/San Jose, Matungao, Tuktukan
- **Malolos ↔ Bulakan:** Bagumbayan/San Jose, Maysantol, San Nicolas, Pitpitan, Mambog, Matimbo, Panasahan, Bagna, Atlag, San Juan/Sto. Rosario

Fare calculation uses the reference Fare Matrix rules: minimum 4 units, ₱13 per regular passenger, ₱11 per student or senior passenger, and +₱2 for every unit beyond the minimum. The selected passenger type and count are applied to the fare.

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
3. Return to the app and tap **Show / Arrange Targets**.
4. Allow the app to display over other apps.
5. Move the ₱50, ₱20, ₱10, ₱5, ₱1, and check targets to the correct positions in the cashier/payment screen.
6. Choose the route, pickup, drop-off, passenger type, and passenger count.
7. Enter the Regular, Student, and Senior passenger counts. You can use any combination, such as 1 regular + 1 student.
8. Enter the passenger's payment amount.
9. The latest fare and change sequence is automatically saved whenever you change the route, pickup, drop-off, passenger counts, or payment. You can also tap **UPDATE / SAVE SUKLI NOW**.
10. You may now close or leave the main app. The floating controller remains available.
11. Tap the blue **▶ Play** button on the floating controller to start the saved auto-sukli sequence.
12. If all denomination targets were closed, tap the green **+** button to restore them. The controller itself stays visible and cannot be closed by closing denomination targets.

For example, if the app calculates ₱35 fare and the passenger pays ₱50, it displays ₱15 change and taps the ₱10, ₱5, and check targets.

The result screen also shows the exact click order. For example, for ₱85 change:

```text
Sukli: ₱85
Status: 50 + 20 + 10 + 5
Auto-click: highest to lowest, then ✓
```

The app calculates this sequence using the available targets in descending order: ₱50, ₱20, ₱10, ₱5, then ₱1. It temporarily hides the targets while Accessibility playback runs so the overlay windows do not intercept the underlying app's buttons, then restores them after playback.

The floating controller is intentionally persistent: the blue Play button starts the latest saved sequence even when the main app screen is no longer open, the red Stop button cancels playback, the green Plus button restores every closed denomination target, and the lock button prevents accidental target movement or closing while the layout is ready. Use the controller's **Hide** button to remove it normally; reopen Auto Sukli and tap **Show Floating Controller** to bring it back without force-stopping the app.

## Important safety note

Accessibility access allows the app to perform screen gestures. Enable it only for this app and disable it in Android Settings when you are finished testing. The overlay and Accessibility permissions are required for the automatic tapping feature. Verify every target position before using the app for real transactions.

## Current limitations

- The supported denominations are fixed at ₱50, ₱20, ₱10, ₱5, and ₱1.
- Mixed regular, student, and senior passenger counts are supported.
- The app currently creates a debug APK through GitHub Actions.
- A signed-release workflow is included, but it needs the private GitHub Secrets described below.

## Signed release APK

The repository includes `.github/workflows/release.yml`. Before using it, create a private Android keystore and add these GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit the keystore or passwords. Then run **Actions → Release signed APK → Run workflow** with a version tag such as `v1.0.0`. The workflow publishes the signed APK under GitHub Releases.
