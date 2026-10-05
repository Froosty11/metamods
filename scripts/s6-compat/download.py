#!/usr/bin/env python3
"""Download every resolved Modrinth file into jars/ and verify sha1."""
import json, hashlib, os, urllib.request
for fn in ("resolved.json", "resolved-extra.json"):
    for slug, v in json.load(open(fn)).items():
        if not v or "url" not in v: continue
        dst = f"jars/{v['file']}"
        if not os.path.exists(dst):
            data = urllib.request.urlopen(urllib.request.Request(v["url"], headers={"User-Agent": "x"}), timeout=120).read()
            assert hashlib.sha1(data).hexdigest() == v["sha1"], slug
            open(dst, "wb").write(data)
        print(slug, dst)
