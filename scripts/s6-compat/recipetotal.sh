#!/usr/bin/env bash
# recipetotal.sh SERVER — total recipes a player can unlock (take all, then give all), Bot1 online.
S=$(dirname "$0"); L=/home/user/s6/servers/$1/console.out
$S/mc.sh cmd $1 "recipe take Bot1 *"; sleep 2; n0=$(wc -l < $L); $S/mc.sh cmd $1 "recipe give Bot1 *"; sleep 2
tail -n +$((n0+1)) $L | grep -o 'Unlocked [0-9]* recipe' | head -1
