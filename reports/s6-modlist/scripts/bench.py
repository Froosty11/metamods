#!/usr/bin/env python3
"""One benchmark run: fresh world, Chunky pregen of a square radius R around 0,0 in one dimension,
sampling mspt (vanilla /tick query), CPU (/proc), heap (jstat), spark tps; spark profiler for the pregen.
Usage: bench.py CONFIG DIM RUN_IDX RADIUS [--keep]   (mods from configs.json)"""
import json,os,sys,time,re,subprocess,shutil,glob,statistics
sys.path.insert(0,os.path.dirname(__file__))
from rcon import Rcon
from mkserver import make
HERE=os.path.dirname(os.path.abspath(__file__))
OUT=os.path.join(HERE,"..","raw")
SEED=int(open(os.path.join(HERE,"SEED")).read())
JAVA=glob.glob("/opt/jdk/jdk-25*/bin/java")[0]; JSTAT=JAVA.replace("/java","/jstat")
JVM=["-Xms6G","-Xmx6G","-XX:+UseG1GC","-XX:+ParallelRefProcEnabled","-XX:MaxGCPauseMillis=200","-XX:+AlwaysPreTouch"]
cfg,dim,idx,R=sys.argv[1],sys.argv[2],int(sys.argv[3]),int(sys.argv[4]); keep="--keep" in sys.argv
CONF=json.load(open(os.path.join(HERE,"configs.json")))[cfg]
B0=["fabric-api","c2me-fabric","distanthorizons","spark","chunky"]
mods=B0+CONF["mods"]
run=f"/srv/mc/runs/{cfg}/{dim}/r{idx}"
make(run,mods,SEED)
log=open(run+"/out.log","w")
t_launch=time.time()
p=subprocess.Popen([JAVA]+JVM+["-jar","fabric-server-launch.jar","nogui"],cwd=run,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE)
def L(): return open(run+"/out.log",errors="replace").read()
res={"config":cfg,"dimension":dim,"run":idx,"seed":SEED,"radius":R,"mods":mods,"jvm":JVM,"status":"error"}
def finish(status):
    res["status"]=status
    os.makedirs(OUT,exist_ok=True)
    json.dump(res,open(f"{OUT}/{cfg}__{dim.split(':')[1]}__r{idx}.json","w"),indent=1)
    print(json.dumps({k:v for k,v in res.items() if k not in("samples","mods_loaded","jvm","mods","verify")}))
