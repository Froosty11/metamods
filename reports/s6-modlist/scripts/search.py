import json, urllib.request, urllib.parse, sys
API="https://api.modrinth.com/v2"
def get(u):
    r=urllib.request.Request(u,headers={"User-Agent":"metacraft-s6-bench/1.0 (KTH SMP)"})
    return json.load(urllib.request.urlopen(r,timeout=60))
out={}
for ptype in ["mod","datapack"]:
  for idx in ["downloads","follows"]:
    facets=[["categories:worldgen"],["versions:26.3"],[f"project_type:{ptype}"],["server_side:required","server_side:optional"]]
    q=urllib.parse.urlencode({"facets":json.dumps(facets),"index":idx,"limit":50})
    for h in get(f"{API}/search?{q}")["hits"]:
        out.setdefault(h["slug"],{k:h.get(k) for k in ["slug","title","project_type","client_side","server_side","downloads","follows","categories","description"]})
        out[h["slug"]].setdefault("found_by",set()).add(f"{ptype}/{idx}")
for v in out.values(): v["found_by"]=sorted(v["found_by"])
json.dump(out,open(sys.argv[1],"w"),indent=1)
for v in sorted(out.values(),key=lambda x:-x["downloads"]):
    print(f'{v["slug"][:32]:32} {v["project_type"]:8} c={v["client_side"]:11} s={v["server_side"]:9} dl={v["downloads"]:>9} f={v["follows"]:>6} {v["description"][:70]}')
