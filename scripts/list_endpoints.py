"""List Retrofit endpoints from jadx output where retrofit2.http annotations were obfuscated.

R8 renamed retrofit2.http.* to jo.a..jo.y in alphabetical order of the original names
(25 annotations, the full retrofit2.http set), e.g. jo.f = GET, jo.s = Path, jo.t = Query.

Usage: python list_endpoints.py <jadx sources dir> > endpoints.md
"""
import pathlib
import re
import sys

RETROFIT = ["Body", "DELETE", "Field", "FieldMap", "FormUrlEncoded", "GET", "HEAD", "HTTP",
            "Header", "HeaderMap", "Headers", "Multipart", "OPTIONS", "PATCH", "POST", "PUT",
            "Part", "PartMap", "Path", "Query", "QueryMap", "QueryName", "Streaming", "Tag", "Url"]
CLASS_OF = {f"InterfaceC{11255 + i}{chr(ord('a') + i)}": name for i, name in enumerate(RETROFIT)}
VERBS = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "HTTP"}

ANNOTATION = re.compile(r'@(InterfaceC112\d\d[a-y]|[A-Z][A-Za-z]+)(?:\(([^)]*)\))?')
RENAMED = re.compile(r'renamed from: ([\w.$]+)')

root = pathlib.Path(sys.argv[1])
rows = []
for path in root.rglob("*.java"):
    text = path.read_text(encoding="utf-8", errors="replace")
    if "jo.InterfaceC112" not in text:
        continue
    renamed = RENAMED.search(text)
    owner = renamed.group(1) if renamed else path.stem
    pending = []  # annotations collected above the current method
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("@"):
            pending.extend(ANNOTATION.findall(stripped))
            if not stripped.endswith(";"):
                continue
        if stripped.startswith("/*") or not pending:
            continue
        if "(" in stripped and stripped.endswith(";"):
            verb = path_ = None
            extras = []
            for cls, arg in pending:
                name = CLASS_OF.get(cls, cls)
                if name in VERBS:
                    verb, path_ = name, arg.strip('"')
                elif name == "Headers":
                    extras.append("Headers" + arg)
                elif name in ("Authorization", "FormUrlEncoded", "Multipart", "Streaming"):
                    extras.append(name)
            params = []
            for cls, arg in ANNOTATION.findall(stripped):
                name = CLASS_OF.get(cls)
                if name in ("Query", "Path", "Header", "Field", "Part"):
                    params.append(f"{name.lower()}:{arg.strip(chr(34))}")
                elif name in ("Body", "QueryMap", "HeaderMap", "FieldMap", "Url"):
                    params.append(name.lower())
            ret = re.search(r'InterfaceC\d+d<\? super ([\w.<>?, ]+)>', stripped)
            if verb:
                rows.append((path_ or "(dynamic @Url)", verb, ", ".join(params), " ".join(extras),
                             ret.group(1) if ret else "", owner))
            pending = []

rows.sort()
print("| Path | Method | Params | Flags | Returns | Interface |")
print("|---|---|---|---|---|---|")
for row in rows:
    print("| " + " | ".join(row) + " |")
print(f"\n{len(rows)} endpoints", file=sys.stderr)
