#!/usr/bin/env python3
"""Compare Rago frame captures (CSV files written by the mod) between two configurations.

Usage:
    python3 bench/analyse.py A_DIR_OR_CSVS... --vs B_DIR_OR_CSVS... [--label-a vanilla] [--label-b argentum]
    python3 bench/analyse.py run/rago/frames-*.csv          # summary of one set only

Each CSV is one run. Metrics use the same definitions as gg.rago.perf.FrameStats:
  avg FPS   = frames / total time
  p50/p99   = nearest-rank percentiles of frame interval (ms)
  1% low    = 1 / mean of the slowest 1% of frame intervals (FPS)
Per-run values are compared with a two-sided Mann-Whitney U test (normal approximation,
no SciPy needed). With 5 runs per side the smallest p-value this approximation gives is about 0.012;
fewer runs cannot show a significant difference. No third-party packages required.
"""
import argparse
import glob
import math
import os
import statistics
import sys

METRICS = [
    ("avg_fps", "avg FPS", True),
    ("low1_fps", "1% low FPS", True),
    ("p50_ms", "p50 frame ms", False),
    ("p99_ms", "p99 frame ms", False),
    ("max_ms", "max frame ms", False),
]


def expand(paths):
    files = []
    for p in paths:
        if os.path.isdir(p):
            files.extend(sorted(glob.glob(os.path.join(p, "*.csv"))))
        else:
            files.extend(sorted(glob.glob(p)) or [p])
    return files


def read_capture(path):
    meta, intervals = {}, []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line.startswith("#"):
                key, _, value = line[1:].strip().partition("=")
                meta[key] = value
            elif line.startswith("frame,"):
                continue
            else:
                intervals.append(int(line.split(",")[1]))
    return meta, intervals


def percentile(sorted_vals, p):
    rank = math.ceil(p * len(sorted_vals))
    return sorted_vals[min(len(sorted_vals), max(1, rank)) - 1]


def run_stats(intervals):
    if not intervals:
        raise ValueError("capture has no frames")
    s = sorted(intervals)
    n = len(s)
    slow = s[n - max(1, n // 100):]
    return {
        "frames": n,
        "avg_fps": n * 1e9 / sum(s),
        "low1_fps": len(slow) * 1e9 / sum(slow),
        "p50_ms": percentile(s, 0.50) / 1e6,
        "p99_ms": percentile(s, 0.99) / 1e6,
        "max_ms": s[-1] / 1e6,
    }


def mann_whitney_p(a, b):
    """Two-sided Mann-Whitney U p-value, normal approximation with tie correction."""
    n1, n2 = len(a), len(b)
    if n1 == 0 or n2 == 0:
        return float("nan")
    combined = sorted([(v, 0) for v in a] + [(v, 1) for v in b])
    ranks = [0.0] * len(combined)
    i, tie_term = 0, 0.0
    while i < len(combined):
        j = i
        while j + 1 < len(combined) and combined[j + 1][0] == combined[i][0]:
            j += 1
        avg = (i + j) / 2 + 1
        for k in range(i, j + 1):
            ranks[k] = avg
        t = j - i + 1
        tie_term += t ** 3 - t
        i = j + 1
    r1 = sum(r for r, (_, g) in zip(ranks, combined) if g == 0)
    u1 = r1 - n1 * (n1 + 1) / 2
    mu = n1 * n2 / 2
    n = n1 + n2
    sigma = math.sqrt(n1 * n2 / 12 * ((n + 1) - tie_term / (n * (n - 1)))) if n > 1 else 0
    if sigma == 0:
        return 1.0
    z = (abs(u1 - mu) - 0.5) / sigma
    return max(0.0, min(1.0, math.erfc(z / math.sqrt(2))))


def load_set(paths):
    runs = []
    for path in expand(paths):
        meta, intervals = read_capture(path)
        stats = run_stats(intervals)
        stats["file"] = os.path.basename(path)
        stats["meta"] = meta
        runs.append(stats)
    return runs


def summarise(label, runs):
    print(f"\n== {label}: {len(runs)} run(s)")
    for r in runs:
        print(f"  {r['file']}: {r['frames']} frames, {r['avg_fps']:.1f} FPS, 1% low {r['low1_fps']:.1f}, "
              f"p99 {r['p99_ms']:.2f} ms")
    keys = ("gpu", "os", "java", "render_distance", "window", "vsync", "mods")
    first = runs[0]["meta"]
    for k in keys:
        values = {r["meta"].get(k, "?") for r in runs}
        flag = "" if len(values) == 1 else "   <-- differs between runs"
        print(f"  {k}: {first.get(k, '?')}{flag}")


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("a", nargs="+", help="CSV files or directories for configuration A")
    ap.add_argument("--vs", nargs="+", help="CSV files or directories for configuration B")
    ap.add_argument("--label-a", default="A")
    ap.add_argument("--label-b", default="B")
    args = ap.parse_args(argv)

    a = load_set(args.a)
    if not a:
        sys.exit("no CSV files found for A")
    summarise(args.label_a, a)
    if not args.vs:
        return
    b = load_set(args.vs)
    if not b:
        sys.exit("no CSV files found for B")
    summarise(args.label_b, b)

    print(f"\n{'metric':<14}{args.label_a + ' median':>16}{args.label_b + ' median':>16}{'change':>10}{'p-value':>10}")
    for key, name, higher_better in METRICS:
        va, vb = [r[key] for r in a], [r[key] for r in b]
        ma, mb = statistics.median(va), statistics.median(vb)
        change = (mb - ma) / ma * 100 if ma else float("nan")
        p = mann_whitney_p(va, vb)
        print(f"{name:<14}{ma:>16.2f}{mb:>16.2f}{change:>+9.1f}%{p:>10.3f}")
    if min(len(a), len(b)) < 5:
        print("\nNote: fewer than 5 runs per side; p-values cannot reach significance. Record more runs.")


if __name__ == "__main__":
    main(sys.argv[1:])
