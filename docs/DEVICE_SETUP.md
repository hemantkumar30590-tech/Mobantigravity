# Device setup (tuned profile)

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

## Keyboard bridge

`gem-ime-inject` hooks text-field focus in every CDP page and calls `127.0.0.1:8899/ime/on`. `gem-ime-server` toggles Gboard with `input keyevent 4`, but only when X11 has focus and with a 1-second debounce, so repeated focus events cannot close the X11 activity.

Body scroll is pinned to `0`, keeping the top bar and sidebar visible when the keyboard opens.

## Launch

```sh
gem
```

Then open **MobAntigravity** and tap **Open Antigravity GUI**.
