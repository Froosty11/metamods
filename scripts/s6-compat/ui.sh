#!/usr/bin/env bash
# ui.sh click X Y | chat "TEXT" | key KEY | shot FILE  — drive the headless client on $DISPLAY (default :99).
export DISPLAY=${DISPLAY:-:99}
w=$(xdotool search --name 'Minecraft' | head -1)
case $1 in
  click) xdotool mousemove --sync $2 $3 sleep 0.3 mousedown 1 sleep 0.15 mouseup 1 ;;
  chat) # open chat with "/" for commands (never type into the game world), then type the rest
    xdotool windowactivate --sync $w 2>/dev/null
    if [ "${2:0:1}" = / ]; then xdotool key --window $w slash; t="${2:1}"; else xdotool key --window $w t; t="$2"; fi
    sleep 1.2; xdotool type --delay 50 "$t"; sleep 0.4; xdotool key --window $w Return ;;
  key) xdotool key --window $w $2 ;;
  shot) import -window root "$2" ;;
esac
