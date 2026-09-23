# Quietline

Press volume-down in a set pattern and your Android phone starts recording audio, quietly.

- **Trigger:** 3 quick presses of volume-down (adjustable 2–6), or press-and-hold (1–6 s).
- **Feedback:** one buzz = recording started, two = stopped, three = failed.
- **Recording:** AAC audio (.m4a), saved in app-private storage. Stops automatically after 60 min by default.
- **The key press is never swallowed:** volume still changes normally, so nothing looks different.
- **Stopping:** in the app, from the notification's Stop action, or (optional) repeating the pattern.

## Project layout

```
src/                         React UI (Vite)
  native.js                  bridge to the native plugin + a browser mock for previewing
plugins/quietline-trigger/   Local Capacitor plugin (Java)
  android/src/main/java/...  
    VolumeTriggerService     Accessibility service that watches ONLY volume-down
    RecordingService         Foreground service (type: microphone) that records
    EmergencyTriggerPlugin   What the UI calls: status, settings, recordings list, share/delete
.github/workflows/           Builds the APK in the cloud on every push to main
```

`android/` is not committed. The workflow generates it each run with `npx cap add android`.

## Getting an APK (no laptop needed)

1. Create a new GitHub repo and upload these files (GitHub's web "Add file → Upload files" works on a tablet).
2. Open the **Actions** tab. The "Build Android APK" workflow runs on each push to `main`, or run it by hand with **Run workflow**.
3. When it finishes, open the run and download **quietline-debug-apk** under Artifacts. Unzip it to get `app-debug.apk`.
4. Install it on the phone (allow "Install unknown apps" for your browser or file manager).

## Setting up on the phone

1. Open Quietline → **Allow** microphone and notifications.
2. **Open settings** → Accessibility → Installed/Downloaded apps → *Quietline emergency trigger* → On.
   On Android 13+ sideloaded apps may show "Restricted setting". Fix: Settings → Apps → Quietline → ⋮ menu → *Allow restricted settings*, then try again.
3. Turn off battery optimisation for Quietline (Settings → Apps → Quietline → Battery → Unrestricted). Samsung, Xiaomi, Oppo and Vivo kill background services aggressively otherwise.
4. Test: lock the phone with the screen still on and press the pattern.

## Known limits (be honest with users about these)

- **Screen fully off:** on many phones, Android doesn't deliver volume presses to apps when the screen is off and nothing is playing. It works reliably with the screen on, including on the lock screen. Test on each target device. Fix for a later version: hold a silent media session so volume keys stay routable with the screen off.
- **Background start rules:** the recording is started from the accessibility service, which Android allows to use the microphone in the background. If a specific phone blocks it, the app shows the error and you'll feel three buzzes.
- **iOS:** not possible. iOS doesn't give apps access to hardware volume buttons in the background.

## Before the Play Store

- Move to the newest Capacitor and target SDK Google currently requires (update `@capacitor/*` in package.json).
- **Accessibility API declaration:** in Play Console, declare the use as a personal-safety feature, `isAccessibilityTool=false` (already set), and show a prominent in-app disclosure before sending the user to Accessibility settings.
- **Foreground service declaration:** microphone type, with a short video showing the trigger.
- **Privacy policy:** audio is recorded on-device, stored privately, only leaves the phone when the user shares it.
- Build a signed release (`./gradlew bundleRelease` with a keystore stored in GitHub Secrets).

## Previewing the UI in a browser

```
npm install
npm run dev
```

In a browser the native calls are simulated, so you can check layout and flows.

## Renaming

Change `appId` / `appName` in `capacitor.config.json`. The plugin's Java package (`com.myfixxer.quietline.trigger`) can stay as is.
