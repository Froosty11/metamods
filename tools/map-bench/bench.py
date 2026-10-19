#!/usr/bin/env python3
"""Map renderer benchmark on a frozen, pregenerated S6 world (built by setup.sh).

bench.py <variant> [--threads N] [--rep N] [--work DIR]

Each run clones <work>/base (so the world is byte-identical), drops in the mapper jars and config,
starts the server headless and measures three phases into results/<variant>-t<N>-r<rep>.json:

  full         full render of the overworld: wall time, process CPU seconds, peak RSS, MSPT
  idle         60 s after the full render, mapper idle (also the baseline subtracted below)
  incremental  64 blocks set in 64 chunks + save-all flush, then a 150 s window: CPU above idle

BlueMap starts its own full update as soon as it has loaded (just after "Done"), so its clock starts
at "Done" and includes its resource loading; squaremap's starts at /squaremap fullrender.
"""
import json, os, re, shutil, subprocess, sys, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import rcon  # noqa: E402

SQUAREMAP = "squaremap-fabric-mc26.3-1.4.3.jar"
BLUEMAP = "bluemap-5.28-fabric.jar"
PATCH = "BlueMapPolymerPatch-1.2.1+26.3.jar"
CLK = os.sysconf("SC_CLK_TCK") if hasattr(os, "sysconf") else 100


def cpu_seconds(pid):
    stat = Path(f"/proc/{pid}/stat")
    if stat.exists():
        f = stat.read_text().rsplit(")", 1)[1].split()
        return (int(f[11]) + int(f[12])) / CLK  # utime + stime, all threads
    t = subprocess.run(["ps", "-o", "time=", "-p", str(pid)], capture_output=True, text=True).stdout.strip()
    s = 0.0
    for p in t.replace("-", ":").split(":"):
        s = s * 60 + float(p)
    return s


def rss_mb(pid):
    t = subprocess.run(["ps", "-o", "rss=", "-p", str(pid)], capture_output=True, text=True).stdout.strip()
    return int(t) / 1024 if t else 0


def du_mb(path):
    if not path.exists():
        return 0.0
    return int(subprocess.run(["du", "-sk", str(path)], capture_output=True, text=True).stdout.split()[0]) / 1024


def file_count(path):
    return sum(len(f) for _, _, f in os.walk(path)) if path.exists() else 0


def alive(pid):
    return subprocess.run(["kill", "-0", str(pid)], capture_output=True).returncode == 0


def tick_query():
    """Vanilla /tick query: average and percentile ms per tick over the last 100 ticks."""
    try:
        out = re.sub(r"§.", "", rcon.run("tick query"))
    except OSError:
        return None
    nums = {k: float(v) for k, v in re.findall(r"(P50|P95|P99|[Aa]verage[^:]*): ?([\d.]+)", out)}
    return nums or {"raw": out[-200:]}


