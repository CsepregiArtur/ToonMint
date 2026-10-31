#!/usr/bin/env python3
"""Adaugă `include snippets/toonmint-security-headers.conf;` în locațiile SPA
din /etc/nginx/sites-available/toonmint.conf. Idempotent: nu dublează includerile.
"""
import re
import sys

PATH = "/etc/nginx/sites-available/toonmint.conf"
INCLUDE = "        include snippets/toonmint-security-headers.conf;"

with open(PATH, "r", encoding="utf-8") as fh:
    text = fh.read()

if "toonmint-security-headers.conf" in text:
    print("already patched")
    sys.exit(0)

# Ancore: începutul fiecărei locații care servește SPA-ul.
anchors = [
    '    location /assets/ {\n',
    '    location / {\n',
    '    location = /index.html {\n',
]
missing = [a for a in anchors if a not in text]
if missing:
    print("ANCHOR MISSING:", missing, file=sys.stderr)
    sys.exit(1)

for anchor in anchors:
    text = text.replace(anchor, anchor + INCLUDE + "\n", 1)

with open(PATH, "w", encoding="utf-8") as fh:
    fh.write(text)

print("patched:", len(anchors), "locations")
