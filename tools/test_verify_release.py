#!/usr/bin/env python3
import contextlib, io
from verify_release import verify
fingerprint='ab'*32
signature=f'Signer #1 certificate DN: CN=LESKOV\nSigner #1 certificate SHA-256 digest: {fingerprint}\n'
badging="package: name='com.aistudio.leskov.musix' versionCode='13' versionName='3.4.5' platformBuildVersionName='16'\n"
with contextlib.redirect_stdout(io.StringIO()): verify(signature,badging,fingerprint)
for sig, info, cert in [(signature,badging,''),(signature,badging,'cd'*32),(signature.replace('CN=LESKOV','CN=Android Debug'),badging,fingerprint),(signature,badging+'application-debuggable\n',fingerprint),(signature,badging.replace('versionCode=\'13\'','versionCode=\'10\''),fingerprint),(signature+signature,badging,fingerprint)]:
    try: verify(sig,info,cert)
    except ValueError: pass
    else: raise AssertionError('Invalid release identity was accepted')
print('PASS 7 release identity scenarios')
