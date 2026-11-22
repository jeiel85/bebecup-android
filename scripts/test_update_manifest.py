import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import update_manifest  # noqa: E402


class UpdateManifestTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.apk = Path(self.dir.name) / "bebecup-v0.9.0.apk"
        self.apk.write_bytes(b"apk bytes")

    def tearDown(self):
        self.dir.cleanup()

    def test_writes_the_shape_the_app_reads(self):
        out = Path(self.dir.name) / "update.json"
        self.assertEqual(0, update_manifest.main(["10", "0.9.0", str(self.apk), str(out)]))
        data = json.loads(out.read_text(encoding="utf-8"))
        self.assertEqual(
            {
                "versionCode": 10,
                "versionName": "0.9.0",
                "apk": "bebecup-v0.9.0.apk",
                "sha256": hashlib.sha256(b"apk bytes").hexdigest(),
                "size": len(b"apk bytes"),
            },
            data,
        )

    def test_rejects_what_the_app_would_reject(self):
        with self.assertRaises(ValueError):
            update_manifest.manifest(0, "0.9.0", self.apk)
        with self.assertRaises(ValueError):
            update_manifest.manifest(10, "v0.9.0", self.apk)
        empty = Path(self.dir.name) / "empty.apk"
        empty.write_bytes(b"")
        with self.assertRaises(ValueError):
            update_manifest.manifest(10, "0.9.0", empty)
        odd = Path(self.dir.name) / "bebecup v0.9.0.apk"
        odd.write_bytes(b"x")
        with self.assertRaises(ValueError):
            update_manifest.manifest(10, "0.9.0", odd)


if __name__ == "__main__":
    unittest.main()
