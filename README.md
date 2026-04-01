# Wallify HD

Kotlin + Jetpack Compose wallpaper app with:

- paginated wallpaper discovery
- category filters and search
- fullscreen preview
- download to gallery
- wallpaper apply for home, lock, or both
- local favorites with Room

## API behavior

- Uses the integrated Wallhaven API key by default.
- Uses Unsplash only when `UNSPLASH_ACCESS_KEY` is configured and no Wallhaven key is present.

Configured Wallhaven key:

```properties
WALLHAVEN_API_KEY=your_key_here
```

Put keys in your local `local.properties` or your user Gradle properties so they do not get committed.

Optional Unsplash key:

```properties
UNSPLASH_ACCESS_KEY=your_key_here
```

## Build

```powershell
.\gradlew.bat assembleDebug
```

This workspace's wrapper currently points at the local Gradle 8.7 zip on this machine:

```text
C:\data\android app\gradle-8.7-bin.zip
```

If you move the project to another machine, update `gradle/wrapper/gradle-wrapper.properties` back to the standard Gradle distribution URL.