def setup(variant, threads, run):
    if run.exists():
        shutil.rmtree(run)
    run.parent.mkdir(parents=True, exist_ok=True)
    clone = ["cp", "-cR"] if sys.platform == "darwin" else ["cp", "-a", "--reflink=auto"]
    subprocess.run(clone + [str(WORK / "base"), str(run)], check=True)
    spec = VARIANTS[variant]
    for jar in spec["jars"]:
        shutil.copy(WORK / "jars" / jar, run / "mods" / jar)
    if "bluemap" in spec:
        cfg = run / "config" / "bluemap"
        shutil.copytree(HERE / "configs" / "bluemap", cfg)
        (run / "bluemap").mkdir(exist_ok=True)
        shutil.copy(WORK / "jars" / "minecraft-client-26.3.jar", run / "bluemap" / "minecraft-client-26.3.jar")
        if threads:
            core = cfg / "core.conf"
            core.write_text(re.sub(r"render-thread-count:.*", f"render-thread-count: {threads}", core.read_text()))
        mp = cfg / "maps" / "world.conf"
        text = mp.read_text()
        for k, v in spec["bluemap"].items():
            if k == "render-mask":
                text = re.sub(r"^render-mask: \[.*?^\]", f"render-mask: [ {v} ]", text, flags=re.M | re.S)
            elif re.search(rf"^{re.escape(k)}:", text, re.M):
                text = re.sub(rf"^{re.escape(k)}:.*$", f"{k}: {v}", text, flags=re.M)
            else:
                text += f"\n{k}: {v}\n"
        mp.write_text(text)
        st = cfg / "storages" / "file.conf"
        for k, v in spec.get("storage", {}).items():
            st.write_text(re.sub(rf"^{k}:.*$", f"{k}: {v}", st.read_text(), flags=re.M))
    if "squaremap" in spec:
        shutil.copytree(HERE / "configs" / "squaremap", run / "squaremap")
        cfgf = run / "squaremap" / "config.yml"
        text = cfgf.read_text()
        if threads:
            text = text.replace("workers: default", f"workers: {threads}")
        cfgf.write_text(text)


def bluemap_idle(state):
    """True once BlueMap has had a task and now reports none pending."""
    try:
        out = re.sub(r"§.", "", rcon.run("bluemap tasks"))
    except OSError:
        return False
    if "all done" not in out:
        state["seen_task"] = True
        return False
    return state["seen_task"]


def main():
    global WORK
    args = sys.argv[1:]
    opt = lambda k, d: args[args.index(k) + 1] if k in args else d  # noqa: E731
    variant, threads, rep = args[0], int(opt("--threads", "2")), int(opt("--rep", "1"))
    WORK = Path(opt("--work", os.environ.get("MAP_BENCH_WORK", str(Path.home() / "map-bench"))))
    spec = VARIANTS[variant]
    name = f"{variant}-t{threads or 'default'}-r{rep}"
    run = WORK / "runs" / name
    setup(variant, threads, run)
    subprocess.run([str(HERE / "start.sh"), str(run)], check=True)
    pid = int((run / "server.pid").read_text())
    log = run / "logs" / "latest.log"
    result = {"variant": variant, "name": name, "threads": threads, "rep": rep, "desc": spec["desc"]}
    try:
        t_start = time.time()
        while not (log.exists() and "Done (" in log.read_text(errors="replace")):
            if not alive(pid):
                raise RuntimeError("server died during boot")
            if time.time() - t_start > 900:
                raise TimeoutError("boot")
            time.sleep(1)
        result["boot_s"] = round(time.time() - t_start, 1)
        offset = log.stat().st_size
        if "squaremap" in spec:
            time.sleep(15)
            result["mspt_before"] = tick_query()
            rcon.run("squaremap fullrender minecraft:overworld")
        cpu0, t0 = cpu_seconds(pid), time.time()

        # Phase 1: full render
        state, samples, peak = {"seen_task": False}, [], 0
        next_sample = t0 + 15
        while True:
            time.sleep(1)
            if not alive(pid):
                raise RuntimeError("server died during render")
            peak = max(peak, rss_mb(pid))
            now = time.time()
            if now >= next_sample:
                samples.append({"t": round(now - t0), "cpu": round(cpu_seconds(pid) - cpu0, 1), "tick": tick_query()})
                next_sample += 15
            if now - t0 > 5 * 3600:
                raise TimeoutError("render > 5h")
            if "squaremap" in spec:
                with open(log, "rb") as f:
                    f.seek(offset)
                    if b"Finished rendering map for minecraft:overworld" in f.read():
                        break
            elif int(now - t0) % 3 == 0 and bluemap_idle(state):
                break
        result["full"] = {
            "wall_s": round(time.time() - t0, 1),
            "cpu_s": round(cpu_seconds(pid) - cpu0, 1),
            "peak_rss_mb": round(peak),
            "samples": samples,
        }

        # Phase 2: idle baseline
        time.sleep(20)
        c, t = cpu_seconds(pid), time.time()
        time.sleep(60)
        idle = (cpu_seconds(pid) - c) / (time.time() - t)
        result["idle"] = {"cpu_per_s": round(idle, 3), "tick": tick_query()}

        # Phase 3: incremental update after 64 chunk changes inside the force-loaded area
        c, t = cpu_seconds(pid), time.time()
        replies = set()
        for i in range(8):
            for j in range(8):
                replies.add(rcon.run(f"setblock {-120 + 32 * i} 200 {-120 + 32 * j} minecraft:gold_block")[:80])
        rcon.run("save-all flush")
        ticks = []
        while time.time() - t < 150:
            time.sleep(15)
            ticks.append(tick_query())
        dt = time.time() - t
        result["incremental"] = {
            "window_s": round(dt),
            "cpu_s_above_idle": round(cpu_seconds(pid) - c - idle * dt, 1),
            "ticks": ticks,
            "setblock_replies": sorted(replies),
        }

        time.sleep(5)
        out = run / ("squaremap/web/tiles" if "squaremap" in spec else "bluemap/web/maps/world")
        result["output_mb"] = round(du_mb(out), 1)
        result["output_files"] = file_count(out)
    except Exception as e:  # keep partial results
        result["error"] = repr(e)
    finally:
        try:
            rcon.run("stop")
        except OSError:
            subprocess.run(["kill", str(pid)])
        for _ in range(120):
            if not alive(pid):
                break
            time.sleep(1)
        if alive(pid):
            subprocess.run(["kill", "-9", str(pid)])
        res = HERE / "results"
        res.mkdir(exist_ok=True)
        (res / f"{name}.json").write_text(json.dumps(result, indent=2))
        shutil.copy(log, res / f"{name}.log") if log.exists() else None
        brief = {k: v for k, v in result.items() if k not in ("full", "incremental", "idle")}
        brief.update({k: {kk: vv for kk, vv in v.items() if kk not in ("samples", "ticks")}
                      for k, v in result.items() if k in ("full", "incremental", "idle")})
        print(json.dumps(brief))
        if "--keep" not in args:
            shutil.rmtree(run, ignore_errors=True)


