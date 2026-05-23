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

사용자가 "새 버전 만들기"를 요청하면 버전 bump, changelog, fastlane changelog, 검증, commit/tag 작업과 함께 바탕화면에 Play Console 제출용 파일을 내보낸다.

```bash
./gradlew :app:exportReleaseToDesktop
```

이 task는 `bundleRelease`에 의존하므로 AAB도 함께 빌드된다. 산출물:

- AAB 파일: `bebecup-vX.Y.Z.aab`
- 릴리즈 노트 TXT 파일: `bebecup-vX.Y.Z-release-notes.txt`

릴리즈 노트는 `fastlane/metadata/android/{ko-KR,en-US}/changelogs/<versionCode>.txt`를 읽어 아래 형식으로 생성한다.

```text
<ko-KR>
...
</ko-KR>
<en-US>
...
</en-US>
```

따라서 새 버전 생성 전에 두 fastlane changelog 파일이 새 `versionCode` 파일명으로 작성되어 있어야 한다. 누락되거나 locale별 500자를 넘으면 task가 실패한다.

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
