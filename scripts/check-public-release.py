#!/usr/bin/env python3
"""Check files that would be published, without printing matched credentials."""
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
FORBIDDEN_PARTS = {
    '.git', 'node_modules', '.next', 'target', 'venv', '.venv', '__pycache__',
    '.idea', '.vscode', 'uploads', 'backups', 'output', 'secrets', 'credentials',
}
PRIVATE_PATTERNS = [
    ('private key', re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')),
    ('machine-specific home path', re.compile(r'/(?:Users|home)/[A-Za-z0-9_.-]+/')),
    ('legacy repository URL', re.compile(r'gitee\.com/[^\s/]+/' + 'cwnu' + r'paaweb', re.I)),
    ('legacy organization domain', re.compile(r'\b(?:[a-z0-9-]+\.)*' + 'cwnu' + r'paa\.(?:cn|com|net)\b', re.I)),
    ('database export', re.compile(r'^COPY public\.\w+ .+ FROM stdin;', re.M)),
]
CREDENTIAL = re.compile(
    r'''(?ix)((?:app[_-]?key|api[_-]?key|access[_-]?token|secret|password|passwd)
        [\w.-]*)["']?\s*[:=]\s*["']([A-Za-z0-9_+/=-]{24,})["']'''
)


def candidates():
    try:
        output = subprocess.check_output(
            ['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'],
            cwd=ROOT,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        raise SystemExit('Run this check in a Git checkout with Git installed.')
    return sorted(set(p for p in output.decode().split('\0') if p))


def text_parts(path):
    try:
        return [path.read_text(encoding='utf-8')]
    except UnicodeError:
        if path.suffix.lower() == '.xlsx':
            with zipfile.ZipFile(path) as archive:
                return [archive.read(name).decode('utf-8') for name in archive.namelist()
                        if name.endswith('.xml')]
        return []


def main():
    findings = []
    paths = candidates()
    for name in paths:
        path = ROOT / name
        if not path.exists() or not path.is_file():
            continue
        parts = Path(name).parts
        if FORBIDDEN_PARTS.intersection(parts):
            findings.append((name, 'runtime/dependency/private directory'))
        if path.name in {'AGENTS.md', 'CLAUDE.md', '.DS_Store'}:
            findings.append((name, 'local developer file'))
        if path.name.startswith('.env') and not path.name.endswith('.example'):
            findings.append((name, 'private environment file'))
        if path.suffix.lower() in {'.pem', '.key', '.p12', '.pfx', '.jks', '.dump', '.backup', '.log'}:
            findings.append((name, 'private credential/backup/log file'))
        for content in text_parts(path):
            for kind, pattern in PRIVATE_PATTERNS:
                if pattern.search(content):
                    findings.append((name, kind))
            if name != Path(__file__).relative_to(ROOT).as_posix():
                for match in CREDENTIAL.finditer(content):
                    if match.group(1).upper().endswith('_ALPHABET'):
                        continue
                    value = match.group(2)
                    # Synthetic fixtures and published default admin hashes are expected.
                    if 'src/test/' in name or name == 'SQL/seed.sql':
                        continue
                    if len(set(value)) >= 14 and not value.lower().startswith(('example', 'your_', 'test_', 'placeholder')):
                        findings.append((name, 'possible hard-coded credential (value hidden)'))
    if findings:
        for name, kind in sorted(set(findings)):
            print(f'FAIL {name}: {kind}')
        print('Review findings before publishing; no matched values were printed.')
        return 1
    print(f'PASS: checked {len(paths)} publishable files; no common private artifacts found.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
