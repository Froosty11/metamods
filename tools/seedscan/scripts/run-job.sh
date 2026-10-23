#!/usr/bin/env bash
# Starts a fresh dedicated server on a seed (Metacraft datapack in place before the world is created)
# and runs a seedscan job in it. The server stops by itself when the job is done.
# Usage: scripts/run-job.sh <instance-dir> <seed> <job> [extra -D options...]
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${SEEDSCAN_BASE:-$HERE/work/server-base}"
DIR="$(mkdir -p "$1" && cd "$1" && pwd)"; SEED="$2"; JOB="$3"; shift 3
mkdir -p "$DIR/mods" "$DIR/world/datapacks"
cp "$BASE"/mods/*.jar "$DIR/mods/"
cp "$HERE"/build/libs/seedscan-1.0.0.jar "$DIR/mods/"
for f in fabric-server-launch.jar eula.txt libraries versions .fabric; do
  [ -e "$DIR/$f" ] || ln -s "$BASE/$f" "$DIR/$f"
done
rm -rf "$DIR/world/datapacks/Metacraft"
if [ -z "${SEEDSCAN_NO_DATAPACK:-}" ]; then
cp -r "$HERE/datapack/Metacraft" "$DIR/world/datapacks/Metacraft"
fi
cat > "$DIR/server.properties" <<PROPS
level-seed=$SEED
level-name=world
online-mode=false
max-tick-time=-1
view-distance=4
simulation-distance=4
spawn-protection=0
server-port=${SEEDSCAN_PORT:-25565}
PROPS
cd "$DIR"
exec "$JAVA_HOME/bin/java" ${SEEDSCAN_XMX:--Xmx6G} -Dseedscan.job="$JOB" -Dseedscan.out="${SEEDSCAN_OUT:-$DIR/out}" "$@" \
  -jar fabric-server-launch.jar nogui
