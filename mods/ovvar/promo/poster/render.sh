#!/bin/sh
# Renders poster.html to poster.png at 2480×3508 (A4 at 300 dpi) with headless Chrome.
# Set CHROME to your Chrome/Chromium binary if it isn't the macOS default.
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
cd "$(dirname "$0")" && "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
	--force-device-scale-factor=2 --window-size=1240,1754 --virtual-time-budget=4000 \
	--screenshot="$PWD/poster.png" "file://$PWD/poster.html" 2>/dev/null
