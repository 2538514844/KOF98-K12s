"""Build the offline Android package and the separate, local-only import ZIP."""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess as sp
import zipfile as z

A=Path(__file__).resolve().parent
ROOT=A.parent
B=A/'build'; OUT=ROOT/'release'; E=ROOT/'evidence'
BT=Path(os.environ.get('K98_SDK',os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME',r'G:\AndroidTools'))))/'build-tools'/'35.0.1'
JAR=BT.parents[1]/'platforms'/'android-35'/'android.jar'
JDK=Path(os.environ.get('K98_JDK',os.environ.get('JAVA_HOME',r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot')))/'bin'
def cmd(*a,record=None):
    a=[str(x) for x in a]
    if record:
        r=sp.run(a,stdout=sp.PIPE,stderr=sp.STDOUT);(E/record).write_bytes(r.stdout)
        if r.returncode: raise RuntimeError(r.stdout.decode(errors='replace'))
        return r.stdout.decode('utf-8',errors='replace')
    sp.run(a,check=True)

def archive_item(archive,src,name,method):
    ent=z.ZipInfo(name,(2026,1,1,0,0,0));ent.external_attr=0o100644<<16;ent.compress_type=method
    with src.open('rb') as inf, archive.open(ent,'w') as outf: shutil.copyfileobj(inf,outf,1024*1024)

def build_frontend():
    zig=Path(os.environ.get('K98_ZIG',str(ROOT/'tooling/ziglang/zig.exe')))
    os.environ.setdefault('ZIG_GLOBAL_CACHE_DIR',str(ROOT/'tooling/global-cache'))
    libs=A/'lib/arm64-v8a'
    flags=['cc','-target','aarch64-linux-android','-O2','-fPIC','-ffreestanding',
           '-fno-builtin','-fno-stack-protector','-fvisibility=hidden',
           '-I'+str(A/'jni/minimal'),'-I'+str(A/'jni'),'-I'+str(ROOT/'host'),
           '-I'+str(JDK.parent/'include')]
    cmd(zig,*flags,'-c',A/'jni/front.c','-o',B/'front.o')
    cmd(zig,*flags,'-c',A/'jni/minimal/mem.c','-o',B/'mem.o')
    # Debug info remains in the local objects. Only ship the bridge's runtime data.
    cmd(zig,'ld.lld','-shared','--no-undefined','--strip-debug','-z','max-page-size=16384',
        '-z','relro','-z','now','-soname','libk98front.so','-o',libs/'libk98front.so',
        B/'front.o',B/'mem.o',libs/'libretro.so')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk-only',action='store_true',help='Build the APK without requiring or generating private game-data archives.')
    args=parser.parse_args()
    classes=B/'classes'; dex=B/'dex'; keys=A/'signing'
    for d in (B,OUT,E,classes,dex,keys):d.mkdir(parents=True,exist_ok=True)
    build_frontend()
    generated=B/'generated'
    generated.mkdir(exist_ok=True)
    cmd(BT/'aapt.exe','package','-f','-0','mp4','-M',A/'AndroidManifest.xml','-S',A/'res','-A',A/'assets','-I',JAR,'-J',generated,'-F',B/'base.apk')
    boot=os.pathsep.join(map(str,(JAR,BT/'core-lambda-stubs.jar')))
    # javac does not remove vanished inner classes: never package an obsolete implementation.
    for old_class in (classes/'local/k98').glob('*.class'): old_class.unlink()
    cmd(JDK/'javac.exe','-encoding','UTF-8','-g:none','-Xlint:-options','-source','8','-target','8','-bootclasspath',boot,'-d',classes,*sorted((A/'src').rglob('*.java')),generated/'R.java')
    cmd(JDK/'jar.exe','cf',B/'classes.jar','-C',classes,'.')
    cmd(JDK/'java.exe','-cp',BT/'lib'/'d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',JAR,'--output',dex,B/'classes.jar')
    unsigned=B/'unsigned.apk';aligned=B/'aligned.apk';final=OUT/'KOF98-K12s.apk'
    with z.ZipFile(B/'base.apk') as base,z.ZipFile(unsigned,'w') as out:
        for info in base.infolist():out.writestr(info,base.read(info))
        archive_item(out,dex/'classes.dex','classes.dex',z.ZIP_DEFLATED)
        for name in ('libretro.so','libk98front.so'):archive_item(out,A/'lib'/'arm64-v8a'/name,'lib/arm64-v8a/'+name,z.ZIP_STORED)
    cmd(BT/'zipalign.exe','-P','16','-f','4',unsigned,aligned)
    key=keys/'k98.p12';pp=keys/'local.pass'
    if not key.exists():
        if not pp.exists():pp.write_text(secrets.token_hex(24),encoding='ascii')
        os.environ['K98_SIGN_PASS']=pp.read_text().strip()
        cmd(JDK/'keytool.exe','-genkeypair','-noprompt','-keystore',key,'-storetype','PKCS12','-storepass:env','K98_SIGN_PASS','-alias','k98','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=K98 Local Device Test')
    os.environ['K98_SIGN_PASS']=pp.read_text().strip()
    cmd(JDK/'java.exe','-jar',BT/'lib'/'apksigner.jar','sign','--ks',key,'--ks-key-alias','k98','--ks-pass','env:K98_SIGN_PASS','--v4-signing-enabled','false','--out',final,aligned)
    sig=cmd(JDK/'java.exe','-jar',BT/'lib'/'apksigner.jar','verify','--verbose','--print-certs',final,record='android-signature.txt')
    assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in sig
    cmd(BT/'zipalign.exe','-c','-P','16','-v','4',final,record='android-zipalign.txt')
    badge=cmd(BT/'aapt.exe','dump','badging',final,record='android-badging.txt')
    assert "package: name='local.k98'" in badge and "launchable-activity: name='local.k98.Main'" in badge and "native-code: 'arm64-v8a'" in badge
    perms=cmd(BT/'aapt.exe','dump','permissions',final,record='android-permissions.txt');assert 'uses-permission' not in perms
    with z.ZipFile(final) as f:assert not any(n.lower().endswith('.zip') or 'roms/' in n for n in f.namelist())
    if args.apk_only:
        with final.open('rb') as f:print(final.name,final.stat().st_size,hashlib.file_digest(f,'sha256').hexdigest(),flush=True)
        return
    items=[(ROOT/'phone'/'kof98.zip','roms/kof98.zip'),(ROOT/'phone'/'neogeo.zip','roms/neogeo.zip'),(ROOT/'host'/'system'/'fbneo'/'patched'/'neogeo.zip','system/fbneo/patched/neogeo.zip')]
    data=OUT/'KOF98-data.zip'
    if data.exists():
        with data.open('rb') as existing:
            if hashlib.file_digest(existing,'sha256').hexdigest()!='09f9b2137d25df7250b66b4fe7247a4885919877adfabea257865b5590768e6f':
                raise ValueError('Existing game data ZIP differs from the release baseline: '+str(data))
    else:
        with z.ZipFile(data,'w') as out:
            for src,name in items:archive_item(out,src,name,z.ZIP_STORED)
    with z.ZipFile(data) as f:assert f.testzip() is None
    with z.ZipFile(final) as f:assert not any(n.lower().endswith('.zip') or 'roms/' in n for n in f.namelist())
    for p in (final,data):
        with p.open('rb') as f: print(p.name,p.stat().st_size,hashlib.file_digest(f,'sha256').hexdigest(),flush=True)

if __name__=='__main__':main()
