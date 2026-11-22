# Release Signing and Play Store Submission

베베컵은 배포 채널마다 따로 빌드한다(product flavor `play` / `github`, 설계: `DECISIONS.md` "채널별 빌드"). applicationId는 둘 다 `com.bebecup.app`이지만 서명 키가 달라 **한 채널 설치본 위에 다른 채널을 덮어 설치할 수 없다**(지우고 다시 설치). keystore와 비밀번호는 저장소에 커밋하지 않는다.

| 채널 | flavor | 산출물 | 서명 키 | 얼굴 인식 모델 | 업데이트 안내 |
| --- | --- | --- | --- | --- | --- |
| Google Play | `play` | AAB | Play 업로드 키 (`BEBECUP_RELEASE_*`) | Google Play 서비스에서 받음 | 홈 배너(Play In-App Updates) |
| GitHub Releases | `github` | APK | GitHub APK 키 (`BEBECUP_GITHUB_*`, v0.9.0 다음 버전부터) | APK에 포함 | 없음(GitHub Releases 페이지) |

GitHub APK는 v0.9.0까지 Play 업로드 키로 서명됐다. 그다음 버전부터 GitHub 키로 바뀌므로, 이전 GitHub APK 설치본은 한 번 지우고 새 APK를 설치해야 한다(앱 데이터는 지워진다).

## Play 업로드 키 (Play App Signing)

Play App Signing을 사용하므로 사용자에게 배포되는 서명 키는 Google이 관리하며, 이 키는 Play Console 업로드 인증에만 쓰인다.

| 항목 | 값 |
| --- | --- |
| 위치 | `.keystore/bebecup-release.p12` (gitignored) |
| 형식 | PKCS12 |
| 키 알고리즘 | RSA 4096-bit |
| 서명 알고리즘 | SHA384withRSA |
| Alias | `bebecup-release` |
| Distinguished Name | `CN=Bebecup, OU=Android, O=Bebecup, L=Seoul, ST=Seoul, C=KR` |
| 생성일 | 2026-05-24 |
| 만료일 | 2053-10-09 (10,000일) |
| SHA-256 | `05:01:95:78:3E:88:0F:19:04:DF:91:4D:EA:5A:BE:20:1E:79:BE:84:47:25:C7:23:60:BD:45:A3:B0:C3:51:B7` |

