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
7. Enter the passenger's payment amount.
8. Tap **CALCULATE FARE + AUTO SUKLI**.

For example, if the app calculates ₱35 fare and the passenger pays ₱50, it displays ₱15 change and taps the ₱10, ₱5, and check targets.

The result screen also shows the exact click order. For example, for ₱85 change:

```text
Sukli: ₱85
Status: 50 + 20 + 10 + 5
Auto-click: highest to lowest, then ✓
```

The app calculates this sequence using the available targets in descending order: ₱50, ₱20, ₱10, ₱5, then ₱1. It taps each matching floating target and finally taps the check target.

## Important safety note

Accessibility access allows the app to perform screen gestures. Enable it only for this app and disable it in Android Settings when you are finished testing. The overlay and Accessibility permissions are required for the automatic tapping feature. Verify every target position before using the app for real transactions.

## Current limitations

- The supported denominations are fixed at ₱50, ₱20, ₱10, ₱5, and ₱1.
- The calculator currently applies one passenger type to the entered passenger count. Mixed regular/student/senior groups can be added in a later update.
- The app currently creates a debug APK through GitHub Actions.
- A production release build still needs a private signing key and release configuration.
