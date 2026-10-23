#!/usr/bin/env bash
# Prepares a Fabric 26.3 server base with the exact METAcraft Season 6 worldgen mods.
# Usage: scripts/setup.sh [base-dir]   (needs JAVA_HOME pointing at Java 25+)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${1:-$HERE/work/server-base}"
mkdir -p "$BASE/mods" "$HERE/libs"
fetch() { # url sha256 dest
  if [ ! -f "$3" ]; then curl -sSfL -o "$3" "$1"; fi
  echo "$2  $3" | sha256sum -c -
}
fetch https://cdn.modrinth.com/data/lWDHr9jE/versions/j2jmkU85/tectonic-3.0.31-fabric-26.3.jar \
  33a534a71c5d81ad4b4327570481355c1356e55febbe8c21cc92ffa15a303046 "$HERE/libs/tectonic-3.0.31-fabric-26.3.jar"
fetch https://cdn.modrinth.com/data/XaDC71GB/versions/FhKHD2LJ/lithostitched-2.0.4-fabric-26.3.jar \
  b1cd7ca605a9e5cdfb43b60970885002c88b2cab132259e65d7294d36bdf3466 "$HERE/libs/lithostitched-2.0.4-fabric-26.3.jar"
cp "$HERE"/libs/*.jar "$BASE/mods/"
curl -sSfL -o "$BASE/mods/fabric-api-0.161.0+26.3.jar" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0+26.3/fabric-api-0.161.0+26.3.jar"
curl -sSfL -o "$BASE/fabric-server-launch.jar" https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/1.1.2/server/jar
echo eula=true > "$BASE/eula.txt"
# First start downloads the vanilla server and libraries, then exits.
(cd "$BASE" && "$JAVA_HOME/bin/java" -jar fabric-server-launch.jar --initSettings nogui >/dev/null)
echo "server base ready in $BASE"
