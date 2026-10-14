"""Main-thread time per frame from `dumpsys gfxinfo <pkg> framestats` (stdin), ignoring GPU/render time.

The emulator renders in software, so total frame time says little; the UI-thread part
(input, animation, layout, recording draw commands) is what our code controls.
"""
import statistics
import sys

lines = sys.stdin.read().splitlines()
ui_ms = []
header = None
for line in lines:
    if line.startswith("Flags,"):
        header = line.rstrip(",").split(",")
        continue
    if header is None or not line or line.startswith("---"):
        continue
    parts = line.rstrip(",").split(",")
    if len(parts) != len(header) or not parts[0].isdigit():
        continue
    row = dict(zip(header, map(int, parts)))
    if row["Flags"] != 0:
        continue
    ui_ms.append((row["SyncQueued"] - row["HandleInputStart"]) / 1e6)

if not ui_ms:
    sys.exit("no frames")
ui_ms.sort()
p = lambda q: ui_ms[min(len(ui_ms) - 1, int(q * len(ui_ms)))]
print(f"frames={len(ui_ms)}  ui p50={p(0.5):.1f}ms p90={p(0.9):.1f}ms max={ui_ms[-1]:.1f}ms  "
      f">16ms: {sum(t > 16 for t in ui_ms)}  >32ms: {sum(t > 32 for t in ui_ms)}  mean={statistics.mean(ui_ms):.1f}ms")
