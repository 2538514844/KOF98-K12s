"""Capture actual local gameplay; no downloaded or generated art."""
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
JDK=Path(os.environ.get("K98_JDK",r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"))/"bin"
OUT=ROOT/"host/jni-test"
target=ROOT/"design/loading-preview"
target.mkdir(parents=True,exist_ok=True)
subprocess.run([str(JDK/"javac.exe"),"-encoding","UTF-8","-cp",str(OUT),"-d",str(OUT),
                str(ROOT/"host/CapturePreview.java")],check=True)
env=os.environ.copy();env["PATH"]=str(ROOT/"host")+os.pathsep+env.get("PATH","")
with (ROOT/"evidence/preview-capture.txt").open("wb") as out,(ROOT/"evidence/preview-native.txt").open("wb") as err:
    p=subprocess.run([str(JDK/"java.exe"),"-Djava.library.path="+str(OUT),"-cp",str(OUT),
                      "local.k98.CapturePreview",str(OUT/"home/game"),str(target)],
                     cwd=ROOT,env=env,stdout=out,stderr=err,timeout=90)
print((ROOT/"evidence/preview-capture.txt").read_text(encoding="utf-8"))
raise SystemExit(p.returncode)
