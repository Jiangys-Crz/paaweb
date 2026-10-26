#!/usr/bin/env bash
# Build a self-contained maintenance page; an optional image replaces the default PAA logo.
set -euo pipefail
maintenance_here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
python3 - "$maintenance_here" "${1:-}" <<'PY'
import base64
import io
import pathlib
import sys
from urllib.parse import quote

here = pathlib.Path(sys.argv[1])
logo_path = sys.argv[2]
if logo_path:
    try:
        from PIL import Image
    except ImportError:
        sys.exit('自定义图片需要 Pillow：python3 -m pip install Pillow')
    image = Image.open(logo_path).convert('RGBA')
    image.thumbnail((384, 384), Image.Resampling.LANCZOS)
    buffer = io.BytesIO()
    image.save(buffer, format='WEBP', quality=88, method=6)
    uri = 'data:image/webp;base64,' + base64.b64encode(buffer.getvalue()).decode('ascii')
else:
    svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 168 168"><rect width="168" height="168" rx="36" fill="#1677ff"/><path d="M30 116 60 50 85 91 109 62 142 116Z" fill="#fff"/><text x="84" y="147" font-family="sans-serif" font-size="22" text-anchor="middle" fill="#fff">PAA</text></svg>'
    uri = 'data:image/svg+xml,' + quote(svg, safe='')
template = (here / 'index.template.html').read_text(encoding='utf-8')
if '{{LOGO_DATA_URI}}' not in template:
    sys.exit('维护页模板缺少 LOGO_DATA_URI 占位符')
(here / 'index.html').write_text(template.replace('{{LOGO_DATA_URI}}', uri), encoding='utf-8')
print('已生成自包含维护页：' + str(here / 'index.html'))
PY
