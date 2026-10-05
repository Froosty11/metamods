#!/usr/bin/env bash
# mc.sh start|cmd|wait|stop NAME [args]  — drives a server console in tmux session mc-NAME.
ROOT=${ROOT:-/home/user/s6}; JAVA=${JAVA:-$(ls -d /opt/jdk/jdk-25*)/bin/java}
op=$1; name=$2; shift 2; d=$ROOT/servers/$name; log=$d/logs/latest.log
case $op in
  start) tmux kill-session -t mc-$name 2>/dev/null; rm -f $log
    tmux new-session -d -s mc-$name -c $d "$JAVA -Xms${XMX:-8G} -Xmx${XMX:-8G} ${JFLAGS:-} -jar fabric-server-launch.jar nogui 2>&1 | tee console.out" ;;
  cmd) tmux send-keys -t mc-$name -l "$*"; tmux send-keys -t mc-$name Enter ;;
  wait) # wait REGEX TIMEOUT_S
    for i in $(seq 1 ${2:-600}); do grep -qE "$1" $d/console.out 2>/dev/null && exit 0
      tmux has-session -t mc-$name 2>/dev/null || { echo "server exited"; exit 2; }; sleep 1; done; echo timeout; exit 1 ;;
  stop) tmux send-keys -t mc-$name stop Enter; for i in $(seq 1 120); do tmux has-session -t mc-$name 2>/dev/null || exit 0; sleep 1; done; tmux kill-session -t mc-$name ;;
esac
