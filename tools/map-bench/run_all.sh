#!/bin/bash
# run_all.sh -> every variant, one at a time, interleaved so slow drift hits all of them alike.
cd "$(dirname "$0")"
run() { python3 bench.py "$@" | tee -a results/summary.jsonl; }
mkdir -p results
for rep in 1 2; do
  run squaremap --rep $rep
  run bluemap --rep $rep
  run bluemap-flat --rep $rep
  run bluemap-lean --rep $rep
  run bluemap-lowres-lean --rep $rep
done
run squaremap --rep 3
run squaremap --threads 0 --rep 1
run bluemap-nopatch --rep 1
run bluemap-nocaves --rep 1
run bluemap-miny --rep 1
run bluemap-lowres --rep 1
run bluemap-zstd --rep 1
run bluemap-lean --threads 1 --rep 1
