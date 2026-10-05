#!/usr/bin/env python3
"""Resolve the newest Modrinth build per slug for MC 26.3 (fabric or datapack). Writes resolved.json."""
import json, sys, urllib.request, urllib.parse
SLUGS = sys.argv[1:] or """fabric-api c2me-fabric distanthorizons spark chunky tectonic terralith scalablelux polymer
alternate-current lithium ferrite-core krypton no-chat-reports ledger vanish luckperms simple-voice-chat squaremap
bluemap worldedit filament booklet carpet fabrictailor forgiving-void noisium leukocyte squaremap-banners antixray
meowanti-xray minertrack dungeons-and-taverns incendium nullscape""".split()
UA = {"User-Agent": "Froosty11/metamods s6-compat-check"}
def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30))
out = {}
for slug in SLUGS:
    res = None
    for loaders in (["fabric"], ["datapack"]):
        q = urllib.parse.urlencode({"game_versions": json.dumps(["26.3"]), "loaders": json.dumps(loaders)})
        try:
            vs = get(f"https://api.modrinth.com/v2/project/{slug}/version?{q}")
        except Exception as e:
            res = {"error": str(e)}; break
        if vs:
            v = vs[0]
            f = next((f for f in v["files"] if f["primary"]), v["files"][0])
            res = {"version": v["version_number"], "loader": loaders[0], "type": v["version_type"],
                   "file": f["filename"], "url": f["url"], "sha1": f["hashes"]["sha1"],
                   "deps": [(d["project_id"], d["dependency_type"]) for d in v["dependencies"]],
                   "game_versions": v["game_versions"]}
            break
    out[slug] = res
    print(f"{slug:22} {res['version'] + ' [' + res['loader'] + ']' if res and 'version' in res else res}")
json.dump(out, open("resolved.json", "w"), indent=1)
