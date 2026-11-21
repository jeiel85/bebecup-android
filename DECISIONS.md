# Decisions

되돌리기 어려운 결정(보안·데이터 형식·공개 API·인프라·주요 의존성)만 기록한다.

## 2026-11-22 채널별 빌드: `play`·`github` flavor와 채널별 서명 키

### 배경

v0.9.0까지 GitHub 릴리스 APK와 Play 업로드 AAB가 한 빌드, 한 키(`bebecup-release.p12`)였다. 전역 규칙은 채널마다
별도 빌드·별도 키를 쓰고, 스토어 업데이트에 맡기는 채널과 그렇지 않은 채널의 코드를 나누라고 한다(#3).
또 Play 서비스에서 받는 얼굴 인식 모델은 Play 서비스가 없는 기기의 GitHub APK 사용자에게 정리 기능을 막았고(#8),
Play 업데이트 배너가 GitHub APK에서도 뜰 수 있었다(#11).

### 선택지

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| 한 빌드 유지, 설치 출처로 런타임 분기 | 빌드 하나 | 전역 규칙 위반, 두 채널 코드·의존성이 한 APK에 섞임 |
| **flavor 분리 + GitHub 키 새로 만들기, Play 업로드 키 유지** | Play 쪽 변경 없음(업로드 키 재설정 불필요) | 기존 GitHub APK 설치본은 한 번 지우고 다시 설치 |
| flavor 분리 + Play 업로드 키 재설정, 기존 키는 GitHub 전용 | GitHub 사용자 재설치 없음 | Play 업로드 키 재설정 승인 대기, 기존 키 비밀번호가 로컬에 없음(#4) |

### 결정

**flavor `play`/`github`(dimension `channel`) + GitHub 전용 새 키**.

- `play`: Play 업로드 키(`BEBECUP_RELEASE_*`, 변화 없음). 얼굴 인식은 Play 서비스 모델(`play-services-mlkit-face-detection`,
  `FaceModelInstaller`가 `ModuleInstallClient`로 받음), 홈 업데이트 배너(`app-update`)가 있다.
- `github`: 새 키 `bebecup-github`(`BEBECUP_GITHUB_*`, SHA-256 `87:34:B5:…:79:66`, 2026-11-21 생성, 드라이브 백업). 얼굴 인식 모델을 APK에
  넣은 번들형(`com.google.mlkit:face-detection`, 같은 `com.google.mlkit.vision.face` API)이라 Play 서비스 없이 동작한다.
  업데이트 배너는 없다. 앱이 직접 APK를 받아 설치하는 업데이터는 이번에 만들지 않았다(수요 확인 전).
- 채널별 코드는 `src/play`·`src/github`의 같은 이름 클래스(`ai/FaceModelInstaller`, `update/UpdateChannel`)로 나누고,
  공통 코드는 `UpdateBannerState`만 안다.
- 릴리스 워크플로는 두 키를 복원해 `assembleGithubRelease`·`bundlePlayRelease`를 만들고, 각 산출물의 인증서 SHA-256을 문서의 값과
  비교해 다르면 멈춘다. GitHub Release에는 GitHub APK와 그 mapping, Play에는 Play AAB와 그 mapping이 간다.
- 기존 GitHub APK(v0.9.0 이하, Play 업로드 키 서명) 사용자는 다음 버전을 덮어 설치할 수 없어 한 번 지우고 설치해야 한다. CHANGELOG에 적는다.

### 되돌리기·재검토 조건

- GitHub 키(`.keystore/bebecup-github-release.p12`와 비밀번호 파일)는 드라이브에 백업돼 있다. 잃으면 GitHub 사용자는 또 재설치해야 한다.
- GitHub 빌드 사용자가 업데이트를 놓친다는 신고가 오면 GitHub 릴리스 기반 업데이트 확인(+ `REQUEST_INSTALL_PACKAGES`)을 `github` 소스셋에만 넣는다.
- 분석 점수는 채널마다 모델이 달라 조금 다를 수 있다. 한 설치본 안에서는 모델이 하나라 섞이지 않는다. v0.7.1 이전 번들 모델 점수가 섞이지 않게
  `CURRENT_ANALYSIS_VERSION`을 3으로 올렸다(이전 결정 "얼굴 인식 모델을 Google Play 서비스에서 받기"의 재검토 조건을 뒤집음). 다시 분석되는 것은
  정리할 때 스캔 기간 안의 사진뿐이라 비용이 작다.

## 2026-11-22 ML Kit 사용 지표 전송은 끄지 않고 방침에 적는다

### 배경

에뮬레이터 앱 데이터에서 `com.google.android.datatransport.events` DB를 발견했다(#15). 개인정보처리방침은 "사용 기록·기기 식별자를
보내지 않고 분석 SDK를 쓰지 않는다"고 적고 있었다.

### 확인한 사실

- Google ML Kit 약관과 Android 데이터 공개 안내(developers.google.com/ml-kit/terms, /ml-kit/android-data-disclosure)는 ML Kit이
  API 성능·사용 지표(기기 제조사·모델, OS 버전, 앱 패키지 이름·버전, 지연 시간, 이벤트 종류, 오류 코드, 설치별 식별자)를 Google에 보낸다고 적는다.
  사진·얼굴 데이터와 인식 결과는 보내지 않는다.
- 이 전송을 끄는 공식 설정은 찾지 못했다. 매니페스트에서 datatransport 백엔드를 빼는 비공식 방법이 알려져 있으나, 라이브러리 내부에 기대
  버전마다 깨질 수 있고 Play 서비스 모델(`play` 빌드)에서는 효과를 확인할 수 없다.

### 결정

비공식 차단은 하지 않는다. 개인정보처리방침(웹·초안), README, 랜딩 페이지, 설정 화면 문구를 사실대로 고친다: 사진·인식 결과는 보내지 않음,
ML Kit이 위 지표를 Google에 보낼 수 있음, 앱에서 끌 수 없음, 광고·추적 SDK 없음. Play Console 데이터 보안 양식 답변은 사용자가 고친다.

### 되돌리기·재검토 조건

- Google이 공식 끄기 설정을 내면 적용하고 방침을 다시 고친다.
- 얼굴 인식을 ML Kit이 아닌 다른 온디바이스 모델로 바꾸면 이 항목을 다시 본다.

## 2026-11-21 Play 업데이트 배너는 채널 분리 전에도 한 빌드에 넣는다

### 배경

Play 테스터가 자동 업데이트를 꺼 두면 새 버전을 모른다. 전역 규칙은 채널별 빌드(flavor)를 쓰고 "앱이 직접 받아 설치하는"
업데이터는 GitHub 빌드에만 두라고 하지만, 베베컵은 아직 GitHub·Play가 같은 빌드다(채널 분리는 #3).

### 결정

Play In-App Updates API로 **확인만** 하고 Play 스토어 페이지를 여는 배너를 지금 빌드(main 소스셋)에 넣는다.

- 앱은 아무것도 내려받거나 설치하지 않으므로 Play 정책(자체 업데이트 금지)과 전역 규칙의 "자동 업데이트" 범위에 들지 않는다.
  `REQUEST_INSTALL_PACKAGES` 같은 권한도 없다.
- GitHub APK처럼 Play에서 설치하지 않은 사본은 보통 Play가 답하지 않아(`ERROR_APP_NOT_OWNED` 등) 배너가 뜨지 않는다.
  설치 출처로 기능을 끄는 분기를 따로 두지 않았다. 다만 Play 테스트에 참여한 계정에서 GitHub APK를 쓰면 배너가 뜰 수 있고,
  서명이 달라 Play에서 업데이트가 안 된다(실기기 미확인).
- 즉시·유연 업데이트(Play가 앱 안에서 내려받는 흐름)는 쓰지 않는다. 배너 → Play 페이지면 충분하고 상태 관리가 없다.

### 되돌리기·재검토 조건

- #3에서 flavor를 나누면 이 코드와 의존성을 `play` 소스셋으로 옮기고, GitHub 빌드에는 GitHub 릴리스 기반 업데이트 확인을 따로 둔다.

## 2026-11-21 얼굴 인식 모델을 Google Play 서비스에서 받기

### 배경

v0.7.0 Play AAB 약 21.5MB 중 번들형 ML Kit 얼굴 인식이 약 16MB(네이티브 라이브러리 4개 ABI 약 13MB, 모델 약 3MB)였다.
사용자 요청: 미리 포함하지 말고 첫 실행(권한 설정 시점) 또는 얼굴 인식을 처음 쓸 때 받을 것.

### 선택지

| 선택지 | 장점 | 단점 |
| --- | --- | --- |
| 번들 유지 + x86 ABI 제외 | Play 서비스 의존 없음 | 약 15MB로 여전히 큼, 에뮬레이터(x86_64) 실행 불가 |
| 비번들 + 매니페스트 `com.google.mlkit.vision.DEPENDENCIES` | 설치 시 Play가 자동으로 받음 | 받는 시점을 앱이 정하지 못함, Play 스토어 설치가 아니면 첫 사용 때 실패 |
| **비번들 + `ModuleInstallClient`로 명시적 요청** | 받는 시점·진행률·실패를 앱이 다룸 | Play 서비스가 없는 기기에서는 얼굴 인식 불가 |

### 결정

**비번들 + `ModuleInstallClient`**(`ai/FaceModelInstaller.kt`).

- 온보딩을 마칠 때(알림 허용·건너뛰기 모두) 백그라운드로 설치를 요청한다.
- 정리를 시작할 때 모듈이 없으면 "얼굴 인식 기능을 준비하는 중" 진행률을 보여 주며 기다린다(최대 3분).
- 받지 못하면 분석을 시작하지 않는다. 얼굴 인식이 실패하면 모든 사진의 분석이 버려져 결과가 조용히 비기 때문이다. 이유와 다시 시도를 안내한다.
- 사진은 여전히 기기 밖으로 나가지 않는다. 내려받는 것은 Play 서비스의 모델뿐이다.

### 되돌리기·재검토 조건

- Play 서비스가 없는 기기(GitHub APK 사용자 일부)에서 사진 정리를 못 쓴다는 신고가 오면, 채널 분리(issue #3) 때 GitHub 빌드만 번들형으로 되돌리는 방안을 검토한다.
- 분석 결과 형식은 같아 `CURRENT_ANALYSIS_VERSION`은 그대로 둔다. Play 서비스 모델 버전에 따라 이미 분석한 사진과 점수가 조금 다를 수 있으나, 재분석 비용(전 사진 다시 분석)에 비해 이득이 작다고 봤다.

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
