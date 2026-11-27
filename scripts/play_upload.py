#!/usr/bin/env python3
"""베베컵 AAB를 Google Play Developer Publishing API(v3)로 테스트 트랙에 올립니다.

    python3 scripts/play_upload.py --aab app-release.aab --version-code 6 \
        --notes-dir fastlane/metadata/android [--mapping mapping.txt] \
        [--track alpha] [--status draft|completed] [--dry-run]
    python3 scripts/play_upload.py --show-tracks   # 트랙별 release 읽기만(변경 없음)

흐름: edit 만들기 → AAB 업로드(versionCode 확인) → R8 mapping 업로드(선택)
→ 트랙에 release 추가(출시 노트 ko-KR·en-US) → commit. 중간에 실패하면 edit를 지워
Play Console에 아무것도 남기지 않습니다.

- 기본 트랙은 alpha(비공개 테스트 - Alpha), 기본 상태는 draft입니다. draft는 Play Console에서
  검토 후 '출시'를 눌러야 테스터에게 나가고, completed는 commit 즉시 테스터에게 출시됩니다.
- --show-tracks는 edit를 만들어 트랙 목록을 읽고 지웁니다(commit하지 않아 Play에 남는 변경 없음).
  서비스 계정 인증·앱 권한을 실제로 확인하는 용도입니다.
- production 트랙은 --allow-production 없이는 거부합니다.
- 서비스 계정 키는 환경 변수 PLAY_SERVICE_ACCOUNT_JSON(JSON 내용, CI용) 또는
  PLAY_SERVICE_ACCOUNT_JSON_FILE(경로) 또는 .keystore/play-service-account.json(git 제외)에서 읽습니다.
  키 내용은 출력하지 않습니다.

의존성: scripts/requirements-play.txt (google-auth, requests). --dry-run은 표준 라이브러리만 씁니다.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

PACKAGE_NAME = "com.bebecup.app"
LOCALES = ("ko-KR", "en-US")
NOTES_MAX_CHARS = 500  # Play Console의 locale별 출시 노트 한도
STATUSES = ("draft", "completed")
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
DEFAULT_KEY_FILE = Path(".keystore/play-service-account.json")
# 22MB AAB 업로드가 느린 회선에서도 끝나도록 읽기 제한을 넉넉히 둔다(연결, 읽기 초).
UPLOAD_TIMEOUT = (30, 900)
API_TIMEOUT = (30, 120)


class UploadError(Exception):
    """사용자에게 그대로 보여 줄 실패."""


def read_release_notes(notes_dir: Path, version_code: int) -> list[dict[str, str]]:
    notes = []
    for locale in LOCALES:
        path = notes_dir / locale / "changelogs" / f"{version_code}.txt"
        if not path.is_file():
            raise UploadError(f"출시 노트가 없어요: {path}")
        text = path.read_text(encoding="utf-8").replace("\r\n", "\n").strip()
        if not text:
            raise UploadError(f"출시 노트가 비어 있어요: {path}")
        if len(text) > NOTES_MAX_CHARS:
            raise UploadError(f"출시 노트가 {NOTES_MAX_CHARS}자를 넘어요({len(text)}자): {path}")
        notes.append({"language": locale, "text": text})
    return notes


def check_track(track: str, allow_production: bool) -> None:
    if track == "production" and not allow_production:
        raise UploadError("production 트랙은 --allow-production 을 붙여야만 올릴 수 있어요")


def load_service_account_info(environ=os.environ) -> dict:
    raw = environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    source = "PLAY_SERVICE_ACCOUNT_JSON"
    if not raw:
        path = Path(environ.get("PLAY_SERVICE_ACCOUNT_JSON_FILE") or DEFAULT_KEY_FILE)
        source = str(path)
        if not path.is_file():
            raise UploadError(
                f"서비스 계정 키가 없어요: {path} (또는 PLAY_SERVICE_ACCOUNT_JSON 환경 변수). docs/RELEASE.md 참고"
            )
        raw = path.read_text(encoding="utf-8")
    try:
        info = json.loads(raw)
    except json.JSONDecodeError as e:
        # 키 내용이 로그에 남지 않도록 위치만 알린다.
        raise UploadError(f"서비스 계정 키({source})가 올바른 JSON이 아니에요(줄 {e.lineno})") from None
    if info.get("type") != "service_account" or not info.get("client_email") or not info.get("private_key"):
        raise UploadError(f"서비스 계정 키({source})에 type=service_account, client_email, private_key 가 있어야 해요")
    return info


def authorized_session(info: dict):
    try:
        from google.auth.transport.requests import AuthorizedSession
        from google.oauth2 import service_account
    except ImportError:
        raise UploadError(
            "google-auth/requests 가 없어요: python -m pip install -r scripts/requirements-play.txt"
        ) from None
    creds = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
    return AuthorizedSession(creds)


def _call(session, method: str, url: str, what: str, **kwargs) -> dict:
    kwargs.setdefault("timeout", API_TIMEOUT)
    resp = session.request(method, url, **kwargs)
    if resp.status_code >= 400:
        try:
            message = resp.json().get("error", {}).get("message") or resp.text
        except ValueError:
            message = resp.text
        raise UploadError(f"{what} 실패(HTTP {resp.status_code}): {message.strip()[:1000]}")
    if not resp.content:
        return {}
    return resp.json()


def upload(
    session,
    *,
    aab: Path,
    version_code: int,
    notes: list[dict[str, str]],
    track: str,
    status: str,
    mapping: Path | None,
    changes_not_sent_for_review: bool,
    package: str = PACKAGE_NAME,
    log=print,
) -> dict:
    app = f"{API}/{package}"
    edit_id = _call(session, "POST", f"{app}/edits", "edit 만들기", json={})["id"]
    log(f"• edit {edit_id}")
    try:
        with aab.open("rb") as f:
            bundle = _call(
                session,
                "POST",
                f"{UPLOAD_API}/{package}/edits/{edit_id}/bundles",
                "AAB 업로드",
                params={"uploadType": "media"},
                headers={"Content-Type": "application/octet-stream"},
                data=f,
                timeout=UPLOAD_TIMEOUT,
            )
        uploaded = int(bundle.get("versionCode", -1))
        if uploaded != version_code:
            raise UploadError(f"올린 AAB의 versionCode가 {uploaded}이에요(기대값 {version_code})")
        log(f"• AAB 업로드 versionCode {uploaded} sha256 {bundle.get('sha256', '?')}")

        if mapping is not None:
            with mapping.open("rb") as f:
                _call(
                    session,
                    "POST",
                    f"{UPLOAD_API}/{package}/edits/{edit_id}/apks/{version_code}/deobfuscationFiles/proguard",
                    "R8 mapping 업로드",
                    params={"uploadType": "media"},
                    headers={"Content-Type": "application/octet-stream"},
                    data=f,
                    timeout=UPLOAD_TIMEOUT,
                )
            log("• R8 mapping 업로드")

        # Track.releases는 update 요청에서 '바라는 변경'이라, 새 release만 보내면
        # completed일 때 이전 release를 대체하고 draft일 때는 초안이 추가된다.
        release = {"versionCodes": [str(version_code)], "status": status, "releaseNotes": notes}
        _call(
            session,
            "PUT",
            f"{app}/edits/{edit_id}/tracks/{track}",
            f"{track} 트랙 갱신",
            json={"track": track, "releases": [release]},
        )
        log(f"• {track} 트랙에 {status} release 추가")

        params = {"changesNotSentForReview": "true"} if changes_not_sent_for_review else None
        _call(session, "POST", f"{app}/edits/{edit_id}:commit", "commit", params=params)
    except BaseException:
        try:
            _call(session, "DELETE", f"{app}/edits/{edit_id}", "edit 지우기")
            log(f"• 실패해서 edit {edit_id}를 지웠어요(Play Console에 남은 변경 없음)")
        except Exception as cleanup_error:  # 원래 실패를 가리지 않도록 정리 실패는 알리기만 한다.
            log(f"⚠ edit {edit_id} 지우기도 실패했어요(Play가 시간이 지나면 만료시켜요): {cleanup_error}")
        raise
    return {"edit": edit_id, "versionCode": version_code, "track": track, "status": status}


def show_tracks(session, *, package: str = PACKAGE_NAME, log=print) -> list[dict]:
    """트랙별 release(이름·versionCodes·status)를 읽어 출력한다. edit는 commit하지 않고 지운다."""
    app = f"{API}/{package}"
    edit_id = _call(session, "POST", f"{app}/edits", "edit 만들기", json={})["id"]
    try:
        tracks = _call(session, "GET", f"{app}/edits/{edit_id}/tracks", "트랙 읽기").get("tracks", [])
    finally:
        try:
            _call(session, "DELETE", f"{app}/edits/{edit_id}", "edit 지우기")
        except Exception as cleanup_error:  # 읽기 결과를 가리지 않도록 알리기만 한다.
            log(f"⚠ edit {edit_id} 지우기 실패(Play가 시간이 지나면 만료시켜요): {cleanup_error}")
    for track in tracks:
        releases = track.get("releases") or []
        if not releases:
            log(f"• {track.get('track')}: (release 없음)")
        for r in releases:
            codes = ",".join(r.get("versionCodes") or []) or "-"
            log(f"• {track.get('track')}: {r.get('name', '?')} vc{codes} {r.get('status', '?')}")
    return tracks


def parse_args(argv):
    p = argparse.ArgumentParser(description="베베컵 AAB를 Google Play 테스트 트랙에 올립니다.")
    p.add_argument("--show-tracks", action="store_true", help="트랙별 release만 읽어 보여 줘요(업로드 인자 불필요)")
    p.add_argument("--aab", type=Path)
    p.add_argument("--version-code", type=int)
    p.add_argument("--notes-dir", type=Path, help="<locale>/changelogs/<versionCode>.txt 가 있는 폴더")
    p.add_argument("--mapping", type=Path, help="R8 mapping.txt (난독화 해제 파일)")
    p.add_argument("--track", default="alpha")
    p.add_argument("--status", default="draft", choices=STATUSES)
    p.add_argument("--allow-production", action="store_true")
    p.add_argument(
        "--changes-not-sent-for-review",
        action="store_true",
        help="commit이 '자동으로 검토에 보낼 수 없음'으로 거부될 때: Play Console에서 직접 검토에 보내요",
    )
    p.add_argument("--dry-run", action="store_true", help="입력만 검증하고 Play에는 접속하지 않아요")
    args = p.parse_args(argv)
    if not args.show_tracks:
        missing = [f"--{n}" for n in ("aab", "version-code", "notes-dir") if getattr(args, n.replace("-", "_")) is None]
        if missing:
            p.error(f"업로드에는 {', '.join(missing)} 가 필요해요(트랙만 보려면 --show-tracks)")
    return args


def main(argv=None) -> int:
    # Windows의 Git Bash 파이프는 기본 cp949라 ✓·• 같은 글자에서 죽지 않도록 UTF-8로 맞춘다.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")
    args = parse_args(argv)
    if args.show_tracks:
        try:
            info = load_service_account_info()
            print(f"{PACKAGE_NAME} 트랙 상태 as {info['client_email']}")
            show_tracks(authorized_session(info))
        except UploadError as e:
            print(f"✗ {e}", file=sys.stderr)
            return 1
        return 0
    try:
        check_track(args.track, args.allow_production)
        if not args.aab.is_file() or args.aab.stat().st_size == 0:
            raise UploadError(f"AAB가 없거나 비어 있어요: {args.aab}")
        if args.mapping is not None and (not args.mapping.is_file() or args.mapping.stat().st_size == 0):
            raise UploadError(f"mapping 파일이 없거나 비어 있어요: {args.mapping}")
        notes = read_release_notes(args.notes_dir, args.version_code)
        info = load_service_account_info()
        print(
            f"{PACKAGE_NAME} vc{args.version_code} → {args.track} ({args.status})"
            f" as {info['client_email']}, notes {', '.join(n['language'] for n in notes)}"
            f"{', mapping' if args.mapping else ''}"
        )
        if args.dry_run:
            print("✓ dry-run: 입력 검증만 했어요(Play에는 접속하지 않음)")
            return 0
        result = upload(
            authorized_session(info),
            aab=args.aab,
            version_code=args.version_code,
            notes=notes,
            track=args.track,
            status=args.status,
            mapping=args.mapping,
            changes_not_sent_for_review=args.changes_not_sent_for_review,
        )
    except UploadError as e:
        print(f"✗ {e}", file=sys.stderr)
        return 1
    done = "초안으로 올렸어요. Play Console에서 검토 후 출시하세요" if result["status"] == "draft" else "출시했어요"
    print(f"✓ vc{result['versionCode']}를 {result['track']} 트랙에 {done}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
