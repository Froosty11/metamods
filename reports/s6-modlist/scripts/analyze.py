"""Read region files of a pregenerated world: surface heights on a fixed grid and biome histogram.
Usage: analyze.py WORLD_DIR DIM [STEP_CHUNKS]  -> JSON on stdout"""
import struct,zlib,os,sys,json,collections,glob,math
def nbt(b,i=0):
    def rd(t,i):
        if t==1: return struct.unpack_from(">b",b,i)[0],i+1
        if t==2: return struct.unpack_from(">h",b,i)[0],i+2
        if t==3: return struct.unpack_from(">i",b,i)[0],i+4
        if t==4: return struct.unpack_from(">q",b,i)[0],i+8
        if t==5: return struct.unpack_from(">f",b,i)[0],i+4
        if t==6: return struct.unpack_from(">d",b,i)[0],i+8
        if t==7: n=struct.unpack_from(">i",b,i)[0]; return b[i+4:i+4+n],i+4+n
        if t==8: n=struct.unpack_from(">H",b,i)[0]; return b[i+2:i+2+n].decode("utf-8","replace"),i+2+n
        if t==9:
            et,n=struct.unpack_from(">bi",b,i); i+=5; out=[]
            for _ in range(n): v,i=rd(et,i); out.append(v)
            return out,i
        if t==10:
            d={}
            while True:
                tt=b[i]; i+=1
                if tt==0: return d,i
                n=struct.unpack_from(">H",b,i)[0]; k=b[i+2:i+2+n].decode("utf-8","replace"); i+=2+n
                d[k],i=rd(tt,i)
        if t==11: n=struct.unpack_from(">i",b,i)[0]; return list(struct.unpack_from(f">{n}i",b,i+4)),i+4+4*n
        if t==12: n=struct.unpack_from(">i",b,i)[0]; return list(struct.unpack_from(f">{n}q",b,i+4)),i+4+8*n
        raise ValueError(t)
    t=b[0]; n=struct.unpack_from(">H",b,1)[0]
    return rd(t,3+n)[0]
def chunk(regdir,cx,cz):
    p=f"{regdir}/r.{cx>>5}.{cz>>5}.mca"
    if not os.path.exists(p): return None
    with open(p,"rb") as f:
        f.seek(4*((cx&31)+(cz&31)*32)); loc=struct.unpack(">I",f.read(4))[0]
        if not loc: return None
        f.seek((loc>>8)*4096); ln,ct=struct.unpack(">IB",f.read(5)); d=f.read(ln-1)
    return nbt(zlib.decompress(d) if ct==2 else d)
def unpack(longs,bits,n):
    per=64//bits; mask=(1<<bits)-1; out=[]
    for l in longs:
        l&=(1<<64)-1
        for k in range(per):
            out.append((l>>(k*bits))&mask)
            if len(out)==n: return out
    return out
def analyze(world,dim,R=1000,step=4):
    ns,name=dim.split(":"); regdir=f"{world}/dimensions/{ns}/{name}/region"
    if not os.path.isdir(regdir): regdir={"overworld":f"{world}/region","the_nether":f"{world}/DIM-1/region","the_end":f"{world}/DIM1/region"}[name]
    rc=R//16; hist=collections.Counter(); heights={}; statuses=collections.Counter(); hs=[]
    for cx in range(-rc,rc+1,step):
        for cz in range(-rc,rc+1,step):
            c=chunk(regdir,cx,cz)
            if c is None: statuses["missing"]+=1; continue
            statuses[c.get("Status","?")]+=1
            miny=c.get("yPos",-4)*16
            hm=c.get("Heightmaps",{}).get("WORLD_SURFACE")
            if hm:
                h=unpack(hm,max(1,math.ceil(math.log2(len(c.get("sections",[]))*16+1))),256)
                heights[f"{cx*16},{cz*16}"]=h[0]+miny; hs.append(h[0]+miny)
            for s in c.get("sections",[]):
                b=s.get("biomes",{}); pal=b.get("palette",[])
                if len(pal)==1: hist[pal[0]]+=64
                elif pal:
                    bits=max(1,math.ceil(math.log2(len(pal))))
                    for v in unpack(b["data"],bits,64): hist[pal[v]]+=1
    tot=sum(hist.values()) or 1
    return {"statuses":dict(statuses),"heights":heights,
            "height_stats":{"mean":round(sum(hs)/len(hs),1) if hs else None,"min":min(hs) if hs else None,"max":max(hs) if hs else None,
                            "sd":round((sum((x-sum(hs)/len(hs))**2 for x in hs)/len(hs))**0.5,1) if hs else None},
            "biomes":{k:round(100*v/tot,2) for k,v in hist.most_common()}}
if __name__=="__main__":
    print(json.dumps(analyze(sys.argv[1],sys.argv[2],int(sys.argv[3]) if len(sys.argv)>3 else 1000),indent=1))
