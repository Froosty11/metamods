import json, urllib.request, urllib.parse, sys
API="https://api.modrinth.com/v2"
def get(u):
    r=urllib.request.Request(u,headers={"User-Agent":"metacraft-s6-bench/1.0 (KTH SMP)"})
    try: return json.load(urllib.request.urlopen(r,timeout=60))
    except Exception as e: return {"error":str(e)}
res={}
for slug in sys.argv[2:]:
    p=get(f"{API}/project/{slug}")
    if "error" in p: res[slug]={"slug":slug,"error":p["error"]}; print(slug,"NOT FOUND"); continue
    best=None
    for loader in (["fabric"],["datapack"]):
        q=urllib.parse.urlencode({"game_versions":json.dumps(["26.3"]),"loaders":json.dumps(loader)})
        vs=get(f"{API}/project/{slug}/version?{q}")
        if isinstance(vs,list) and vs:
            v=vs[0]; f=[x for x in v["files"] if x["primary"]] or v["files"]
            best={"loader":loader[0],"version_number":v["version_number"],"version_id":v["id"],"date":v["date_published"],
                  "file":f[0]["filename"],"url":f[0]["url"],"sha1":f[0]["hashes"]["sha1"],"size":f[0]["size"],
                  "deps":[(d["project_id"],d["dependency_type"]) for d in v["dependencies"]],"loaders":v["loaders"]}
            break
    res[slug]={"slug":slug,"title":p["title"],"id":p["id"],"client_side":p["client_side"],"server_side":p["server_side"],
               "project_type":p["project_type"],"categories":p["categories"],"description":p["description"],"v263":best}
    print(f'{slug:34} c={p["client_side"]:11} {("26.3: "+best["loader"]+" "+best["version_number"]+" "+best["file"]) if best else "NO 26.3 BUILD"} deps={best and best["deps"]}')
old=json.load(open(sys.argv[1])) if len(sys.argv)>1 and __import__("os").path.exists(sys.argv[1]) else {}
old.update(res); json.dump(old,open(sys.argv[1],"w"),indent=1)
