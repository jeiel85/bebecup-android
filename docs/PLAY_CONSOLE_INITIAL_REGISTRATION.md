# Play Console Initial Registration

최종 확인일: 2026-05-24

이 문서는 베베컵 최초 Play Console 등록 시 입력할 값을 정리한다. Play Console 계정 로그인, 개발자 프로그램 정책/미국 수출법/Play App Signing 약관 동의, 개인정보처리방침 공개 URL 입력은 콘솔에서 직접 완료해야 한다.

## 앱 생성

| 항목 | 값 |
| --- | --- |
| 앱 이름 | 베베컵 |
| 기본 언어 | 한국어 (대한민국) - ko-KR |
| 앱 또는 게임 | 앱 |
| 무료 또는 유료 | 무료 |
| 앱 연락 이메일 | pedaiah85@gmail.com |
| 개발자 이름 | Yongeun Park |
| 개발자 계정 | pedaiah85@gmail.com (Play Console 로그인 계정) |
| 패키지 이름 | `com.bebecup.app` |
| Play App Signing | 사용 (업로드 키: `.keystore/bebecup-release.p12`, alias `bebecup-release`) |

## 기본 스토어 등록정보

| 항목 | ko-KR | en-US |
| --- | --- | --- |
| 앱 이름 | 베베컵 | Bebecup |
| 짧은 설명 | 직접 고른 아기 사진으로 만드는 가족 사진 월드컵 | A family photo tournament made from baby photos you choose |
| 전체 설명 | `fastlane/metadata/android/ko-KR/full_description.txt` 참조 | `fastlane/metadata/android/en-US/full_description.txt` 참조 |
| 앱 카테고리 | 육아 | Parenting |
| 태그 | 사진, 육아, 가족 | Photos, Parenting, Family |

## 앱 콘텐츠

| 항목 | 입력값 |
| --- | --- |
| 개인정보처리방침 URL | https://jeiel85.github.io/bebe-cup/privacy/ (GitHub Pages, `docs/privacy/index.html`. 공개·로그인 불필요·지역 제한 없음. 소스: [docs/privacy/index.html](privacy/index.html)) |
| 앱 액세스 | 제한 없음. 로그인/계정/테스트 계정 없음. |
| 광고 포함 여부 | 아니요 |
| 데이터 보안 - 수집 | 수집하지 않음. 사용자가 선택한 로컬 이미지 URI는 기기 내부 Room DB에만 저장하며 외부 서버로 전송하지 않음. |
| 데이터 보안 - 공유 | 공유하지 않음 |
| 데이터 삭제 | 앱 삭제 또는 앱 데이터 삭제로 로컬 데이터 제거. 앱 내부 삭제 기능은 사진 후보 목록에서 제거. |
| 콘텐츠 등급 | 폭력/성적 콘텐츠/도박/사용자 간 상호작용/위치 공유 없음으로 답변 |
| 타겟 연령 | 18세 이상 부모/보호자 대상 |
| 어린이 대상 여부 | 어린이를 대상으로 제작한 앱 아님 |
| 뉴스 앱 여부 | 아니요 |
| 정부 앱 여부 | 아니요 |
| 금융 기능 | 없음 |
| 건강 기능 | 없음 |
| 위치 권한 | 사용하지 않음 |
| 외부 인화 사이트 | 사용자가 명시적으로 브라우저를 여는 handoff만 제공. 앱이 사진 파일을 업로드했다고 표현하지 않음. |

## 그래픽/스토어 자산

| 항목 | 상태 |
| --- | --- |
| 앱 아이콘 | Android launcher icon 포함 (`app/src/main/res/mipmap-*`) |
| 대표 이미지 | TBD: 1024 x 500 PNG/JPG feature graphic 필요. 별도 디자인 작업 필요 항목. |
| 휴대전화 스크린샷 | TBD: 실제 앱 화면 2장 이상. 320~3840px, 16:9 또는 9:16. |
| 7인치/10인치 태블릿 스크린샷 | 해당 폼에서 요구 시 준비. |
| 아이콘 (512 x 512) | TBD: 32-bit PNG, alpha 포함. Play Console 별도 업로드 항목. |

## 릴리즈 업로드

| 항목 | 값 |
| --- | --- |
| 업로드 파일 | `app/build/outputs/bundle/release/app-release.aab` |
| 현재 버전 | `versionCode = 2`, `versionName = "0.2.0"` |
| 릴리즈 노트 | `fastlane/metadata/android/{ko-KR,en-US}/changelogs/2.txt` |
| 데스크톱 제출 파일 생성 | `./gradlew.bat :app:exportReleaseToDesktop` |

## 사전 작업 체크리스트

Play Console에 값을 입력하기 전에 완료해 두어야 하는 외부 작업.

1. **GitHub Pages 활성화**
   - `https://github.com/jeiel85/bebe-cup/settings/pages`
   - Source: `Deploy from a branch`
   - Branch: `main` / Folder: `/docs`
   - 저장 후 1~2분 뒤 https://jeiel85.github.io/bebe-cup/privacy/ 접근 확인. 공개 상태·로그인 불필요·지역 제한 없음이 보장되어야 한다.
2. **Play Console 개발자 계정 로그인**
   - 계정: `pedaiah85@gmail.com` (Chrome account chooser에서 선택).
   - `jeiel85@gmail.com`의 옛 Play Console 개발자 계정(ID `4685898627432283006`)은 해지 상태이므로 이 계정으로 업로드 시도 금지.
3. **콘솔에서 직접 동의해야 하는 항목**
   - Google Play 개발자 프로그램 정책.
   - 미국 수출법.
   - Play App Signing 약관 (업로드 키로 `.keystore/bebecup-release.p12` 사용 예정).
4. **그래픽 자산 준비**
   - 512x512 앱 아이콘, 1024x500 feature graphic, 휴대전화 스크린샷 2장 이상. 자산 미준비 상태에서는 스토어 등록정보 저장 불가.

## 확인 근거

- Google Play Console 앱 생성에는 기본 언어, 앱 이름, 앱/게임 여부, 무료/유료 여부, 연락 이메일, 정책/수출법/Play App Signing 동의가 필요하다.
- 스토어 등록정보 제한: 앱 이름 30자, 짧은 설명 80자, 전체 설명 4000자.
- 모든 앱은 Play Console 개인정보처리방침 필드에 공개 URL을 입력해야 하며, 데이터 보안 섹션도 제출해야 한다.
- 새 앱은 콘텐츠 등급 설문과 타겟 연령/콘텐츠 설정을 완료해야 한다.
