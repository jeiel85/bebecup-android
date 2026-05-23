# Changelog

## v0.2.0 - 데이터베이스 강건화 및 릴리즈 파이프라인 자동화 구축 - 2026-05-23

### Fixed
- Room 데이터베이스 초기 기동 시 잔존하는 레거시 목업 데이터를 자동으로 정리하는 영구 안전 삭제 로직을 추가했습니다.

### Added
- GitHub Actions를 통한 정적 분석, 린트, 테스트 및 Tag push 릴리즈 자동화 파이프라인(`android-build.yml`)을 구축했습니다.
- fastlane versionCode 2 다국어 출시 노트 파일을 신설하고 `docs/RELEASE.md`에 배포 상세 정책을 보완했습니다.
- `versionCode`를 `2`로, `versionName`을 `"0.2.0"`으로 올렸습니다.

## v0.1.0 - Play 등록 준비와 실제 사진 데이터 전환 - 2026-05-23

- 첫 실행 시 목업/프리셋 사진을 자동 생성하지 않도록 변경했습니다.
- 사진 등록은 Android Photo Picker에서 사용자가 선택한 실제 이미지 URI를 저장하도록 정리했습니다.
- 인화 주문 화면은 앱 내부에서 파일을 전송했다고 표현하지 않고, 외부 주문 페이지로 이동하는 흐름으로 정리했습니다.
- Play Store 제출을 위해 배포용 `applicationId`, release R8/shrink 설정, 서명 설정, AAB export task를 추가했습니다.
- `새 버전 만들기` 요청 시 사용할 릴리즈 문서와 fastlane changelog 구조를 추가했습니다.
