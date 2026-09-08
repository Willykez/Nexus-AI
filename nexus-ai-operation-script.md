# Nexus AI — How the App Operates

A native Android app that runs an AI coding agent against a private, sandboxed
file workspace on the device. This is the operational script: what happens,
in order, from launch to finished output.

---

## 1. Launch

1. `MainActivity` starts and creates a single `ChatViewModel` (survives
   rotation, tied to the Activity's lifecycle via `AndroidViewModel`).
2. The ViewModel constructs:
   - `WorkspaceEngine` — owns a sandbox folder at
     `context.filesDir/workspace` (all agent file I/O is confined here).
   - `SettingsManager` — reads saved provider settings (base URL, model,
     encrypted API key) from DataStore.
3. It loads the current file tree from disk and publishes the real
   workspace path into UI state — nothing here is hardcoded; it reflects
   whatever the OS actually assigned.
4. It subscribes to `SettingsManager.settingsFlow`. Whenever settings
   change, it rebuilds the HTTP client (`AiClient`) pointed at the new
   base URL / key, and updates the "provider" badge shown in the UI.
5. The four core tabs remain **Chat & Stream**, **Code Diff**, **Workspace**, and **Provider**. **Paste & Organize** is a sibling mode inside Chat for the separate whole-project source-dump workflow described by the product vision.

---

## 2. Configuring a provider (Provider tab)

1. User enters a **Base URL** (any OpenAI-compatible `/v1` endpoint —
   OpenAI, Gemini's OpenAI shim, DeepSeek, a local Ollama, or a custom
   gateway like Alibaba DashScope), a **Model** name, and optionally an
   **API key**.
2. Tapping a provider chip (OpenAI / Gemini / DeepSeek / Ollama)
   pre-fills the URL + a sensible default model — it's a shortcut, not a
   requirement; any compatible URL works.
3. **Save & Activate**:
   - Rejects the save if URL or model is blank.
   - The API key is encrypted at rest via Android Keystore before being
     persisted, in a profile keyed to the gateway URL; if the key field is
     left blank, the previously saved key for that gateway is kept.
   - On success, the key field is cleared from screen state.
   - Reopening a saved conversation restores its saved gateway URL/model and
     retrieves that gateway's encrypted key profile without destroying other providers.
4. **Agent Capabilities** toggles control what the agent is *allowed* to
   do this session:
   - File System Read/Write → enables/disables the `read_file` /
     `write_file` tools.
   - Auto-Package ZIP → enables/disables the `zip_project` tool.
   - These aren't cosmetic — the disabled tool is both left out of what's
     offered to the model **and** rejected server-side if invoked anyway.
5. A **READY / NEEDS KEY** indicator reflects real state (has a key, or
   is a keyless local provider like Ollama) — not a fake uptime number.

---

## 3. Sending a message (Chat & Stream tab)

1. User types a prompt and hits send. Ignored if empty or if an agent
   run is already in progress.
2. The prompt is appended to the visible message list immediately, and
   an agent run kicks off:
   - System prompt is built fresh each turn, listing only the tools
     currently enabled by the capability toggles.
   - Full prior chat history (user + assistant text turns) is replayed
     so the model has context.
3. **The agent loop** (max 8 rounds per user turn):
   - Sends a streaming chat-completion request with the active tool
     set, temperature, and max-token budget from settings.
   - Tokens stream back over SSE and render live in the UI as they
     arrive (`streamingText` / `streamingCode`).
   - If the model requests tool calls, each is executed against the
     sandboxed `WorkspaceEngine`:
     - `read_file` / `write_file` — confined to the workspace root; no
       absolute paths, no `..` traversal.
     - `list_files` — recursive listing, always available.
     - `zip_project` — packages the whole workspace into a timestamped
       ZIP under `filesDir/exports`.
   - Tool results are fed back to the model and the loop continues
     until the model returns plain text with no further tool calls, or
     the round cap is hit.
4. The finished assistant reply is appended to the message list; the
   chat auto-scrolls to the actual last rendered item (not a guessed
   offset).
5. Every file write during a run is logged to an in-session diff list
   (path, added/removed line counts) — this list is **per session**, not
   a mirror of disk state, so it resets on app restart even though the
   files themselves persist.

---

## 4. Organize mode

1. User pastes a whole source dump into **Organize**. The input remains fully editable and scrollable even for very large pastes.
2. A streaming request asks the active provider to infer one safe project root and a list of relative file paths/content. Large pastes show a context-size warning.
3. Model output is parsed and model-provided paths are independently revalidated; absolute paths, drive letters, `.`/`..` segments, and blank path segments are discarded.
4. The inferred file list is shown for review. Nothing is written until the user explicitly confirms.
5. Confirming writes every file through the same sandboxed `WorkspaceEngine`, refreshes the workspace after each file, logs activity, and switches to Workspace when complete.

## 5. Inspecting output

- **Code Diff tab** — shows what the *current session's* agent run
  wrote or changed, with line-count deltas. Empty here just means
  nothing has been written yet this session, regardless of what already
  exists on disk.
- **Workspace tab** — the source of truth: the real, persisted file
  tree read straight from `filesDir/workspace`, with storage size, node
  count, and generation count. Supports:
  - Tapping a file to preview its content.
  - A per-file menu (⋮) to **rename** or **delete** — both go through
    `WorkspaceEngine`, which re-validates the target path and refuses to
    escape the sandbox.
  - **Zip Workspace** — packages everything on demand; failures now
    surface an error and reset agent state instead of hanging silently.
  - **Refresh** — re-reads the tree from disk.

---

## 6. Exporting

1. From the Workspace tab, once a ZIP exists, an **Archive card**
   appears with a share/export action.
2. Uses `FileProvider` to hand the ZIP to any installed app (Drive,
   email, Files, etc.) without exposing a raw file:// path.

---

## Core invariants the app is required to hold

- **Sandboxing**: the agent can never read/write/delete outside
  `filesDir/workspace`, regardless of what the model asks for.
- **Capability toggles are authoritative**: a disabled capability is
  unavailable to the model both by omission from its tool list and by
  server-side rejection if called anyway.
- **UI state always reflects real state**: workspace path, provider
  readiness, and capability status are derived from actual values, not
  hardcoded placeholders.
- **No silent failures**: zip/export/tool errors update `error` state
  and emit a snackbar rather than leaving the UI stuck mid-task.
- **Session log vs. disk state are distinct**: the diff/activity feed is
  scoped to the running session; the Workspace tree is the persistent
  ground truth.
- **Provider profiles are isolated**: API keys are encrypted per normalized gateway URL, so changing providers does not copy one provider's credential into another profile.
- **Wide-screen behavior is adaptive**: a wide Chat surface keeps the live Workspace inspector visible beside the conversation; narrow layouts use the tabbed surfaces.
