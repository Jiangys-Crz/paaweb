#!/usr/bin/env python3
"""Read-only regression checks against an isolated, ended XCPC test board.

Usage: RESOLVER_TEST_PASSWORD=... python3 scripts/test-resolver-http.py http://127.0.0.1:18081 3
The selected board must be public, frozen, resolver-enabled, and contain submissions
on both sides of its freeze. Never point this script at a production service.
"""
import datetime
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

base = sys.argv[1].rstrip('/')
board_id = int(sys.argv[2])
password = os.environ['RESOLVER_TEST_PASSWORD']

def request(path, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(base + path, data=data, headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(req, timeout=25)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        raw = response.read()
        try:
            body = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            body = None
        return response.status, response.headers, body

def success(path, payload=None):
    status, headers, body = request(path, payload)
    assert status == 200 and body and body.get('code') == 200, f'{path} failed: HTTP {status}, {body}'
    return headers, body['data']

def row_state(board):
    return {str(row['itemId']): (row.get('rank'), row.get('solved'), row.get('penalty'), row.get('problemStatus')) for row in board['rows']}

_, visible = success(f'/leaderboard/{board_id}')
assert visible['type'] == 'XCPC' and not visible['unfrozen'] and visible['resolverEnabled']
assert visible['rows'] and visible['problems'] and visible.get('freezeTime')
for supplied in (None, 'definitely-wrong'):
    status, _, body = request(f'/leaderboard/{board_id}/resolver', {'password': supplied})
    assert status in (401, 403) or body and body.get('code') in (401, 403), 'resolver accepted missing or incorrect password'
    assert not body or not body.get('data'), 'failed authentication returned data'
headers, resolved = success(f'/leaderboard/{board_id}/resolver', {'password': password})
assert 'no-store' in headers.get('Cache-Control', ''), 'resolver response must not be cached'
steps = resolved.get('resolutionSteps')
assert isinstance(steps, list) and steps, 'official resolver steps are missing'
step_types = [step.get('type') for step in steps]
assert step_types[-1] == 'complete', 'resolver does not end with complete'
assert {'selectTeam', 'judge', 'pause'} <= set(step_types), 'resolver is missing required official steps'
assert not any('presentation' in str(kind).lower() for kind in step_types), 'presentation-only step leaked into resolver'
frozen, final = resolved['frozenBoard'], resolved['finalBoard']
assert row_state(frozen) == row_state(visible), 'public leaderboard differs from frozen snapshot'
assert row_state(final) != row_state(frozen), 'test fixture does not exercise hidden results'
assert len(final['rows']) == len(frozen['rows'])
_, future = success(f'/leaderboard/{board_id}?timePoint=4102444800000')
assert row_state(future) == row_state(visible), 'future timePoint bypasses frozen leaderboard'
_, after = success(f'/leaderboard/{board_id}')
assert row_state(after) == row_state(visible), 'resolver changed public board or persisted an unfreeze'

def parse_time(value):
    # Python 3.9 accepts only three or six fractional digits; Java can emit 1-9.
    value = re.sub(r'\.(\d+)', lambda m: '.' + (m.group(1) + '000000')[:6], value)
    return datetime.datetime.fromisoformat(value)

freeze = parse_time(visible['freezeTime'])
frozen_count = 0
page = 1
while True:
    _, result = success(f'/leaderboard/submissions/{board_id}?page={page}&pageSize=200')
    records = result['records']
    for record in records:
        if parse_time(record['submitTime']) > freeze:
            frozen_count += 1
            assert record['status'] == 'PENDING' and record.get('score') is None
            assert record.get('scoreDisplay') == '-' and record.get('time') == '-' and record.get('memory') == '-'
            if frozen_count == 1:
                _, _, code = request(f"/leaderboard/submission/{record['submissionId']}/code")
                assert not code or code.get('code') != 200 or not code.get('data'), 'frozen submission code is accessible'
    if not records or page * 200 >= result['total']:
        break
    page += 1
assert frozen_count > 0, 'test fixture has no frozen submissions'
for status_name in ('Accepted', 'Wrong Answer'):
    _, filtered = success(f'/leaderboard/submissions/{board_id}?' + urllib.parse.urlencode({'page': 1, 'pageSize': 200, 'status': status_name}))
    assert all(parse_time(item['submitTime']) <= freeze for item in filtered['records']), 'status filter reveals frozen verdicts'
_, stats = success(f'/leaderboard/{board_id}/stats?timePoint=4102444800000')
_, stats_live = success(f'/leaderboard/{board_id}/stats')
assert stats == stats_live, 'future timePoint bypasses frozen statistics'
print(f'PASS: board {board_id}; {len(visible["rows"])} participants; {frozen_count} frozen submissions masked; resolver authorization and replay verified')
