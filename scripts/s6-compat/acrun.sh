#!/usr/bin/env bash
# acrun.sh SERVER — install the s6ac datapack, boot, build the rigs, run them twice, stop.
S=$(dirname "$0"); d=/home/user/s6/servers/$1; L=$d/console.out
python3 $S/make_acpack.py $d/world >/dev/null
$S/mc.sh start $1; $S/mc.sh wait $1 'Done \(' 600 || exit 1
$S/mc.sh cmd $1 "datapack list enabled"; sleep 2
$S/mc.sh cmd $1 "function s6ac:build"; $S/mc.sh wait $1 's6ac built' 60; sleep 5
for r in 1 2; do $S/mc.sh cmd $1 "function s6ac:run"; sleep 8; done
grep -c 'S6AC T' $L; grep -o 's6ac.\{0,40\}' $L | grep -v 'S6AC' | sort | uniq -c | head
$S/mc.sh stop $1
