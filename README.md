# MobAntigravity

One-tap Kotlin launcher for **Google Antigravity 2.0 on Android** running through **AntiGem + Debian PRoot + Termux-X11**.

The app includes a **one-tap guided setup**: one button downloads the AntiGem and Termux-X11 installers, launches Android's install prompts, extracts the helper scripts and `setup.sh` to `Download/MobAntigravity`, opens the AntiGem terminal, and (after a one-time Accessibility toggle) types the setup command itself. It also detects the `Setup complete` output and returns to the launcher automatically. After that, the launcher opens the X11 GUI directly.

Android does not allow an app to silently install another app or execute shell commands inside another app's sandbox, so the two install confirmations and the Accessibility toggle are unavoidable. Everything else is automated — the user never types or pastes anything.

## What is included

- `app/` — Kotlin Android launcher with first-run setup wizard (`com.mobantigravity.launcher`)
- GitHub Release assets `antigem.apk` and `x11.apk` — downloaded by the guided installer at runtime
- `app/src/main/assets/scripts/` — tested runtime scripts extracted to `Download/MobAntigravity/`:
  - `gem` — starts X11, Antigravity and the smart keyboard bridge
  - `gem-ime` / `gem-ime-server` / `gem-ime-inject` — keyboard-on-focus bridge and CDP UI injection
  - `patch_va39.py` — VA39 ARM64 binary patcher
- `app/src/main/assets/setup.sh` — one-command PRoot/Debian/Antigravity installer
- `.github/workflows/build-apk.yml` — GitHub Action that builds and publishes a debug APK

## Download

Latest APK: <https://github.com/hemantkumar30590-tech/Mobantigravity/releases/latest>

## First run on a new phone

1. Install `MobAntigravity-debug.apk`.
2. Open it and tap **1-Tap Install & Setup**.
3. Confirm the two Android install prompts for AntiGem and Termux-X11.
4. When asked, enable **MobAntigravity** in Android Accessibility once — the app then types this command into the AntiGem terminal automatically:
   ```sh
   bash /sdcard/Download/MobAntigravity/setup.sh
   ```
5. The app detects `Setup complete` and shows the launcher. If Accessibility is unavailable, the manual buttons remain as a fallback.
6. From then on, **Open Antigravity GUI** launches directly.

See [docs/DEVICE_SETUP.md](docs/DEVICE_SETUP.md) for the tuned resolution/keyboard settings.

## Build locally

```sh
gradle :app:assembleDebug --no-daemon
```

A stable `debug.keystore` is committed in the project root (alias `androiddebugkey`, passwords `android`) so every GitHub Action build has the same signature and users can update without uninstalling.

## GitHub Action

Every push to `main` (or manual `workflow_dispatch`) builds the APK, uploads it as an artifact, and publishes it to the `latest-apk` release.
