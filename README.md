# 베베컵

실제 아기 사진으로 이상형 월드컵을 만들고 인화 후보를 정리하는 Android 앱입니다.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Run the app on an emulator or physical device.

## Release

릴리즈 서명과 Play 제출 산출물 절차는 `docs/RELEASE.md`를 따른다.

```bash
./gradlew :app:bundleRelease
./gradlew :app:exportReleaseToDesktop
```
