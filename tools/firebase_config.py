#!/usr/bin/env python3
"""Offline structural validation; does not establish server acceptance or FCM delivery."""
import re

def valid_config(data):
    try:
        project = data['project_info']
        number = project['project_number']
        if (not isinstance(number, str) or not re.fullmatch(r'[0-9]{6,}', number)
                or number == '100000000000' or project['project_id'] != 'leskov-b3863'):
            return False
        clients = [c for c in data['client'] if c['client_info'].get('android_client_info', {}).get('package_name') == 'com.aistudio.leskov.musix']
        if len(clients) != 1:
            return False
        client = clients[0]
        if not re.fullmatch(r'1:' + number + r':android:[0-9a-fA-F]+', client['client_info']['mobilesdk_app_id']):
            return False
        keys = [k['current_key'] for k in client['api_key']]
        return bool(keys) and all(isinstance(k, str) and re.fullmatch(r'AIza[A-Za-z0-9_-]{35}', k) and 'Fake' not in k for k in keys)
    except (KeyError, TypeError, AttributeError, ValueError):
        return False
