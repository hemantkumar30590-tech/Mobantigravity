#!/data/data/ai.antigem/files/usr/bin/env bash
# MobAntigravity v1.4.0 Fast Script & Display Profile Sync (no Debian reinstall needed)
set -e

SRC="/sdcard/Download/MobAntigravity"
PREFIX="${PREFIX:-/data/user/0/ai.antigem/files/usr}"
NO_LAUNCH=0
if [ "${1:-}" == "--no-launch" ]; then
    NO_LAUNCH=1
fi

# Load dynamic display profile if exported by LauncherActivity
MOB_DISPLAY_RES="1080x2161"
MOB_SCALE_FACTOR="2"
MOB_XFT_DPI="288"
if [ -f "$SRC/display_profile.env" ]; then
    # shellcheck disable=SC1090
    source "$SRC/display_profile.env" 2>/dev/null || true
fi

echo "=== MobAntigravity v1.4.0 Fast Sync ==="
mkdir -p "$PREFIX/bin"

if [ -d "$SRC/scripts" ]; then
    cp "$SRC"/scripts/* "$PREFIX/bin/" 2>/dev/null || true
fi
for f in gem gem-ime gem-ime-inject gem-ime-server patch_va39.py; do
    if [ -f "$SRC/$f" ]; then
        cp "$SRC/$f" "$PREFIX/bin/$f"
    fi
    if [ -f "$PREFIX/bin/$f" ]; then
        chmod +x "$PREFIX/bin/$f"
    fi
done

RUNSH="$PREFIX/var/lib/proot-distro/containers/debian/rootfs/opt/antigravity/run.sh"
if [ -f "$RUNSH" ]; then
    sed -i "s/force-device-scale-factor=[0-9.]*/force-device-scale-factor=${MOB_SCALE_FACTOR}/g" "$RUNSH"
    sed -i "s/Xft.dpi: [0-9]*/Xft.dpi: ${MOB_XFT_DPI}/g" "$RUNSH"

    python3 - "$RUNSH" "$MOB_SCALE_FACTOR" "$MOB_XFT_DPI" <<'PY'
import re
import sys
from pathlib import Path

p = Path(sys.argv[1])
scale_factor = sys.argv[2]
xft_dpi = sys.argv[3]
s = p.read_text()

marker_re = re.compile(r'echo "Xft\.dpi: \d+" \| xrdb -merge 2>/dev/null \|\| true\n')
m_xft = marker_re.search(s)
if m_xft:
    header = s[:m_xft.start()] + f'echo "Xft.dpi: {xft_dpi}" | xrdb -merge 2>/dev/null || true\n'
else:
    match = re.search(r'(#.*(?:Hardware Acceleration|WebGL|GPU)|export GALLIUM_DRIVER|export LIBGL_ALWAYS_SOFTWARE)', s)
    header = s[:match.start()] if match else s

