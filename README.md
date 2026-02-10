# RandomList

An Android app to create custom lists and pick random elements from them.

## Features

- Create unlimited custom lists with any items
- Random selection with smooth animations
- Edit and delete lists
- Color-coded lists
- Multi-language support: English, Catalan, Spanish

## Tech Stack

- **Kotlin** + **Jetpack Compose**
- **Material 3** design system
- **Coroutines** for async animations
- Local persistence with **SharedPreferences**

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

## Setup

1. Open Android Studio
2. Select "Open" and navigate to the `RandomList` directory
3. Wait for Gradle to sync the project
4. Connect an Android device or start an emulator
5. Press "Run" (▶️)

## Project Structure

```
RandomList/
├── app/
│   ├── src/main/
│   │   ├── java/com/randomlist/
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   ├── values/strings.xml          (English - default)
│   │   │   ├── values-ca/strings.xml       (Catalan)
│   │   │   ├── values-es/strings.xml       (Spanish)
│   │   │   ├── values/colors.xml
│   │   │   ├── values/themes.xml
│   │   │   └── drawable/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## License

MIT License
