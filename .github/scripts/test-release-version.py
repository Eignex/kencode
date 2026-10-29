"""Exercise exact release-tag routing and automatic main snapshots."""

import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("versions", Path(__file__).with_name("release-version.py"))
versions = importlib.util.module_from_spec(spec)
spec.loader.exec_module(versions)


class ReleaseVersionTest(unittest.TestCase):
    def test_main_snapshot_ignores_prerelease_tags(self):
        tags = ["v1.9.0", "v1.10.0", "v2.0.0-rc.9"]
        result = versions.resolve(tags, "branch", "main")
        self.assertEqual("1.10.1-SNAPSHOT", result["version"])
        self.assertEqual("true", result["is_snapshot"])
        self.assertEqual("false", result["test_release"])
        self.assertEqual("0.0.1-SNAPSHOT", versions.resolve([], "branch", "main")["version"])

    def test_rc_tag_is_used_exactly_without_incrementing(self):
        result = versions.resolve(["v1.3.0", "v1.3.1-rc.10"], "tag", "v1.3.1-rc.2")
        self.assertEqual("1.3.1-rc.2", result["version"])
        self.assertEqual("v1.3.1-rc.2", result["release_tag"])
        self.assertEqual("true", result["test_release"])
        self.assertEqual("true", result["is_prerelease"])
        self.assertEqual("false", result["is_snapshot"])

    def test_stable_tag_routes_to_central(self):
        result = versions.resolve(["v1.3.0"], "tag", "v1.4.0")
        self.assertEqual("1.4.0", result["version"])
        self.assertEqual("false", result["test_release"])
        self.assertEqual("false", result["is_prerelease"])
        self.assertEqual("false", result["is_snapshot"])

    def test_invalid_tags_and_non_main_branches_fail(self):
        for tag in ["v01.3.1", "v1.3.1-rc.01", "v1.3.1-rc..1", "v1.3.1-", "1.3.1"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                versions.resolve([], "tag", tag)
        with self.assertRaisesRegex(ValueError, "require a version tag"):
            versions.resolve([], "branch", "feature")


if __name__ == "__main__":
    unittest.main()
