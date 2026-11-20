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

p = Path(sys.argv[1])
s = p.read_text()

# 1) Replace old Zink/Turnip GPU block if present
old = """export MESA_SHADER_CACHE_DIR=/sdcard/.gem/mesa_shader_cache
export MESA_DISK_CACHE_SINGLE_FILE=1
export __GL_SHADER_DISK_CACHE_PATH=/sdcard/.gem/mesa_shader_cache
export __GL_SHADER_DISK_CACHE=1
export vblank_mode=0
GPU_ARGS="--ignore-gpu-blocklist --enable-gpu-rasterization --enable-unsafe-swiftshader --use-gl=angle --use-angle=vulkan --enable-features=Vulkan --disable-vulkan-surface --enable-webgl --enable-accelerated-2d-canvas --disable-gpu-sandbox --no-sandbox --force-device-scale-factor=2 --enable-smooth-scrolling --password-store=gnome-libsecret\""""
new = """export MESA_SHADER_CACHE_DIR=/sdcard/.gem/mesa_shader_cache
export MESA_DISK_CACHE_SINGLE_FILE=1
export __GL_SHADER_DISK_CACHE_PATH=/sdcard/.gem/mesa_shader_cache
export __GL_SHADER_DISK_CACHE=1
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
export MESA_GL_VERSION_OVERRIDE=4.5
export MESA_GLES_VERSION_OVERRIDE=3.2
export VK_ICD_FILENAMES=/opt/antigravity/vk_swiftshader_icd.json
export vblank_mode=0
GPU_ARGS="--ignore-gpu-blocklist --enable-unsafe-swiftshader --use-gl=angle --use-angle=swiftshader --enable-webgl --enable-accelerated-2d-canvas --disable-gpu-sandbox --in-process-gpu --no-sandbox --force-device-scale-factor=2 --enable-smooth-scrolling --password-store=gnome-libsecret\""""
if old in s:
    s = s.replace(old, new)

# 2) Upgrade earlier/broken SwiftShader patch variants to working config
if ("swiftshader-webgl" in s
    or ("--use-angle=swiftshader" in s and "--in-process-gpu" not in s)
    or ("GPU_ARGS=" in s and "VK_ICD_FILENAMES" not in s and "swiftshader" in s)):
    s = s.replace("--use-angle=swiftshader-webgl", "--use-angle=swiftshader")
    if "VK_ICD_FILENAMES=/opt/antigravity/vk_swiftshader_icd.json" not in s and "export vblank_mode=0" in s:
        s = s.replace("export vblank_mode=0",
                      "export VK_ICD_FILENAMES=/opt/antigravity/vk_swiftshader_icd.json\nexport vblank_mode=0", 1)
    if "--in-process-gpu" not in s and "--use-angle=swiftshader" in s:
        s = s.replace("--disable-gpu-sandbox", "--disable-gpu-sandbox --in-process-gpu")

p.write_text(s)
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

echo "6/6  Starting Antigravity GUI..."
if ! pgrep -f '/opt/antigravity/antigravity' >/dev/null 2>&1; then
    nohup "$PREFIX/bin/gem" >/dev/null 2>&1 < /dev/null &
fi

echo ""
echo "Setup complete. Go back to MobAntigravity and tap 'Open Antigravity GUI'."