def bm(desc, jars=(BLUEMAP, PATCH), storage=None, **cfg):
    v = {"desc": desc, "jars": list(jars), "bluemap": {k.replace("_", "-"): x for k, x in cfg.items()}}
    if storage:
        v["storage"] = storage
    return v


FLAT = dict(enable_perspective_view="false", enable_free_flight_view="false")
LEAN = dict(**FLAT, remove_caves_below_y=10000, render_mask="{ min-y: 0 }", render_edges="false")
VARIANTS = {
    "squaremap": {"desc": "squaremap 1.4.3, default config", "jars": [SQUAREMAP], "squaremap": {}},
    "bluemap": bm("BlueMap 5.28 + Polymer patch 1.2.1, default map config"),
    "bluemap-nopatch": bm("BlueMap 5.28 alone, default map config", jars=(BLUEMAP,)),
    "bluemap-flat": bm("flat view only (perspective + free-flight off)", **FLAT),
    "bluemap-nocaves": bm("remove-caves-below-y: 10000 (no caves anywhere)", remove_caves_below_y=10000),
    "bluemap-miny": bm("render-mask min-y: 0, render-edges false", render_mask="{ min-y: 0 }", render_edges="false"),
    "bluemap-lowres": bm("enable-hires: false (lowres layer only)", enable_hires="false"),
    "bluemap-zstd": bm("storage compression zstd", storage={"compression": "zstd"}),
    "bluemap-lean": bm("flat only + no caves + min-y 0", **LEAN),
    "bluemap-lowres-lean": bm("lean + enable-hires: false (closest to squaremap)", **LEAN, enable_hires="false"),
}

if __name__ == "__main__":
    main()
