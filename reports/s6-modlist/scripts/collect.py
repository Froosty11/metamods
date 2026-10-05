"""Aggregate raw/*.json into results.json and bench.csv."""
import json,glob,os,csv
H=os.path.dirname(os.path.abspath(__file__)); R=H+"/.."
rows=[json.load(open(f)) for f in sorted(glob.glob(R+"/raw/*.json"))]
json.dump(rows,open(R+"/results.json","w"),indent=1)
C=json.load(open(H+"/configs.json"))
with open(R+"/bench.csv","w",newline="") as f:
    w=csv.writer(f); w.writerow(["config","type","dimension","run","seed","radius","status","chunks","wall_s","chunks_per_s","mean_mspt","mean_tps","cpu_pct","peak_heap_mb","post_cpu_pct_20s","dh_sqlite_mb","spark_health","spark_profile"])
    for r in rows:
        l=r.get("spark_links",[])+["",""]
        w.writerow([r["config"],C.get(r["config"],{}).get("type",""),r["dimension"],r["run"],r["seed"],r["radius"],r["status"],r.get("chunks"),r.get("wall_s"),r.get("chunks_per_s"),
                    r.get("mean_mspt"),r.get("mean_tps"),r.get("cpu_pct_of_machine"),r.get("peak_heap_mb"),r.get("post_cpu_pct_20s"),r.get("dh_sqlite_mb"),l[0],l[1]])
print(len(rows),"runs collected")
