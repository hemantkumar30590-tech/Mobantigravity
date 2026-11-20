#!/data/data/ai.antigem/files/usr/bin/env bash
# MobAntigravity guided setup - run this once inside the AntiGem terminal.
set -e

SRC="/sdcard/Download/MobAntigravity"
PREFIX="${PREFIX:-/data/user/0/ai.antigem/files/usr}"

echo ""
echo "=== MobAntigravity setup ==="
echo "1/5  Installing host packages..."
pkg update -y >/dev/null 2>&1 || true
pkg install -y proot-distro curl tar python xdotool >/dev/null 2>&1

echo "2/5  Preparing Debian PRoot container..."
if ! proot-distro list 2>/dev/null | grep -q '^debian'; then
    proot-distro install debian
else
    echo "     Debian already installed."
fi

echo "3/5  Installing Antigravity core..."
curl -sL https://raw.githubusercontent.com/tr1xx-tech/Antigravity-Mobile/main/install.sh | bash

echo "4/5  Installing fixed MobAntigravity scripts..."
mkdir -p "$PREFIX/bin"
cp "$SRC"/scripts/* "$PREFIX/bin/"
chmod +x "$PREFIX/bin/gem" "$PREFIX/bin/gem-ime" "$PREFIX/bin/gem-ime-inject" \
         "$PREFIX/bin/gem-ime-server" "$PREFIX/bin/patch_va39.py"

RUNSH="$PREFIX/var/lib/proot-distro/containers/debian/rootfs/opt/antigravity/run.sh"
if [ -f "$RUNSH" ]; then
    sed -i 's/force-device-scale-factor=[0-9.]*/force-device-scale-factor=2/g' "$RUNSH"
    python3 - "$RUNSH" <<'PY'
import sys
from pathlib import Path
import re

p = Path(sys.argv[1])
s = p.read_text()

marker = 'echo "Xft.dpi: 288" | xrdb -merge 2>/dev/null || true\n'
if marker in s:
    header = s.split(marker)[0] + marker
else:
    match = re.search(r'(#.*(?:Hardware Acceleration|WebGL|GPU)|export GALLIUM_DRIVER|export LIBGL_ALWAYS_SOFTWARE)', s)
    header = s[:match.start()] if match else s

