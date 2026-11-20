#!/usr/bin/env bash
# Google Play용 AAB를 dist/에 준비합니다.
#
#   scripts/play-bundle.sh 0.7.0
#
# 업로드 키 비밀번호는 지금 GitHub Secrets에만 있으므로(issue #4) 로컬에서 서명하지 않고,
# 태그 v<버전>의 릴리스 워크플로가 Play 업로드 키로 서명해 남긴 산출물 `bebecup-release-aab`를 받습니다.
# 결과: dist/bebecup-play-<버전>-vc<versionCode>-<커밋>.aab + 같은 이름의 -release-notes.txt
# 같은 이름이 있으면 멈추고, 받을 때마다 dist/play-builds.log에 시각·버전·커밋·SHA-256을 남깁니다.
set -euo pipefail
cd "$(dirname "$0")/.."
VER="${1:?usage: scripts/play-bundle.sh <version>}"
TAG="v$VER"
# Play Console에 등록된 업로드 키 인증서(docs/RELEASE.md).
UPLOAD_SHA256="05:01:95:78:3E:88:0F:19:04:DF:91:4D:EA:5A:BE:20:1E:79:BE:84:47:25:C7:23:60:BD:45:A3:B0:C3:51:B7"

COMMIT="$(git rev-parse --short=7 "$TAG^{commit}")"
CODE="$(git show "$TAG:app/build.gradle.kts" | sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' | head -1)"
[ -n "$CODE" ] || { echo "✗ $TAG의 versionCode를 읽지 못했어요"; exit 1; }
for loc in ko-KR en-US; do
  git cat-file -e "$TAG:fastlane/metadata/android/$loc/changelogs/$CODE.txt" 2>/dev/null \
    || { echo "✗ $TAG에 fastlane/metadata/android/$loc/changelogs/$CODE.txt 가 없어요"; exit 1; }
done

mkdir -p dist
BASE="dist/bebecup-play-$VER-vc$CODE-$COMMIT"
[ ! -e "$BASE.aab" ] || { echo "✗ $BASE.aab 이(가) 이미 있어요. 다시 받으려면 먼저 지우세요"; exit 1; }

RUN="$(gh run list --workflow android-build.yml --branch "$TAG" --event push --limit 1 --json databaseId,conclusion \
  --jq '.[0] | select(.conclusion != null) | .databaseId')"
[ -n "$RUN" ] || { echo "✗ $TAG의 릴리스 워크플로 실행을 찾지 못했어요(아직 도는 중일 수 있어요)"; exit 1; }
RELEASE_JOB="$(gh run view "$RUN" --json jobs --jq '.jobs[] | select(.name=="release") | .conclusion')"
[ "$RELEASE_JOB" = "success" ] || { echo "✗ 실행 $RUN의 release 작업이 성공하지 않았어요($RELEASE_JOB)"; exit 1; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
gh run download "$RUN" -n bebecup-release-aab -D "$TMP"
SIGNER="$(keytool -printcert -jarfile "$TMP/app-release.aab" | sed -n 's/^\s*SHA256: //p' | head -1)"
[ "$SIGNER" = "$UPLOAD_SHA256" ] || { echo "✗ 서명 인증서가 Play 업로드 키와 달라요: $SIGNER"; exit 1; }
cp "$TMP/app-release.aab" "$BASE.aab"

{
  printf '<ko-KR>\n%s\n</ko-KR>\n' "$(git show "$TAG:fastlane/metadata/android/ko-KR/changelogs/$CODE.txt" | tr -d '\r')"
  printf '<en-US>\n%s\n</en-US>\n' "$(git show "$TAG:fastlane/metadata/android/en-US/changelogs/$CODE.txt" | tr -d '\r')"
} > "$BASE-release-notes.txt"

SHA="$(sha256sum "$BASE.aab" | cut -d' ' -f1)"
printf '%s\t%s\t%s\t%s\t%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$VER" "$(git rev-parse "$TAG^{commit}")" "$SHA" "$BASE.aab" >> dist/play-builds.log
echo "✓ $BASE.aab"
echo "  run    $RUN"
echo "  sha256 $SHA"
echo "  signer $SIGNER"
echo "✓ $BASE-release-notes.txt"
