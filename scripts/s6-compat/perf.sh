#!/usr/bin/env bash
# perf.sh MODSET — fresh world (same seed), idle spark profile, then a 500-block-radius Chunky pregen
# under a spark profile of all threads. Writes $ROOT/reports/perf-MODSET/{summary.txt,errors.txt}.
S=$(dirname "$0"); ROOT=${ROOT:-/home/user/s6}; set_=$1; name=perf-${PERF_NAME:-$1}
d=$ROOT/servers/$name; out=$ROOT/reports/$name; mkdir -p $out; L=$d/console.out
ROOT=$ROOT $S/mkserver.sh $name $set_ >/dev/null
[ -n "$PRE_HOOK" ] && eval "$PRE_HOOK"
$S/mc.sh start $name; t0=$(date +%s); $S/mc.sh wait $name 'Done \(' 900 || { echo "boot failed"; exit 1; }
echo "boot: $(grep -o 'Done ([0-9.]*s)' $L) wall=$(( $(date +%s)-t0 ))s" > $out/summary.txt
mark() { wc -l < $L; }
# settle: let spawn-time DH and pack generation finish
for i in $(seq ${SETTLE:-90}); do sleep 1; done
n=$(mark); $S/mc.sh cmd $name "spark health --memory"; sleep 6
{ echo "== idle health report"; tail -n +$((n+1)) $L | grep -oE 'https://spark.lucko.me/[A-Za-z0-9]{6,}' | head -1; } >> $out/summary.txt
newlink() { for i in $(seq ${2:-200}); do tail -n +$(($1+1)) $L | grep -qE 'spark.lucko.me/[A-Za-z0-9]{6,}' && break; sleep 1; done
  tail -n +$(($1+1)) $L | grep -oE 'https://spark.lucko.me/[A-Za-z0-9]{6,}' | head -1; }
n=$(mark); $S/mc.sh cmd $name "spark tps"; sleep 2
{ echo "== idle tps"; tail -n +$((n+1)) $L | sed 's/^\[[0-9:]*\] \[[^]]*\]: //' | grep -v '^\s*$'; } >> $out/summary.txt
[ -z "$QUICK" ] && { n=$(mark); $S/mc.sh cmd $name "spark profiler start --timeout 60"; }
[ -z "$QUICK" ] && echo "== idle profile (60 s, server thread): $(newlink $n 200)" >> $out/summary.txt
# pregen
n=$(mark); $S/mc.sh cmd $name "chunky center 0 0"; $S/mc.sh cmd $name "chunky radius 500"; sleep 1
$S/mc.sh cmd $name "spark profiler start --thread *"; sleep 2
$S/mc.sh cmd $name "chunky start"; $S/mc.sh cmd $name "chunky confirm"; p0=$(date +%s)
for i in $(seq 3600); do grep -q 'Task finished for' <(tail -n +$((n+1)) $L) && break
  [ $((i % 60)) = 0 ] && $S/mc.sh cmd $name "spark tps"; sleep 1; done
p1=$(date +%s)
$S/mc.sh cmd $name "spark tps"; sleep 2
m=$(mark); $S/mc.sh cmd $name "spark profiler stop"
plink=$(newlink $m 180)
{ echo "== pregen r=500: wall=$((p1-p0))s"
  tail -n +$((n+1)) $L | grep -E 'Task finished|Task (running|finished)' | tail -2
  echo "== tps samples during pregen"; tail -n +$((n+1)) $L | grep -A9 -E 'TPS from last' | grep -vE '^--$|^\\s*$' | sed 's/^\[[0-9:]*\] \[[^]]*\]: //'
  echo "== pregen profile (all threads): $plink"
} >> $out/summary.txt
tail -n +$((n+1)) $L | grep -nE '/(ERROR|FATAL)\]|Exception' | grep -v '^\s*at ' | head -60 > $out/pregen-errors.txt
echo "pregen error lines: $(wc -l < $out/pregen-errors.txt)" >> $out/summary.txt
du -sh $d/world/dimensions/minecraft/overworld/region $d/world 2>/dev/null | tr '\n' ' ' >> $out/summary.txt; echo >> $out/summary.txt
find $d -name '*.sqlite' -path '*Distant*' -exec du -ch {} + 2>/dev/null | tail -1 | sed 's/^/DH db: /' >> $out/summary.txt
$S/mc.sh stop $name
cat $out/summary.txt
