"""Checks that the CI report guard rejects green builds without passing tests."""

import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("reports", Path(__file__).with_name("verify-test-results.py"))
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)


class ReportVerificationTest(unittest.TestCase):
    def test_missing_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "No test reports"):
                reports.verify(directory)

    def test_rejects_empty_skipped_and_failing_results(self):
        examples = [
            '<testsuite tests="0"/>',
            '<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>',
            '<testsuite tests="1"><testcase><failure/></testcase></testsuite>',
            '<testsuite tests="1" errors="1"><testcase/></testsuite>',
        ]
        for xml in examples:
            with self.subTest(xml=xml), tempfile.TemporaryDirectory() as directory:
                Path(directory, "TEST-example.xml").write_text(xml)
                with self.assertRaises(ValueError):
                    reports.verify(directory)

    def test_accepts_executed_tests_alongside_skipped_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "TEST-example.xml").write_text(
                '<testsuite tests="2" skipped="1"><testcase/><testcase><skipped/></testcase></testsuite>'
            )
            reports.verify(directory)


if __name__ == "__main__":
    unittest.main()
