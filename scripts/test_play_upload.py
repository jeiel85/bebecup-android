"""scripts/play_upload.py 단위 테스트 (표준 라이브러리만 사용).

    python3 -m unittest discover -s scripts -p "test_*.py"
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import play_upload as pu  # noqa: E402


class FakeResponse:
    def __init__(self, status_code=200, body=None):
        self.status_code = status_code
        self._body = body
        self.content = b"" if body is None else json.dumps(body).encode()
        self.text = self.content.decode()

    def json(self):
        if self._body is None:
            raise ValueError("no body")
        return self._body


class FakeSession:
    """URL 조각 → 응답 표. 호출 기록을 남긴다."""

    def __init__(self, routes):
        self.routes = routes
        self.calls = []

    def request(self, method, url, **kwargs):
        if "data" in kwargs and hasattr(kwargs["data"], "read"):
            kwargs["data"] = kwargs["data"].read()
        self.calls.append((method, url, kwargs))
        for (m, fragment), resp in self.routes.items():
            if m == method and url.endswith(fragment):
                return resp
        raise AssertionError(f"unexpected {method} {url}")


def ok_routes(version_code=6):
    return {
        ("POST", "/edits"): FakeResponse(200, {"id": "E1"}),
        ("POST", "/edits/E1/bundles"): FakeResponse(200, {"versionCode": version_code, "sha256": "ab"}),
        ("POST", "/deobfuscationFiles/proguard"): FakeResponse(200, {"deobfuscationFile": {}}),
        ("PUT", "/edits/E1/tracks/alpha"): FakeResponse(200, {"track": "alpha"}),
        ("POST", "/edits/E1:commit"): FakeResponse(200, {"id": "E1"}),
        ("DELETE", "/edits/E1"): FakeResponse(204),
    }


class PlayUploadTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.aab = self.tmp / "app.aab"
        self.aab.write_bytes(b"AAB")
        self.mapping = self.tmp / "mapping.txt"
        self.mapping.write_text("a -> b\n")
        for loc, text in (("ko-KR", "• 한국어 노트\r\n"), ("en-US", "• English note\n")):
            d = self.tmp / "notes" / loc / "changelogs"
            d.mkdir(parents=True)
            (d / "6.txt").write_text(text, encoding="utf-8", newline="")

    def run_upload(self, session, **overrides):
        kwargs = dict(
            aab=self.aab,
            version_code=6,
            notes=pu.read_release_notes(self.tmp / "notes", 6),
            track="alpha",
            status="draft",
            mapping=self.mapping,
            changes_not_sent_for_review=False,
            log=lambda *_: None,
        )
        kwargs.update(overrides)
        return pu.upload(session, **kwargs)

    def test_happy_path_uploads_bundle_mapping_track_and_commits(self):
        session = FakeSession(ok_routes())
        result = self.run_upload(session)
        self.assertEqual(result["status"], "draft")
        methods = [(m, u.rsplit("/v3/applications/", 1)[1]) for m, u, _ in session.calls]
        self.assertEqual(
            methods,
            [
                ("POST", "com.bebecup.app/edits"),
                ("POST", "com.bebecup.app/edits/E1/bundles"),
                ("POST", "com.bebecup.app/edits/E1/apks/6/deobfuscationFiles/proguard"),
                ("PUT", "com.bebecup.app/edits/E1/tracks/alpha"),
                ("POST", "com.bebecup.app/edits/E1:commit"),
            ],
        )
        _, bundle_url, bundle_kwargs = session.calls[1]
        self.assertIn("/upload/androidpublisher/v3/", bundle_url)
        self.assertEqual(bundle_kwargs["params"], {"uploadType": "media"})
        self.assertEqual(bundle_kwargs["data"], b"AAB")
        track_body = session.calls[3][2]["json"]
        self.assertEqual(
            track_body,
            {
                "track": "alpha",
                "releases": [
                    {
                        "versionCodes": ["6"],
                        "status": "draft",
                        "releaseNotes": [
                            {"language": "ko-KR", "text": "• 한국어 노트"},
                            {"language": "en-US", "text": "• English note"},
                        ],
                    }
                ],
            },
        )
        self.assertIsNone(session.calls[4][2]["params"])

    def test_changes_not_sent_for_review_is_passed_to_commit(self):
        session = FakeSession(ok_routes())
        self.run_upload(session, changes_not_sent_for_review=True, mapping=None)
        self.assertEqual(session.calls[-1][2]["params"], {"changesNotSentForReview": "true"})
        self.assertFalse(any("deobfuscation" in u for _, u, _ in session.calls))

    def test_version_code_mismatch_deletes_edit_and_stops(self):
        session = FakeSession(ok_routes(version_code=5))
        with self.assertRaisesRegex(pu.UploadError, "versionCode가 5"):
            self.run_upload(session)
        self.assertEqual(session.calls[-1][0], "DELETE")
        self.assertFalse(any(m == "PUT" for m, _, _ in session.calls))

    def test_api_error_is_reported_and_edit_deleted(self):
        routes = ok_routes()
        routes[("POST", "/edits/E1:commit")] = FakeResponse(
            400, {"error": {"message": "Changes cannot be sent for review automatically."}}
        )
        session = FakeSession(routes)
        with self.assertRaisesRegex(pu.UploadError, r"commit 실패\(HTTP 400\): Changes cannot"):
            self.run_upload(session)
        self.assertEqual(session.calls[-1][0], "DELETE")

    def test_production_requires_explicit_flag(self):
        with self.assertRaises(pu.UploadError):
            pu.check_track("production", allow_production=False)
        pu.check_track("production", allow_production=True)
        pu.check_track("alpha", allow_production=False)

    def test_release_notes_limits(self):
        long_note = self.tmp / "notes" / "en-US" / "changelogs" / "6.txt"
        long_note.write_text("x" * 501, encoding="utf-8")
        with self.assertRaisesRegex(pu.UploadError, "500자"):
            pu.read_release_notes(self.tmp / "notes", 6)
        with self.assertRaisesRegex(pu.UploadError, "출시 노트가 없어요"):
            pu.read_release_notes(self.tmp / "notes", 7)

    def test_service_account_key_validation_never_echoes_contents(self):
        with self.assertRaises(pu.UploadError) as ctx:
            pu.load_service_account_info({"PLAY_SERVICE_ACCOUNT_JSON": "{secret-not-json"})
        self.assertNotIn("secret", str(ctx.exception))
        with self.assertRaisesRegex(pu.UploadError, "service_account"):
            pu.load_service_account_info({"PLAY_SERVICE_ACCOUNT_JSON": '{"type": "user"}'})
        info = {"type": "service_account", "client_email": "ci@x.iam.gserviceaccount.com", "private_key": "k"}
        key_file = self.tmp / "key.json"
        key_file.write_text(json.dumps(info))
        self.assertEqual(pu.load_service_account_info({"PLAY_SERVICE_ACCOUNT_JSON_FILE": str(key_file)}), info)
        with self.assertRaisesRegex(pu.UploadError, "서비스 계정 키가 없어요"):
            pu.load_service_account_info({"PLAY_SERVICE_ACCOUNT_JSON_FILE": str(self.tmp / "missing.json")})

    def test_show_tracks_reads_and_deletes_edit_without_commit(self):
        routes = {
            ("POST", "/edits"): FakeResponse(200, {"id": "E1"}),
            ("GET", "/edits/E1/tracks"): FakeResponse(
                200,
                {
                    "tracks": [
                        {"track": "alpha", "releases": [{"name": "0.13.0", "versionCodes": ["14"], "status": "completed"}]},
                        {"track": "production", "releases": []},
                    ]
                },
            ),
            ("DELETE", "/edits/E1"): FakeResponse(204),
        }
        session = FakeSession(routes)
        lines = []
        tracks = pu.show_tracks(session, log=lines.append)
        self.assertEqual([t["track"] for t in tracks], ["alpha", "production"])
        self.assertEqual(lines, ["• alpha: 0.13.0 vc14 completed", "• production: (release 없음)"])
        self.assertEqual([m for m, _, _ in session.calls], ["POST", "GET", "DELETE"])
        self.assertFalse(any(":commit" in u for _, u, _ in session.calls))

    def test_show_tracks_deletes_edit_when_read_fails(self):
        routes = {
            ("POST", "/edits"): FakeResponse(200, {"id": "E1"}),
            ("GET", "/edits/E1/tracks"): FakeResponse(403, {"error": {"message": "The caller does not have permission"}}),
            ("DELETE", "/edits/E1"): FakeResponse(204),
        }
        session = FakeSession(routes)
        with self.assertRaisesRegex(pu.UploadError, "HTTP 403"):
            pu.show_tracks(session, log=lambda *_: None)
        self.assertEqual(session.calls[-1][0], "DELETE")

    def test_upload_args_required_unless_show_tracks(self):
        self.assertTrue(pu.parse_args(["--show-tracks"]).show_tracks)
        with self.assertRaises(SystemExit):
            pu.parse_args(["--aab", str(self.aab)])

    def test_dry_run_validates_without_network(self):
        info = {"type": "service_account", "client_email": "ci@x.iam.gserviceaccount.com", "private_key": "k"}
        key_file = self.tmp / "key.json"
        key_file.write_text(json.dumps(info))
        argv = ["--aab", str(self.aab), "--version-code", "6", "--notes-dir", str(self.tmp / "notes"), "--dry-run"]
        old = dict(pu.os.environ)
        try:
            pu.os.environ.pop("PLAY_SERVICE_ACCOUNT_JSON", None)
            pu.os.environ["PLAY_SERVICE_ACCOUNT_JSON_FILE"] = str(key_file)
            self.assertEqual(pu.main(argv), 0)
            self.assertEqual(pu.main(argv + ["--track", "production"]), 1)
        finally:
            pu.os.environ.clear()
            pu.os.environ.update(old)


if __name__ == "__main__":
    unittest.main()
