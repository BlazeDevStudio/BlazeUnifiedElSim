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


def horizontal_strip_bounds(raw, anchor):
    """Select only a scrollable ancestor containing the known toolbar control."""
    root = ET.fromstring(raw)
    for node in root.iter("node"):
        if node.attrib.get("scrollable") != "true":
            continue
        if not any(child.attrib.get("text") == anchor for child in node.iter("node")):
            continue
        rect = BOUNDS.fullmatch(node.attrib.get("bounds", ""))
        if rect:
            bounds = tuple(map(int, rect.groups()))
            if bounds[2] - bounds[0] >= 120 and bounds[3] > bounds[1]:
                return bounds
    return None


def find_target(args):
    # UIAutomator may show only the visible portion of a horizontally-scrolled
    # toolbar. Re-snapshot after each gesture; never invent a successful tap.
    scroll_bounds = None
    attempts = 20 if args.action == "tap" and args.scroll_anchor else (5 if args.action == "assert" else 1)
    for attempt in range(attempts):
        raw, current = nodes()
        if args.evidence:
            args.evidence.parent.mkdir(parents=True, exist_ok=True)
            args.evidence.write_bytes(raw)
        matches = [node for node in current if args.text in node.attrib.get("text", "")]
        if args.action == "tap":
            matches = [node for node in matches if node.attrib.get("text") == args.text]
        if matches:
            return matches[0], current
        if args.action == "tap" and args.scroll_anchor:
            if scroll_bounds is None:
                scroll_bounds = horizontal_strip_bounds(raw, args.scroll_anchor)
            if scroll_bounds is None:
                raise RuntimeError(f"Cannot find a scrollable toolbar containing {args.scroll_anchor!r}")
            x1, y1, x2, y2 = scroll_bounds
            y = (y1 + y2) // 2
            left = x1 + (x2 - x1) // 5
            right = x2 - (x2 - x1) // 5
            subprocess.run(["adb", "shell", "input", "swipe", str(right), str(y),
                            str(left), str(y), "350"], check=True, timeout=12)
            time.sleep(0.7)
        elif attempt + 1 < attempts:
            time.sleep(2)
    available = [node.attrib.get("text", "") for node in current if node.attrib.get("text", "")]
    print(f"UI {args.action} FAILED: {args.text!r}; visible text: {available[:45]!r}", file=sys.stderr)
    return None, current


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("assert", "tap"))
    parser.add_argument("text", help="Text fragment for assertion; exact button text for tap")
    parser.add_argument("--evidence", type=pathlib.Path)
    parser.add_argument("--scroll-anchor", help="Exact visible label within the intended horizontal toolbar (tap only)")
    args = parser.parse_args()
    if args.scroll_anchor and args.action != "tap":
        parser.error("--scroll-anchor is only valid with tap")
    target, _ = find_target(args)
    if target is None:
        return 1
    if args.action == "assert":
        print(f"UI text confirmed: {target.attrib.get('text', '')[:160]}")
        return 0

    rect = BOUNDS.fullmatch(target.attrib.get("bounds", ""))
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
