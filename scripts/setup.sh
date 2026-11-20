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
    python3 - <<PY
from pathlib import Path
p = Path(r"$RUNSH")
s = p.read_text()
old = '''# Hardware Acceleration Flags (Native Zink + Turnip KGSL)
export GALLIUM_DRIVER=zink
export MESA_LOADER_DRIVER_OVERRIDE=zink
export TU_DEBUG=sysmem,noconform
export ZINK_DESCRIPTORS=lazy
export MESA_VK_WSI_PRESENT_MODE=mailbox
export MESA_VK_IGNORE_EXTENSIONS="VK_EXT_calibrated_timestamps VK_KHR_calibrated_timestamps"
export MESA_GL_VERSION_OVERRIDE=4.6COMPAT
export MESA_GLES_VERSION_OVERRIDE=3.2
export MESA_GLTHREAD=true
export vblank_mode=0
GPU_ARGS="--ignore-gpu-blocklist --disable-vulkan --enable-gpu-rasterization --enable-oop-rasterization --canvas-oop-rasterization --gpu-rasterization-msaa-sample-count=0 --enable-zero-copy --use-gl=angle --enable-webgl --enable-accelerated-2d-canvas --num-raster-threads=8 --start-maximized --disable-gpu-sandbox --force-device-scale-factor=2 --enable-smooth-scrolling --password-store=gnome-libsecret"'''
new = '''# WebGL: software SwiftShader via ANGLE (reliable in PRoot/X11)
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLES_VERSION_OVERRIDE=3.2
export vblank_mode=0
GPU_ARGS="--ignore-gpu-blocklist --enable-unsafe-swiftshader --use-gl=angle --use-angle=swiftshader-webgl --enable-webgl --enable-accelerated-2d-canvas --disable-gpu-sandbox --no-sandbox --force-device-scale-factor=2 --enable-smooth-scrolling --password-store=gnome-libsecret"'''
if old in s:
    p.write_text(s.replace(old, new))
PY
    sed -i 's/--use-angle=swiftshader[^ "]*"/--use-angle=swiftshader-webgl/g' "$RUNSH"
    sed -i 's/ --in-process-gpu//g' "$RUNSH"
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

echo "6/6  Starting Antigravity GUI..."
if ! pgrep -f '/opt/antigravity/antigravity' >/dev/null 2>&1; then
    nohup "$PREFIX/bin/gem" >/dev/null 2>&1 < /dev/null &
fi

echo ""
echo "Setup complete. Go back to MobAntigravity and tap 'Open Antigravity GUI'."
