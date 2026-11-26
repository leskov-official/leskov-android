#!/usr/bin/env python3
"""Offline regression tests for Firebase preflight, dependency scan and backup policy."""
import copy
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET
from firebase_config import valid_config
from audit_dependencies import load_inventory, scan

root = Path(__file__).resolve().parents[1]
# Synthetic fixture, not a real key.
config = {'project_info': {'project_id': 'leskov-b3863', 'project_number': '123456789123'},
          'client': [{'client_info': {'mobilesdk_app_id': '1:123456789123:android:abc123',
                                      'android_client_info': {'package_name': 'com.aistudio.leskov.musix'}},
                      'api_key': [{'current_key': 'AIza'+'x'*35}]}]}
assert valid_config(config)
for target, key, value in [(('project_info',), 'project_id', 'wrong'),
                           (('project_info',), 'project_number', '123456789124'),
                           (('client', 0, 'client_info'), 'mobilesdk_app_id', 'wrong'),
                           (('client', 0, 'client_info', 'android_client_info'), 'package_name', 'wrong'),
                           (('client', 0, 'api_key', 0), 'current_key', 'FakeKeyForLeskov')]:
    bad = copy.deepcopy(config)
    node = bad
    for part in target: node = node[part]
    node[key] = value
    assert not valid_config(bad)
bad = copy.deepcopy(config)
bad['client'].append(copy.deepcopy(bad['client'][0]))
assert not valid_config(bad)
assert not valid_config({})
modules = [('group:artifact', '1.2.3')]
assert scan(modules, lambda q: {'results': [{}]}) == []
assert scan(modules, lambda q: {'results': [{'vulns': [{'id': 'TEST-1'}]}]}) == [{'module': 'group:artifact', 'version': '1.2.3', 'id': 'TEST-1'}]
assert scan(modules, lambda q: {'results': [{'vulns': [{'id': 'TEST-2'}]}]} if 'page_token' in q[0] else {'results': [{'next_page_token': 'next'}]})[0]['id'] == 'TEST-2'
try: scan(modules, lambda q: {'results': []})
except ValueError: pass
else: raise AssertionError('Incomplete scan accepted')
with tempfile.TemporaryDirectory() as d:
    f = Path(d)/'modules.tsv'; f.write_text('group:artifact\t1.2.3\n')
    assert load_inventory(f) == modules
    f.write_text('')
    try: load_inventory(f)
    except ValueError: pass
    else: raise AssertionError('Empty inventory accepted')
required = {'root','file','database','sharedpref','external','device_root','device_file','device_database','device_sharedpref'}
xml = root/'app/src/main/res/xml'
legacy = ET.parse(xml/'backup_rules.xml').getroot()
modern = ET.parse(xml/'data_extraction_rules.xml').getroot()
for rules in (legacy, modern.find('cloud-backup'), modern.find('device-transfer')):
    assert {e.attrib['domain'] for e in rules.findall('exclude') if e.attrib['path'] == '.'} == required
print('PASS Firebase config, OSV scanner and backup policy regressions (offline)')
