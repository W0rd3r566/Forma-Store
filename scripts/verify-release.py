"""Fail closed if Android signature, package/version or upgrade identity is wrong."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess

report = Path('verification-report.txt')
def log(text):
    print(text, flush=True)
    with report.open('a') as out:
        out.write(text + '\n')
def run(*args):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    log(result.stdout)
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed with code {result.returncode}')
    return result.stdout

try:
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '35.0.0'
    if not (tools / 'apksigner').is_file():
        candidates = [p for p in tools.parent.iterdir() if (p / 'apksigner').is_file() and (p / 'aapt').is_file()]
        if not candidates:
            raise RuntimeError('Android build tools missing')
        tools = sorted(candidates, key=lambda p: [int(x) for x in re.findall(r'\d+', p.name)])[-1]
    log('Build tools: ' + str(tools))
    apk = Path('app/build/outputs/apk/release/app-release.apk')
    previous = Path('previous-release/forma-store.apk')
    def certificate(file):
        output = run(str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(file))
        # Some apksigner versions use "Signer (minSdkVersion=...)" rather than "Signer #1".
        digests = re.findall(r'certificate SHA-256 digest:\s*([a-fA-F0-9:]+)', output)
        digests = {value.replace(':', '').lower() for value in digests if len(value.replace(':', '')) == 64}
        if not digests:
            raise RuntimeError('No certificate SHA-256 digest found; do not skip verification')
        return digests
    new_cert = certificate(apk)
    old_cert = certificate(previous)
    if new_cert != old_cert:
        raise RuntimeError('Signing certificate differs from v0.1.0; refusing publication')
    def package(file):
        output = run(str(tools / 'aapt'), 'dump', 'badging', str(file))
        match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output)
        if not match:
            raise RuntimeError('Cannot parse APK package metadata')
        return match.group(1), int(match.group(2)), match.group(3)
    name, code, version = package(apk)
    old_name, old_code, _ = package(previous)
    if name != 'dev.formastore.app' or name != old_name or code <= old_code:
        raise RuntimeError('Package changed or versionCode did not increase')
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise RuntimeError('Invalid release version')
    ref = os.environ.get('GITHUB_REF', '')
    if ref.startswith('refs/tags/') and ref != 'refs/tags/v' + version:
        raise RuntimeError('Tag differs from APK version')
    shutil.copyfile(apk, 'forma-store.apk')
    digest = hashlib.file_digest(open('forma-store.apk', 'rb'), 'sha256').hexdigest()
    Path('forma-store.apk.sha256').write_text(digest + '  forma-store.apk\n')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as out:
        out.write('tag=v' + version + '\n')
    log('PASS: valid APK, same signing certificate, same package and increased versionCode.')
except Exception as error:
    log('ERROR: ' + str(error))
    raise
