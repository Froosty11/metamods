#!/usr/bin/env python3
"""Write the s6ac datapack: four redstone rigs (piston door, observer clock, comparator subtraction,
quasi-connectivity) built at y=200 above 0,0 and a per-tick recorder that logs every probe with `say`.

Usage: make_acpack.py WORLD_DIR. In game: /function s6ac:build, wait a second, then /function s6ac:run.
Each tick for 80 ticks the server log gets a line like
  [Not Secure] [Server] T12 A111 B010 C0,3,7,11,14 Q0 D0
which compare_ac.py diffs between servers.
"""
import json, os, sys

Y = 200
world = sys.argv[1]
root = os.path.join(world, "datapacks", "s6ac")
fn = os.path.join(root, "data", "s6ac", "function")
os.makedirs(fn, exist_ok=True)
os.makedirs(os.path.join(root, "data", "minecraft", "tags", "function"), exist_ok=True)
json.dump({"pack": {"description": "S6 Alternate Current comparison rigs", "min_format": [121, 0], "max_format": [121, 0]}},
          open(os.path.join(root, "pack.mcmeta"), "w"))
json.dump({"values": ["s6ac:tick"]}, open(os.path.join(root, "data", "minecraft", "tags", "function", "tick.json"), "w"))

# Comparator rigs: side input distance d -> side power 16-d, rear 15, expected subtract output 15-(16-d) = d-1.
COMP_D = [1, 4, 8, 12, 15]
COMP_X = [40 + 6 * i for i in range(len(COMP_D))]
CZ = 20

build = [
    "forceload add -16 -16 80 64",
    f"fill -10 {Y - 1} -10 75 {Y - 1} 60 minecraft:stone",
    f"fill -10 {Y} -10 75 {Y + 4} 60 minecraft:air",
    # A: piston door. Input at (0,Y,0); dust (1..6,Y,0) into stone (7,Y,0), which powers 3 sticky pistons.
    f"fill 1 {Y} 0 6 {Y} 0 minecraft:redstone_wire",
    f"setblock 7 {Y} 0 minecraft:stone",
    f"setblock 7 {Y} 1 minecraft:sticky_piston[facing=south]",
    f"setblock 7 {Y} -1 minecraft:sticky_piston[facing=north]",
    f"setblock 8 {Y} 0 minecraft:sticky_piston[facing=east]",
    f"setblock 7 {Y} 2 minecraft:oak_planks",
    f"setblock 7 {Y} -2 minecraft:oak_planks",
    f"setblock 9 {Y} 0 minecraft:oak_planks",
    # A2: a longer dust line with pistons tapped along it, to expose dust update order.
    f"fill 0 {Y} 5 14 {Y} 5 minecraft:redstone_wire",
    f"setblock 15 {Y} 5 minecraft:sticky_piston[facing=east]",
    f"setblock 16 {Y} 5 minecraft:oak_planks",
    f"setblock 4 {Y} 6 minecraft:redstone_wire",
    f"setblock 4 {Y} 7 minecraft:sticky_piston[facing=south]",
    f"setblock 4 {Y} 8 minecraft:oak_planks",
    f"setblock 9 {Y} 4 minecraft:redstone_wire",
    f"setblock 9 {Y} 3 minecraft:sticky_piston[facing=north]",
    f"setblock 9 {Y} 2 minecraft:oak_planks",
    # D: quasi-connectivity. Sticky piston at (0,Y,40) facing east, pushes planks at (1,Y,40).
    f"setblock 0 {Y} 40 minecraft:sticky_piston[facing=east]",
    f"setblock 1 {Y} 40 minecraft:oak_planks",
    # D2: QC through dust. Dust on glass (glass carries no power) at y+1 points into the space above the piston.
    f"setblock 10 {Y} 45 minecraft:sticky_piston[facing=east]",
    f"setblock 11 {Y} 45 minecraft:oak_planks",
    f"fill 5 {Y} 45 9 {Y} 45 minecraft:glass",
    f"fill 5 {Y + 1} 45 9 {Y + 1} 45 minecraft:redstone_wire",
]
for d, x in zip(COMP_D, COMP_X):
    build += [
        f"setblock {x - 1} {Y} {CZ} minecraft:redstone_block",
        f"setblock {x} {Y} {CZ} minecraft:comparator[facing=west,mode=subtract]",
        f"setblock {x + 1} {Y} {CZ} minecraft:redstone_wire",
        f"setblock {x + 2} {Y} {CZ} minecraft:redstone_wire",
        f"fill {x} {Y} {CZ - 1} {x} {Y} {CZ - d} minecraft:redstone_wire",
        f"setblock {x} {Y} {CZ - d - 1} minecraft:redstone_block",
    ]
