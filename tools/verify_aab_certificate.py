#!/usr/bin/env python3
"""Check the AAB signer output from keytool after jarsigner verification."""
import os
import re
import sys
from pathlib import Path

expected = os.environ.get('EXPECTED_CERT_SHA256', '').replace(':', '').strip().lower()
report = Path(sys.argv[1]).read_text()
certs = re.findall(r'^\s*SHA256:\s*([0-9A-Fa-f:]+)\s*$', report, re.M)
if not re.fullmatch(r'[0-9a-f]{64}', expected) or len(certs) != 1 or certs[0].replace(':', '').lower() != expected:
    sys.exit('AAB certificate does not match expected signing certificate')
print('PASS AAB certificate identity')
