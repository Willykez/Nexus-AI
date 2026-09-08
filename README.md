# Nexus AI — Unified Mobile Coding Agent

Nexus AI is a native Android coding agent that combines live AI chat, real file operations, a persistent private workspace, session history, provider routing, code diffs, ZIP export, and a dedicated whole-project paste/organize workflow.

## Product surfaces

- **Chat & Stream** — one-thumb composer, live SSE output, clean Markdown/code rendering, visible agent activity, live file writes, stop/cancel, and one-tap copy for a whole reply or individual code block.
- **Code Diff** — session-scoped writes with NEW/MOD status plus added/removed line counts and previews.
- **Workspace** — persistent private `filesDir/workspace` tree, inspector, rename/delete, project-folder import, refresh, ZIP export, and Android share sheet.
- **Paste & Organize** — a sibling mode inside Chat: paste a complete project/source dump, stream the model's parsing work, review the inferred file manifest, then explicitly confirm writing it into the private workspace.
- **Provider** — OpenAI-compatible routing for OpenAI, Gemini shim, DeepSeek, Ollama, Qwen, Groq, Mistral, or any compatible gateway; per-gateway encrypted API-key profiles; temperature/max-token controls; authoritative file/ZIP capability toggles.

On narrow phones the app is a one-surface-at-a-time experience. On wide screens, Chat & Stream and Workspace are shown together so the conversation and actual files stay visible simultaneously.

## Safety invariants

1. Agent file operations resolve through one sandbox policy rooted at the app's canonical private workspace. Absolute paths, drive-letter paths, `.` segments, `..` traversal, and escapes through symlinks are rejected.
2. Capability toggles are enforced twice: disabled tools are omitted from the model tool list and rejected if a model attempts to call them anyway.
3. Imported projects are copied into the private workspace; the agent never receives direct access to the original external folder.
4. Provider API keys are encrypted with Android Keystore + AES/GCM. Keys are stored per gateway profile so switching providers does not silently destroy another provider's credential.
5. ZIP files are created under private `filesDir/exports` and shared through `FileProvider`, never a raw `file://` URI.
6. Each user request is capped at eight agent rounds. Hitting the limit becomes a visible stop/error state rather than an infinite loop.
7. Tool, network, import, and export errors are surfaced in plain-language UI state/snackbars.
8. Completed agent turns and the provider/model/base URL needed to reopen them are persisted locally. The raw tool-call wire history is retained so reopening a session does not lose agent context.
9. Workspace and export data are excluded from Android backup/device transfer, and the manifest explicitly wires both backup rule systems.

## Project import and organize mode

**Import Project** is the safe folder workflow: Android's system folder picker is used, readable project files are copied into the app's private workspace, and generated directories such as `.git`, `.gradle`, `build`, and `node_modules` are skipped. Other dotfiles such as `.gitignore` and `.env.example` are retained.

**Organize** is the source-dump workflow: paste a large source blob, let the active provider infer a safe root and relative paths, review the result, then explicitly confirm the write. Large pastes display a context-size warning rather than silently truncating the input.

## Provider examples

- OpenAI: `https://api.openai.com/v1`
- DeepSeek: `https://api.deepseek.com/v1`
- Gemini OpenAI-compatible shim: `https://generativelanguage.googleapis.com/v1beta/openai`
- Ollama emulator host: `http://10.0.2.2:11434/v1`
- Qwen compatible mode: `https://dashscope-intl.aliyuncs.com/compatible-mode/v1`
- Groq: `https://api.groq.com/openai/v1`
- Mistral: `https://api.mistral.ai/v1`

The app uses streaming OpenAI-compatible `/chat/completions` requests. Any gateway implementing that contract can be entered manually.

## Build

Open the project in Android Studio with **JDK 17**. The project targets Android 15 (`compileSdk 35`) and uses Android Gradle Plugin 8.7.3 with Gradle 8.9.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The sandbox used for this review did not have network access to download the Gradle distribution, so an APK build could not be executed here. CI is configured to perform the debug build and unit tests on GitHub Actions.

## Release signing

Set these environment variables for a signed release build:

- `RELEASE_KEYSTORE_PATH`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Without them, `assembleRelease` remains usable and produces an unsigned APK.


## Validation status

The source includes unit coverage for the workspace path policy. The sandbox used to prepare this archive does not contain the Android SDK or a locally cached Gradle 8.9 distribution, so a final APK build cannot be honestly claimed from this environment. CI is configured to build the debug APK and run unit tests with JDK 17.
