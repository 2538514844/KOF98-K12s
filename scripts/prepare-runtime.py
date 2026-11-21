"""Extract the pinned, unmodified arm64 emulator core from a downloaded release APK."""
import argparse
import hashlib
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = '82637a59dbe07f544be97c79267acafa17ffe3766bcbe0fcee5b6efd540f2e0b'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--from-apk', type=Path, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.from_apk) as apk:
        core = apk.read('lib/arm64-v8a/libretro.so')
    if hashlib.sha256(core).hexdigest() != EXPECTED:
        raise SystemExit('The emulator core does not match the pinned release SHA-256.')
    target = ROOT / 'android/lib/arm64-v8a/libretro.so'
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.read_bytes() != core:
        raise SystemExit('Existing core differs; move it aside explicitly before replacing it.')
    target.write_bytes(core)
    print('Pinned core ready:', target, EXPECTED)

if __name__ == '__main__':
    main()
