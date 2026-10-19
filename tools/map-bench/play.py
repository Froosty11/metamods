#!/usr/bin/env python3
"""Tick time during play-like load: no mapper vs squaremap vs BlueMap (no caves, Polymer patch).

play.py <variant> [--rep N] [--work DIR] [--explore-s 300] [--settled-s 300]

The map is fully rendered first (once per variant, kept in <work>/prerendered/<variant>), so a run
starts from the steady state of a live server. Each run then drives 4 Carpet fake players over RCON:

  explore  2 bots fly outward from the edge of the pregenerated area at 8 blocks/s (new chunks get
           generated, lit, saved and mapped); 2 bots drift around inside it placing a block a second
  settled  all 4 bots drift inside the pregenerated area placing a block a second (no generation;
           the mapper only has to keep up with edits and with chunks being loaded/saved)
  catchup  bots gone: seconds until BlueMap reports no pending tasks (squaremap has no such signal)

Every 5 s: /tick query (average, P50/P95/P99 over the last 100 ticks), process CPU and RSS.
Results: results/play-<variant>-r<rep>.json and a summary line on stdout.
"""
import json, math, os, re, shutil, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bench, rcon  # noqa: E402

CARPET = "fabric-carpet-26.3+v260915.jar"
CARPET_URL = "https://cdn.modrinth.com/data/TQTTVgYE/versions/yt9oDFOj/fabric-carpet-26.3%2Bv260915.jar"
CARPET_SHA1 = "564c46b80e3a98430f0265b0485833f335d94a8d"
MAPPER = {"none": None, "squaremap": "squaremap", "bluemap-nocaves": "bluemap-nocaves"}
THREADS = 2


def wait_done(pid, log, timeout=900):
    t = time.time()
    while not (log.exists() and "Done (" in log.read_text(errors="replace")):
        if not bench.alive(pid):
            raise RuntimeError("server died during boot")
        if time.time() - t > timeout:
            raise TimeoutError("boot")
        time.sleep(1)


def wait_mapper_idle(variant, pid, log, offset, timeout=3 * 3600):
    """Full render (squaremap: started by command) or BlueMap's own startup update, until idle."""
    t, state = time.time(), {"seen_task": False}
    while time.time() - t < timeout:
        time.sleep(3)
        if not bench.alive(pid):
            raise RuntimeError("server died while rendering")
        if variant == "squaremap":
            with open(log, "rb") as f:
                f.seek(offset)
                if b"Finished rendering map for minecraft:overworld" in f.read():
                    return time.time() - t
        elif bench.bluemap_idle(state):
            return time.time() - t
        elif not state["seen_task"] and time.time() - t > 120:
            return time.time() - t  # nothing to update (already rendered)
    raise TimeoutError("mapper never went idle")


def stop(pid):
    try:
        rcon.run("stop")
    except OSError:
        subprocess.run(["kill", str(pid)])
    for _ in range(180):
        if not bench.alive(pid):
            return
        time.sleep(1)
    subprocess.run(["kill", "-9", str(pid)])


def clone(src, dst):
    if dst.exists():
        shutil.rmtree(dst)
    dst.parent.mkdir(parents=True, exist_ok=True)
    flags = ["cp", "-cR"] if sys.platform == "darwin" else ["cp", "-a", "--reflink=auto"]
    subprocess.run(flags + [str(src), str(dst)], check=True)


def prerender(variant):
    """<work>/prerendered/<variant>: base + Carpet + mapper with the overworld fully rendered."""
    dst = bench.WORK / "prerendered" / variant
    if (dst / ".ready").exists():
        return dst
    dst.parent.mkdir(parents=True, exist_ok=True)
    if MAPPER[variant]:
        bench.setup(MAPPER[variant], THREADS, dst)
    else:
        clone(bench.WORK / "base", dst)
    shutil.copy(bench.WORK / "jars" / CARPET, dst / "mods" / CARPET)
    if MAPPER[variant]:
        subprocess.run([str(HERE / "start.sh"), str(dst)], check=True)
        pid, log = int((dst / "server.pid").read_text()), dst / "logs" / "latest.log"
        try:
            wait_done(pid, log)
            offset = log.stat().st_size
            if variant == "squaremap":
                time.sleep(10)
                rcon.run("squaremap fullrender minecraft:overworld")
            took = wait_mapper_idle(variant, pid, log, offset)
            print(f"prerendered {variant} in {took:.0f} s", flush=True)
        finally:
            stop(pid)
    for junk in ("logs", "console.log", "server.pid"):
        p = dst / junk
        shutil.rmtree(p) if p.is_dir() else p.unlink(missing_ok=True)
    (dst / ".ready").write_text("")
    return dst


