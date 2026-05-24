# History

## 2026-05-24 (Play Console 최초 등록 준비)

- **릴리즈 키스토어 생성**: 기존 release keystore가 없어 `.keystore/bebecup-release.p12`를 PKCS12/RSA 4096bit로 생성하고, `release-signing.properties` 및 `.keystore/release-signing-backup.properties`에 서명 값을 저장함.
- **비밀 파일 보호**: `.keystore/`를 `.gitignore`에 추가하여 릴리즈 키스토어와 비밀번호 백업이 Git에 포함되지 않도록 처리함.
- **Play Console 등록값 정리**: `docs/PLAY_CONSOLE_INITIAL_REGISTRATION.md`에 최초 앱 생성, 스토어 등록정보, 앱 콘텐츠, 그래픽 자산, 릴리즈 업로드 값을 정리함.
- **스토어 메타데이터 추가**: fastlane `title`, `short_description`, `full_description`을 ko-KR/en-US로 작성함.
- **개인정보처리방침 초안 추가**: `docs/PRIVACY_POLICY_DRAFT.md`에 로컬 사진 URI 저장, 외부 업로드 없음, 광고/분석/추적 SDK 없음 정책을 정리함.

## 2026-05-23 (v0.2.0 릴리즈 작업)

- **데이터베이스 강건화**: `BabyPhotoDao`에 `pruneMockPhotos` 쿼리를 신설하고, `BabyCupViewModel` 기동 시 비정상/목업 URI 데이터를 자동 안전 소거하도록 처리함.
- **CI/CD 파이프라인 수립**: `markleaf-android` 프로젝트에서 `android-build.yml` 파일을 이식하여 테스트, 린트, APK/AAB 서명/비서명 빌드 및 Tag push 시 릴리즈 자동 생성 액션을 통합함.
- **버전 2차 범프**: `app/build.gradle.kts`의 `versionCode = 2`, `versionName = "0.2.0"`으로 올리고, 다국어 fastlane changelog `2.txt`를 신설함.
- **산출물 내보내기 및 검증**: `./gradlew :app:exportReleaseToDesktop`을 정상 구동하여 바탕화면에 AAB 및 통합 릴리즈 노트 TXT가 올바르게 내보내짐을 로컬 검증함.

## 2026-05-23

- `D:\Project\markleaf-android`의 release artifact export 규칙을 베베컵에 맞게 이식했다.
- `applicationId`를 `com.bebecup.app`으로 설정하고 release signing 값을 선택적으로 읽도록 변경했다.
- 첫 실행 목업 사진 주입을 제거하고, 사진 등록을 실제 Photo Picker URI 기반으로 정리했다.
- Play Console 제출용 AAB와 bilingual release-notes TXT를 바탕화면에 내보내는 `:app:exportReleaseToDesktop` task를 추가했다.
