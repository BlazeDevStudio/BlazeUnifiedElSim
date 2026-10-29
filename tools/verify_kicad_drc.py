#!/usr/bin/env python3
"""Classify real KiCad 9 PCB DRC JSON without claiming fabrication approval.

Run KiCad first with --format json --severity-all --exit-code-violations.
KiCad returns 0 for no reported findings and 5 for reported findings.
A complete report is required: rule violations, unconnected items, and parity
are separate arrays and MUST be counted independently.
"""
import argparse
import json
from pathlib import Path
from collections import Counter


class DrcEvidenceError(ValueError):
    pass


CATEGORIES = ("violations", "unconnected_items", "schematic_parity")


def classify(report, cli_exit):
    if cli_exit not in (0, 5):
        raise DrcEvidenceError(f"KiCad CLI failed unexpectedly (exit {cli_exit}); no validated DRC")
    if not isinstance(report, dict):
        raise DrcEvidenceError("KiCad DRC report must be a JSON object")
    if not all(category in report for category in CATEGORIES):
        missing = [key for key in CATEGORIES if key not in report]
        raise DrcEvidenceError(f"KiCad report missing required category arrays: {missing}")
    counts = {}
    types = Counter()
    for category in CATEGORIES:
        findings = report[category]
        if not isinstance(findings, list):
            raise DrcEvidenceError(f"KiCad report category {category} must be an array")
        if not all(isinstance(item, dict) for item in findings):
            raise DrcEvidenceError(f"Invalid DRC item in {category}")
        counts[category] = len(findings)
        for item in findings:
            # KiCad puts rule identity in 'type'; retain a stable fallback.
            kind = item.get("type", "UNSPECIFIED")
            if not isinstance(kind, str):
                raise DrcEvidenceError(f"Invalid DRC rule type in {category}")
            types[f"{category}:{kind}"] += 1
    total = sum(counts.values())
    if (cli_exit == 0 and total) or (cli_exit == 5 and not total):
        raise DrcEvidenceError(
            f"KiCad DRC exit {cli_exit} contradicts report total {total}; refusing clean verdict"
        )
    return {
        "schema": 1,
        "tool": "KiCad native PCB DRC",
        "kicad_version": report.get("kicad_version", "unspecified"),
        "cli_exit": cli_exit,
        "counts": counts,
        "total_findings": total,
        "rule_types": dict(sorted(types.items())),
        "reported_drc_clean": total == 0,
        # NEVER conflate file/DRC success with manufacturing approval.
        "fabrication_ready": False,
        "admission": "NO_GO",
        "reason": "BlazeBasic reference footprints are not manufacturer-qualified; unplaced physical components and manufacturing rules remain outside this gate.",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--cli-exit", type=int, required=True)
    parser.add_argument("--summary", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        report = json.loads(args.report.read_text(encoding="utf-8"))
        summary = classify(report, args.cli_exit)
    except (DrcEvidenceError, OSError, ValueError) as error:
        parser.exit(1, f"DRC evidence INVALID: {error}\n")
    args.summary.parent.mkdir(parents=True, exist_ok=True)
    args.summary.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2, sort_keys=True))
    print("DRC evidence captured; fabrication approval remains NO_GO.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
