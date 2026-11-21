"""Verify APK metadata, signature, alignment and optional unchanged-payload baseline."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('K98_SDK',os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME',r'G:\AndroidTools'))))
BT=SDK/'build-tools/35.0.1'
JDK=Path(os.environ.get('K98_JDK',os.environ.get('JAVA_HOME',r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot')))/'bin'
CORE_SHA='82637a59dbe07f544be97c79267acafa17ffe3766bcbe0fcee5b6efd540f2e0b'

def run(*args):
    p=subprocess.run(list(map(str,args)),stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    if p.returncode:raise RuntimeError(p.stdout.decode(errors='replace'))
    return p.stdout.decode('utf-8',errors='replace')

def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def certificate(path):
    result=run(JDK/'java.exe','-jar',BT/'lib/apksigner.jar','verify','--verbose','--print-certs',path)
    for scheme in ('v2','v3'):
        assert 'Verified using '+scheme+' scheme (APK Signature Scheme '+scheme+'): true' in result
    return re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',result)[1]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--baseline',type=Path)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    signer=certificate(args.apk)
    run(BT/'zipalign.exe','-c','-P','16','4',args.apk)
    badge=run(BT/'aapt.exe','dump','badging',args.apk)
    assert "package: name='local.k98'" in badge and "launchable-activity: name='local.k98.Main'" in badge
    assert "native-code: 'arm64-v8a'" in badge
    assert 'uses-permission' not in run(BT/'aapt.exe','dump','permissions',args.apk)
    version=re.search(r"versionName='([^']+)'",badge)[1]
    count=0
    with zipfile.ZipFile(args.apk) as apk:
        assert apk.testzip() is None
        assert all(name in apk.namelist() for name in ('classes.dex','AndroidManifest.xml','resources.arsc'))
        assert not any(n.lower().endswith(('.zip','.p12','.jks','.pass')) or 'roms/' in n.lower() for n in apk.namelist())
        assert hashlib.sha256(apk.read('lib/arm64-v8a/libretro.so')).hexdigest()==CORE_SHA
        if args.baseline:
            assert certificate(args.baseline)==signer,'Signer changed'
            with zipfile.ZipFile(args.baseline) as old:
                assert set(old.namelist())==set(apk.namelist())
                for name in old.namelist():
                    if name in ('classes.dex','AndroidManifest.xml') or name.startswith('META-INF/'):continue
                    assert old.read(name)==apk.read(name),'Unexpected payload change: '+name
                    count+=1
                assert old.read('classes.dex')!=apk.read('classes.dex')
    report={'version':version,'apkBytes':args.apk.stat().st_size,'apkSha256':sha(args.apk),'certificateSha256':signer,'signatures':['v2','v3'],'zipAlignment':'16KiB','architecture':'arm64-v8a','permissions':[],'unchangedPayloadEntriesCompared':count,'coreSha256':CORE_SHA,'deviceTested':False}
    if args.baseline:report.update(baselineSha256=sha(args.baseline),sameSigner=True)
    if args.output:
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
