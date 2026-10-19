#!/bin/sh
# start.sh <server-dir>  -> starts the server headless in the background, pid in <dir>/server.pid
cd "$1" || exit 1
nohup "${JAVA:-java}" -Xms${HEAP:-6G} -Xmx${HEAP:-6G} -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -jar fabric-server.jar nogui < /dev/null > console.log 2>&1 &
echo $! > server.pid
