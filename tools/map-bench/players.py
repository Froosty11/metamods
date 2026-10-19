#!/usr/bin/env python3
"""TPS with simulated players: Carpet fake players exploring while a mapper works.

players.py <variant|none> [--threads N] [--bots 4] [--minutes 10] [--speed 6] [--rep N] [--work DIR]

Clones <work>/base like bench.py, adds Carpet, starts the server and (for squaremap) a full render;
BlueMap starts its own. Then spawns <bots> spectator fake players at spawn that travel diagonally
outward at <speed> blocks/s (tp once a second): first through the pregenerated area, then into new
terrain, so the server is loading, generating and ticking chunks the way exploring players make it.
`/tick query` every 5 s (it covers the last 100 ticks, so the samples tile the whole window).
Writes results/players/<variant>-t<N>-r<rep>.json.
"""
import json, math, os, re, shutil, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bench, rcon  # noqa: E402

CARPET = "fabric-carpet-26.3+v260915.jar"
VARIANTS = dict(bench.VARIANTS, none={"desc": "no mapper", "jars": []})


def main():
    args = sys.argv[1:]
    opt = lambda k, d: args[args.index(k) + 1] if k in args else d  # noqa: E731
    variant, threads, rep = args[0], int(opt("--threads", "2")), int(opt("--rep", "1"))
    bots, minutes, speed = int(opt("--bots", "4")), float(opt("--minutes", "10")), float(opt("--speed", "6"))
    bench.WORK = Path(opt("--work", os.environ.get("MAP_BENCH_WORK", str(Path.home() / "map-bench"))))
    bench.VARIANTS = VARIANTS
    spec = VARIANTS[variant]
    name = f"{variant}-t{threads or 'default'}-r{rep}"
    run = bench.WORK / "runs" / f"players-{name}"
    bench.setup(variant, threads, run)
    shutil.copy(bench.WORK / "jars" / CARPET, run / "mods" / CARPET)
    subprocess.run([str(HERE / "start.sh"), str(run)], check=True)
    pid = int((run / "server.pid").read_text())
    log = run / "logs" / "latest.log"
    result = {"variant": variant, "name": name, "threads": threads, "rep": rep, "desc": spec["desc"],
              "bots": bots, "minutes": minutes, "speed": speed}
    try:
        t_start = time.time()
        while not (log.exists() and "Done (" in log.read_text(errors="replace")):
            if not bench.alive(pid):
                raise RuntimeError("server died during boot")
            if time.time() - t_start > 900:
                raise TimeoutError("boot")
            time.sleep(1)
        time.sleep(15)
        result["mspt_before"] = bench.tick_query()
        if "squaremap" in spec:
            rcon.run("squaremap fullrender minecraft:overworld")
        offset = log.stat().st_size
        dirs = [(math.cos(math.radians(45 + 360 * i / bots)), math.sin(math.radians(45 + 360 * i / bots)))
                for i in range(bots)]
        result["spawn_replies"] = [rcon.run(f"player bot{i} spawn at 0 120 0 facing 0 0 in minecraft:overworld"
                                            f" in spectator")[:120] for i in range(bots)]
        cpu0 = t0 = None
        samples, next_sample = [], None
        while True:
            now = time.time()
            if t0 is None:
                cpu0, t0, next_sample = bench.cpu_seconds(pid), now, now + 5
            el = now - t0
            if el >= minutes * 60:
                break
            if not bench.alive(pid):
                raise RuntimeError("server died during player phase")
            for i, (dx, dz) in enumerate(dirs):
                rcon.run(f"tp bot{i} {dx * speed * el:.1f} 120 {dz * speed * el:.1f}")
            if now >= next_sample:
                samples.append({"t": round(el), "cpu": round(bench.cpu_seconds(pid) - cpu0, 1),
                                "tick": bench.tick_query()})
                next_sample += 5
            time.sleep(max(0.0, 1 - (time.time() - now)))
        result["cpu_s"] = round(bench.cpu_seconds(pid) - cpu0, 1)
        result["samples"] = samples
        with open(log, "rb") as f:
            f.seek(offset)
            tail = f.read().decode(errors="replace")
        result["cant_keep_up"] = len(re.findall(r"Can't keep up", tail))
        result["ticks_behind"] = sum(int(n) for n in re.findall(r"Running \d+ms or (\d+) ticks behind", tail))
        if "bluemap" in spec:
            result["bluemap_tasks_end"] = re.sub(r"§.", "", rcon.run("bluemap tasks"))[:400]
        for i in range(bots):
            rcon.run(f"player bot{i} kill")
    except Exception as e:  # keep partial results
        result["error"] = repr(e)
    finally:
        try:
            rcon.run("stop")
        except OSError:
            subprocess.run(["kill", str(pid)])
        for _ in range(120):
            if not bench.alive(pid):
                break
            time.sleep(1)
        if bench.alive(pid):
            subprocess.run(["kill", "-9", str(pid)])
        res = HERE / "results" / "players"
        res.mkdir(parents=True, exist_ok=True)
        ticks = [s["tick"] for s in result.get("samples", []) if s.get("tick") and "P95" in s["tick"]]
        if ticks:
            avg = [t["Average time per tick"] for t in ticks]
            result["summary"] = {
                "mspt_avg": round(sum(avg) / len(avg), 1),
                "mspt_p95_mean": round(sum(t["P95"] for t in ticks) / len(ticks), 1),
                "mspt_worst_avg": max(avg),
                "p99_max": max(t["P99"] for t in ticks),
                "tps_mean": round(sum(min(20.0, 1000 / a) for a in avg) / len(avg), 2),
                "tps_min": round(min(min(20.0, 1000 / a) for a in avg), 2),
                "samples_below_20tps": sum(a > 50 for a in avg),
                "n_samples": len(avg),
            }
        (res / f"{name}.json").write_text(json.dumps(result, indent=2))
        shutil.copy(log, res / f"{name}.log") if log.exists() else None
        print(json.dumps({k: v for k, v in result.items() if k not in ("samples",)}))
        if "--keep" not in args:
            shutil.rmtree(run, ignore_errors=True)


if __name__ == "__main__":
    main()
