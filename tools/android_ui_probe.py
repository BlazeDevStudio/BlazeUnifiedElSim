#!/usr/bin/env python3
"""Assertions and touch actions against the real Android accessibility hierarchy.

Unlike process-alive checks, these verify actual native controls and readouts.
"""
import argparse
import pathlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def nodes():
    last_error = None
    for _ in range(5):
        try:
            subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/bcl-window.xml"],
                           capture_output=True, check=True, timeout=22)
            result = subprocess.run(["adb", "exec-out", "cat", "/sdcard/bcl-window.xml"],
                                    capture_output=True, check=True, timeout=12)
            root = ET.fromstring(result.stdout)
            return result.stdout, list(root.iter("node"))
        except (subprocess.SubprocessError, ET.ParseError) as error:
            last_error = error
            time.sleep(1)
    raise RuntimeError(f"Android UI tree unavailable: {last_error}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("assert", "tap"))
    parser.add_argument("text", help="Text fragment for assertion; exact button text for tap")
    parser.add_argument("--evidence", type=pathlib.Path)
    args = parser.parse_args()

    raw, current = nodes()
    if args.evidence:
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_bytes(raw)
    matches = [node for node in current if args.text in node.attrib.get("text", "")]
    if not matches:
        available = [node.attrib.get("text", "") for node in current if node.attrib.get("text", "")]
        print(f"UI {args.action} FAILED: {args.text!r}; visible text: {available[:35]!r}", file=sys.stderr)
        return 1
    if args.action == "assert":
        print(f"UI text confirmed: {matches[0].attrib.get('text', '')[:160]}")
        return 0

    exact = [node for node in matches if node.attrib.get("text") == args.text]
    if not exact:
        print(f"UI tap FAILED: expected exact label {args.text!r}", file=sys.stderr)
        return 1
    rect = BOUNDS.fullmatch(exact[0].attrib.get("bounds", ""))
    if not rect:
        print("UI tap FAILED: missing accessibility bounds", file=sys.stderr)
        return 1
    x1, y1, x2, y2 = map(int, rect.groups())
    if x2 <= x1 or y2 <= y1:
        print("UI tap FAILED: invalid control bounds", file=sys.stderr)
        return 1
    x, y = (x1 + x2) // 2, (y1 + y2) // 2
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], check=True, timeout=12)
    print(f"Tapped {args.text!r} at ({x},{y})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
