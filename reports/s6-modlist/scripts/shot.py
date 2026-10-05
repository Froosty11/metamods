#!/usr/bin/env python3
"""Screenshots with a real vanilla 26.3 client (Xvfb + Mesa llvmpipe), joined to the kept benchmark world of a config.
Usage: shot.py CONFIG [VIEW_ID ...]   views from ../shots/views.json, output ../shots/<config>/<view>.jpg"""
import json,os,sys,time,subprocess,glob,re,shutil,hashlib
H=os.path.dirname(os.path.abspath(__file__)); S=H+"/../shots"
JAVA=glob.glob("/opt/jdk/jdk-25*/bin/java")[0]; CL="/srv/mc/client"; GAME=CL+"/game"; DISP=":98"
cfg=sys.argv[1]; VIEWS=json.load(open(S+"/views.json"))["views"]
want=sys.argv[2:] or [v["id"] for v in VIEWS]
CONF=json.load(open(H+"/configs.json")).get(cfg,{"dims":["minecraft:overworld"]})
env=dict(os.environ,DISPLAY=DISP,LIBGL_ALWAYS_SOFTWARE="1",GALLIUM_DRIVER="llvmpipe",LP_NUM_THREADS="4")
def sh(*a,**k): return subprocess.run(a,capture_output=True,text=True,env=env,**k)
# --- server: kept r1 world of the config for each dimension it has
def server_dir(dim): return f"/srv/mc/runs/{cfg}/{dim}/r1"
procs=[]
def start_xvfb():
    if sh("xdpyinfo","-display",DISP).returncode!=0 if shutil.which("xdpyinfo") else True:
        procs.append(subprocess.Popen(["Xvfb",DISP,"-screen","0","1280x720x24","-nolisten","tcp"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)); time.sleep(2)
def client_cmd():
    V=json.load(open(f"{CL}/versions/26.3/26.3.json"))
    cp=[]
    for l in V["libraries"]:
        a=l.get("downloads",{}).get("artifact")
        if not a: continue
        p=f"{CL}/libraries/{a['path']}"
        if os.path.exists(p): cp.append(p)
    cp.append(f"{CL}/versions/26.3/26.3.jar")
    os.makedirs(GAME,exist_ok=True)
    open(GAME+"/options.txt","w").write("\n".join([
        "renderDistance:12","simulationDistance:6","fov:0.0","guiScale:2","maxFps:30","enableVsync:false",
        "pauseOnLostFocus:false","tutorialStep:none","onboardAccessibility:false","skipMultiplayerWarning:true",
        "joinedFirstServer:true","narrator:0","soundCategory_master:0.0","entityShadows:false","particles:2",
        "graphicsMode:1","renderClouds:\"false\"","ao:true","biomeBlendRadius:2","bobView:false","hideServerAddress:true",
        "darkMojangStudiosBackground:false","fullscreen:false","overrideWidth:1280","overrideHeight:720"])+"\n")
    return [JAVA,"-Xmx3G","-Djava.library.path="+CL+"/natives","-cp",":".join(cp),V["mainClass"],
            "--username","Shot","--version","26.3","--gameDir",GAME,"--assetsDir",CL+"/assets","--assetIndex",V["assetIndex"]["id"],
            "--uuid","00000000000000000000000000000001","--accessToken","0","--userType","legacy","--versionType","release",
            "--width","1280","--height","720","--quickPlayMultiplayer","127.0.0.1:25565"]
def grab(path):
    sh("import","-display",DISP,"-window","root","/tmp/shot_raw.png")
    sh("convert","/tmp/shot_raw.png","-resize","1280x720!","-quality","80","-strip",path)
def settle(maxwait=150,minwait=25):
    # wait until two captures 6 s apart are near-identical (chunk meshing done)
    t=time.time(); time.sleep(minwait); prev=None
    while time.time()-t<maxwait:
        sh("import","-display",DISP,"-window","root","-resize","160x90!","gray:/tmp/settle.raw")
        cur=open("/tmp/settle.raw","rb").read() if os.path.exists("/tmp/settle.raw") else b""
        if prev and len(cur)==len(prev) and sum(abs(a-b) for a,b in zip(cur,prev))/max(1,len(cur))<0.6: return round(time.time()-t)
        prev=cur; time.sleep(6)
    return round(time.time()-t)
os.makedirs(GAME,exist_ok=True)
log={"config":cfg,"views":{}}
# one server per config: use the overworld world if present, else the first dim's world (views in other dims tp across dims)
dims=[d for d in ["minecraft:overworld","minecraft:the_nether","minecraft:the_end"] if os.path.isdir(server_dir(d)+"/world")]
if not dims: sys.exit(f"no kept world for {cfg}")
srv=server_dir(dims[0])
props=open(srv+"/server.properties").read()
props=re.sub(r"view-distance=\d+","view-distance=12",props); open(srv+"/server.properties","w").write(props)
# worlds of other dims of the same config live in other run dirs: copy their dimension folders in
for d in dims[1:]:
    ns,name=d.split(":"); src=f"{server_dir(d)}/world/dimensions/{ns}/{name}"; dst=f"{srv}/world/dimensions/{ns}/{name}"
    if os.path.isdir(src): shutil.rmtree(dst,ignore_errors=True); shutil.copytree(src,dst)
slog=open(srv+"/shot_server.log","w")
sp=subprocess.Popen([JAVA,"-Xms4G","-Xmx4G","-jar","fabric-server-launch.jar","nogui"],cwd=srv,stdin=subprocess.PIPE,stdout=slog,stderr=subprocess.STDOUT,text=True)
procs.append(sp)
def con(c): sp.stdin.write(c+"\n"); sp.stdin.flush()
def L(): return open(srv+"/shot_server.log",errors="replace").read()
try:
    while "Done (" not in L():
        if sp.poll() is not None: raise SystemExit("server died")
        time.sleep(1)
    for c in ["time set 6000","weather clear 1000000","whitelist off","op Shot","kill @e[type=!player]"]: con(c)
    clog=open(GAME+"/client.log","w")
    cp=subprocess.Popen(client_cmd(),cwd=GAME,env=env,stdout=clog,stderr=subprocess.STDOUT); procs.append(cp)
    t=time.time()
    while "Shot joined the game" not in L():
        if cp.poll() is not None or time.time()-t>400: raise SystemExit("client failed to join; see "+GAME+"/client.log")
        time.sleep(2)
    log["join_s"]=round(time.time()-t)
    con("gamemode spectator Shot"); time.sleep(5)
    sh("xdotool","mousemove","640","360"); time.sleep(1)
    sh("xdotool","key","F1"); time.sleep(1)
    os.makedirs(f"{S}/{cfg}",exist_ok=True)
    for v in VIEWS:
        if v["id"] not in want: continue
        if v["dim"] not in CONF.get("dims",[]) and not (cfg in ("B0",) or v["dim"]=="minecraft:overworld" and "minecraft:overworld" in dims): continue
        if v["dim"] not in dims: continue
        con(f"execute in {v['dim']} run tp Shot {v['x']} {v['y']} {v['z']} {v['yaw']} {v['pitch']}")
        st=settle(maxwait=120,minwait=20)
        con("time set 6000"); con("weather clear 1000000"); time.sleep(4)  # 26.3 has no daylight-cycle gamerule; reset the clock right before capture
        out=f"{S}/{cfg}/{v['id']}.jpg"; grab(out)
        log["views"][v["id"]]={"settle_s":st,"bytes":os.path.getsize(out)}
        print(cfg,v["id"],st,"s",os.path.getsize(out)//1024,"KB",flush=True)
finally:
    try: con("stop")
    except Exception: pass
    for p in procs[::-1]:
        if p is not sp:
            p.terminate()
    try: sp.wait(timeout=90)
    except Exception: sp.kill()
    json.dump(log,open(f"{S}/{cfg}/shotlog.json","w") if os.path.isdir(f"{S}/{cfg}") else open("/dev/null","w"),indent=1)
