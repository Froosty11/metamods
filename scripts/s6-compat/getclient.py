#!/usr/bin/env python3
"""Download the vanilla 26.3 client (jar, libraries, natives, assets) into ./client for a headless join test."""
import json, os, hashlib, urllib.request, concurrent.futures as cf

UA = {"User-Agent": "x"}


def get(u):
    return urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=60).read()


def fetch(u, p, sha1=None):
    if os.path.exists(p) and (not sha1 or hashlib.sha1(open(p, 'rb').read()).hexdigest() == sha1):
        return
    os.makedirs(os.path.dirname(p), exist_ok=True)
    err = None
    for _ in range(4):
        try:
            d = get(u)
            if sha1 and hashlib.sha1(d).hexdigest() != sha1:
                err = "sha1 mismatch"
                continue
            open(p, "wb").write(d)
            return
        except Exception as e:
            err = e
    raise RuntimeError(f"{u}: {err}")


def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        name = r.get("os", {}).get("name")
        if name in (None, "linux"):
            ok = r["action"] == "allow"
    return ok


man = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
ver = next(v for v in man["versions"] if v["id"] == "26.3")
vj = json.loads(get(ver["url"]))
os.makedirs("client", exist_ok=True)
json.dump(vj, open("client/26.3.json", "w"))
jobs = [(vj["downloads"]["client"]["url"], "client/client.jar", vj["downloads"]["client"]["sha1"])]
cp = []
for lib in vj["libraries"]:
    if not allowed(lib):
        continue
    a = lib.get("downloads", {}).get("artifact")
    if a:
        p = "client/libraries/" + a["path"]
        jobs.append((a["url"], p, a["sha1"]))
        cp.append(p)
idx = json.loads(get(vj["assetIndex"]["url"]))
os.makedirs("client/assets/indexes", exist_ok=True)
json.dump(idx, open(f"client/assets/indexes/{vj['assetIndex']['id']}.json", "w"))
for o in idx["objects"].values():
    h = o["hash"]
    jobs.append((f"https://resources.download.minecraft.net/{h[:2]}/{h}", f"client/assets/objects/{h[:2]}/{h}", h))
with cf.ThreadPoolExecutor(16) as ex:
    list(ex.map(lambda j: fetch(*j), jobs))
open("client/classpath.txt", "w").write(":".join(os.path.abspath(p) for p in cp + ["client/client.jar"]))
print("ok", len(jobs), "files; main", vj["mainClass"], "assets", vj["assetIndex"]["id"])
