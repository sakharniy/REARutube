"""Live check of the Rutube endpoints found in the Android TV APK (anonymous access only).

Usage: python probe_api.py <samples dir>
Saves each JSON response to <samples dir>/<name>.json and prints a one-line verdict per endpoint.
"""
import json
import pathlib
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
API = "https://rutube.ru/api/"
PANGOLIN = "https://rutube.ru/pangolin/api/tv/"
HEADERS = {"User-Agent": "okhttp/4.12.0", "Accept": "application/json", "x-client-version": "31.18.0"}
VIDEO_ID = re.compile(r"^[0-9a-f]{32}$")
results = []


def fetch(name, url, raw=False):
    req = urllib.request.Request(url, headers=HEADERS)
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            body = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        results.append((name, e.code, 0, url, ""))
        return None
    except (urllib.error.URLError, TimeoutError) as e:
        results.append((name, "ERR", 0, url, str(e)[:60]))
        return None
    if raw:
        results.append((name, status, len(body), url, body[:60].decode("utf-8", "replace").splitlines()[0]))
        return body.decode("utf-8", "replace")
    try:
        data = json.loads(body)
    except json.JSONDecodeError:
        results.append((name, status, len(body), url, "non-JSON: " + body[:40].decode("utf-8", "replace")))
        return None
    (OUT / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    keys = ",".join(list(data)[:7]) if isinstance(data, dict) else f"list[{len(data)}]"
    results.append((name, status, len(body), url, keys))
    return data


def find(obj, pred):
    """Depth-first search for the first dict matching pred."""
    if isinstance(obj, dict):
        if pred(obj):
            return obj
        children = obj.values()
    elif isinstance(obj, list):
        children = obj
    else:
        return None
    for child in children:
        hit = find(child, pred)
        if hit is not None:
            return hit
    return None


def play_options(name, vid):
    query = urllib.parse.urlencode({
        "pver": "v2", "referer": "https://tv-androidtv.rutube.ru", "ver": "31.18.0", "vr_ver": "2",
        "av1": "0", "ac_client": "android_tv", "ad_ver": "v2", "ac_request_id": str(uuid.uuid4())})
    return fetch(name, f"{API}play/options/{vid}/?{query}")


# 1. Server-driven TV navigation and screens
fetch("tv_navigation", f"{PANGOLIN}navigation/Android_TV")
for slug in ("tab_new_main", "sport", "live_smarttv", "movies-serials", "kids", "smart_category", "premier", "tv-channels"):
    fetch(f"tv_feed_{slug}", f"{PANGOLIN}feeds/{slug}/")
main = fetch("feed_tab_new_main", f"{API}feeds/tab_new_main/")

# 2. One row of the main page (first resource of the "main" tab)
row_url = None
if main:
    tab = next((t for t in main.get("tabs", []) if t.get("slug") == "main"), None)
    row_url = tab and tab["resources"][2]["url"]
row = fetch("row_sample", row_url) if row_url else None

# 3. Search
q = urllib.parse.quote("документальный")
fetch("search_combined", f"{API}search/combined/cards/list?query={q}")
search = fetch("search_video", f"{API}search/video/?query={q}")
fetch("search_autocomplete", f"{API}search/autocomplete/video/?query={urllib.parse.quote('докум')}")

# 4. Video page: info, stream, related, end screen, reactions
video = (search or {}).get("results", [{}])[0]
vid, author = video.get("id"), (video.get("author") or {}).get("id")
if vid:
    fetch("video_info", f"{API}video/{vid}/")
    opts = play_options("play_options_tv", vid)
    if opts:
        m3u8 = (opts.get("video_balancer") or {}).get("m3u8")
        ads = opts.get("advert") or {}
        results.append(("  advert.video.ad_breaks", "", 0, "", str([b.get("name") for b in (ads.get("video") or {}).get("ad_breaks", [])])))
        if m3u8:
            fetch("hls_master_vod", m3u8, raw=True)
    fetch("video_related", f"{API}video/recommendation/{vid}/")
    fetch("video_end_screen", f"{API}v2/video/end-screen/{vid}/?limit=0")
    fetch("video_votes", f"{API}numerator/video/{vid}/vote")

# 5. Channel
if author:
    fetch("channel_profile", f"{API}profile/user/{author}")
    fetch("channel_videos", f"{API}video/person/{author}/?page=1")
    fetch("channel_pinned_playlists", f"{API}v1/channels/{author}/pinned_playlists?limit=5&limit_video=5")

# 6. Live: find a live item in the live feed (or in its first row)
live = fetch("tv_feed_live_smarttv_raw", f"{API}feeds/live_smarttv/")
is_live = lambda d: d.get("is_livestream") is True and VIDEO_ID.match(str(d.get("id", "")))
item = find(live, is_live) if live else None
if live and not item:
    first_url = find(live, lambda d: str(d.get("url", "")).startswith(API))
    if first_url:
        item = find(fetch("live_row", first_url["url"]), is_live)
if item:
    lopts = play_options("play_options_live", item["id"])
    hls = ((lopts or {}).get("live_streams") or {}).get("hls") or []
    results.append(("  live", "", 0, "", f"{item.get('title', '')[:40]!r}, live_streams.hls: {len(hls)}"))
    if hls:
        fetch("hls_master_live", hls[0]["url"], raw=True)

for name, status, size, url, note in results:
    print(f"{name:<28} {status!s:>4} {size:>8}  {note[:70]:<70}  {url[:90]}")
