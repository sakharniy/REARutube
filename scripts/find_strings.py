"""Aggregate string literals from jadx output that match a regex.

Usage: python find_strings.py <jadx sources dir> <regex> [limit]
Prints: literal <TAB> occurrences <TAB> first locations
"""
import collections
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
pattern = re.compile(sys.argv[2])
limit = int(sys.argv[3]) if len(sys.argv) > 3 else 400

LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')
hits = collections.defaultdict(list)
for path in root.rglob("*.java"):
    text = path.read_text(encoding="utf-8", errors="replace")
    for lineno, line in enumerate(text.splitlines(), 1):
        for m in LITERAL.finditer(line):
            if pattern.search(m.group(1)):
                hits[m.group(1)].append(f"{path.relative_to(root).as_posix()}:{lineno}")

for literal in sorted(hits)[:limit]:
    locations = hits[literal]
    print(f"{literal}\t{len(locations)}\t{' '.join(locations[:2])}")
print(f"# {len(hits)} unique literals", file=sys.stderr)
