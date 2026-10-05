#!/usr/bin/env bash
# pdcount.sh SERVER OUTFILE — with Bot1 online, list which PolyDecorations recipe ids exist (recipe take succeeds).
S=$(dirname "$0"); L=/home/user/s6/servers/$1/console.out; out=$2; : > $out
n0=$(wc -l < $L); $S/mc.sh cmd $1 "recipe give Bot1 *"; sleep 2; tail -n +$((n0+1)) $L | grep -o 'Unlocked [0-9]* recipe' > $out.total
while read r; do n=$(wc -l < $L); $S/mc.sh cmd $1 "recipe take Bot1 polydecorations:$r"
  for i in $(seq 20); do [ $(wc -l < $L) -gt $n ] && break; sleep 0.1; done
  tail -n +$((n+1)) $L | grep -q Took && echo $r >> $out; done < /home/user/s6/pd-recipes.txt
echo "$(cat $out.total); polydecorations recipes present: $(wc -l < $out)"
