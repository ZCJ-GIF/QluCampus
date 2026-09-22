"""Generate QluCampus version.json for a GitHub release. Does not publish anything.
GPL-3.0. Run after building; APK identity is checked before writing metadata.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', required=True, help='owner/repository')
    parser.add_argument('--apk', type=Path, default=ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--notes-file', type=Path, required=True)
    parser.add_argument('--aapt', type=Path, default=ROOT.parent / 'sdk/build-tools/36.0.0/aapt.exe')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', args.repo):
        parser.error('--repo must be owner/repository')
    source = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    version = re.search(r'versionName = "([0-9.]+)"', source).group(1)
    code = int(re.search(r'versionCode = (\d+)', source).group(1))
    badging = subprocess.check_output([str(args.aapt), 'dump', 'badging', str(args.apk)], text=True, encoding='utf-8')
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package.groups() != ('com.qlucampus.app', str(code), version):
        parser.error('APK does not match current QluCampus package/version')
    payload = dict(applicationId='com.qlucampus.app', versionCode=code, versionName=version,
        title=f'齐鲁课表 {version}', updateContent=args.notes_file.read_text(encoding='utf-8'),
        forceUpdate=False, type='feature', date=datetime.date.today().isoformat(),
        downloadUrl=f'https://github.com/{args.repo}/releases/download/v{version}/QluCampus-{version}-debug.apk',
        sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest())
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'Generated {args.out} for {args.repo} v{version} ({code})')

if __name__ == '__main__':
    main()
