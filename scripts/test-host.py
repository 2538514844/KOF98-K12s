"""Run meaningful layout, input, move-guide and playback regressions without game data."""
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get('K98_JDK', os.environ.get('JAVA_HOME', r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'))) / 'bin'
OUT = ROOT / 'host/build/regressions'
NAMES = ('CenteringRegressionTest','DisplayFlowTest','GuideLayoutTest','TouchReplayTest','FightPlaybackTest')

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'android/src/local/k98'
    classes = ['ControlProfile','DisplayOptions','PadLayout','TouchInput','MoveBook','FightPlayback']
    files = [source/(name+'.java') for name in classes]
    files += [ROOT/'host'/(name+'.java') for name in NAMES]
    files.append(ROOT/'host/input-test/LegacyControls.java')
    subprocess.run([str(JDK/'javac.exe'),'-encoding','UTF-8','-d',str(OUT),*map(str,files)], check=True)
    results = {}
    for name in NAMES:
        args = [str(JDK/'java.exe'),'-cp',str(OUT),'local.k98.'+name]
        if name == 'GuideLayoutTest':args.append(str(ROOT/'android/assets/guide/commands-zh.tsv'))
        result = subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        output = result.stdout.decode('utf-8',errors='replace').replace('\r','').strip()
        results[name] = {'exitCode':result.returncode,'output':output}
        print(name+': '+output.splitlines()[-1])
        if result.returncode:print(output)
    (OUT/'results.json').write_text(json.dumps(results,indent=2,ensure_ascii=False),encoding='utf-8')
    raise SystemExit(any(v['exitCode'] for v in results.values()))

if __name__ == '__main__':
    main()
