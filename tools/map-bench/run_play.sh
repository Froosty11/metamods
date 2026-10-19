#!/bin/bash
# run_play.sh -> tick time under fake-player load, 3 reps each, interleaved.
cd "$(dirname "$0")"
mkdir -p results
for v in none squaremap bluemap-nocaves; do python3 play.py $v --rep 0 --explore-s 30 --settled-s 30 > /dev/null; done  # prerender + warm-up, discarded
rm -f results/play-*-r0.*
for rep in 1 2 3; do
  for v in none squaremap bluemap-nocaves; do
    python3 play.py $v --rep $rep | tee -a results/play-summary.jsonl
  done
done
