#!/usr/bin/env python3
"""Fail closed unless the signed APK matches the expected release identity."""
import os, re, sys
from pathlib import Path

def verify(signature, badging, expected):
    expected = expected.strip().replace(':','').lower()
    if not re.fullmatch(r'[0-9a-f]{64}', expected):
        raise ValueError('RELEASE_CERT_SHA256 must contain the known release certificate fingerprint')
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$', signature, re.M)
    if len(digests) != 1 or digests[0].lower() != expected:
        raise ValueError('APK signing certificate does not match expected release certificate')
    if re.search(r'CN\s*=\s*Android Debug', signature, re.I):
        raise ValueError('Debug signing certificate is not a release certificate')
    if not re.search(r"^package: name='com\.aistudio\.leskov\.musix' versionCode='11' versionName='3\.4\.3'", badging, re.M):
        raise ValueError('Unexpected package or version')
    if re.search(r'^application-debuggable\b', badging, re.M):
        raise ValueError('Debuggable APK is not a release candidate')
    print('PASS release identity, version and certificate')

if __name__ == '__main__':
    try:
        verify(Path(sys.argv[1]).read_text(), Path(sys.argv[2]).read_text(), os.getenv('EXPECTED_CERT_SHA256',''))
    except (OSError, ValueError, IndexError) as error:
        sys.exit(str(error))
