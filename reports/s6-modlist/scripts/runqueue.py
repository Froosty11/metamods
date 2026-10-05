#!/usr/bin/env python3
"""Run benchmark queue: each line 'CONFIG DIM IDX R [--keep]' (or 'PUSH message').
After every finished run: collect results into results.json/bench.csv, commit and push.
Done lines are recorded in /srv/mc/queue.done. Pause between runs by creating /srv/mc/PAUSE.
Aborts after 2 failed runs in a row."""
import subprocess,os,time,sys
HERE=os.path.dirname(os.path.abspath(__file__)); REPO=os.path.abspath(HERE+"/../../..")
Q=HERE+"/../queue.txt"; D="/srv/mc/queue.done"
def push(msg):
    subprocess.run([sys.executable,HERE+"/collect.py"],cwd=REPO)
    subprocess.run(["git","add","reports/s6-modlist"],cwd=REPO)
    if subprocess.run(["git","diff","--cached","--quiet"],cwd=REPO).returncode==0: return
    subprocess.run(["git","commit","-qm",msg+"\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_012XtDCjjezAxG2UHVCcod2Y"],cwd=REPO)
    for i in range(4):
        if subprocess.run(["git","push","-q","-u","origin","s6-modlist-bench"],cwd=REPO).returncode==0: return
        time.sleep(2**(i+1))
fails=0
while True:
    done=set(open(D).read().splitlines()) if os.path.exists(D) else set()
    todo=[l.strip() for l in open(Q) if l.strip() and not l.startswith("#") and l.strip() not in done]
    if not todo: break
    while os.path.exists("/srv/mc/PAUSE"): time.sleep(10)
    l=todo[0]; print(time.strftime("%H:%M:%S"),l,flush=True)
    if l.startswith("PUSH"): push(l[5:])
    else:
        a=l.split(); rc=subprocess.run([sys.executable,HERE+"/bench.py"]+a,cwd=HERE).returncode
        fails=fails+1 if rc!=0 else 0
        open(D,"a").write(l+"\n")
        push(f"s6-modlist: bench {a[0]} {a[1].split(':')[1]} run {a[2]} ({'ok' if rc==0 else 'FAILED'})")
        if fails>=2: print("ABORT: 2 failed runs in a row",flush=True); sys.exit(1)
        continue
    open(D,"a").write(l+"\n")
print("QUEUE EMPTY",flush=True)
