#!/usr/bin/env bash
# bootcheck.sh NAME — boot, wait for Done or exit, summarize errors/warnings, keep the server running if BOOT_KEEP=1.
S=$(dirname "$0"); ROOT=${ROOT:-/home/user/s6}; d=$ROOT/servers/$1; out=$ROOT/reports/$1; mkdir -p $out
$S/mc.sh start $1; t0=$(date +%s)
$S/mc.sh wait $1 'Done \([0-9.]+s\)!|Crash report saved|Incompatible mods found|Mixin apply failed' ${2:-900}; rc=$?
t1=$(date +%s); log=$d/console.out
echo "boot_rc=$rc wall=$((t1-t0))s $(grep -oE 'Done \([0-9.]+s\)' $log)" | tee $out/boot.txt
grep -m1 -E 'Loading [0-9]+ mods' $log >> $out/boot.txt
grep -nE '/(ERROR|FATAL)\]|Exception|Mixin apply failed|conflict|@Overwrite|Mixin.*(WARN|failed)' $log | grep -v 'at ' | head -80 > $out/errors.txt
grep -nE '/WARN\]' $log | head -200 > $out/warns.txt
echo "errors: $(wc -l < $out/errors.txt) warn lines: $(wc -l < $out/warns.txt)" | tee -a $out/boot.txt
ls $d/crash-reports 2>/dev/null | tee -a $out/boot.txt
[ "${BOOT_KEEP:-0}" = 1 ] && [ $rc = 0 ] || $S/mc.sh stop $1
exit $rc
