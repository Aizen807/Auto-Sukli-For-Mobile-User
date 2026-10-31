# Auto Sukli for Mobile User

Auto Sukli is an Android fare-matrix calculator for the Diesel N' Steel route setup. Choose the route, pickup, drop-off, passenger types and counts, and payment amount. The app calculates the fare and change; if the change is representable by the available whole-peso denominations, the configured Accessibility Service can tap those targets in order.

## Fare rules included

The current Fare Matrix data includes:

- **Balagtas ↔ Bulakan:** Bagumbayan/San Jose, Matungao, Panginay Guiguinto, Panginay Balagtas, Wawa
- **Guiguinto ↔ Bulakan:** Bagumbayan/San Jose, Matungao, Tuktukan
- **Malolos ↔ Bulakan:** Bagumbayan/San Jose, Maysantol, San Nicolas, Pitpitan, Mambog, Matimbo, Panasahan, Bagna, Atlag, San Juan/Sto. Rosario

Fare calculation applies the same rates to all three route options: minimum 4 units, ₱14 per regular passenger, ₱12 per student or senior passenger, and +₱2 for each unit beyond the minimum. Mixed passenger types are calculated separately. Confirm these route stops and fares against the current official Fare Matrix before using the app for real transactions.

## Download the debug APK from GitHub Actions

1. Open the repository on GitHub: <https://github.com/Aizen807/Auto-Sukli-For-Mobile-User>.
2. Tap **Actions**.
3. Open the newest successful **Build APK** workflow run.
4. Download **auto-sukli-debug-apk** under Artifacts and extract the `.apk` file.
5. Install the APK on your Android phone, allowing installation from that source if Android asks.

The APK is currently published as a workflow artifact, not under **Releases**. Artifacts may expire under GitHub’s retention settings, so download a copy when needed.

## Build locally

Install Android Studio, Android SDK Platform 34, and Java 17. In a terminal at the repository root, run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows, run `gradlew.bat testDebugUnitTest assembleDebug`. The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. A debug APK is for testing; it is not a Play Store release and is not signed with a production key.

## First-time phone setup

1. Install and open the APK.
2. Tap **Enable Accessibility Service**, select **Auto Sukli**, and enable it.
3. Tap **Show / Arrange Targets** and allow the app to display over other apps.
4. On Android 13+, allow notifications so the persistent service notification and its **Stop overlays** action can be shown.
5. Move the ₱50, ₱20, ₱10, ₱5, ₱1, and check targets to the matching positions in the cashier/payment screen.
6. Choose the route, pickup, and drop-off.
7. Enter non-negative whole-number counts for Regular, Student, and Senior passengers. You can combine passenger types, such as 1 regular + 1 student.
8. Enter the payment amount, using at most two decimal places.
9. Review the fare, exact change, and click order. The latest result is saved as trip details change; you can also tap **UPDATE / SAVE SUKLI NOW**.
10. Open the intended cashier/payment screen, confirm the target positions and move the controller away from all target buttons, then tap the blue **▶ Play** button.
11. A three-second countdown gives you time to confirm the screen and cancel with the red **Stop** button. Playback refuses to start if a required denomination target is missing or under the controller.
12. If a denomination target was closed, tap the green **+** button to restore it. Use **Hide** to hide the controller, or **Exit** / the notification’s **Stop overlays** action to stop the service and remove all overlays.

For example, if the fare is ₱35 and the passenger pays ₱50, the app shows ₱15 change and plans the ₱10, ₱5, and check taps.

For ₱85 change, the click order is ₱50 + ₱20 + ₱10 + ₱5, then ✓. Playback hides the targets and makes their windows click-through so they do not intercept taps, then restores them afterward. If Android reports a failed or cancelled tap, Auto Sukli stops rather than continuing to the next denomination.

## Important safety note

The Accessibility Service is configured for gestures and not to retrieve screen contents. It still taps the current screen at the saved coordinates; Auto Sukli does not verify which app is open or what a button does. Enable Accessibility only for Auto Sukli, verify the active screen and each target position before every run, use the countdown and Stop control, and disable the service in Android Settings when finished testing.

## Current limitations

- Automatic targets support only ₱50, ₱20, ₱10, ₱5, and ₱1.
- Exact fractional-peso change is displayed but must be handled manually; the app never rounds it into a whole-peso auto sequence.
- Automatic plans are capped at 50 individual taps. Larger change amounts must be handled manually.
- Fare and route data are hard-coded and should be checked against the current official matrix.
- The standard GitHub Actions workflow creates a debug APK. A signed-release workflow is also included and requires private signing secrets.

## Signed release APK

The repository includes `.github/workflows/release.yml`. Create a private Android keystore and add these GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit the keystore or passwords. Then run **Actions → Release signed APK → Run workflow** with a version tag such as `v1.0.0`. The workflow checks the required secrets, runs the regression tests, builds a release APK, and publishes it under GitHub Releases.
