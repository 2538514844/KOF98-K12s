"""Encode original practice input recordings, no Android emulator/save required at runtime.
Author first with CaptureGuide on the isolated JNI host test home. Frames are 304x168, 20 fps.
PNG screenshots and raw RGB stay in evidence; only silent H.264, small posters and the keyed index ship.
"""
from pathlib import Path
import concurrent.futures as futures, subprocess as sp, json, sys
ROOT=Path(__file__).resolve().parents[1]; RAW=ROOT/'evidence/v0.8-preview/capture'; OUT=ROOT/'android/assets/guide/scenes';OUT.mkdir(exist_ok=True)
sys.path.insert(0,str(ROOT/'tooling/media'))
from imageio_ffmpeg import get_ffmpeg_exe
ff=get_ffmpeg_exe()
def run(*args):
 return sp.run([ff,'-nostdin','-hide_banner','-loglevel','error','-y',*map(str,args)],check=True,stdout=sp.PIPE,stderr=sp.PIPE).stdout
def encode(name):
 if name.startswith('p'):
  path=OUT/(name+'.webp');run('-i',RAW/(name+'.png'),'-frames:v','1','-c:v','libwebp','-quality','86','-compression_level','5',path);return path.stat().st_size
 src=RAW/(name+'.rgb');frames,rem=divmod(src.stat().st_size,304*168*3);assert frames>=35 and not rem,(name,frames,rem)
 # Keep the full pixel-art frame, cap bitrate and resample timestamps (not game speed) to limit APK growth.
 path=OUT/(name+'.mp4');run('-f','rawvideo','-pixel_format','rgb24','-video_size','304x168','-framerate','20','-i',src,'-vf','fps=12','-c:v','libx264','-threads','1','-preset','slow','-crf','28','-maxrate','108k','-bufsize','90k','-profile:v','baseline','-level','2.0','-pix_fmt','yuv420p','-an','-movflags','+faststart',path)
 decoded=run('-i',path,'-f','framemd5','-').decode().splitlines();md=[x.rsplit(',',1)[-1].strip() for x in decoded if x and not x.startswith('#')];assert abs(len(md)-frames*12/20)<=1 and len(set(md))>=8,(name,frames,len(set(md)));return path.stat().st_size
records=(RAW/'media.tsv').read_text(encoding='utf-8').splitlines();assert len(records)==327
posters={r.split('\t')[2] for r in records if r.startswith('@\t')}; movies={r.split('\t')[1] for r in records if not r.startswith('@\t')}-{'-'}
assert len(posters)==51 and len(movies)==275
with futures.ThreadPoolExecutor(4) as pool: sizes=list(pool.map(encode,sorted(posters|movies)))
(OUT.parent/'media.tsv').write_text('\n'.join(records)+'\n',encoding='utf-8',newline='\n')
report={'posters':len(posters),'silentPracticeReplays':len(movies),'conditionalInputOnly':275-len(movies),'encodedBytes':sum(sizes),'allClipsDecodedAndFrameCountChecked':True,'fps':12,'authoringFps':20,'width':304,'height':168}
(ROOT/'evidence/v0.8-preview/media-check.json').write_text(json.dumps(report,indent=2),encoding='utf-8');print(report)
