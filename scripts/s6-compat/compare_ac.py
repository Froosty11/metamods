#!/usr/bin/env python3
"""compare_ac.py LOG_A LOG_B — pull the S6AC tick lines out of two server logs and print them side by side, marking ticks that differ."""
import re, sys
def runs(path):
    out, cur = [], {}
    for line in open(path, errors="replace"):
        if "s6ac run" in line: cur = {}; out.append(cur)
        m = re.search(r"S6AC T(\d+) (.*)$", line)
        if m and out is not None and cur is not None: cur[int(m.group(1))] = m.group(2).strip()
    return [r for r in out if r]
a, b = runs(sys.argv[1])[-1], runs(sys.argv[2])[-1]
diff = 0
for t in sorted(set(a) | set(b)):
    mark = "  " if a.get(t) == b.get(t) else "!!"
    diff += mark == "!!"
    print(f"{mark} T{t:<3} {a.get(t, '-'):<42} {b.get(t, '-')}")
print(f"ticks differing: {diff} of {len(set(a) | set(b))}")
