"""Generate REPORT.md and report.html from results.json, configs.json, versions.json, candidates.md, shots/, packs.json, notes.json."""
import json,os,statistics as st,html,glob
H=os.path.dirname(os.path.abspath(__file__)); R=os.path.abspath(H+"/..")
rows=[r for r in json.load(open(R+"/results.json"))]
C=json.load(open(H+"/configs.json")); V=json.load(open(R+"/versions.json"))
P=json.load(open(R+"/packs.json")) if os.path.exists(R+"/packs.json") else {}
N=json.load(open(R+"/notes.json")) if os.path.exists(R+"/notes.json") else {}
pre=open(R+"/preflight.txt").read()
views=json.load(open(R+"/shots/views.json"))["views"] if os.path.exists(R+"/shots/views.json") else []
DN={"minecraft:overworld":"Overworld","minecraft:the_nether":"Nether","minecraft:the_end":"End"}
agg={}
for r in rows:
    if r.get("status")!="ok": continue
    agg.setdefault((r["config"],r["dimension"]),[]).append(r)
fails=[r for r in rows if r.get("status")!="ok"]
def stats(rs):
    c=[r["chunks_per_s"] for r in rs]
    return {"n":len(rs),"cps":st.mean(c),"min":min(c),"max":max(c),"sd":st.stdev(c) if len(c)>1 else 0,
            "wall":st.mean(r["wall_s"] for r in rs),"mspt":st.mean(r["mean_mspt"] for r in rs if r.get("mean_mspt") is not None),
            "tps":st.mean([r["mean_tps"] for r in rs if r.get("mean_tps")] or [0]),"cpu":st.mean(r["cpu_pct_of_machine"] for r in rs),
            "heap":max(r["peak_heap_mb"] or 0 for r in rs),"post":st.mean(r.get("post_cpu_pct_20s") or 0 for r in rs),
            "links":[l for r in rs for l in r.get("spark_links",[])],"chunks":rs[0]["chunks"],"radius":rs[0]["radius"]}
S={k:stats(v) for k,v in agg.items()}
def base(dim): return S.get(("B0",dim))
def pct(k):
    b=base(k[1]); return None if not b else 100*(S[k]["cps"]/b["cps"]-1)
order=["baseline","terrain","nether","end","perf","structures","features","pack"]
keys=sorted(S,key=lambda k:(list(DN).index(k[1]),order.index(C.get(k[0],{}).get("type","pack")) if C.get(k[0],{}).get("type","pack") in order else 9,k[0]))
def ver(slug):
    v=V.get(slug,{}).get("v263"); return v["version_number"] if v else "?"
md=[]; A=md.append
A("# METAcraft Season 6 — server-side worldgen pack benchmark"); A("")
A(N.get("summary","")); A("")
A("## Machine and method"); A("")
A("```"); A("\n".join(l for l in pre.splitlines() if not l.startswith("tmpfs") and "model_tools" not in l and "/opt/" not in l)); A("```"); A("")
A(N.get("method","")); A("")
A("## Exact versions"); A("")
A("Minecraft 26.3 (release), Fabric loader 0.19.5, Fabric API 0.161.0+26.3, Java: Temurin 25.0.4.1+1. Every mod jar was downloaded from Modrinth and checked against its sha1.");A("")
used=sorted({m for r in rows for m in r.get("mods",[])})
A("| slug | version | file | sha1 |"); A("|---|---|---|---|")
for s in used:
    v=V.get(s,{}).get("v263") or {}
    A(f"| {s} | {v.get('version_number','?')} | `{v.get('file','?')}` | `{v.get('sha1','?')[:12]}` |")
