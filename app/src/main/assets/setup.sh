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
