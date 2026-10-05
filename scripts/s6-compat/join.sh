#!/usr/bin/env bash
# join.sh SERVER [BOT] — (re)start the headless client, accept the server pack, wait for join, op + creative.
S=$(dirname "$0"); ROOT=${ROOT:-/home/user/s6}; srv=$1; bot=${2:-Bot1}
port=$(grep "^server-port=" $ROOT/servers/$srv/server.properties | cut -d= -f2)
tmux kill-session -t cl-$bot 2>/dev/null; pkill -f "username $bot " ; sleep 2
$S/seed-options.sh $ROOT/clients/$bot
tmux new-session -d -s cl-$bot "XDG_RUNTIME_DIR=/tmp/xdg $S/client.sh $bot $port > $ROOT/client-$bot.log 2>&1"
n0=$(grep -c "$bot joined the game" $ROOT/servers/$srv/console.out)
for i in $(seq 150); do
  [ $(grep -c "$bot joined the game" $ROOT/servers/$srv/console.out) -gt $n0 ] && break
  grep -q 'Connecting to' $ROOT/client-$bot.log && { sleep 4; $S/ui.sh click 484 453; }
  sleep 2; done
[ $(grep -c "$bot joined the game" $ROOT/servers/$srv/console.out) -gt $n0 ] || { echo "JOIN FAILED"; exit 1; }
sleep 8; $S/mc.sh cmd $srv "op $bot"; $S/mc.sh cmd $srv "gamemode creative $bot"; sleep 2; echo "joined $srv"