A("")
A("## Candidates"); A("")
A("Full table: [candidates.md](candidates.md). Short version:"); A("")
A(N.get("candidates_summary","")); A("")
A("## Benchmark results"); A("")
A("Chunks/s = Chunky chunks ÷ wall time from `chunky start` to Chunky's 'Task finished' line. Δ is relative to B0 in the same dimension. Spread = min–max over runs. mspt = mean of vanilla `/tick query` 100-tick averages sampled every 5 s; TPS = mean of spark `tps` 5 s values; CPU = process CPU time ÷ wall ÷ 4 cores. Post-CPU = process CPU in the 20 s after Chunky finished (DH LOD backlog).");A("")
A("| config | type | dim | runs | chunks/s (mean, min–max) | wall s | Δ vs B0 | mspt | TPS | CPU % | peak heap MB | post-CPU % | spark (health / profile, run 1) |")
A("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
for k in keys:
    s=S[k]; p=pct(k); t=C.get(k[0],{}).get("type","pack")
    ver_=N.get("verify",{}).get(f"{k[0]}|{k[1]}","")
    links=" / ".join(f"[{i}]({l})" for i,l in zip(["h","p"],s["links"][:2]))
    A(f"| {k[0]} | {t} | {DN[k[1]]} | {s['n']} | {s['cps']:.1f} ({s['min']:.1f}–{s['max']:.1f}) | {s['wall']:.0f} | {'—' if k[0]=='B0' else f'{p:+.1f}%'} | {s['mspt']:.2f} | {s['tps']:.2f} | {s['cpu']:.0f} | {s['heap']} | {s['post']:.0f} | {links} |")
A("")
if fails:
    A("Failed runs (not in the table):"); A("")
    for r in fails: A(f"- {r['config']} {DN.get(r['dimension'],r['dimension'])} run {r['run']}: {r.get('status')} — {r.get('error','')}")
    A("")
A("Radius: "+", ".join(f"{DN[d]} R={base(d)['radius']} blocks ({base(d)['chunks']} chunks)" for d in DN if base(d))+". Seed: "+str(rows[0]["seed"] if rows else "?")+"."); A("")
A("![chunks/s relative to B0](chart.svg)" if os.path.exists(R+"/chart.svg") else ""); A("")
A("## Screenshots"); A("")
A(N.get("shots_method","")); A("")
shotcfgs=[d for d in N.get("shot_order",[]) if os.path.isdir(f"{R}/shots/{d}")]
if views and shotcfgs:
    A("Views (`shots/views.json`): "+"; ".join(f"**{v['id']}** {DN[v['dim']]} ({v['x']}, {v['y']}, {v['z']}) yaw {v['yaw']} pitch {v['pitch']} — {v.get('note','')}" for v in views)); A("")
    for v in views:
        cs=[c for c in shotcfgs if os.path.exists(f"{R}/shots/{c}/{v['id']}.jpg")]
        if not cs: continue
        A(f"### {v['id']}"); A("")
        A("| "+" | ".join(cs)+" |"); A("|"+"---|"*len(cs))
        A("| "+" | ".join(f"![{c} {v['id']}](shots/{c}/{v['id']}.jpg)" for c in cs)+" |"); A("")
A(N.get("visual_notes","")); A("")
A("## The three packs"); A("")
for name,pk in P.items():
    k=(name,"minecraft:overworld")
    cost=f"{S[k]['cps']:.1f} chunks/s, {pct(k):+.1f}% vs B0" if k in S else "not measured"
    A(f"### {name} — {cost}"); A("")
    A(pk.get("why","")); A("")
    A("Mods on top of B0 (Fabric API, C2ME, Distant Horizons, spark, Chunky): "+", ".join(f"{m} {ver(m)}" for m in C[name]["mods"])); A("")
A("## Recommendation"); A(""); A(N.get("recommendation","")); A("")
A("## Geophilic"); A(""); A(N.get("geophilic","")); A("")
A("## What was not tested, and open problems"); A(""); A(N.get("open","")); A("")
open(R+"/REPORT.md","w").write("\n".join(md)+"\n")
# ---- chart (SVG, inline in HTML, also standalone file)
bars=[k for k in keys if k[0]!="B0"]
W=900; rowh=18; top=30; left=260; h=top+rowh*len(bars)+40; mx=max([120]+[100*S[k]["max"]/base(k[1])["cps"] for k in bars if base(k[1])])+5
def x(v): return left+(W-left-60)*v/mx
svg=[f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {h}" width="100%" role="img" aria-label="Chunks per second relative to B0" style="font:12px system-ui,sans-serif">',
     f'<style>.t{{fill:var(--fg,#222)}} .g{{stroke:var(--grid,#ccc)}} .b{{fill:var(--bar,#4a7bd1)}} .w{{stroke:var(--fg,#222)}} .ref{{stroke:#c0392b;stroke-dasharray:4 3}}</style>',
     f'<text class="t" x="{left}" y="16">chunks/s as % of B0 in the same dimension (bar = mean, whisker = min–max)</text>']
for v in range(0,int(mx)+1,20):
    svg.append(f'<line class="g" x1="{x(v):.1f}" y1="{top-4}" x2="{x(v):.1f}" y2="{h-30}"/><text class="t" x="{x(v):.1f}" y="{h-14}" text-anchor="middle">{v}%</text>')
svg.append(f'<line class="ref" x1="{x(100):.1f}" y1="{top-4}" x2="{x(100):.1f}" y2="{h-30}"/>')
for i,k in enumerate(bars):
    b=base(k[1]); 
    if not b: continue
    y=top+i*rowh; m=100*S[k]["cps"]/b["cps"]; lo=100*S[k]["min"]/b["cps"]; hi=100*S[k]["max"]/b["cps"]
    svg.append(f'<text class="t" x="{left-6}" y="{y+12}" text-anchor="end">{html.escape(k[0])} ({DN[k[1]]})</text>')
    svg.append(f'<rect class="b" x="{left}" y="{y+3}" width="{x(m)-left:.1f}" height="{rowh-6}"/><line class="w" x1="{x(lo):.1f}" y1="{y+rowh/2}" x2="{x(hi):.1f}" y2="{y+rowh/2}"/>')
    svg.append(f'<text class="t" x="{x(max(m,hi))+4:.1f}" y="{y+12}">{m:.0f}%</text>')
svg.append("</svg>"); SVG="\n".join(svg)
open(R+"/chart.svg","w").write(SVG.replace("var(--fg,#222)","#222").replace("var(--grid,#ccc)","#ccc").replace("var(--bar,#4a7bd1)","#4a7bd1"))
# ---- HTML: convert the markdown tables/headings minimally
import re
def inline(t):
    t=html.escape(t)
    t=re.sub(r"!\[([^\]]*)\]\(([^)]+)\)",lambda m:f'<img src="{m.group(2)}" alt="{m.group(1)}" loading="lazy">' if not m.group(2).endswith(".svg") else SVG,t)
    t=re.sub(r"\[([^\]]+)\]\(([^)]+)\)",r'<a href="\2">\1</a>',t)
    t=re.sub(r"\*\*([^*]+)\*\*",r"<b>\1</b>",t); t=re.sub(r"`([^`]+)`",r"<code>\1</code>",t)
    return t
out=[]; lines=open(R+"/REPORT.md").read().split("\n"); i=0
while i<len(lines):
    l=lines[i]
    if l.startswith("```"):
        j=i+1
        while not lines[j].startswith("```"): j+=1
        out.append("<pre>"+html.escape("\n".join(lines[i+1:j]))+"</pre>"); i=j+1; continue
    if l.startswith("|"):
        tb=[]
        while i<len(lines) and lines[i].startswith("|"): tb.append(lines[i]); i+=1
        cells=lambda r:[c.strip() for c in r.strip("|").split(" | ")]
        o='<div class="tw"><table><thead><tr>'+"".join(f"<th>{inline(c)}</th>" for c in cells(tb[0]))+"</tr></thead><tbody>"
        for r in tb[2:]: o+="<tr>"+"".join(f"<td>{inline(c)}</td>" for c in cells(r))+"</tr>"
        out.append(o+"</tbody></table></div>"); continue
    m=re.match(r"(#+) (.*)",l)
    if m: out.append(f"<h{len(m.group(1))}>{inline(m.group(2))}</h{len(m.group(1))}>")
    elif l.startswith("- "): out.append(f"<li>{inline(l[2:])}</li>")
    elif l.strip(): out.append(f"<p>{inline(l)}</p>")
    i+=1
page=f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>S6 Worldgen Benchmark</title><style>
:root{{--bg:#fff;--fg:#1d1f23;--muted:#666;--grid:#d6d9de;--bar:#4a7bd1;--line:#e3e5e8}}
@media (prefers-color-scheme:dark){{:root:not([data-theme="light"]){{--bg:#16181c;--fg:#e6e7ea;--muted:#a0a3a8;--grid:#3a3d44;--bar:#6d97e3;--line:#2c2f35}}}}
:root[data-theme="dark"]{{--bg:#16181c;--fg:#e6e7ea;--muted:#a0a3a8;--grid:#3a3d44;--bar:#6d97e3;--line:#2c2f35}}
body{{background:var(--bg);color:var(--fg);font:15px/1.5 system-ui,sans-serif;max-width:1400px;margin:0 auto;padding:16px}}
.tw{{overflow-x:auto}} table{{border-collapse:collapse;font-size:13px;margin:8px 0}} td,th{{border:1px solid var(--line);padding:4px 6px;vertical-align:top}}
th{{text-align:left}} img{{width:280px;max-width:100%;height:auto;display:block}} pre{{overflow-x:auto;font-size:12px;border:1px solid var(--line);padding:8px}}
a{{color:var(--bar)}} code{{font-size:12px}}
</style></head><body>{"".join(out)}</body></html>"""
open(R+"/report.html","w").write(page)
print("report written",len(S),"configs")
