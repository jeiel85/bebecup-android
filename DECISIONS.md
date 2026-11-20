# Decisions

되돌리기 어려운 결정(보안·데이터 형식·공개 API·인프라·주요 의존성)만 기록한다.

## 2026-11-21 Play 비공개 테스트(Alpha) 업로드 자동화

### 배경

태그 `vX.Y.Z`의 릴리스 워크플로가 Play 업로드 키로 서명한 AAB(산출물 `bebecup-release-aab`)를 만들면,
`scripts/play-bundle.sh`로 `dist/`에 받아 사용자가 Play Console에 끌어다 놓았다. Chrome 확장의 파일
업로드는 10MB까지라 약 22MB인 AAB는 에이전트가 올릴 수 없었다. 업로드 키 비밀번호는 GitHub Secrets에만
있어(issue #4) 서명된 AAB는 CI에서만 나온다.

요구: 서비스 계정 키만 넣으면 태그 푸시(또는 명령 하나)로 Alpha 트랙까지 올라갈 것, 기본 트랙 alpha,
production은 명시적 옵션 없이는 불가, 출시 노트는 `fastlane/metadata/android/{ko-KR,en-US}/changelogs/<versionCode>.txt`,
키 JSON은 저장소 밖(GitHub Secrets 또는 git 제외 `.keystore/`).

### 선택지

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| gradle-play-publisher (Gradle 플러그인) | Gradle 안에서 끝남, 기능 많음 | 앱 빌드에 플러그인 의존성 추가, 출시 노트를 `src/main/play/release-notes/<locale>/default.txt` 배치로 읽어 fastlane 파일을 복사·이중 관리해야 함, 빌드와 업로드가 한 단계로 묶여 업로드만 다시 하기 어려움 |
| fastlane supply | fastlane 메타데이터 배치를 그대로 읽음, 널리 쓰임 | CI와 로컬(Windows)에 Ruby + fastlane gem 트리가 필요, 기본값이 스토어 등록정보·이미지까지 덮어써서 skip 플래그를 빠짐없이 붙여야 함, `fastlane/metadata/android/play_store` 같은 비 locale 폴더 처리 등 동작을 우리가 통제하기 어려움 |
| 서드파티 GitHub Action (예: r0adkll/upload-google-play) | 워크플로 몇 줄 | 서비스 계정 키를 서드파티 코드에 넘김(공급망 위험), 출시 노트를 `whatsnew-<locale>` 배치로 요구, 로컬 재시도 경로 없음 |
| 직접 API 호출 스크립트 + 릴리스 워크플로 작업 | 의존성은 공식 `google-auth` + `requests` 둘, fastlane 배치를 그대로 읽음, alpha·draft 기본값과 production 차단을 코드로 강제, CI와 로컬이 같은 스크립트 | 스크립트(약 200줄)를 우리가 유지보수, API 변경에 직접 대응 |

### 결정

**직접 API 호출 스크립트(`scripts/play_upload.py`) + 릴리스 워크플로의 별도 `play-alpha` 작업**을 쓴다.

- 흐름: `edits.insert` → `edits.bundles.upload`(돌아온 versionCode가 기대값과 같은지 확인) →
  `edits.deobfuscationfiles.upload`(R8 mapping) → `edits.tracks.update`(새 release + ko-KR/en-US 출시 노트) →
  `edits.commit`. 중간 실패 시 edit를 지워 Play Console에 반쯤 된 변경을 남기지 않는다.
- `play-alpha`는 `release` 작업 뒤에 따로 돈다. Play 단계만 실패하면 GitHub Release는 그대로 두고 그 작업만
  다시 실행할 수 있다. 트랙은 워크플로에서 `alpha`로 고정한다.
- 시크릿 `PLAY_SERVICE_ACCOUNT_JSON`이 없으면 경고 주석을 남기고 건너뛴다(키를 넣기 전에도 기존 수동 흐름 유지).
- 기본 release 상태는 `draft`다. Play Console에서 확인한 뒤 '출시'를 누르면 테스터에게 나간다. 바로 출시하려면
  저장소 변수 `PLAY_RELEASE_STATUS=completed`(CI) 또는 `--status completed`(로컬).
- production 트랙은 `--allow-production` 없이는 스크립트가 거부한다. 워크플로에는 그 경로가 없다.
- 로컬 재시도용 `scripts/play-upload.sh X.Y.Z`는 `dist/`의 AAB(없으면 `play-bundle.sh`로 받음), 태그 시점 출시 노트,
  GitHub Release의 mapping, `.keystore/play-service-account.json`을 써서 같은 스크립트를 부른다.
- 의존성 버전은 `scripts/requirements-play.txt`에 고정한다.

### 되돌리기·재검토 조건

- 업로드가 대용량 때문에 실패하면(현재 단순 `uploadType=media`, 22MB) resumable 업로드로 바꾼다.
- 스토어 등록정보·스크린샷까지 자동화하게 되면 fastlane supply를 다시 검토한다.
- 서비스 계정 권한은 이 앱 하나, 출시 관련 권한으로만 준다(docs/RELEASE.md). 키가 새면 Google Cloud에서 키를
  폐기하고 새 키를 시크릿에 넣는다.
