"""Create corresponding native source plus generated Xcode project, never build caches/signing data."""
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
repo = root.parent
files = subprocess.check_output(['git', 'ls-files', '-z', 'ios-app'], cwd=repo).decode().split('\0')
destination = root / 'dist' / 'QluCampus-iOS-0.1.0-source.zip'
destination.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in files:
        path = repo / name
        if path.is_file():
            assert not path.name.endswith(('.ipa', '.p12', '.mobileprovision'))
            archive.write(path, name)
    for folder in ['QluCampus.xcodeproj', 'Config', 'Resources/Assets.xcassets']:
        for path in (root / folder).rglob('*'):
            if path.is_file() and 'xcuserdata' not in path.parts:
                archive.write(path, path.relative_to(repo).as_posix())
    lock = root / 'Package.resolved'
    if lock.exists() and 'ios-app/Package.resolved' not in files:
        archive.write(lock, 'ios-app/Package.resolved')
    archive.write(repo / 'LICENSE', 'LICENSE')
    for name in ['docs/QLU-IOS-VALIDATION.md', '.github/workflows/ios-ci.yml']:
        path = repo / name
        if path.is_file():
            archive.write(path, name)
print(destination)
