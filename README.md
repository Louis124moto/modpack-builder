# Modpack Builder

Native Android app (Kotlin + Jetpack Compose) that turns a natural-language Minecraft modpack request into a Modrinth mod list, a Prism Launcher-compatible Modrinth index, or a ZIP containing the selected JARs.

## Current implementation

- Compose UI for a prompt, Fabric / Forge / NeoForge / Quilt, automatic or manual Minecraft version, and three output formats.
- Settings screen for an OpenAI-compatible endpoint or Anthropic, model, and API key.
- API key stored in AndroidX EncryptedSharedPreferences (Android Keystore-backed).
- LLM prompt asks for a JSON array of mod project names.
- Modrinth v2 search, loader/version filtering, stable-version preference, common Minecraft version selection in automatic mode, and recursive required dependency resolution.
- Direct download links, Modrinth index JSON, and ZIP export through Android's document picker.
- GitHub Actions debug APK build.

## Build

Requirements: Android Studio with JDK 17 and Android SDK 35, or Gradle 8.11.1.

```bash
gradle assembleDebug
```

The CI workflow installs the Android SDK and runs the same build. The APK is uploaded as the modpack-builder-debug workflow artifact.

## Configure

1. Install the debug APK.
2. Open the gear icon.
3. Select the provider, enter its API key and model, and save.
4. For OpenAI-compatible services, set the API base URL ending in /v1 (the app appends /chat/completions).

The app sends the prompt to the configured LLM provider and queries the public Modrinth API. API keys are not sent to Modrinth and must never be committed to this repository.

## Notes / known limitations

- Project matching currently selects Modrinth's first search hit; inspect the resulting list before using the pack.
- Automatic mode requires a common Minecraft version across the LLM-selected root mods. If none exists, choose a version manually.
- ZIP generation downloads the selected JAR files on-device. Large packs require sufficient free storage.
- Modrinth index metadata uses the selected Minecraft and loader versions. The generated index is intended for Prism Launcher; validate the resulting pack in the launcher before sharing it.
- This is an initial implementation. It has not yet been verified by a successful Android CI build.

## License

MIT
