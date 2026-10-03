#!/usr/bin/env python3
"""Repeatable launch/scroll checks on a selected emulator; writes raw evidence as JSON.

Network-backed catalog timing is not a controlled benchmark. Gfx/startup samples use
the same emulator and screen sizes; run baseline and optimized APKs in alternating
order after builds finish. Never target a physical device implicitly.
"""

import argparse
import hashlib
import json
import re
import statistics
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path


class Emulator:
    def __init__(self, adb, serial):
        if not serial.startswith("emulator-"):
            raise ValueError("This performance helper only operates on an explicitly named emulator")
        self.adb = adb
        self.serial = serial

    def command(self, *args, binary=False):
        result = subprocess.run(
            [self.adb, "-s", self.serial, *args],
            capture_output=True, timeout=45,
        )
        if result.returncode:
            raise RuntimeError((result.stdout + result.stderr).decode(errors="replace"))
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def shell(self, *args):
        return self.command("shell", *map(str, args))

    def hierarchy(self):
        self.shell("uiautomator", "dump", "/sdcard/anilocal-perf.xml")
        return ET.fromstring(self.shell("cat", "/sdcard/anilocal-perf.xml"))

    def click(self, label):
        root = self.hierarchy()
        for node in root.iter("node"):
            if node.get("text") == label or node.get("content-desc") == label:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
                self.shell("input", "tap", (x1 + x2) // 2, (y1 + y2) // 2)
                return True
        return False

    def capture(self, folder, name):
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{name}.png").write_bytes(self.command("exec-out", "screencap", "-p", binary=True))
        root = self.hierarchy()
        ET.ElementTree(root).write(folder / f"{name}.xml", encoding="utf-8")
        return sorted({n.get("text") for n in root.iter("node") if n.get("text")})

    def frames(self):
        raw = self.shell("dumpsys", "gfxinfo", "com.anilocal.app", "framestats")
        metrics = {}
        for name, pattern in {
            "frames": r"Total frames rendered: (\d+)",
            "janky_frames": r"Janky frames: (\d+)",
            "p50_ms": r"50th percentile: (\d+)ms",
            "p90_ms": r"90th percentile: (\d+)ms",
            "p95_ms": r"95th percentile: (\d+)ms",
            "p99_ms": r"99th percentile: (\d+)ms",
        }.items():
            match = re.search(pattern, raw)
            if match:
                metrics[name] = int(match.group(1))
        return metrics, raw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=str(Path.home() / "Android/Sdk/platform-tools/adb"))
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--seed-apk", type=Path, help="Install a debug APK for fixture setup before the measured optimized APK")
    parser.add_argument("--label", required=True)
    parser.add_argument("--layout", choices=("phone", "tablet"), default="tablet")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--launches", type=int, default=5)
    parser.add_argument("--seed-db", type=Path, help="Install an identical Room fixture in a debuggable APK")
    parser.add_argument("--clear-emulator-data", action="store_true", help="Reset only the selected emulator's app data")
    parser.add_argument("--settle-seconds", type=float, default=5, help="Allow live catalog/images to settle before scroll sampling")
    args = parser.parse_args()
    emulator = Emulator(args.adb, args.serial)
    if args.seed_apk or args.apk:
        emulator.command("install", "-r", str(args.seed_apk or args.apk))
    emulator.shell("logcat", "-c")
    if args.clear_emulator_data:
        emulator.shell("pm", "clear", "com.anilocal.app")
    if args.seed_db:
        emulator.shell("am", "force-stop", "com.anilocal.app")
        emulator.command("push", str(args.seed_db), "/data/local/tmp/anilocal-perf.db")
        emulator.shell("run-as", "com.anilocal.app", "mkdir", "-p", "databases")
        emulator.shell("run-as", "com.anilocal.app", "cp", "/data/local/tmp/anilocal-perf.db", "databases/anilocal.db")
        emulator.shell("run-as", "com.anilocal.app", "rm", "-f", "databases/anilocal.db-wal", "databases/anilocal.db-shm")
    if args.seed_apk and args.apk:
        emulator.command("install", "-r", str(args.apk))
    width, height = (2960, 1848) if args.layout == "tablet" else (1080, 2424)
    emulator.shell("wm", "size", f"{width}x{height}")
    emulator.shell("wm", "density", 320 if args.layout == "tablet" else 420)
    emulator.shell("input", "keyevent", 82)
    folder = args.output.parent / args.output.stem
    folder.mkdir(parents=True, exist_ok=True)
    evidence = {
        "label": args.label,
        "serial": args.serial,
        "layout": args.layout,
        "display": [width, height],
        "android": emulator.shell("getprop", "ro.build.version.release").strip(),
        "api": emulator.shell("getprop", "ro.build.version.sdk").strip(),
        "density": emulator.shell("wm", "density").strip(),
        "apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest() if args.apk else None,
        "startup_ms": [],
        "screens": {},
        "seed_db": str(args.seed_db) if args.seed_db else None,
        "seed_apk": str(args.seed_apk) if args.seed_apk else None,
        "cleared_app_data": args.clear_emulator_data,
        "settle_seconds": args.settle_seconds,
    }
    for number in range(args.launches):
        raw = emulator.shell("am", "start", "-W", "-S", "com.anilocal.app/.MainActivity")
        match = re.search(r"TotalTime: (\d+)", raw)
        if not match:
            raise RuntimeError(raw)
        evidence["startup_ms"].append(int(match.group(1)))
        (folder / f"launch-{number}.txt").write_text(raw)
        time.sleep(1)
    evidence["startup_median_ms"] = statistics.median(evidence["startup_ms"])
    time.sleep(args.settle_seconds)

    for tab in ("Home", "Explore", "Library", "Downloads", "More"):
        if not emulator.click(tab):
            raise RuntimeError(f"Cannot find navigation tab: {tab}")
        time.sleep(args.settle_seconds)
        visible = emulator.capture(folder, tab.lower())
        # Prime the same nearby rows/images before measuring scrolling in either APK.
        emulator.shell("input", "swipe", width // 2, height * 3 // 4, width // 2, height // 4, 300)
        emulator.shell("input", "swipe", width // 2, height // 4, width // 2, height * 3 // 4, 300)
        time.sleep(1)
        emulator.shell("dumpsys", "gfxinfo", "com.anilocal.app", "reset")
        for _ in range(6):
            emulator.shell("input", "swipe", width // 2, height * 3 // 4, width // 2, height // 4, 300)
            emulator.shell("input", "swipe", width // 2, height // 4, width // 2, height * 3 // 4, 300)
        metrics, raw = emulator.frames()
        (folder / f"{tab.lower()}-frames.txt").write_text(raw)
        evidence["screens"][tab] = {"visible_text": visible, "scroll_frames": metrics}
        args.output.write_text(json.dumps(evidence, indent=2) + "\n")
        print(f"{args.label} {args.layout} {tab}: {metrics}", flush=True)
    evidence["fatal_errors"] = emulator.shell("logcat", "-d", "-s", "AndroidRuntime:E")
    args.output.write_text(json.dumps(evidence, indent=2) + "\n")
    print(f"Startup median: {evidence['startup_median_ms']}ms", flush=True)


if __name__ == "__main__":
    main()
