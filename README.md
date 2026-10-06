# MVP BizManager – Android WebView App

Clean Kotlin WebView wrapper for **https://www.mvp.com.ai/**

## Features

- Modern **Splash Screen** (Android 12+ SplashScreen API)
- Full **WebView** loading `https://www.mvp.com.ai/`
- **Pull-to-refresh**
- **Offline detection** + retry screen
- **Camera & Gallery** file chooser (for uploads)
- Runtime **permission** requests (Camera, Photos)
- Deep links for `mvp.com.ai` / `www.mvp.com.ai`
- Back button navigates WebView history
- Cookies, JavaScript, DOM storage enabled

## Package

`com.mvp.bizmanager`

## Requirements

- Android Studio Hedgehog or newer (or Arctic Fox+)
- JDK 17
- minSdk 26 / targetSdk 35

## How to open & run

1. Open **Android Studio**
2. **File → Open** → select the `MVPBizManager` folder
3. Wait for Gradle sync
4. Connect a device / start emulator
5. Click **Run**

## Permissions used

| Permission                  | Purpose                          |
|-----------------------------|----------------------------------|
| INTERNET                    | Load website                     |
| ACCESS_NETWORK_STATE        | Detect offline                   |
| CAMERA                      | Take photo for uploads           |
| READ_MEDIA_IMAGES           | Pick images (Android 13+)        |
| READ_EXTERNAL_STORAGE       | Pick images (Android ≤ 12)       |

## Project structure

```
MVPBizManager/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mvp/bizmanager/
│       │   └── MainActivity.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── xml/file_paths.xml
│           ├── values/
│           └── mipmap-anydpi-v26/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Customization

- Change URL → `siteUrl` in `MainActivity.kt`
- Colors → `res/values/colors.xml`
- Splash background → `splash_background` color
- App name → `res/values/strings.xml`
