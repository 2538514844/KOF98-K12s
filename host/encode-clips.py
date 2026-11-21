"""Encode captured frames at 20 fps, ~0.676x the original game speed."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tooling/media'))
from imageio_ffmpeg import get_ffmpeg_exe
ffmpeg=get_ffmpeg_exe()
rawdir=ROOT/'evidence/fight-capture'
assets=ROOT/'android/assets/fights'
assets.mkdir(parents=True,exist_ok=True)
names=['草薙京','二阶堂红丸','特瑞','坂崎良','麻宫雅典娜','八神庵']
report={'sourceFps':59.18,'captureStride':2,'playbackFps':20,'speedRatio':40/59.18,'secondsPerClip':15,'clips':[]}
for i,name in enumerate(names,1):
    source=rawdir/f'fight-{i}.rgb'
    assert source.stat().st_size==300*304*168*3
    movie=assets/f'fight-{i}.mp4'
    poster=assets/f'fight-{i}.png'
    log=rawdir/f'encode-{i}.txt'
    with log.open('wb') as err:
        subprocess.run([ffmpeg,'-y','-hide_banner','-f','rawvideo','-pixel_format','rgb24','-video_size','304x168','-framerate','20','-i',str(source),'-vf','scale=608:336:flags=neighbor','-c:v','libx264','-profile:v','baseline','-level','3.0','-pix_fmt','yuv420p','-preset','medium','-crf','22','-g','40','-an','-movflags','+faststart',str(movie)],stderr=err,stdout=subprocess.DEVNULL,check=True)
    subprocess.run([ffmpeg,'-y','-hide_banner','-loglevel','error','-i',str(movie),'-frames:v','1',str(poster)],check=True)
    decoded=subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-i',str(movie),'-f','framemd5','-'],stdout=subprocess.PIPE,check=True).stdout.decode()
    frames=[line for line in decoded.splitlines() if line and not line.startswith('#')]
    unique=len({line.rsplit(',',1)[-1].strip() for line in frames})
    assert len(frames)==300 and unique>100,(i,len(frames),unique)
    (rawdir/f'decode-{i}.md5').write_text(decoded,encoding='utf-8',newline='\n')
    report['clips'].append({'file':movie.name,'fighter':name,'frames':len(frames),'uniqueFrames':unique,'sha256':hashlib.sha256(movie.read_bytes()).hexdigest(),'bytes':movie.stat().st_size})
    print(f'Encoded clip {i}: 300 frames / 15 seconds, {unique} unique decoded frames, {movie.stat().st_size} bytes',flush=True)
assert len({x['sha256'] for x in report['clips']})==6
(ROOT/'evidence/fight-clips-verification.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
(assets/'clips.json').write_text(json.dumps(report,indent=2,ensure_ascii=False),encoding='utf-8')
