"""Offline authoring: isolated AES practice, original player moves and a stationary practice opponent.
Never writes into a user's saved game. Requires the same local data and host JNI fixtures as capture-clips.py.
"""
from pathlib import Path
import os,subprocess as sp
ROOT=Path(__file__).resolve().parents[1];H=ROOT/'host';E=ROOT/'evidence/v0.8-preview';game=E/'game';C=E/'classes';out=E/'capture';J=Path(os.environ.get('K98_JDK') or os.environ['JAVA_HOME'])/'bin'
for p in (game/'save',C,out):p.mkdir(parents=True,exist_ok=True)
for source,name in [(ROOT/'phone/kof98.zip','roms/kof98.zip'),(ROOT/'phone/neogeo.zip','roms/neogeo.zip'),(H/'system/fbneo/patched/neogeo.zip','system/fbneo/patched/neogeo.zip')]:
 dest=game/name;dest.parent.mkdir(parents=True,exist_ok=True)
 if not dest.exists():os.link(source,dest)
cp=os.pathsep.join(str(x) for x in (H/'jni-test',C,ROOT/'android/build/classes'))
sp.run([J/'javac.exe','-encoding','UTF-8','-cp',cp,'-d',C,H/'CaptureGuide.java'],check=True)
env=dict(os.environ);env['PATH']=str(H)+os.pathsep+env['PATH']
with (out/'final-capture.log').open('wb') as err:
 sp.run([J/'java.exe','-Djava.library.path='+str(H/'jni-test'),'-cp',cp,'local.k98.CaptureGuide',game,out,ROOT/'android/assets/guide/commands-zh.tsv','capture'],env=env,cwd=ROOT,stderr=err,check=True)
