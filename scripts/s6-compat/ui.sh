#!/usr/bin/env bash
# ui.sh click X Y | chat "TEXT" | key KEY | shot FILE  — drive the headless client on $DISPLAY (default :99).
export DISPLAY=${DISPLAY:-:99}
w=$(xdotool search --name 'Minecraft' | head -1)
case $1 in
  click) xdotool mousemove --sync $2 $3 click 1 ;;
  chat) xdotool windowactivate --sync $w 2>/dev/null; xdotool key --window $w t; sleep 0.7; xdotool type --delay 40 "$2"; sleep 0.3; xdotool key --window $w Return ;;
  key) xdotool key --window $w $2 ;;
  shot) import -window root "$2" ;;
esac
