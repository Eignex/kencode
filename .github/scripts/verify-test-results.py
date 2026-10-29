#!/usr/bin/env python3
"""Reject missing, empty, fully skipped, or failing expected test targets."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def verify(directory):
    reports = sorted(Path(directory).glob("TEST-*.xml"))
    if not reports:
        raise ValueError(f"No test reports in {directory}")
    executed = 0
    for report in reports:
        suite = ET.parse(report).getroot()
        cases = list(suite.iter("testcase"))
        if (int(suite.get("failures", 0)) or int(suite.get("errors", 0))
                or any(case.find("failure") is not None or case.find("error") is not None for case in cases)):
            raise ValueError(f"Failing test report: {report}")
        executed += sum(case.find("skipped") is None for case in cases)
    if not executed:
        raise ValueError(f"No executed tests in {directory}")
    print(f"Verified {executed} tests in {directory}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: verify-test-results.py DIRECTORY...")
    try:
        for directory in sys.argv[1:]:
            verify(directory)
    except (ValueError, ET.ParseError) as error:
        sys.exit(str(error))
