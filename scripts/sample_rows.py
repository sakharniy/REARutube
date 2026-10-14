"""Sample one row of every content type found in the TV feeds, so the client's renderer knows each shape.

Usage: python sample_rows.py <out dir>
"""
import json
import pathlib
import sys
import urllib.request

OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
HEADERS = {"User-Agent": "okhttp/4.12.0", "Accept": "application/json"}
PANGOLIN = "https://rutube.ru/pangolin/api/tv/"
SLUGS = ["tab_new_main", "smart_category", "movies-serials", "kids", "sport", "live_smarttv", "premier", "birthday"]


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=25) as r:
        return json.loads(r.read())


seen = {}
for slug in SLUGS:
    feed = get(f"{PANGOLIN}feeds/{slug}/")
    tabs = feed.get("tabs") or []
    if slug == SLUGS[0] and tabs and tabs[0].get("resources"):
        print("resource keys:", list(tabs[0]["resources"][0]))
    print(f"feed {slug}: tabs={[t.get('name') for t in tabs]}")
    for tab in tabs:
        for res in (tab.get("resources") or [])[:40]:
            ct = res.get("content_type") or {}
            key = f"{ct.get('app_label')}.{ct.get('model')}"
            if key in seen:
                continue
            url = res.get("url")
            info = {"feed": slug, "row": res.get("name"), "url": url}
            try:
                data = get(url)
            except Exception as e:  # keep sampling other types
                info["error"] = str(e)
                seen[key] = info
                continue
            items = data.get("results") if isinstance(data, dict) else data
            first = items[0] if items else None
            info["top_keys"] = list(data)[:12] if isinstance(data, dict) else f"list[{len(data)}]"
            info["count"] = len(items or [])
            info["item_keys"] = list(first)[:45] if isinstance(first, dict) else type(first).__name__
            seen[key] = info
            (OUT / f"row_{key}.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")

for key, info in seen.items():
    print(f"\n== {key}  ({info['feed']} / {info['row']!r})")
    for k, v in info.items():
        if k not in ("feed", "row"):
            print(f"   {k}: {v}")