gpu_block = '''
# --- GPU Configuration: Turnip + Zink Hardware Acceleration with Auto-Fallback to llvmpipe ---
GPU_MODE="auto"
DEBUG_MODE=0
ARGS=()
for arg in "$@"; do
    if [ "$arg" == "--debug" ]; then
        DEBUG_MODE=1
    elif [ "$arg" == "--software" ] || [ "$arg" == "--llvmpipe" ]; then
        GPU_MODE="software"
    elif [ "$arg" == "--gpu" ] || [ "$arg" == "--turnip" ] || [ "$arg" == "--zink" ]; then
        GPU_MODE="gpu"
    else
        ARGS+=("$arg")
    fi
done

# Ensure Mesa shader cache directory exists
mkdir -p /sdcard/.gem/mesa_shader_cache 2>/dev/null || true
export MESA_SHADER_CACHE_DIR=/sdcard/.gem/mesa_shader_cache
export MESA_DISK_CACHE_SINGLE_FILE=1
export __GL_SHADER_DISK_CACHE_PATH=/sdcard/.gem/mesa_shader_cache
export __GL_SHADER_DISK_CACHE=1
export vblank_mode=0

setup_gpu_profile() {
    local target="$1"
    if [ "$target" == "gpu" ]; then
        echo -e "\033[1;32m[GPU]\033[0m Activating Hardware Acceleration (Turnip + Zink / Adreno KGSL)"
        unset LIBGL_ALWAYS_SOFTWARE
        export GALLIUM_DRIVER=zink
        export MESA_LOADER_DRIVER_OVERRIDE=zink
        export TU_DEBUG=sysmem,noconform
        export ZINK_DESCRIPTORS=lazy
        export MESA_VK_WSI_PRESENT_MODE=mailbox
        export MESA_VK_IGNORE_EXTENSIONS="VK_EXT_calibrated_timestamps VK_KHR_calibrated_timestamps"
        export MESA_GL_VERSION_OVERRIDE=4.6COMPAT
        export MESA_GLES_VERSION_OVERRIDE=3.2
        export MESA_GLTHREAD=true
        GPU_ARGS="--ignore-gpu-blocklist --disable-vulkan --enable-gpu-rasterization --enable-oop-rasterization --canvas-oop-rasterization --gpu-rasterization-msaa-sample-count=0 --enable-zero-copy --use-gl=angle --use-angle=gl --enable-webgl --enable-accelerated-2d-canvas --num-raster-threads=8 --start-maximized --disable-gpu-sandbox --no-sandbox --force-device-scale-factor=2 --enable-smooth-scrolling --password-store=gnome-libsecret"
    else
        echo -e "\033[1;33m[GPU]\033[0m Activating Software Fallback (llvmpipe + SwiftShader WebGL)"
        export LIBGL_ALWAYS_SOFTWARE=1
        export GALLIUM_DRIVER=llvmpipe
        export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
        export MESA_GL_VERSION_OVERRIDE=4.5
        export MESA_GLES_VERSION_OVERRIDE=3.2
        unset TU_DEBUG
        unset ZINK_DESCRIPTORS
        unset MESA_GLTHREAD
        GPU_ARGS="--ignore-gpu-blocklist --enable-unsafe-swiftshader --use-gl=angle --use-angle=swiftshader-webgl --enable-webgl --enable-accelerated-2d-canvas --disable-gpu-sandbox --no-sandbox --force-device-scale-factor=2 --enable-smooth-scrolling --password-store=gnome-libsecret"
    fi
}

# Auto-detect Adreno KGSL for Turnip + Zink
if [ "$GPU_MODE" == "auto" ]; then
    if [ -e /dev/kgsl-3d0 ] || [ -c /dev/kgsl-3d0 ]; then
        GPU_MODE="gpu"
    else
        GPU_MODE="software"
    fi
fi

setup_gpu_profile "$GPU_MODE"

if ! pgrep -f "matchbox-window-manager" > /dev/null 2>&1; then matchbox-window-manager -use_titlebar no & sleep 0.2; fi

# Clean up orphaned language_server instances and singleton locks to prevent white screen on auto-restart
pkill -9 -f "language_server" >/dev/null 2>&1 || true
rm -rf "$HOME/.config/Antigravity/Singleton*" "$HOME/.config/Antigravity/lockfile" 2>/dev/null || true

# Launch App with automatic fallback if GPU crashes on startup
launch_electron() {
    if [ "$DEBUG_MODE" -eq 1 ]; then
        export ELECTRON_ENABLE_LOGGING=1
        export ELECTRON_ENABLE_STACK_DUMPING=1
        /opt/antigravity/antigravity --no-sandbox --remote-debugging-port=9222 $GPU_ARGS --enable-logging --v=1 --log-level=0 "${ARGS[@]}"
    else
        /opt/antigravity/antigravity --no-sandbox --remote-debugging-port=9222 $GPU_ARGS "${ARGS[@]}" >/dev/null 2>&1
    fi
}

T_START=$(date +%s)
launch_electron
APP_EXIT=$?
T_END=$(date +%s)

# Fallback: If hardware GPU mode crashed within 4 seconds, retry immediately with software rendering
if [ "$GPU_MODE" == "gpu" ] && [ $(( T_END - T_START )) -lt 4 ] && [ "$APP_EXIT" -ne 0 ]; then
    echo -e "\033[1;31m[GPU]\033[0m Hardware acceleration failed (exit $APP_EXIT). Retrying with software rendering fallback..."
    setup_gpu_profile "software"
    launch_electron
fi
'''

new_content = header.rstrip() + "\n" + gpu_block.lstrip()
p.write_text(new_content)
PY
fi
python3 "$PREFIX/bin/patch_va39.py" >/dev/null 2>&1 || true

echo "5/5  Applying mobile display profile..."
P=com.termux.x11.CHANGE_PREFERENCE
R=ai.antigem.x11/com.termux.x11.LoriePreferences\$Receiver
am broadcast -a "$P" -n "$R" --es displayResolutionMode custom >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es displayResolutionCustom 1080x2161 >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es displayStretch false >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es adjustHeightForEK false >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es showIMEWhileExternalConnected true >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es backButtonAction "toggle soft keyboard" >/dev/null 2>&1 || true

echo "6/6  Starting Antigravity GUI..."
if ! pgrep -f '/opt/antigravity/antigravity' >/dev/null 2>&1; then
    nohup "$PREFIX/bin/gem" >/dev/null 2>&1 < /dev/null &
fi

echo ""
echo "Setup complete. Go back to MobAntigravity and tap 'Open Antigravity GUI'."
