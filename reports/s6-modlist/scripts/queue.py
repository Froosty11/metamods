#!/usr/bin/env python3
"""Run benchmark queue: each line 'CONFIG DIM IDX R [--keep]' or 'PUSH message'. Done lines are recorded in /srv/mc/queue.done.
Pause between runs by creating /srv/mc/PAUSE."""
import subprocess,os,time,sys
HERE=os.path.dirname(os.path.abspath(__file__)); REPO=os.path.abspath(HERE+"/../../..")
Q=HERE+"/../queue.txt"; D="/srv/mc/queue.done"
while True:
    done=set(open(D).read().splitlines()) if os.path.exists(D) else set()
    todo=[l.strip() for l in open(Q) if l.strip() and not l.startswith("#") and l.strip() not in done]
    if not todo: break
    while os.path.exists("/srv/mc/PAUSE"): time.sleep(10)
    l=todo[0]; print(time.strftime("%H:%M:%S"),l,flush=True)
    if l.startswith("PUSH"):
        subprocess.run([sys.executable,HERE+"/collect.py"],cwd=REPO)
        subprocess.run(["git","add","reports/s6-modlist"],cwd=REPO)
        subprocess.run(["git","commit","-qm",l[5:]+"\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_012XtDCjjezAxG2UHVCcod2Y"],cwd=REPO)
        for i in range(4):
            if subprocess.run(["git","push","-u","origin","s6-modlist-bench"],cwd=REPO).returncode==0: break
            time.sleep(2**(i+1))
    else:
        a=l.split(); subprocess.run([sys.executable,HERE+"/bench.py"]+a,cwd=HERE)
    open(D,"a").write(l+"\n")
print("QUEUE EMPTY",flush=True)
