# Create a fresh server dir with given mod slugs (from /srv/mc/cache) and properties.
import os,sys,shutil,glob,json
BASE="/srv/mc/base"
def make(path,slugs,seed,extra_props=None):
    if os.path.exists(path): shutil.rmtree(path)
    os.makedirs(path+"/mods")
    for f in ["fabric-server-launch.jar","libraries","versions",".fabric"]:
        if os.path.exists(f"{BASE}/{f}"): os.symlink(f"{BASE}/{f}",f"{path}/{f}")
    open(path+"/eula.txt","w").write("eula=true\n")
    for s in slugs:
        m=glob.glob(f"/srv/mc/cache/{s}__*"); assert len(m)==1,(s,m)
        shutil.copy(m[0],f"{path}/mods/{os.path.basename(m[0]).split('__',1)[1]}")
    props={"level-seed":str(seed),"online-mode":"false","enable-rcon":"true","rcon.password":"bench","rcon.port":"25575",
           "view-distance":"10","simulation-distance":"6","spawn-protection":"0","max-tick-time":"-1","sync-chunk-writes":"true",
           "difficulty":"peaceful","motd":"s6bench","enable-command-block":"true","allow-flight":"true"}
    props.update(extra_props or {})
    open(path+"/server.properties","w").write("".join(f"{k}={v}\n" for k,v in props.items()))
if __name__=="__main__":
    make(sys.argv[1],sys.argv[3:],sys.argv[2])
