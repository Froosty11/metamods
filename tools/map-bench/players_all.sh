#!/bin/bash
# players_all.sh -> TPS with 4 exploring Carpet fake players, per mapper, interleaved reps.
cd "$(dirname "$0")"
run() { python3 players.py "$@" | tee -a results/players/summary.jsonl; }
mkdir -p results/players
for rep in 1 2; do
  run none --rep $rep
  run squaremap --rep $rep
  run bluemap --rep $rep
  run bluemap-flat --threads 1 --rep $rep
  run bluemap-flat --rep $rep
done
