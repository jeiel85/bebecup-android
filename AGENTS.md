# AGENTS.md

이 문서는 베베컵 프로젝트에서 AI 코딩 에이전트가 매번 확인해야 하는 작업 규칙입니다.

## Project

베베컵은 부모가 직접 고른 실제 아기 사진으로 이상형 월드컵을 만들고, 인화 후보를 정리하는 Android 앱입니다.

Application ID:

```text
com.bebecup.app
```

## Non-Negotiable Rules

- Android `applicationId`는 반드시 `com.bebecup.app`을 사용한다.
- 목업 사진, 프리셋 사진, 샘플 사진을 첫 실행 데이터로 주입하지 않는다.
- 사진 데이터는 Android Photo Picker 등 사용자가 직접 선택한 실제 로컬 이미지 URI만 저장한다.
- 사진 파일을 앱이 임의로 외부 서비스에 업로드했다고 표현하지 않는다.
- 외부 인화 사이트 연동은 사용자가 명시적으로 브라우저를 여는 handoff까지만 제공한다.
- 분석, 광고, 추적 SDK를 추가하지 않는다.
- 릴리즈 서명 키와 비밀번호는 저장소에 커밋하지 않는다.

## Preferred Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Kotlin Coroutines + Flow
- Gradle Kotlin DSL

## Quality Checks

가능하면 다음 명령을 사용한다.

```bash
./gradlew test
./gradlew assembleDebug
./gradlew :app:lintRelease
./gradlew :app:bundleRelease
```

릴리즈 검증 시에는 AAB 산출물 확인을 반드시 포함한다.

- `app/build/outputs/bundle/release/app-release.aab` 파일 존재 여부 확인
- 파일 크기가 0보다 큰지 확인

## Release Artifact Export

사용자가 "새 버전 만들기"를 요청하면 버전 bump, CHANGELOG, fastlane changelog(`fastlane/metadata/android/{ko-KR,en-US}/changelogs/<versionCode>.txt`, locale별 500자 이하), 검증, PR 머지, `vX.Y.Z` 태그 푸시까지 진행한다. 바탕화면으로 파일을 내보내지 않는다(2026-11-20 사용자 지시).

- **GitHub**: 태그 푸시 시 릴리스 워크플로가 서명된 APK와 mapping을 GitHub Release에 올린다.
- **Google Play**: 같은 워크플로의 `play-alpha` 작업이 Play 업로드 키로 서명한 AAB를 Google Play Developer Publishing API로 **비공개 테스트 - Alpha** 트랙에 올린다(출시 노트는 위 fastlane changelog, mapping 포함). 기본은 **draft**라 태그 푸시 후 Play Console에서 초안을 확인하고 '검토 → 출시'를 누른다(저장소 변수 `PLAY_RELEASE_STATUS=completed`면 바로 출시). 결과는 `play-alpha` 작업 로그로 확인한다. 세부와 서비스 계정 준비는 `docs/RELEASE.md`, 설계는 `DECISIONS.md`.
  - 시크릿 `PLAY_SERVICE_ACCOUNT_JSON`이 없으면 작업이 경고만 남기고 건너뛴다. 이때나 업로드가 실패해 다시 올릴 때는 `scripts/play-upload.sh X.Y.Z`(키: `.keystore/play-service-account.json`, `dist/`에 AAB가 없으면 `scripts/play-bundle.sh`로 먼저 받음)를 쓴다.
  - 프로덕션은 사용자가 따로 지시할 때만 `--track production --allow-production`으로 올린다. 워크플로는 alpha만 쓴다.
- **수동 업로드(자동 업로드를 못 쓸 때)**: `scripts/play-bundle.sh X.Y.Z`로 `dist/`에 받고(업로드 키 인증서 지문 확인, 같은 이름이 있으면 멈춤), 사용자의 Chrome에서 `pedaiah85@gmail.com` → 개발자 계정 **EL Studio** → 베베컵 → 테스트 및 출시 → **비공개 테스트 - Alpha** → 새 버전 만들기로 올린다. Chrome 확장의 파일 업로드는 10MB까지라 AAB(약 22MB)는 사용자가 업로드 칸에 끌어다 놓고, 출시 노트 입력·검토·출시는 이어서 진행한다.

## Commit Style

Conventional Commits를 사용한다.

예:

```text
feat: add real photo picker flow
chore: prepare play release export
docs: document release workflow
```

## Documentation And History

- `CHANGELOG.md`: 사용자에게 공개 가능한 변경 요약
- `HISTORY.md`: 작업 과정, 검증, 후속 작업 기록
- `docs/RELEASE.md`: 릴리즈 서명과 Play 제출 산출물 절차
