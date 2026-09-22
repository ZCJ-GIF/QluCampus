"""Check the actual device package before publishing it."""
from pathlib import Path
import plistlib
import struct
import zipfile

ipa = Path('dist/QluCampus-iOS-0.1.0-unsigned.ipa')
with zipfile.ZipFile(ipa) as package:
    prefix = 'Payload/QluCampus.app/'
    info = plistlib.loads(package.read(prefix + 'Info.plist'))
    assert info['CFBundleIdentifier'] == 'com.qlucampus.ios'
    assert info['CFBundleShortVersionString'] == '0.1.0', info
    assert info['CFBundleVersion'] == '1', info
    assert info['MinimumOSVersion'] == '16.0'
    assert info['CFBundleSupportedPlatforms'] == ['iPhoneOS']
    binary = package.read(prefix + info['CFBundleExecutable'])
    assert struct.unpack_from('<II', binary) == (0xfeedfacf, 0x0100000c)
    assert b'--uitest' not in binary, 'Debug-only fixtures present in Release'
    assert not any(name.endswith('embedded.mobileprovision') for name in package.namelist())
    for name in ['GPL-3.0.txt', 'THIRD-PARTY-NOTICES.txt', 'PrivacyInfo.xcprivacy']:
        assert prefix + name in package.namelist(), name
print('Verified native arm64 / iOS 16 / 0.1.0 (1) unsigned package, without UI fixtures.')