gpu_block = f'''
# --- MobAntigravity v1.4.0: GPU + MediaTek Mali Thermal/RAM Optimization ---
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

# Auto-tune RAM limits (prevents ColorOS LMK / OOM kills on 6-8 GB devices like Oppo A79 5G)
TOTAL_MEM_KB=$(awk '/MemTotal/ {{print $2}}' /proc/meminfo 2>/dev/null || echo 8000000)
if [ "$TOTAL_MEM_KB" -le 4500000 ]; then
    NODE_HEAP_MB=1536
    LP_THREADS=2
elif [ "$TOTAL_MEM_KB" -le 9000000 ]; then
    NODE_HEAP_MB=2048
    LP_THREADS=4
else
    NODE_HEAP_MB=3072
    LP_THREADS=6
fi
export NODE_OPTIONS="--max-old-space-size=${{NODE_HEAP_MB}}"
export MALLOC_ARENA_MAX=2

# Ensure Mesa shader cache directory exists
mkdir -p /sdcard/.gem/mesa_shader_cache 2>/dev/null || true
export MESA_SHADER_CACHE_DIR=/sdcard/.gem/mesa_shader_cache
export MESA_DISK_CACHE_SINGLE_FILE=1
export __GL_SHADER_DISK_CACHE_PATH=/sdcard/.gem/mesa_shader_cache
export __GL_SHADER_DISK_CACHE=1
export vblank_mode=0

setup_gpu_profile() {{
    local target="$1"
    if [ "$target" == "gpu" ]; then
        echo -e "\\033[1;32m[GPU]\\033[0m Activating Hardware Acceleration (Turnip + Zink / Adreno KGSL)"
        unset LIBGL_ALWAYS_SOFTWARE
        unset LP_NUM_THREADS
        export GALLIUM_DRIVER=zink
        export MESA_LOADER_DRIVER_OVERRIDE=zink
        export TU_DEBUG=sysmem,noconform
        export ZINK_DESCRIPTORS=lazy
        export MESA_VK_WSI_PRESENT_MODE=mailbox
        export MESA_VK_IGNORE_EXTENSIONS="VK_EXT_calibrated_timestamps VK_KHR_calibrated_timestamps"
        export MESA_GL_VERSION_OVERRIDE=4.6COMPAT
        export MESA_GLES_VERSION_OVERRIDE=3.2
        export MESA_GLTHREAD=true
        GPU_ARGS="--ignore-gpu-blocklist --disable-vulkan --enable-gpu-rasterization --enable-oop-rasterization --canvas-oop-rasterization --gpu-rasterization-msaa-sample-count=0 --enable-zero-copy --use-gl=angle --use-angle=gl --enable-webgl --enable-accelerated-2d-canvas --num-raster-threads=8 --start-maximized --disable-gpu-sandbox --no-sandbox --force-device-scale-factor={scale_factor} --enable-smooth-scrolling --password-store=gnome-libsecret"
    else
        echo -e "\\033[1;33m[GPU]\\033[0m Activating Mali-Tuned Software Fallback (llvmpipe ${{LP_THREADS}}T + SwiftShader, Heap ${{NODE_HEAP_MB}}MB)"
        export LIBGL_ALWAYS_SOFTWARE=1
        export GALLIUM_DRIVER=llvmpipe
        export MESA_LOADER_DRIVER_OVERRIDE=llvmpipe
        export LP_NUM_THREADS="${{LP_THREADS}}"
        export MESA_GL_VERSION_OVERRIDE=4.5
        export MESA_GLES_VERSION_OVERRIDE=3.2
        unset TU_DEBUG
        unset ZINK_DESCRIPTORS
        unset MESA_GLTHREAD
        GPU_ARGS="--ignore-gpu-blocklist --enable-unsafe-swiftshader --use-gl=angle --use-angle=swiftshader-webgl --enable-webgl --enable-accelerated-2d-canvas --disable-gpu-vsync --in-process-gpu --disable-dev-shm-usage --renderer-process-limit=3 --disable-gpu-sandbox --no-sandbox --force-device-scale-factor={scale_factor} --enable-smooth-scrolling --password-store=gnome-libsecret"
    fi
}}

# Auto-detect Adreno KGSL for Turnip + Zink; MediaTek Mali uses tuned software profile
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
launch_electron() {{
    if [ "$DEBUG_MODE" -eq 1 ]; then
        export ELECTRON_ENABLE_LOGGING=1
        export ELECTRON_ENABLE_STACK_DUMPING=1
        /opt/antigravity/antigravity --no-sandbox --remote-debugging-port=9222 $GPU_ARGS --enable-logging --v=1 --log-level=0 "${{ARGS[@]}}"
    else
        /opt/antigravity/antigravity --no-sandbox --remote-debugging-port=9222 $GPU_ARGS "${{ARGS[@]}}" >/dev/null 2>&1
    fi
}}

T_START=$(date +%s)
launch_electron
APP_EXIT=$?
T_END=$(date +%s)

# Fallback: If hardware GPU mode crashed within 4 seconds, retry immediately with software rendering
if [ "$GPU_MODE" == "gpu" ] && [ $(( T_END - T_START )) -lt 4 ] && [ "$APP_EXIT" -ne 0 ]; then
    echo -e "\\033[1;31m[GPU]\\033[0m Hardware acceleration failed (exit $APP_EXIT). Retrying with software rendering..."
    setup_gpu_profile "software"
    launch_electron
fi
'''

new_content = header.rstrip() + "\n" + gpu_block.lstrip()
p.write_text(new_content)
PY
fi

# Apply dynamic Termux-X11 display preferences
P=com.termux.x11.CHANGE_PREFERENCE
R='ai.antigem.x11/com.termux.x11.LoriePreferences$Receiver'
am broadcast -a "$P" -n "$R" --es displayResolutionMode custom >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es displayResolutionCustom "$MOB_DISPLAY_RES" >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es displayStretch false >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es adjustHeightForEK false >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es showIMEWhileExternalConnected true >/dev/null 2>&1 || true
am broadcast -a "$P" -n "$R" --es backButtonAction "toggle soft keyboard" >/dev/null 2>&1 || true

python3 "$PREFIX/bin/patch_va39.py" >/dev/null 2>&1 || true

echo "✓ Fast sync complete (Display: ${MOB_DISPLAY_RES}, Scale: ${MOB_SCALE_FACTOR}x, Xft: ${MOB_XFT_DPI})"
if [ "$NO_LAUNCH" -eq 0 ]; then
    echo "Run 'gem' to start Antigravity with the upgraded v1.4.0 profile."
fi
