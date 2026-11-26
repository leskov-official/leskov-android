#!/usr/bin/env python3
"""Query OSV for the resolved Maven runtime modules. No key, token or app code is sent."""
import argparse
import json
from pathlib import Path
import sys
import urllib.request

URL = 'https://api.osv.dev/v1/querybatch'

def load_inventory(path):
    modules = []
    for line in Path(path).read_text().splitlines():
        name, version = line.split('\t')
        if name.count(':') != 1 or not all(name.split(':')) or not version.strip():
            raise ValueError('Malformed dependency inventory')
        modules.append((name, version))
    if not modules:
        raise ValueError('Empty dependency inventory')
    return sorted(set(modules))

def request_batch(queries):
    req = urllib.request.Request(URL, json.dumps({'queries': queries}).encode(),
                                 {'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)

def scan(modules, request=request_batch):
    findings = []
    for start in range(0, len(modules), 100):
        batch = modules[start:start+100]
        pending = [(name, version, {'package': {'ecosystem': 'Maven', 'name': name},
                                   'version': version}) for name, version in batch]
        for _ in range(20):
            if not pending:
                break
            data = request([q for _, _, q in pending])
            results = data['results']
            if not isinstance(results, list) or len(results) != len(pending):
                raise ValueError('Incomplete OSV response')
            next_pending = []
            for (name, version, query), result in zip(pending, results):
                for vuln in result.get('vulns', []):
                    findings.append({'module': name, 'version': version, 'id': vuln['id']})
                token = result.get('next_page_token')
                if token:
                    next_pending.append((name, version, dict(query, page_token=token)))
            pending = next_pending
        if pending:
            raise ValueError('OSV pagination incomplete')
    return findings

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inventory')
    parser.add_argument('report')
    args = parser.parse_args()
    report = Path(args.report)
    report.parent.mkdir(parents=True, exist_ok=True)
    try:
        modules = load_inventory(args.inventory)
        findings = scan(modules)
        result = {'status': 'findings' if findings else 'no_known_findings',
                  'modules_checked': len(modules), 'findings': findings, 'source': URL}
        report.write_text(json.dumps(result, indent=2) + '\n')
        print(f"OSV: {len(modules)} modules; {len(findings)} advisory matches. See report.")
        return 1 if findings else 0
    except Exception as error:
        report.write_text(json.dumps({'status': 'incomplete', 'error_type': type(error).__name__}, indent=2) + '\n')
        print('OSV audit incomplete: ' + type(error).__name__, file=sys.stderr)
        return 2

if __name__ == '__main__':
    sys.exit(main())
