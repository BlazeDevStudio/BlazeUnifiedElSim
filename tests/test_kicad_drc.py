"""Regression tests for actual KiCad DRC JSON admission classification."""
import importlib.util
from pathlib import Path
import unittest

TARGET = Path(__file__).resolve().parents[1] / "tools" / "verify_kicad_drc.py"
spec = importlib.util.spec_from_file_location("verify_kicad_drc", TARGET)
drc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(drc)


def report(**overrides):
    value = dict(violations=[], unconnected_items=[], schematic_parity=[], kicad_version="9.0.9")
    value.update(overrides)
    return value


class NativeDrcEvidenceTest(unittest.TestCase):
    def test_clean_report_still_not_manufacturing_approved(self):
        result = drc.classify(report(), 0)
        self.assertEqual(result["total_findings"], 0)
        self.assertTrue(result["reported_drc_clean"])
        self.assertFalse(result["fabrication_ready"])
        self.assertEqual(result["admission"], "NO_GO")

    def test_short_and_clearance_errors_are_counted(self):
        result = drc.classify(report(violations=[{"type": "shorting_items"}, {"type": "clearance"}]), 5)
        self.assertEqual(result["counts"]["violations"], 2)
        self.assertEqual(result["rule_types"]["violations:shorting_items"], 1)
        self.assertFalse(result["reported_drc_clean"])

    def test_unconnected_only_is_not_a_clean_board(self):
        result = drc.classify(report(unconnected_items=[{"type": "unconnected_items"}]), 5)
        self.assertEqual(result["total_findings"], 1)
        self.assertEqual(result["counts"]["violations"], 0)
        self.assertEqual(result["counts"]["unconnected_items"], 1)
        self.assertFalse(result["reported_drc_clean"])

    def test_schematic_parity_only_is_not_clean(self):
        result = drc.classify(report(schematic_parity=[{"type": "footprint_mismatch"}]), 5)
        self.assertEqual(result["counts"]["schematic_parity"], 1)

    def test_missing_array_fails_closed(self):
        for missing in drc.CATEGORIES:
            entry = report()
            del entry[missing]
            with self.subTest(missing=missing), self.assertRaises(drc.DrcEvidenceError):
                drc.classify(entry, 0)

    def test_bad_item_shape_fails_closed(self):
        for invalid in (None, "not-array", [{"type": 17}], ["not-item"]):
            with self.subTest(invalid=invalid), self.assertRaises(drc.DrcEvidenceError):
                drc.classify(report(unconnected_items=invalid), 5)

    def test_cli_error_and_contradictory_exit_fail_closed(self):
        for status, contents in (
            (1, report()), (8, report()), (0, report(violations=[{"type": "shorting_items"}])),
            (5, report()),
        ):
            with self.subTest(status=status), self.assertRaises(drc.DrcEvidenceError):
                drc.classify(contents, status)

    def test_unregistered_blazebasic_is_a_hard_regression(self):
        issue = {"type": "lib_footprint_issues",
                 "description": "The current configuration does not include the footprint library 'BlazeBasic'."}
        with self.assertRaisesRegex(drc.DrcEvidenceError, "cannot resolve"):
            drc.classify(report(violations=[issue]), 5, require_library="BlazeBasic")
        # Recorded errors remain visible when no specific library assertion is supplied.
        result = drc.classify(report(violations=[issue]), 5)
        self.assertEqual(result["total_findings"], 1)
        self.assertFalse(result["reported_drc_clean"])

    def test_disabled_blazebasic_is_a_hard_regression(self):
        issue = {"type": "lib_footprint_issues",
                 "description": "The footprint library 'BlazeBasic' is not enabled in the current configuration."}
        with self.assertRaisesRegex(drc.DrcEvidenceError, "cannot resolve"):
            drc.classify(report(violations=[issue]), 5, require_library="BlazeBasic")

    def test_other_real_violations_are_never_suppressed(self):
        issue = {"type": "clearance", "description": "Copper clearance violation"}
        result = drc.classify(report(violations=[issue]), 5, require_library="BlazeBasic")
        self.assertEqual(result["rule_types"]["violations:clearance"], 1)
        self.assertFalse(result["reported_drc_clean"])
        self.assertEqual(result["admission"], "NO_GO")

    def test_incorrect_top_level_shape_fails_closed(self):
        for value in (None, [], "broken", {}):
            with self.subTest(value=value), self.assertRaises(drc.DrcEvidenceError):
                drc.classify(value, 0)


if __name__ == "__main__":
    unittest.main()
