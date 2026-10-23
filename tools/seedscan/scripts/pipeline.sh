#!/usr/bin/env bash
# Runs everything after pass 1: pass 2, final, verify, render, screenshots.
set -euo pipefail
export JAVA_HOME=/opt/jdk25
cd "$(dirname "$0")/.."
W=$PWD/work
until grep -q "seedscan job pass1 done" $W/pass1.log; do sleep 10; done
mkdir -p $W/pass2/out $W/final/out results
python3 -I select.py pass1 $W/pass1/out/pass1.jsonl $W/pass2/out/pass2-input.txt 200 60
scripts/run-job.sh $W/pass2 0 pass2 > $W/pass2.log 2>&1
python3 -I select.py pass2 $W/pass2/out/pass2.jsonl $W/final/out/pass2-input.txt 10
scripts/run-job.sh $W/final 0 pass2 -Dseedscan.final=true > $W/final.log 2>&1
mkdir -p $W/verify
head -6 $W/final/out/pass2-input.txt | while read seed x z; do
  # centre from final.jsonl (final keeps the centre)
  scripts/run-job.sh $W/v-$seed $seed verify -Dseedscan.centre=$x,$z > $W/v-$seed.log 2>&1
  cp $W/v-$seed/out/verify-$seed.jsonl $W/verify/
done
for d in $W/final/out/dump/*; do python3 -I render.py $d results; done
echo PIPELINE_RENDERED
