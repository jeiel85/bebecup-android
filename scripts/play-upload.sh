#!/usr/bin/env bash
# dist/에 받아 둔 Play용 AAB를 Google Play API로 테스트 트랙에 올립니다(기본 alpha, draft).
#
#   scripts/play-upload.sh 0.7.0                      # 비공개 테스트 - Alpha에 초안
#   scripts/play-upload.sh 0.7.0 --status completed   # 바로 테스터에게 출시
#
# 태그 푸시 때 릴리스 워크플로의 play-alpha 작업이 같은 일을 하므로, 이 스크립트는 그 작업을
# 건너뛰었거나(키를 나중에 넣음) 다시 올려야 할 때 씁니다.
# - AAB가 dist/에 없으면 scripts/play-bundle.sh <버전>으로 먼저 받습니다(서명 인증서 확인 포함).
# - 출시 노트는 태그 시점의 fastlane/metadata/android/{ko-KR,en-US}/changelogs/<versionCode>.txt,
#   mapping은 play-bundle.sh가 AAB 옆에 받아 둔 같은 이름의 -mapping.txt를 씁니다(GitHub Release의
#   mapping은 GitHub APK 빌드의 것이라 Play AAB와 맞지 않습니다).
# - 서비스 계정 키: PLAY_SERVICE_ACCOUNT_JSON_FILE 또는 .keystore/play-service-account.json(git 제외)
# - 나머지 인자는 scripts/play_upload.py로 넘깁니다(--track, --status, --dry-run 등).
set -euo pipefail
cd "$(dirname "$0")/.."
VER="${1:?usage: scripts/play-upload.sh <version> [play_upload.py options]}"
shift
TAG="v$VER"

CODE="$(git show "$TAG:app/build.gradle.kts" | sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' | head -1)"
[ -n "$CODE" ] || { echo "✗ $TAG의 versionCode를 읽지 못했어요"; exit 1; }

shopt -s nullglob
AABS=(dist/bebecup-play-"$VER"-vc"$CODE"-*.aab)
if [ ${#AABS[@]} -eq 0 ]; then
  scripts/play-bundle.sh "$VER"
  AABS=(dist/bebecup-play-"$VER"-vc"$CODE"-*.aab)
fi
[ ${#AABS[@]} -eq 1 ] || { echo "✗ dist/에 $VER(vc$CODE) AAB가 하나가 아니에요: ${AABS[*]:-없음}"; exit 1; }

PY="${PYTHON:-}"
if [ -z "$PY" ]; then
  for c in python3 python; do
    if "$c" -c 'import sys; sys.exit(sys.version_info < (3, 9))' >/dev/null 2>&1; then PY="$c"; break; fi
  done
fi
[ -n "$PY" ] || { echo "✗ Python 3.9 이상을 찾지 못했어요(PYTHON 환경 변수로 지정 가능)"; exit 1; }

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
for loc in ko-KR en-US; do
  mkdir -p "$TMP/notes/$loc/changelogs"
  git show "$TAG:fastlane/metadata/android/$loc/changelogs/$CODE.txt" > "$TMP/notes/$loc/changelogs/$CODE.txt" \
    || { echo "✗ $TAG에 $loc 출시 노트($CODE.txt)가 없어요"; exit 1; }
done

MAPPING_ARGS=()
MAPPING="${AABS[0]%.aab}-mapping.txt"
if [ -s "$MAPPING" ]; then
  MAPPING_ARGS=(--mapping "$MAPPING")
else
  echo "⚠ $MAPPING 이(가) 없어 난독화 해제 파일 없이 올려요"
fi

"$PY" scripts/play_upload.py --aab "${AABS[0]}" --version-code "$CODE" --notes-dir "$TMP/notes" \
  "${MAPPING_ARGS[@]}" "$@"
