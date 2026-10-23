#!/usr/bin/env python3
"""Picks candidates between passes.

python3 -I select.py pass1 <pass1.jsonl> <out pass2-input.txt> [seeds=200] [extra=60]
    best centre of the top `seeds` seeds, plus the second centre of the top `extra` seeds.
python3 -I select.py pass2 <pass2.jsonl> <out input.txt> [n=5]
    the top n (seed, centre) results of pass 2, one per seed.
"""
import json
import sys


def main():
    mode, src, dst = sys.argv[1:4]
    rows = [json.loads(l) for l in open(src) if l.strip()]
    lines = []
    if mode == "pass1":
        n = int(sys.argv[4]) if len(sys.argv) > 4 else 200
        extra = int(sys.argv[5]) if len(sys.argv) > 5 else 60
        rows.sort(key=lambda r: -r["centres"][0]["score"])
        for r in rows[:n]:
            c = r["centres"][0]
            lines.append(f"{r['seed']} {c['x']} {c['z']}")
        for r in rows[:extra]:
            if len(r["centres"]) > 1:
                c = r["centres"][1]
                lines.append(f"{r['seed']} {c['x']} {c['z']}")
        print(f"{len(rows)} seeds in pass 1; best scores", [r["centres"][0]["score"] for r in rows[:5]],
              "cut-off", rows[min(n, len(rows)) - 1]["centres"][0]["score"])
    else:
        n = int(sys.argv[4]) if len(sys.argv) > 4 else 5
        rows.sort(key=lambda r: -r["score"])
        seen = set()
        for r in rows:
            if r["seed"] in seen:
                continue
            seen.add(r["seed"])
            lines.append(f"{r['seed']} {r['x']} {r['z']}")
            if len(lines) == n:
                break
    open(dst, "w").write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
