"""Download vanilla client 26.3 (jar, libraries, assets) from Mojang's launcher meta into /srv/mc/client."""
import json,os,urllib.request,hashlib,concurrent.futures as cf
M="/srv/mc/client"; UA={"User-Agent":"metacraft-s6-bench/1.0"}
def get(u): return urllib.request.urlopen(urllib.request.Request(u,headers=UA),timeout=120).read()
def dl(u,p,sha=None):
    if os.path.exists(p) and (not sha or hashlib.sha1(open(p,"rb").read()).hexdigest()==sha): return 0
    os.makedirs(os.path.dirname(p),exist_ok=True); d=get(u)
    if sha: assert hashlib.sha1(d).hexdigest()==sha,u
    open(p+".tmp","wb").write(d); os.replace(p+".tmp",p); return len(d)
man=json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
vu=[v for v in man["versions"] if v["id"]=="26.3"][0]["url"]
V=json.loads(get(vu)); os.makedirs(f"{M}/versions/26.3",exist_ok=True); json.dump(V,open(f"{M}/versions/26.3/26.3.json","w"))
jobs=[(V["downloads"]["client"]["url"],f"{M}/versions/26.3/26.3.jar",V["downloads"]["client"]["sha1"])]
for l in V["libraries"]:
    a=l.get("downloads",{}).get("artifact")
    rules=l.get("rules")
    if rules and not any(r["action"]=="allow" and r.get("os",{}).get("name") in (None,"linux") for r in rules): continue
    if rules and any(r["action"]=="disallow" and r.get("os",{}).get("name")=="linux" for r in rules): continue
    if a: jobs.append((a["url"],f"{M}/libraries/{a['path']}",a["sha1"]))
ai=V["assetIndex"]; dl(ai["url"],f"{M}/assets/indexes/{ai['id']}.json",ai["sha1"])
for k,o in json.load(open(f"{M}/assets/indexes/{ai['id']}.json"))["objects"].items():
    h=o["hash"]; jobs.append((f"https://resources.download.minecraft.net/{h[:2]}/{h}",f"{M}/assets/objects/{h[:2]}/{h}",h))
print(len(jobs),"files")
with cf.ThreadPoolExecutor(4) as ex: tot=sum(ex.map(lambda j:dl(*j),jobs))
print("downloaded",tot//1e6,"MB")
