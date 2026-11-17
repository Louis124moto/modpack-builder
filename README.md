# Modpack Builder

Native Android app (Kotlin + Jetpack Compose) that turns a natural-language Minecraft modpack request into a Modrinth mod list, a Prism Launcher-compatible Modrinth index, or a ZIP containing the selected JARs.

## Current implementation

- Compose UI for a prompt, Fabric / Forge / NeoForge / Quilt, automatic or manual Minecraft version, and three output formats.
- LLM providers: OpenAI-compatible APIs, Anthropic, and Google Gemini.
- Gemini uses Google's native `generateContent` API; the default model is `gemini-2.5-flash`.
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
3. Select Google Gemini, enter a Gemini API key and model (default: `gemini-2.5-flash`), then save.
4. Create a Gemini API key in Google AI Studio: https://aistudio.google.com/app/apikey
5. OpenAI-compatible services can use a custom API base URL ending in `/v1`.

The app sends the prompt to the configured LLM provider and queries the public Modrinth API. API keys are stored encrypted on-device, are not sent to Modrinth, and must never be committed to this repository.

## Notes / known limitations

- Project matching currently selects Modrinth's first search hit; inspect the resulting list before using the pack.
- Automatic mode requires a common Minecraft version across the LLM-selected root mods. If none exists, choose a version manually.
- ZIP generation downloads the selected JAR files on-device. Large packs require sufficient free storage.
- Modrinth index metadata uses the selected Minecraft and loader versions. The generated index is intended for Prism Launcher; validate the resulting pack in the launcher before sharing it.
- This is an initial implementation. A successful CI build verifies compilation, but does not replace testing on a physical Android device.

## License

MIT