build += ["scoreboard objectives add s6ac dummy", "scoreboard players set #t s6ac -1",
          "say s6ac built"]
open(os.path.join(fn, "build.mcfunction"), "w").write("\n".join(build) + "\n")

open(os.path.join(fn, "run.mcfunction"), "w").write("scoreboard players set #t s6ac 0\nsay s6ac run\n")

ev = {
    1: [f"setblock 0 {Y} 0 minecraft:redstone_block",       # A on
        f"setblock -1 {Y} 5 minecraft:redstone_block",      # A2 on
        f"setblock 0 {Y} 10 minecraft:observer[facing=east]",  # B: clock starts when the pair forms
        f"setblock 1 {Y} 10 minecraft:observer[facing=west]",
        f"setblock 1 {Y + 1} 40 minecraft:redstone_block",  # D: QC power, piston gets no update
        f"setblock 4 {Y + 1} 45 minecraft:redstone_block"],  # D2: dust lights up, points at the QC spot
    21: [f"setblock -1 {Y} 40 minecraft:stone",              # D: block update next to the piston
         f"setblock 10 {Y} 46 minecraft:stone"],             # D2: block update next to the piston
    41: [f"setblock 0 {Y} 0 minecraft:air",                  # A off
         f"setblock -1 {Y} 5 minecraft:air",
         f"setblock 1 {Y + 1} 40 minecraft:air",             # D off: QC piston should stay out until updated
         f"setblock 4 {Y + 1} 45 minecraft:air"],
    61: [f"setblock -1 {Y} 40 minecraft:air",
         f"setblock 10 {Y} 46 minecraft:air"],
}
tick = ["execute if score #t s6ac matches 0.. run scoreboard players add #t s6ac 1",
        "execute if score #t s6ac matches 81.. run scoreboard players set #t s6ac -1"]
for t, cmds in ev.items():
    tick += [f"execute if score #t s6ac matches {t} run {c}" for c in cmds]
tick.append("execute if score #t s6ac matches 1..80 run function s6ac:probe")
open(os.path.join(fn, "tick.mcfunction"), "w").write("\n".join(tick) + "\n")

probes = {
    "a1": f"block 7 {Y} 1 minecraft:sticky_piston[extended=true]",
    "a2": f"block 7 {Y} -1 minecraft:sticky_piston[extended=true]",
    "a3": f"block 8 {Y} 0 minecraft:sticky_piston[extended=true]",
    "e1": f"block 4 {Y} 7 minecraft:sticky_piston[extended=true]",
    "e2": f"block 9 {Y} 3 minecraft:sticky_piston[extended=true]",
    "e3": f"block 15 {Y} 5 minecraft:sticky_piston[extended=true]",
    "b1": f"block 0 {Y} 10 minecraft:observer[powered=true]",
    "b2": f"block 1 {Y} 10 minecraft:observer[powered=true]",
    "q1": f"block 0 {Y} 40 minecraft:sticky_piston[extended=true]",
    "q2": f"block 10 {Y} 45 minecraft:sticky_piston[extended=true]",
}
probe = ["execute store result storage s6ac:p t int 1 run scoreboard players get #t s6ac"]
probe += [f"execute store success storage s6ac:p {k} int 1 if {v}" for k, v in probes.items()]
for i, x in enumerate(COMP_X):
    probe.append(f"execute store result storage s6ac:p c{i} int 1 run data get block {x} {Y} {CZ} OutputSignal")
probe.append("function s6ac:log with storage s6ac:p")
open(os.path.join(fn, "probe.mcfunction"), "w").write("\n".join(probe) + "\n")
cs = ",".join(f"$(c{i})" for i in range(len(COMP_X)))
open(os.path.join(fn, "log.mcfunction"), "w").write(
    f"$say S6AC T$(t) A$(a1)$(a2)$(a3) E$(e1)$(e2)$(e3) B$(b1)$(b2) C{cs} Q$(q1)$(q2)\n")
print("wrote", root)
