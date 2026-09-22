#!/usr/bin/env python3
"""Enumerate every native test, fail if an annotation cannot be accounted for."""
import json
import re
from pathlib import Path

def inventory(root: Path) -> list[str]:
    found = []
    for source in sorted(root.rglob('*.java')):
        text = source.read_text()
        if re.search(r'@Ignore\b|\bAssume\.', text):
            raise ValueError(f'Skipped native tests are not permitted: {source}')
        methods = re.findall(r'@Test(?:\s*\([^)]*\))?\s+public\s+void\s+(\w+)\s*\(', text)
        if len(methods) != len(re.findall(r'@Test\b', text)):
            raise ValueError(f'Unaccounted test annotation: {source}')
        if methods:
            package = re.search(r'^package\s+([\w.]+);', text, re.M).group(1)
            found += [f'{package}.{source.stem}#{method}' for method in methods]
    if len(found) != len(set(found)) or len(found) < 46:
        raise ValueError('Native test inventory lost tests or contains duplicates')
    return sorted(found)

if __name__ == '__main__':
    destination = Path('device-tests/inventory.json')
    destination.parent.mkdir(exist_ok=True)
    tests = inventory(Path('app/src/androidTest/java'))
    destination.write_text(json.dumps(tests, indent=2) + '\n')
    print(f'{len(tests)} native tests inventoried, no ignored annotations')
