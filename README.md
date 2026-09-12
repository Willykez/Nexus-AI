# Nexus Forge

One app, built from the three prior attempts (Code-Collector-fixed, NexusAI-1,
Code-Organizer-fixed-1) and the three vision docs (PRODUCT_BRIEF.md,
nexus-ai-operation-script.md, code-organizer-vision.md), reconciled into a single
Jetpack Compose Android app. "Nexus Forge" is a placeholder name — rename the
`namespace`/`applicationId` in `app/build.gradle.kts` and `strings.xml` freely.

- **Visual design ported from an HTML reference** (`ui/theme/Color.kt`, `ui/markdown/Markdown.kt`):
  warm orange accent system (`#F97316`/`#FB923C` dark, darker `#EA580C` for light-mode contrast),
  near-black/near-white surfaces with border-based elevation instead of heavy shadows, code
  blocks with a macOS-style traffic-light header + pill "Copy" button, an asymmetric
  "chat-tail" shape on user bubbles, and a 2×2 suggestion-card grid for the empty chat state.

- **Edge-to-edge, seamless chrome** (`MainActivity.kt`): status bar and navigation bar draw the
  same flat background color as the rest of the app instead of a separate black strip; the
  TopAppBar's container color matches the screen background exactly so there's no visible seam.
- **Back navigation that behaves like an app, not a stack of dead ends**: any non-Chat tab goes
  back to Chat on the first press; Chat itself needs a second press within 2 seconds to actually
  exit (with a snackbar confirming), so a stray back tap never kills the app mid-conversation.
- **Streaming that shows its work without overwhelming the screen**: a rotating "thinking" phrase
  + typing-dots indicator before the first token arrives, and — while a code block is still being
  written — a capped-height, auto-scrolling "live tail" view instead of an ever-growing wall of
  text, which snaps to the normal full-size code block the moment it's done.
- **A genuinely useful Organizer screen**: phase-aware UI (thinking → writing → done/failed) with
  a paste-from-clipboard shortcut, a character counter, per-file status rows, and a success
  banner with a one-tap jump to the Workspace tab.

- **Saved provider profiles, not one global key** (`data/ProviderProfile.kt`, `data/ProviderProfileStore.kt`):
  add as many provider credentials as you need — three different Gemini keys, OpenAI, a local
  Ollama — each fully saved (Keystore-encrypted) and switchable by name from a tappable chip in
  the chat input or from Settings. No more re-pasting a key you forgot you had.
- **@ mentions for project files** (`ui/components/MentionPopup.kt`): typing `@` in the chat
  input opens a live-filtered list of every file in the active project; picking one inserts the
  path as a token, and when the message sends, that file's real content is folded into the
  prompt (`AppViewModel.expandMentions`) — the model genuinely has the file, not just its name.
- **A history sidebar** (`ui/components/HistorySidebar.kt`), modeled on the Aurora reference's
  left panel: new-chat action, live search, and the conversation list, opened via the hamburger
  icon or a swipe from the left edge.
- **A file-tree bottom sheet** (`ui/components/FileTreeBottomSheet.kt`) as an alternative to the
  full Workspace screen: reachable from a button in the chat input, it floats over Chat or
  Organizer, and tapping a file expands its content inline (tap again to collapse) instead of
  navigating away.

## What got unified

- **Per-conversation projects, not one shared global workspace** (`data/ProjectStore.kt`,
  `ui/components/ProjectPickerSheet.kt`): every chat is pointed at an explicit **Project** — a
  private Sandbox (`SandboxWorkspaceEngine`, now scoped at `filesDir/workspaces/<projectId>` so
  each sandbox project is isolated) or a real attached folder via Storage Access Framework
  (`RealFolderWorkspaceEngine`). A picker modeled on GitHub Copilot's repo selector — search,
  list, create/attach, rename/delete — lets you switch or manage projects from a chip in the
  chat input bar or from Settings, instead of every conversation silently piling files into one
  folder.
- **One agent loop** (`viewmodel/AppViewModel.kt`) drives both native tool use in Chat *and*
  the paste-a-whole-project Organizer mode — the Organizer just calls the same
  `WorkspaceEngine.writeFile` the chat agent's `write_file` tool calls.
- **Capability toggles are enforced twice**: once in which tools are even offered to the
  model (`ToolRegistry.activeTools`), and again inside `executeTool` before anything runs —
  so a jailbroken tool call can't bypass a toggle the model saw as absent.
- **API key encryption** via Android Keystore (AES/GCM) ported from Code-Collector-fixed,
  now backing every provider, and explicitly excluded from cloud/device-transfer backup.
- **Persistent, browsable chat history** (from Code-Organizer) — every session is JSON on
  disk, resumable with full context, browsable/deletable from the History tab.
- **A real markdown renderer** with per-code-block and per-reply copy buttons, adapted from
  Code-Organizer's hand-rolled renderer (the least "poor UI" thing in the batch) — no more
  raw asterisks/backticks in chat.
- **Live tool activity cards** (`ui/components/ActionCard.kt`) — running/done/error state
  visible at a glance, per the PRODUCT_BRIEF's "judge it by a glance" bar.
- **Adaptive layout** — phones get bottom-tab navigation; screens ≥840dp wide get a
  navigation rail plus chat-and-workspace side by side, so file writes are visible as they
  happen without switching tabs.

## Repo workflow files

Ported/adapted from the source repos, not newly invented:

- `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` — the actual
  wrapper jar, not just the properties file, so `./gradlew assembleDebug` runs
  standalone without Android Studio regenerating anything first.
- `.github/workflows/ci.yml` — debug APK on every push/PR; signed release APK
  on `main`, conditional on `KEYSTORE_B64`/`STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`
  secrets being set (falls back to an unsigned release build otherwise — see
  `app/build.gradle.kts`'s `hasReleaseSigning` check).
- `.gitignore`, `CONTRIBUTING.md`, `SECURITY.md` — carried over and updated
  for this project's structure and its two project-source modes.

## Known caveats — please read before opening in Android Studio

- **This was written and reviewed, but not compiled.** This environment has no Android SDK
  and no network access to fetch Gradle/AGP/dependencies, so I could not run a build or
  catch every compile error the way Android Studio's inline checker would. Open the project,
  let Gradle sync, and fix anything it flags — I'd expect small things (an import ordering
  issue, a Compose API name drift between library versions) rather than structural problems.
- **No launcher icon / branding assets** — it'll build with the default system icon until
  you add one.
- **The step-limit message** ("I've hit my step limit and stopped") is deliberately visible
  prose, not a silent failure — per the brief's requirement that the agent tell you when it's
  stuck rather than loop quietly.
- **Not implemented**: multi-provider *simultaneous* comparison (only one active provider at
  a time, switchable), in-app diff view of a file's before/after (the Workspace preview shows
  current content only, not a diff), and Git operations (none of the three source apps had
  this either).

## Structure

```
app/src/main/java/com/nexusforge/app/
  data/                    wire models, AiClient (SSE), ToolRegistry, SettingsStore
  data/workspace/          WorkspaceEngine + Sandbox/RealFolder implementations
  data/history/            ChatSession model + on-disk JSON store
  data/organizer/          project-dump-to-files engine
  ui/theme/                color, type, Material3 theme
  ui/markdown/             the markdown renderer
  ui/components/           ActionCard, FileTreeView, ChatBubble, ChatInputBar, ProviderBadge
  ui/screens/               Chat, Workspace, Organizer, History, Settings
  viewmodel/AppViewModel.kt the agent loop + all app state
  MainActivity.kt           adaptive scaffold
```
