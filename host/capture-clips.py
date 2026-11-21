"""Capture six local fights and encode slow, silent loading-screen clips."""
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
JDK=Path(os.environ.get('K98_JDK',r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'))/'bin'
CLASSES=ROOT/'host/jni-test'
OUT=ROOT/'evidence/fight-capture'
OUT.mkdir(parents=True,exist_ok=True)
mode=sys.argv[1] if len(sys.argv)>1 else 'scout'
subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-cp',str(CLASSES),'-d',str(CLASSES),str(ROOT/'host/CaptureClips.java')],check=True)
env=os.environ.copy()
env['PATH']=str(ROOT/'host')+os.pathsep+env.get('PATH','')
with (OUT/(mode+'-native.txt')).open('wb') as err:
    run=subprocess.run([str(JDK/'java.exe'),'-Djava.library.path='+str(CLASSES),'-cp',str(CLASSES),'local.k98.CaptureClips',str(CLASSES/'home/game'),str(OUT),mode],cwd=ROOT,env=env,stdout=subprocess.PIPE,stderr=err,timeout=180)
(OUT/(mode+'-output.txt')).write_bytes(run.stdout)
print(run.stdout.decode('utf-8',errors='replace'))
raise SystemExit(run.returncode)
