# Auto Sukli for Mobile User

Auto Sukli helps calculate a trip fare and change, then can tap the matching denomination buttons on the current Android screen. It combines a route-based fare calculator with draggable floating controls, so you can edit a trip while keeping the cashier or payment app visible.

> **Important:** Automatic tapping uses saved screen coordinates. Auto Sukli does not check which app is open, read screen contents, or confirm what a button does. Always verify the active screen and target positions before playback.

## Features

- **Route and stop selection:** Choose Balagtas, Guiguinto, or Malolos, then set pickup and drop-off stops.
- **Tap-based passenger counters:** Add or remove Regular, Student, and Senior passengers independently; mixed passenger groups are supported.
- **Fare and change calculator:** The current fare rules use a ₱14 regular base and a ₱12 student/senior base, with ₱2 added per route unit beyond the four-unit minimum. The app shows the fare breakdown, amount paid, exact change, and planned tap sequence.
- **Quick payment amounts:** Enter a payment amount or use the ₱20, ₱50, and ₱100 presets. Amounts may include up to two decimal places.
- **Automatic denomination sequence:** For supported whole-peso change, Auto Sukli taps the saved ₱50, ₱20, ₱10, ₱5, and ₱1 targets in order, followed by the check target.
- **Floating controller:** Start playback, stop it, edit the trip, restore targets, lock or unlock target movement, hide or show denomination targets, cycle their size, hide the controller, or exit and remove the overlays.
- **Floating Quick Edit:** Open the draggable editor over the current app. Its route, stop, passenger, and payment fields share and save the trip details automatically. The fields scroll while **Save & play change sequence** stays pinned at the bottom.
- **Playback safety controls:** A three-second countdown gives you time to check the screen. Missing or obstructed targets prevent playback from starting; use the controller’s **Stop** button to cancel an active sequence.

## Routes and fare rules

The app currently includes these route stops:

- **Balagtas ↔ Bulakan:** Bagumbayan/San Jose, Matungao, Panginay Guiguinto, Panginay Balagtas, Wawa
- **Guiguinto ↔ Bulakan:** Bagumbayan/San Jose, Matungao, Tuktukan
- **Malolos ↔ Bulakan:** Bagumbayan/San Jose, Maysantol, San Nicolas, Pitpitan, Mambog, Matimbo, Panasahan, Bagna, Atlag, San Juan/Sto. Rosario

The same fare calculation is used for all three routes. Student and Senior passengers use the reduced fare, and their totals are calculated separately from Regular passengers. Route and fare data are built into the app; verify them against the current official Fare Matrix before relying on them for real transactions.

## Download the debug APK from GitHub Actions

1. Open the [Auto Sukli repository](https://github.com/Aizen807/Auto-Sukli-For-Mobile-User).
2. Tap **Actions**.
3. Open the newest successful **Build APK** workflow run.
4. Download **auto-sukli-debug-apk** under Artifacts and extract the `.apk` file.
5. Install the APK on your Android phone, allowing installation from that source if Android asks.

The APK is currently published as a workflow artifact, not under **Releases**. Artifacts may expire under GitHub’s retention settings, so download a copy when needed.

## Set up and use on your phone

1. Install and open the APK.
2. Tap **Enable Accessibility Service**, select **Auto Sukli**, and enable it.
3. Tap **Show / Arrange Targets** and allow the app to display over other apps. On Android 13 or later, allow notifications so the active service notification and its **Stop overlays** action are available.
4. Move the ₱50, ₱20, ₱10, ₱5, ₱1, and check targets onto the matching buttons in the cashier/payment screen. Use the controller’s size control if needed, and lock targets once they are positioned.
5. Select the route, pickup, and drop-off. Use **+** and **−** for the Regular, Student, and Senior counts; you can combine passenger types.
6. Enter the amount paid or tap a quick amount. Check the displayed fare, change, and click order.
7. For edits without leaving the cashier screen, tap **Edit** on the floating controller. Drag the Quick Edit panel by its top bar; scroll through the fields as needed. Changes save as you make them. Choose **Save & play change sequence** to start playback, or close the panel and use the controller’s **Play** button.
8. Confirm that the intended cashier/payment screen is still open. Wait through the three-second countdown, or tap **Stop** to cancel. Auto Sukli hides the targets and makes them click-through during playback, then restores them afterward.
9. If you closed a target, tap **Restore**. Use **Hide targets / Show targets** to toggle the denomination targets. **Hide** hides the controller; reopen it from the app’s controller button. **Exit** or the notification’s **Stop overlays** action stops the service and removes the overlays.

For example, a ₱35 fare paid with ₱50 produces ₱15 change and the sequence ₱10, ₱5, then ✓. For ₱85 change, the sequence is ₱50, ₱20, ₱10, ₱5, then ✓.

## Safety and limitations

- Accessibility is used to perform taps, not to retrieve screen contents. The app does not identify the foreground app or verify that the saved coordinates still point to the correct buttons.
- Check the open screen and every target position before each run. Keep the controller’s **Stop** button accessible and use the countdown to cancel if anything looks wrong.
- Automatic targets support only ₱50, ₱20, ₱10, ₱5, and ₱1. Fractional-peso change is displayed but must be handled manually; it is never rounded into an automatic sequence.
- Automatic denomination plans are limited to 50 denomination taps, followed by the check tap. Larger change amounts must be handled manually.
- If Android reports a failed or cancelled tap, Auto Sukli stops instead of continuing to the next denomination.
- The standard GitHub Actions workflow builds a debug APK for testing, not a Play Store release. A signed-release workflow is also included and requires private signing secrets.

## Build locally

Install Android Studio, Android SDK Platform 34, and Java 17. From a terminal at the repository root, run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows, run `gradlew.bat testDebugUnitTest assembleDebug`. The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Build a signed release APK

The repository includes `.github/workflows/release.yml`. Create a private Android keystore and add these GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit the keystore or passwords. Then run **Actions → Release signed APK → Run workflow** with a version tag such as `v1.0.0`. The workflow checks the required secrets, runs the regression tests, builds a release APK, and publishes it under GitHub Releases.
