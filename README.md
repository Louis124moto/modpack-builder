# Modpack Builder

Android application for generating Minecraft modpacks from a natural-language description, using an LLM and the Modrinth API.

> Initial project bootstrap. See the source tree for implementation details.

## Build

Open the project in Android Studio or run:

```bash
./gradlew assembleDebug
```

The GitHub Actions workflow builds the debug APK on pushes and pull requests.

## Security

Configure your LLM provider and API key in the app settings. The key is stored locally using Android Keystore-backed encrypted preferences. Never commit API keys.

## License

MIT
