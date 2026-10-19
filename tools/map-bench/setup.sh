#!/bin/bash
# setup.sh [work-dir]  -> builds <work>/base: Fabric 26.3 server, mods.txt, a pregenerated world
# (S6 seed, Chunky square radius 1024 around 0,0, 256 chunks force-loaded around spawn), and
# <work>/jars with BlueMap 5.28, squaremap 1.4.3, the Polymer patch and the 26.3 client jar.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${1:-$HOME/map-bench}"
JAVA="${JAVA:-java}"
MC=26.3 LOADER=0.19.5
mkdir -p "$WORK/base/mods" "$WORK/jars"
cd "$WORK"

fetch() {  # fetch <url> <sha1> <dest>
  curl -sSfL --retry 3 -o "$3" "$1"
  echo "$2  $3" | sha1sum -c --quiet
}

INSTALLER=$(curl -sSf https://meta.fabricmc.net/v2/versions/installer | python3 -c 'import json,sys;print(json.load(sys.stdin)[0]["version"])')
curl -sSfL -o base/fabric-server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"
grep -v '^#' "$HERE/mods.txt" | while read -r url sha; do
  [ -n "$url" ] && fetch "$url" "$sha" "base/mods/$(basename "${url//%2B/+}")"
done
fetch https://cdn.modrinth.com/data/swbUV1cr/versions/bbzcTCOs/bluemap-5.28-fabric.jar 0f4289092d6cd5e1e028ace14b4464f096e90595 jars/bluemap-5.28-fabric.jar
fetch https://cdn.modrinth.com/data/PFb7ZqK6/versions/YPMtDS9D/squaremap-fabric-mc26.3-1.4.3.jar 871594177a5bf4c445b1b0efb3a3f56fbfd2f28c jars/squaremap-fabric-mc26.3-1.4.3.jar
cp "$HERE/jars/BlueMapPolymerPatch-1.2.1+26.3.jar" jars/
CLIENT=$(curl -sSf https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
  | python3 -c "import json,sys;print(next(v['url'] for v in json.load(sys.stdin)['versions'] if v['id']=='$MC'))" \
  | xargs curl -sSf | python3 -c 'import json,sys;print(json.load(sys.stdin)["downloads"]["client"]["url"])')
curl -sSfL -o "jars/minecraft-client-$MC.jar" "$CLIENT"

cat > base/eula.txt <<<'eula=true'
cat > base/server.properties <<'EOF'
level-seed=127721965112300492
online-mode=false
view-distance=10
simulation-distance=10
enable-rcon=true
rcon.port=25575
rcon.password=metacraft-dev
server-port=25565
spawn-protection=0
max-tick-time=60000
sync-chunk-writes=true
EOF

"$HERE/start.sh" base
until grep -q 'Done (' base/logs/latest.log 2>/dev/null; do kill -0 "$(cat base/server.pid)" || exit 1; sleep 2; done
R="python3 $HERE/rcon.py"
$R chunky world minecraft:overworld; $R chunky center 0 0; $R chunky radius 1024; $R chunky start
until grep -q 'Task finished for minecraft:overworld' base/logs/latest.log; do kill -0 "$(cat base/server.pid)" || exit 1; sleep 5; done
grep 'Task finished for minecraft:overworld' base/logs/latest.log | tail -1
# Something for the server to tick while the mappers run (block entities, random ticks, fluids).
$R forceload add -128 -128 127 127
$R save-all flush
$R stop
while kill -0 "$(cat base/server.pid)" 2>/dev/null; do sleep 1; done
rm -rf base/logs base/console.log base/server.pid
du -sh base/world
