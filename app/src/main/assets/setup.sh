#!/data/data/ai.antigem/files/usr/bin/env bash
# MobAntigravity v1.4.0 guided setup - tuned for Android & Oppo A79 5G (MediaTek Dimensity 6020 / Mali-G57).
set -e

SRC="/sdcard/Download/MobAntigravity"
PREFIX="${PREFIX:-/data/user/0/ai.antigem/files/usr}"

echo ""
echo "=== MobAntigravity v1.4.0 setup ==="
echo "1/6 Installing host packages..."
pkg update -y >/dev/null 2>&1 || true
pkg install -y proot-distro curl tar python xdotool >/dev/null 2>&1

echo "2/6 Preparing Debian PRoot container..."
if ! proot-distro list 2>/dev/null | grep -q '^debian'; then
    proot-distro install debian
else
    echo "    Debian already installed."
fi

echo "3/6 Installing Antigravity core..."
curl -sL https://raw.githubusercontent.com/tr1xx-tech/Antigravity-Mobile/main/install.sh | bash

echo "4/6 Installing MobAntigravity v1.4.0 scripts & Mali/RAM tuning..."
bash "$SRC/sync-scripts.sh" --no-launch

echo "5/6 Verifying VA39 binary patch..."
python3 "$PREFIX/bin/patch_va39.py" || true

echo "6/6 Starting Antigravity GUI..."
if ! pgrep -f '/opt/antigravity/antigravity' >/dev/null 2>&1; then
    nohup "$PREFIX/bin/gem" >/dev/null 2>&1 </dev/null &
fi

echo ""
echo "Setup complete. Go back to MobAntigravity and tap 'Open Antigravity GUI'."