이 키의 비밀번호는 지금 GitHub Secrets에만 있다(issue #4). 로컬·드라이브 백업에 비밀번호 파일이 없어 로컬에서는 Play 빌드를 서명할 수 없고, 서명된 AAB는 릴리스 워크플로에서만 나온다. Secrets가 사라지면 Play Console의 업로드 키 재설정("Request upload key reset")이 필요하다.

## GitHub APK 키

| 항목 | 값 |
| --- | --- |
| 위치 | `.keystore/bebecup-github-release.p12` (gitignored) |
| 비밀번호·별칭 | `.keystore/github-release-signing.properties` (gitignored) |
| 백업 | `G:\내 드라이브\모바일 앱 개발\bebecup-android\릴리즈 키스토어\` (위 두 파일) |
| 형식 | PKCS12 |
| 키 알고리즘 | RSA 4096-bit |
| 서명 알고리즘 | SHA384withRSA |
| Alias | `bebecup-github` |
| Distinguished Name | `CN=BebeCup GitHub Release` |
| 생성일 | 2026-11-21 |
| 만료일 | 2054-04-08 (10,000일) |
| SHA-256 | `87:34:B5:82:F9:BE:30:B6:F1:1B:33:AC:60:8F:82:B4:E5:99:AD:C8:70:DA:7A:0F:BB:C7:C4:56:F1:80:79:66` |

이 키를 잃으면 GitHub APK 사용자는 다음 버전을 덮어 설치할 수 없다. 드라이브 백업을 지우지 않는다.

같은 지문이 세 곳에 있다: 이 표, 릴리스 워크플로의 `GITHUB_APK_SHA256`, 앱의 `UPDATE_SIGNER_SHA256`(`app/build.gradle.kts`, GitHub 빌드 업데이터가 설치본 서명과 비교). 키를 바꾸면 셋을 함께 고친다. 에뮬레이터에서 디버그 키로 업데이트를 시험할 때는 `-Pbebecup.updateSignerSha256=<디버그 키 SHA-256>`으로 빌드한다.

## Local Signed Release Build

GitHub APK는 `.keystore/github-release-signing.properties`가 있으면 로컬에서 서명된다.

```properties
BEBECUP_GITHUB_STORE_FILE=.keystore/bebecup-github-release.p12
BEBECUP_GITHUB_STORE_PASSWORD=...
BEBECUP_GITHUB_KEY_ALIAS=bebecup-github
BEBECUP_GITHUB_KEY_PASSWORD=...
```

Play 빌드는 저장소 루트의 `release-signing.properties`(`BEBECUP_RELEASE_STORE_FILE` 등 같은 형식)로 서명되지만, 위 이유로 지금은 로컬에 이 파일이 없다. 두 값 모두 환경 변수로도 줄 수 있다.

서명 값이 없어도 검증용 unsigned release 빌드는 만들 수 있다.

```bash
./gradlew :app:bundlePlayRelease :app:assembleGithubRelease
```

서명이 반드시 있어야 하는 release-candidate 검증에서는 property를 켠다(두 채널 키가 모두 있어야 통과).

```bash
./gradlew :app:bundlePlayRelease :app:assembleGithubRelease -Pbebecup.requireReleaseSigning=true
```

PowerShell:

```powershell
./gradlew.bat :app:assembleGithubRelease
```

산출물:

```text
app/build/outputs/bundle/playRelease/app-play-release.aab      # Play Console
app/build/outputs/apk/github/release/app-github-release.apk    # GitHub Releases (서명 없으면 -unsigned)
```

## R8

Release 빌드는 R8 minify와 resource shrinking을 사용한다(AGP 9는 R8 full mode). 필요한 keep rule은 `app/proguard-rules.pro`에 최소 범위로 추가하고, 왜 필요한지 주석을 남긴다.

R8 full mode가 ML Kit 컴포넌트 등록 클래스(`FaceRegistrar` 등, 매니페스트 메타데이터로 리플렉션 생성)의 기본 생성자를 지워 v0.7.1~v0.10.0 릴리스 빌드(두 채널 모두)에서 "베베컵에게 맡기기"가 처음부터 실패했다. 디버그 빌드는 축소하지 않아 테스트에 걸리지 않았다. CI는 두 채널의 `mapping/<flavor>Release/seeds.txt`에 세 생성자가 남았는지 확인한다. 리플렉션을 쓰는 라이브러리를 바꾸면 릴리스 빌드를 디버그 키로 다시 서명해 에뮬레이터에서 직접 확인한다.

R8 mapping 파일은 채널마다 다르다(코드가 달라 난독화 결과도 다름):

```text
app/build/outputs/mapping/playRelease/mapping.txt     # Play AAB용
app/build/outputs/mapping/githubRelease/mapping.txt   # GitHub APK용
```

출시한 각 버전의 mapping 파일은 난독화 해제용으로 보관한다.

Play Console이 "네이티브 코드를 포함하며 디버그 기호가 업로드되지 않았다"고 경고하지만 출시를 막지는 않는다. 앱의 네이티브 코드는 Compose가 쓰는 `libandroidx.graphics.path.so`뿐이고, 이 라이브러리는 기호가 제거된 채 배포돼 `ndk.debugSymbolLevel`을 켜도 AAB에 기호 파일이 생기지 않는다(v0.10.0에서 확인). 직접 만든 네이티브 코드가 생기면 그때 기호를 올린다.

## Play Console Export

바탕화면으로 내보내지 않는다(2026-11-20). 업로드 키 비밀번호가 GitHub Secrets에만 있어(issue #4) 서명된 AAB는 릴리스 워크플로에서만 나온다.

### 자동 업로드 (Google Play Developer Publishing API)

태그 `vX.Y.Z`를 푸시하면 릴리스 워크플로의 `play-alpha` 작업이 `release` 작업 뒤에 돈다(설계: `DECISIONS.md`).

1. 산출물 `bebecup-release-aab`(Play AAB)와 `bebecup-release-mapping`(그 빌드의 R8 mapping)을 받는다.
2. `scripts/play_upload.py`가 edit를 만들고 AAB를 올린 뒤 versionCode가 `app/build.gradle.kts`와 같은지 확인하고, mapping을 난독화 해제 파일로 올린다.
3. **비공개 테스트 - Alpha**(`alpha`) 트랙에 release를 추가한다. 출시 노트는 아래 원천 파일의 ko-KR·en-US이다.
4. commit한다. 중간에 실패하면 edit를 지워 Play Console에 남는 변경이 없다.

- 기본 상태는 **draft**다. Play Console → 베베컵 → 테스트 및 출시 → 비공개 테스트 - Alpha에서 초안을 확인하고 '검토 → 출시'를 누르면 테스터에게 나간다. 바로 출시하려면 저장소 변수 `PLAY_RELEASE_STATUS`를 `completed`로 둔다.
- 시크릿 `PLAY_SERVICE_ACCOUNT_JSON`이 없으면 작업이 경고(`Play upload skipped`)만 남기고 건너뛴다. 키를 나중에 넣었으면 그 실행의 `play-alpha` 작업만 다시 실행(`gh run rerun <run-id> --job <job-id>`)하거나 아래 로컬 명령을 쓴다.
- 워크플로는 alpha 트랙만 쓴다. production은 `scripts/play_upload.py --track production --allow-production`을 사용자가 직접 지시할 때만 쓴다. 서비스 계정에도 프로덕션 출시 권한을 주지 않는다.
- Play가 "자동으로 검토에 보낼 수 없음"으로 commit을 거부하면 `--changes-not-sent-for-review`를 붙여 로컬에서 다시 올리고 Play Console에서 검토에 보낸다.

로컬에서 다시 올리기(같은 스크립트, 키는 `.keystore/play-service-account.json` 또는 `PLAY_SERVICE_ACCOUNT_JSON_FILE`):

```bash
python -m pip install -r scripts/requirements-play.txt   # 처음 한 번
scripts/play-upload.sh X.Y.Z                     # alpha, draft
scripts/play-upload.sh X.Y.Z --status completed  # alpha, 바로 출시
scripts/play-upload.sh X.Y.Z --dry-run           # 입력만 검증, Play 접속 없음
```

`dist/`에 그 버전 AAB가 없으면 먼저 `scripts/play-bundle.sh X.Y.Z`로 받는다. 출시 노트는 태그 시점의 원천 파일, mapping은 `play-bundle.sh`가 AAB 옆에 받아 둔 `…-mapping.txt`를 쓴다(GitHub Release의 mapping은 GitHub APK 빌드의 것이라 Play AAB와 맞지 않는다).

### 서비스 계정 준비 (사용자가 직접, 한 번만)

1. **Google Cloud Console**(키를 관리할 Google 계정, 보통 `pedaiah85@gmail.com`)에서 프로젝트를 하나 만들고(예: `bebecup-play`) **API 및 서비스 → 라이브러리 → Google Play Android Developer API**를 사용 설정한다.
2. **IAM 및 관리자 → 서비스 계정 → 서비스 계정 만들기**(예: `play-uploader`). 프로젝트 역할은 주지 않는다. 만든 계정 → **키 → 키 추가 → 새 키 만들기 → JSON**으로 키 파일을 받는다.
3. **Play Console**(`pedaiah85@gmail.com` → 개발자 계정 EL Studio) → **사용자 및 권한 → 새 사용자 초대** → 서비스 계정 이메일(`…@….iam.gserviceaccount.com`) 입력 → **앱 권한**에 베베컵만 추가 → **테스트 트랙에 앱 출시** 권한만 준다(프로덕션 출시·스토어 등록정보 권한은 주지 않음) → 초대. 서비스 계정은 수락 절차 없이 바로 추가된다. 권한이 API에 반영되기까지 시간이 걸릴 수 있다.
4. **GitHub 시크릿**: 받은 JSON을 그대로 넣는다.

   ```bash
   gh secret set PLAY_SERVICE_ACCOUNT_JSON < 받은-키.json
   ```

5. **로컬에서도 쓸 때만**: 키를 `.keystore/play-service-account.json`(git 제외)으로 옮긴다. 쓰지 않으면 받은 파일을 지운다. 키는 Google Cloud에서 언제든 새로 만들 수 있어 키스토어처럼 드라이브에 백업하지 않는다. 키가 샜다고 의심되면 Google Cloud에서 그 키를 삭제하고 새 키를 시크릿에 넣는다.
6. (선택) 테스터에게 바로 출시하려면 `gh variable set PLAY_RELEASE_STATUS --body completed`.

연결 확인: `scripts/play-upload.sh <이미 Play에 올린 버전>`을 실행해 "version code … has already been used"류의 AAB 업로드 거부가 나오면 인증과 권한은 정상이다(edit는 지워져 남는 변경 없음). 403(`The caller does not have permission`)이면 3번 권한이나 반영 대기 문제다.

### 수동 업로드 (자동 업로드를 쓰지 못할 때)

```bash
scripts/play-bundle.sh X.Y.Z
```

- 워크플로 산출물 `bebecup-release-aab`를 받아 서명 인증서가 위 업로드 키 SHA-256과 같은지 확인하고, 그 빌드의 mapping(`bebecup-release-mapping`)을 `…-mapping.txt`로 함께 받는다.
- `dist/bebecup-play-X.Y.Z-vc<versionCode>-<커밋>.aab`와 `…-release-notes.txt`(아래 원천 파일을 `<ko-KR>`/`<en-US>` 블록으로 묶은 것)를 만들고, `dist/play-builds.log`에 시각·버전·커밋·SHA-256을 남긴다. 같은 이름이 있으면 멈춘다.
- Play Console(`pedaiah85@gmail.com` → 개발자 계정 EL Studio) → 베베컵 → 테스트 및 출시 → 비공개 테스트 - Alpha → 새 버전 만들기 → AAB 업로드 → 출시 노트 → 검토 → 출시. Chrome 확장 업로드는 10MB까지다. v0.7.1부터 AAB는 약 5MB(얼굴 인식 모델을 Play 서비스에서 받음)라 확장으로 올릴 수 있고, 10MB를 넘으면 사용자가 끌어다 놓는다.

### 출시 노트 원천 파일

Play Console locale별 500자 제한(스크립트가 넘으면 거부한다):

```text
fastlane/metadata/android/ko-KR/changelogs/<versionCode>.txt
fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
```

## CI Gates and GitHub Releases

GitHub Actions를 통해 코드 품질 및 릴리즈 빌드가 자동 검증된다. `main` 브랜치에 푸시하거나 Pull Request를 생성할 때 다음이 수행된다:

- `./gradlew testPlayDebugUnitTest testGithubDebugUnitTest` (두 채널 단위·Robolectric 테스트)
- `./gradlew connectedPlayDebugAndroidTest` (에뮬레이터 계측 테스트)
- `./gradlew assemblePlayDebug assembleGithubDebug`
- `./gradlew :app:lintPlayRelease :app:lintGithubRelease` (Release 린트 검사)
- `./gradlew :app:bundlePlayRelease :app:assembleGithubRelease` (R8 Release 빌드 검사)

### GitHub Actions Secrets

GitHub 저장소의 **Settings > Secrets and variables > Actions**에 아래 보안 값을 설정한다:

```text
BEBECUP_RELEASE_KEYSTORE_BASE64  # Play 업로드 키 keystore (.p12) Base64
BEBECUP_RELEASE_STORE_PASSWORD   # 그 비밀번호
BEBECUP_RELEASE_KEY_ALIAS        # 그 별칭
BEBECUP_RELEASE_KEY_PASSWORD     # 그 키 비밀번호
BEBECUP_GITHUB_KEYSTORE_BASE64   # GitHub APK 키 keystore (.p12) Base64
BEBECUP_GITHUB_STORE_PASSWORD    # 그 비밀번호
BEBECUP_GITHUB_KEY_ALIAS         # 그 별칭
BEBECUP_GITHUB_KEY_PASSWORD      # 그 키 비밀번호
PLAY_SERVICE_ACCOUNT_JSON        # Play 업로드용 서비스 계정 키 JSON (없으면 play-alpha 작업이 건너뜀)
```

저장소 변수(Variables): `PLAY_RELEASE_STATUS` = `draft`(기본, 미설정 시) 또는 `completed`.

`v*`와 일치하는 버전 태그가 푸시되면 GitHub Actions가 빌드를 실행하고 최종적으로 다음 산출물을 포함하여 GitHub Release를 자동 생성한다:

- `bebecup-vX.Y.Z.apk` (GitHub 키로 서명된 `github` 빌드 APK)
- `bebecup-vX.Y.Z.mapping.txt` (그 APK의 R8 난독화 해제 매핑 파일)
- `update.json` (GitHub 빌드의 앱 내 업데이트 확인용: `versionCode`, `versionName`, `apk`, `sha256`, `size`. `scripts/update_manifest.py`가 만든다. 앱은 `releases/latest/download/update.json`을 읽고, APK 주소는 저장소·`v<versionName>` 태그·파일 이름으로 직접 만든다. 그래서 `versionName`이 태그와 다르면 워크플로가 멈춘다. 이미 설치된 앱이 읽는 공개 계약이므로 형식을 바꾸지 않는다)

릴리스 작업은 서명 뒤 GitHub APK와 Play AAB의 인증서 SHA-256이 위 두 표의 값과 같은지 확인하고, 다르면 멈춘다.

Play 업로드용 AAB(`play` 빌드)는 GitHub Release에 붙이지 않고 워크플로 산출물(`bebecup-release-aab`)로만 남기며, 이어지는 `play-alpha` 작업이 Alpha 트랙에 올린다(위 "자동 업로드").
