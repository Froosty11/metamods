#!/usr/bin/env bash
# mkserver.sh NAME MODSET [extra server.properties lines...]
# Creates $ROOT/servers/NAME with the Fabric launcher, a cached 26.3 server jar and the mod set.
set -euo pipefail
ROOT=${ROOT:-/home/user/s6}; JARS=$ROOT/jars
. "$(dirname "$0")/modsets.sh"
name=$1; set_=$2; shift 2
d=$ROOT/servers/$name; rm -rf "$d"; mkdir -p "$d/mods" "$d/config"
cp $ROOT/fabric-server-launch.jar "$d/"
cp -r $ROOT/cache/.fabric $ROOT/cache/versions "$d/"
[ -d $ROOT/cache/libraries ] && cp -r $ROOT/cache/libraries "$d/"
echo eula=true > "$d/eula.txt"
port=$((25565 + $(echo -n "$name" | cksum | cut -d' ' -f1) % 1000))
cat > "$d/server.properties" <<P
online-mode=false
server-port=$port
level-seed=6262026
spawn-protection=0
view-distance=10
simulation-distance=10
enable-command-block=true
max-tick-time=-1
white-list=false
P
for l in "$@"; do echo "$l" >> "$d/server.properties"; done
for g in $(modset "$set_"); do cp $JARS/$g "$d/mods/"; done
# PolyDecorations config from the metacraft-booklet branch (S6 sets only)
if ls "$d/mods" | grep -q polydecorations && [ -z "${NO_PD_CONFIG:-}" ]; then
  cp $ROOT/polydecorations.json "$d/config/polydecorations.json"; fi
echo "$d port=$port mods=$(ls "$d/mods" | wc -l)"
# Polymer auto-host on, so vanilla clients receive the generated pack as they would in production
if ls "$d/mods" | grep -q polymer; then mkdir -p "$d/config/polymer"; cp $ROOT/autohost.json "$d/config/polymer/auto-host.json"; fi
