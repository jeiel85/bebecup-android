# Release Signing and Play Store Submission

베베컵 release 빌드는 서명 값이 제공된 경우에만 release keystore로 서명된다. keystore와 비밀번호는 저장소에 커밋하지 않는다.

## Local Signed Release Build

로컬에 `release-signing.properties` 파일을 만든다.

```properties
BEBECUP_RELEASE_STORE_FILE=.secrets/bebecup-release.p12
BEBECUP_RELEASE_STORE_PASSWORD=your-keystore-password
BEBECUP_RELEASE_KEY_ALIAS=bebecup-release
BEBECUP_RELEASE_KEY_PASSWORD=your-keystore-password
```

PKCS12 keystore를 쓰는 경우 `BEBECUP_RELEASE_KEY_PASSWORD`는 보통 store password와 동일하게 둔다.

서명 값을 제공하지 않아도 검증용 unsigned release bundle은 만들 수 있다.

```bash
./gradlew :app:bundleRelease
```

서명이 반드시 있어야 하는 release-candidate 검증에서는 property를 켠다.

```bash
./gradlew :app:bundleRelease -Pbebecup.requireReleaseSigning=true
```

PowerShell:

```powershell
./gradlew.bat :app:bundleRelease '-Pbebecup.requireReleaseSigning=true'
```

Play Console용 AAB:

```text
app/build/outputs/bundle/release/app-release.aab
```

## R8

Release 빌드는 R8 minify와 resource shrinking을 사용한다. 필요한 keep rule은 `app/proguard-rules.pro`에 최소 범위로 추가하고, 왜 필요한지 주석을 남긴다.

R8 mapping 파일:

```text
app/build/outputs/mapping/release/mapping.txt
```

출시한 각 버전의 mapping 파일은 Play Console 난독화 해제용으로 보관한다.

## Play Console Export

Play 제출 파일을 바탕화면으로 내보낸다.

```bash
./gradlew :app:exportReleaseToDesktop
```

생성 파일:

- `bebecup-vX.Y.Z.aab`
- `bebecup-vX.Y.Z-release-notes.txt`

릴리즈 노트 원천 파일:

```text
fastlane/metadata/android/ko-KR/changelogs/<versionCode>.txt
fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
```

각 locale의 Play Console 릴리즈 노트는 500자 제한이 있으므로 Gradle task가 초과 여부를 검사한다.

## CI Gates and GitHub Releases

GitHub Actions를 통해 코드 품질 및 릴리즈 빌드가 자동 검증된다. `main` 브랜치에 푸시하거나 Pull Request를 생성할 때 다음이 수행된다:

- `./gradlew assembleDebug`
- `./gradlew test`
- `./gradlew :app:lintRelease` (Release 린트 검사)
- `./gradlew :app:assembleRelease` (Release APK 빌드 검사)

### GitHub Actions Secrets

GitHub 저장소의 **Settings > Secrets and variables > Actions**에 아래 보안 값을 설정한다:

```text
BEBECUP_RELEASE_KEYSTORE_BASE64  # Base64로 인코딩된 keystore (.p12) 파일 내용
BEBECUP_RELEASE_STORE_PASSWORD   # Keystore 비밀번호
BEBECUP_RELEASE_KEY_ALIAS        # 키 별칭
BEBECUP_RELEASE_KEY_PASSWORD     # 키 비밀번호
```

`v*`와 일치하는 버전 태그가 푸시되면 GitHub Actions가 빌드를 실행하고 최종적으로 다음 3개 산출물을 포함하여 GitHub Release를 자동 생성한다:

- `bebecup-vX.Y.Z.apk` (서명된 릴리즈 APK)
- `bebecup-vX.Y.Z.aab` (서명된 Play Store 업로드용 AAB)
- `bebecup-vX.Y.Z.mapping.txt` (R8 난독화 해제 매핑 파일)
