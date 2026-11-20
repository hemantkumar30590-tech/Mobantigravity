# Device setup (tuned profile)

## First-run wizard

On first launch, **1-Tap Install & Setup** downloads the installers from GitHub Release assets (`antigem.apk`, `x11.apk`), launches the Android install prompts, extracts `setup.sh` and the helper scripts into `Download/MobAntigravity/`, opens the AntiGem terminal, and types this command automatically after a one-time Accessibility toggle:

```sh
bash /sdcard/Download/MobAntigravity/setup.sh
```

That script installs PRoot/Debian/Antigravity core, copies the fixed scripts into `$PREFIX/bin`, patches the VA39 binary and applies the tuned X11 preferences. The Accessibility service watches only `ai.antigem`, sends the command to the focused terminal input, and marks setup complete when `Setup complete` appears. Android only allows silent installation for system apps, so the two install confirmations are expected; manual buttons remain available as a fallback.

These are the settings used for the tested phone (`1080x2400`, density `480`).

## X11 resolution

Usable screen area excludes the status bar (`107px`) and navigation bar (`132px`):

```text
1080 x (2400 - 107 - 132) = 1080 x 2161
```

Set custom resolution in `ai.antigem.x11`:

```sh
P=com.termux.x11.CHANGE_PREFERENCE
R=ai.antigem.x11/com.termux.x11.LoriePreferences\$Receiver
am broadcast -a $P -n "$R" --es displayResolutionMode custom
am broadcast -a $P -n "$R" --es displayResolutionCustom 1080x2161
am broadcast -a $P -n "$R" --es displayStretch false
am broadcast -a $P -n "$R" --es adjustHeightForEK false
```

## Electron scale

`opt/antigravity/run.sh` must use:

```sh
--force-device-scale-factor=2
```

At scale `2`, the CSS viewport is `540px`, which lets the sidebar stay about `215px` while the chat input remains visible (`~258px`).

## GPU acceleration & software fallback

MobAntigravity automatically selects the best GPU rendering pipeline for the phone:

1. **Hardware Acceleration (Turnip + Zink)**:
   - Activated automatically on Qualcomm Snapdragon devices with Adreno GPU via the native KGSL driver (`/dev/kgsl-3d0`).
   - Uses Mesa Zink (OpenGL over Vulkan) + Turnip driver for hardware-accelerated 2D canvas and WebGL rendering.
2. **Software Fallback (llvmpipe + SwiftShader WebGL)**:
   - Activated automatically on MediaTek (Mali), Exynos, Tensor, or if `/dev/kgsl-3d0` is not accessible.
   - Also acts as a 4-second instant auto-recovery fallback if Turnip + Zink encounters driver-level crashes.
3. **Manual Overrides**:
   - Force hardware acceleration: `gem --gpu`
   - Force software rendering: `gem --software`

## Keyboard bridge (Zero ADB)

MobAntigravity includes a 100% native, zero-ADB keyboard focus bridge:

1. `gem-ime-inject` hooks text-field focus in every Electron/CDP page and sends HTTP requests (`127.0.0.1:8899/ime/on` and `/ime/off`).
2. `gem-ime-server` communicates directly with MobAntigravity's native Accessibility Service (`GemAutoTypeService` on `127.0.0.1:8898`):
   - Inspects active window and soft keyboard (`TYPE_INPUT_METHOD`) state natively.
   - Toggles soft keyboard via `performGlobalAction(GLOBAL_ACTION_BACK)` when X11 is focused.
   - **Requires zero ADB, zero PC setup, and zero wireless debugging**.
3. If Accessibility is temporarily unavailable, it automatically falls back to ADB (`adb shell input keyevent 4`).
4. Body scroll is pinned to `0`, keeping the top bar and sidebar visible when the keyboard opens.

## Launch

```sh
gem
```

Then open **MobAntigravity** and tap **Open Antigravity GUI**.
