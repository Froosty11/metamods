# Heuristic server-only scan: count client asset files and registry use in each jar
import zipfile,glob,os,re,json,collections
out={}
for p in sorted(glob.glob("/srv/mc/cache/*")):
    slug=os.path.basename(p).split("__")[0]
    z=zipfile.ZipFile(p); n=z.namelist()
    c=collections.Counter()
    for f in n:
        if re.match(r"assets/[^/]+/blockstates/.+\.json$",f): c["blockstates"]+=1
        elif re.match(r"assets/[^/]+/(models/item|items)/.+\.json$",f): c["item_models"]+=1
        elif re.match(r"assets/[^/]+/textures/entity/",f): c["entity_tex"]+=1
        elif re.match(r"assets/[^/]+/textures/",f): c["textures"]+=1
        elif re.match(r"assets/[^/]+/lang/",f): c["lang"]+=1
        elif re.match(r"data/[^/]+/worldgen/biome/",f): c["biomes"]+=1
        elif re.match(r"data/[^/]+/(worldgen/)?structures?/",f): c["structures"]+=1
        elif re.match(r"data/[^/]+/worldgen/",f): c["worldgen"]+=1
        elif f.endswith(".class"): c["classes"]+=1
        elif f.endswith(".mcfunction"): c["functions"]+=1
    fmj={}
    if "fabric.mod.json" in n:
        fmj=json.loads(z.read("fabric.mod.json").decode("utf-8","replace"),strict=False)
    out[slug]=dict(c,modid=fmj.get("id"),env=fmj.get("environment","*"),
                   client_entry=bool((fmj.get("entrypoints") or {}).get("client")),
                   nested=[j.get("file") for j in fmj.get("jars",[])])
    print(f"{slug:40} {dict(c)} env={out[slug]['env']} clientEntry={out[slug]['client_entry']} nested={len(out[slug]['nested'])}")
json.dump(out,open("../jarscan.json","w"),indent=1)
