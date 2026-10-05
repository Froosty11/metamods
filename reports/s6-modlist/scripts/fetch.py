# download every resolved 26.3 file to /srv/mc/cache, verify sha1
import json,urllib.request,hashlib,os,sys
d=json.load(open(os.path.join(os.path.dirname(__file__),"../versions.json")))
for slug in (sys.argv[1:] or d):
    v=d.get(slug,{}).get("v263")
    if not v: continue
    p=f"/srv/mc/cache/{slug}__{v['file']}"
    if not os.path.exists(p):
        data=urllib.request.urlopen(urllib.request.Request(v["url"],headers={"User-Agent":"metacraft-s6-bench/1.0"}),timeout=120).read()
        assert hashlib.sha1(data).hexdigest()==v["sha1"],slug
        open(p,"wb").write(data)
    print(slug,os.path.getsize(p))
