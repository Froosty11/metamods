"""Add structure-start counts (full pregen square) to raw run-1 JSONs whose world was kept."""
import json,glob,os,sys
from concurrent.futures import ProcessPoolExecutor
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from analyze import structure_starts
H=os.path.dirname(os.path.abspath(__file__))
def job(f):
    r=json.load(open(f)); w=f"/srv/mc/runs/{r['config']}/{r['dimension']}/r1/world"
    if r.get("status")!="ok" or r["run"]!=1 or not os.path.isdir(w): return None
    if "structure_starts" in r.get("analysis",{}): return None
    r["analysis"]["structure_starts"]=structure_starts(w,r["dimension"],r["radius"])
    json.dump(r,open(f,"w"),indent=1); return f
if __name__=="__main__":
    with ProcessPoolExecutor(4) as ex:
        for x in ex.map(job,sorted(glob.glob(H+"/../raw/*__r1.json"))):
            if x: print(os.path.basename(x))