try:
    while True:
        s=L()
        if re.search(r"Done \([\d.]+s\)!",s): break
        if p.poll() is not None or "Crash report" in s or time.time()-t_launch>600:
            res["error"]="server failed to start"; res["log_tail"]=s[-4000:]; p.kill(); finish("start_failed"); sys.exit(1)
        time.sleep(1)
    res["startup_s"]=round(time.time()-t_launch,1)
    m=re.search(r"Loading (\d+) mods:\n((?:\t.*\n)+)",L()); res["mods_loaded"]=m.group(2).split("\n") if m else []
    r=Rcon()
    r.cmd("gamerule doDaylightCycle false"); r.cmd("gamerule doWeatherCycle false")
    r.cmd("spark profiler start --thread *"); time.sleep(3)
    for c in [f"chunky world {dim}","chunky center 0 0",f"chunky radius {R}","chunky shape square","chunky quiet 30"]: r.cmd(c)
    pid=p.pid; clk=os.sysconf("SC_CLK_TCK")
    def cpu():
        f=open(f"/proc/{pid}/stat").read().rsplit(")",1)[1].split(); return (int(f[11])+int(f[12]))/clk
    def heap():
        try:
            o=subprocess.run([JSTAT,"-gc",str(pid)],capture_output=True,text=True,timeout=10).stdout.split("\n")
            h=dict(zip(o[0].split(),o[1].split())); return sum(float(h[k]) for k in ("S0U","S1U","EU","OU"))/1024
        except Exception: return None
    t0=time.time(); c0=cpu(); r.cmd("chunky start"); samples=[]; last_tps=0
    fin_re=re.compile(r"Task finished for "+re.escape(dim)+r"\. Processed: (\d+) chunks.*Total time: ([\d:]+)")
    while True:
        time.sleep(5)
        tq=r.cmd("tick query"); m=re.search(r"Average time per tick: ([\d.]+)ms",tq)
        samples.append({"t":round(time.time()-t0,1),"mspt":float(m.group(1)) if m else None,"cpu_s":cpu(),"heap_mb":heap()})
        if time.time()-last_tps>15: p.stdin.write(b"spark tps\n"); p.stdin.flush(); last_tps=time.time()
        fm=fin_re.search(L())
        if fm: break
        if p.poll() is not None: raise RuntimeError("server died")
        if time.time()-t0>3600: raise RuntimeError("timeout")
    t1=time.time(); c1=cpu()
    res["chunks"]=int(fm.group(1)); res["chunky_total_time"]=fm.group(2)
    res["wall_s"]=round(t1-t0,1); res["chunks_per_s"]=round(res["chunks"]/res["wall_s"],2)
    res["cpu_pct_of_machine"]=round(100*(c1-c0)/res["wall_s"]/os.cpu_count(),1)
    ms=[x["mspt"] for x in samples if x["mspt"] is not None]; res["mean_mspt"]=round(statistics.mean(ms),2) if ms else None
    hp=[x["heap_mb"] for x in samples if x["heap_mb"]]; res["peak_heap_mb"]=round(max(hp)) if hp else None
    res["samples"]=samples
    r.cmd("spark health --upload"); r.cmd("spark profiler stop"); time.sleep(4)
    # DH tail: does LOD work continue after Chunky? measure CPU over 20s idle window
    ca=cpu(); time.sleep(20); res["post_cpu_pct_20s"]=round(100*(cpu()-ca)/20/os.cpu_count(),1)
    # verification
    res["verify"]={}
    for v in CONF.get("verify",[]):
        res["verify"][v]=r.cmd(f"execute in {dim} positioned 0 100 0 run {v}")[:300]
    for _ in range(30):
        links=re.findall(r"https://spark\.lucko\.me/(?!docs)\w+",L())
        if len(links)>=2: break
        time.sleep(2)
    res["spark_links"]=links
    lg=L()
    res["tps_samples"]=[float(a) for a in re.findall(r"TPS from last 5s.*\n.*?\[⚡\]\s+\*?([\d.]+)",lg)]
    res["spark_mspt_med_samples"]=[float(a) for a in re.findall(r"Tick durations.*\n.*?\[⚡\]\s+[\d.]+/([\d.]+)/",lg)]
    res["spark_proc_cpu_samples"]=[float(a) for a in re.findall(r"\[⚡\]\s+(\d+)%, \d+%, \d+%\s+\(process\)",lg)]
    res["mean_tps"]=round(statistics.mean(res["tps_samples"]),2) if res["tps_samples"] else None
    r.cmd("stop")
    p.wait(timeout=180)
    dh=glob.glob(f"{run}/world/dimensions/*/*/data/DistantHorizons.sqlite"); res["dh_sqlite_mb"]=round(sum(os.path.getsize(x) for x in dh)/1e6,1)
    res["world_mb"]=round(sum(os.path.getsize(os.path.join(a,f)) for a,_,fs in os.walk(run+"/world") for f in fs)/1e6,1)
    os.makedirs("/srv/mc/logs",exist_ok=True); shutil.copy(run+"/out.log",f"/srv/mc/logs/{cfg}__{dim.split(':')[1]}__r{idx}.log")
    finish("ok")
except Exception as e:
    res["error"]=repr(e); res["log_tail"]=L()[-3000:]
    try: p.kill()
    except Exception: pass
    finish("failed")
finally:
    if p.poll() is None: p.kill()
    if not keep and os.path.exists(run+"/world"): shutil.rmtree(run+"/world")
