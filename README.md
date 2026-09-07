# Nexus AI — Android Agentic Coding Assistant

A provider-agnostic Android AI coding agent. Connects to any OpenAI-compatible
`/chat/completions` REST endpoint (OpenAI, Ollama, DeepSeek, Gemini's OpenAI shim, LM Studio,
vLLM, etc.), streams responses token-by-token, and lets the model natively read/write files,
list the workspace tree, and zip the project — all sandboxed inside the app's private storage.

## Opening the project

1. Unzip this archive.
2. Open the root folder (`NexusAI/`) in Android Studio (Koala/2024.1+ recommended).
3. Android Studio will offer to generate the Gradle wrapper jar automatically on first sync
   (this archive ships `gradle-wrapper.properties` pinned to Gradle 8.9, but not the binary
   `gradle-wrapper.jar` — Studio regenerates it, or run `gradle wrapper` once if you have a
   local Gradle install).
4. Sync Gradle, then Run on a device/emulator (minSdk 26).

## Architecture

```
app/src/main/java/com/nexusai/agent/
├── MainActivity.kt              Adaptive dual-pane root (Chat | Inspector), Compose entry point
├── data/
│   ├── Models.kt                OpenAI-wire Kotlin@Serializable models + tool/function schemas
│   ├── WorkspaceEngine.kt       Sandboxed read/write/list/zip file tool executor
│   ├── AiClient.kt              Ktor SSE streaming client (provider-agnostic)
│   └── SettingsStore.kt         SharedPreferences-backed provider config persistence
└── ui/
    ├── ChatViewModel.kt         Coroutine agent loop: stream -> detect tool calls -> execute -> repeat
    ├── theme/                   Color.kt / Type.kt / Theme.kt — Material3 theme sourced from
    │                            the Stitch "nexus_ai_*" design reference tokens
    ├── components/              ActionCard, FileTreeView, StreamingCodeViewer, MessageBubble
    └── screens/                 ChatScreen (Panel 1), InspectorScreen (Panel 2), SettingsScreen
```

## How the agent loop works

1. User sends a message → appended to the OpenAI-format conversation history.
2. `AiClient.streamChatCompletion` opens a streaming POST to `{baseUrl}/chat/completions` with
   `tools` set to the four native functions, parses `data:` SSE lines, and emits fine-grained
   `StreamEvent`s (`ContentDelta`, `ToolCallStart`, `ToolCallArgumentsDelta`, `Done`, `Error`).
3. `ChatViewModel` reduces those events into live Compose state — assistant text grows character
   by character, and as soon as a tool call's `path` argument becomes readable mid-stream, an
   `ActionCard` appears in the Activity Feed and a matching row lights up in the File Inspector.
4. Once a turn finishes, any requested tool calls are executed via `WorkspaceEngine` (confined to
   `context.filesDir/workspace`), their results are appended back into the conversation as
   `role: "tool"` messages, and the loop calls the model again — up to 8 iterations — so the
   agent can chain multiple file writes, re-read what it wrote, and finally zip the project.

## Configuring a provider

Open Settings (top-right icon) and either pick a preset chip (OpenAI / Ollama / DeepSeek /
Gemini shim) or enter a custom Base URL + API key + model manually. Everything is stored
on-device only, in `nexus_ai_settings` SharedPreferences (excluded from Android backups).

## CI (GitHub Actions)

`.github/workflows/ci.yml` builds a debug APK on every push/PR, and a signed release APK on
pushes to `main`. It expects four repo secrets for the release job:

| Secret            | Purpose                                        |
|--------------------|-------------------------------------------------|
| `KEYSTORE_B64`     | base64 of your upload `.jks`/`.keystore` file   |
| `STORE_PASSWORD`   | keystore password                               |
| `KEY_ALIAS`        | alias of the signing key inside the keystore    |
| `KEY_PASSWORD`     | password of that key entry                      |

`app/build.gradle.kts` only wires up `signingConfigs["release"]` when
`<repo-root>/my-upload-key.jks` exists on disk (i.e. only inside the CI job, after the
"Decode release keystore" step runs) — so a local `./gradlew assembleRelease` without those
secrets just produces an unsigned APK instead of failing the build.

To generate a new upload keystore locally:

```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 my-upload-key.jks   # paste the output into the KEYSTORE_B64 secret
```

## Notes / things to double check before shipping

- **Fonts**: the theme references Geist + JetBrains Mono conceptually but falls back to
  `FontFamily.SansSerif` / `FontFamily.Monospace` so the project compiles without bundled font
  assets. Swap in `androidx.compose.ui.text.googlefonts.GoogleFont` providers (or drop `.ttf`
  files into `res/font/`) in `ui/theme/Type.kt` for the exact reference typefaces.
- **App icon**: no custom launcher icon/mipmap set is included; the manifest omits
  `android:icon` so the platform default is used. Add your own adaptive icon before release.
- **Ollama base URL**: defaults to `http://10.0.2.2:11434/v1`, which is the emulator's alias for
  the host machine's `localhost`. On a physical device, point it at your machine's LAN IP instead.
- This project was written and balance-checked (braces/parens) by hand in a sandboxed
  environment without Android SDK/Gradle available, so it has **not** been compiled. Skim for
  typos on first sync, though the code has been reviewed carefully for API correctness.