class Bots:
    """4 spectator bots moved by /tp; explorers fly out along a ray, drifters circle inside the map."""

    def __init__(self):
        self.pos = {}

    def spawn(self):
        for i, (x, z) in enumerate([(900, 0), (-900, 0), (300, 300), (-300, -300)]):
            name = f"bot{i}"
            rcon.run(f"player {name} spawn at {x} 200 {z} facing 0 0 in minecraft:overworld in spectator")
            self.pos[name] = (x, z)
        time.sleep(5)
        online = rcon.run("list")
        if not all(n in online.lower() for n in self.pos):
            raise RuntimeError(f"bots did not spawn: {online}")

    def step(self, t, explore):
        replies = set()
        for i, name in enumerate(self.pos):
            if explore and i < 2:
                x = (900 + 8 * t) * (1 if i == 0 else -1)
                z = 0
            else:
                # drift: circle of radius 400 around a fixed centre, ~2 blocks/s
                cx, cz = [(500, 0), (-500, 0), (300, 300), (-300, -300)][i]
                a = (t * 2 / 400) + i
                x, z = int(cx + 400 * math.cos(a)), int(cz + 400 * math.sin(a))
                replies.add(rcon.run(f"setblock {x} 199 {z} minecraft:gold_block")[:60])
            rcon.run(f"tp {name} {int(x)} 200 {int(z)}")
            self.pos[name] = (x, z)
        return replies

    def kill(self):
        for name in self.pos:
            rcon.run(f"player {name} kill")


def phase(name, pid, seconds, bots, explore, t_start):
    samples, replies = [], set()
    c0, t0 = bench.cpu_seconds(pid), time.time()
    next_sample = t0 + 5
    while time.time() - t0 < seconds:
        replies |= bots.step(time.time() - t_start, explore)
        time.sleep(1)
        if time.time() >= next_sample:
            samples.append({"t": round(time.time() - t0), "tick": bench.tick_query(),
                            "cpu": round(bench.cpu_seconds(pid) - c0, 1), "rss": round(bench.rss_mb(pid))})
            next_sample += 5
    ticks = [s["tick"] for s in samples if s["tick"] and "P95" in s["tick"]]
    avg = [s.get("Average time per tick") for s in ticks if s.get("Average time per tick") is not None]
    return {
        "seconds": round(time.time() - t0),
        "cores_used": round((bench.cpu_seconds(pid) - c0) / (time.time() - t0), 2),
        "mspt_avg": round(sum(avg) / len(avg), 2) if avg else None,
        "mspt_p95_mean": round(sum(s["P95"] for s in ticks) / len(ticks), 2) if ticks else None,
        "mspt_p99_max": max((s["P99"] for s in ticks), default=None),
        "samples_over_50ms_avg": sum(1 for a in avg if a > 50),
        "setblock_replies": sorted(replies),
        "samples": samples,
    }


def main():
    args = sys.argv[1:]
    opt = lambda k, d: args[args.index(k) + 1] if k in args else d  # noqa: E731
    variant, rep = args[0], int(opt("--rep", "1"))
    bench.WORK = Path(opt("--work", os.environ.get("MAP_BENCH_WORK", str(Path.home() / "map-bench"))))
    explore_s, settled_s = int(opt("--explore-s", "300")), int(opt("--settled-s", "300"))
    jar = bench.WORK / "jars" / CARPET
    if not jar.exists():
        subprocess.run(["curl", "-sSfL", "-o", str(jar), CARPET_URL], check=True)
    if subprocess.run(["shasum" if sys.platform == "darwin" else "sha1sum", str(jar)],
                      capture_output=True, text=True).stdout.split()[0] != CARPET_SHA1:
        raise SystemExit("carpet sha1 mismatch")

    src = prerender(variant)
    name = f"play-{variant}-r{rep}"
    run = bench.WORK / "runs" / name
    clone(src, run)
    subprocess.run([str(HERE / "start.sh"), str(run)], check=True)
    pid, log = int((run / "server.pid").read_text()), run / "logs" / "latest.log"
    result = {"variant": variant, "rep": rep, "threads": THREADS, "explore_s": explore_s, "settled_s": settled_s}
    try:
        wait_done(pid, log)
        offset = log.stat().st_size
        if variant == "bluemap-nocaves":
            result["startup_update_s"] = round(wait_mapper_idle(variant, pid, log, offset))
        time.sleep(30)
        bots = Bots()
        bots.spawn()
        time.sleep(30)  # let the bots' first view-distance load settle
        t_start = time.time()
        result["explore"] = phase("explore", pid, explore_s, bots, True, t_start)
        result["settled"] = phase("settled", pid, settled_s, bots, False, t_start)
        bots.kill()
        if variant == "bluemap-nocaves":
            t = time.time()
            state = {"seen_task": False}
            while not bench.bluemap_idle(state) and time.time() - t < 1800:
                if not state["seen_task"] and time.time() - t > 30:
                    break
                time.sleep(3)
            result["catchup_s"] = round(time.time() - t)
        result["output_mb"] = round(bench.du_mb(run / ("squaremap/web/tiles" if variant == "squaremap"
                                                       else "bluemap/web/maps/world")), 1)
    except Exception as e:
        result["error"] = repr(e)
    finally:
        stop(pid)
        res = HERE / "results"
        res.mkdir(exist_ok=True)
        (res / f"{name}.json").write_text(json.dumps(result, indent=2))
        if log.exists():
            shutil.copy(log, res / f"{name}.log")
        brief = {k: v for k, v in result.items() if k not in ("explore", "settled")}
        for p in ("explore", "settled"):
            if p in result:
                brief[p] = {k: v for k, v in result[p].items() if k not in ("samples", "setblock_replies")}
        print(json.dumps(brief), flush=True)
        if "--keep" not in args:
            shutil.rmtree(run, ignore_errors=True)


if __name__ == "__main__":
    main()
