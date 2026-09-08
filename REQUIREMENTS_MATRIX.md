# Requirements → Implementation Matrix

| Requirement / design intent | Implementation | Status |
|---|---|---|
| One-thumb mobile coding assistant | Compose chat-first root, compact composer, bottom navigation | Implemented |
| Live response, not spinner | OkHttp SSE stream + streaming state cards | Implemented |
| Visible agent work | Activity feed + live tool-call buffer + progress | Implemented |
| Clean human-readable Markdown | Dedicated Markdown parser with headings, lists, quotes, inline formatting and code blocks | Implemented |
| Copy whole response | Message-level copy button | Implemented |
| Copy only code | Code-block copy button | Implemented |
| User text selectable/copyable | SelectionContainer around message bodies | Implemented |
| Large composer stays usable | Height-limited scrolling BasicTextField | Implemented |
| Project import | OpenDocumentTree then copy into private workspace | Implemented |
| Agent never sees external folder | Only private WorkspaceEngine paths are exposed to tools | Implemented |
| Read/write/list tools | OpenAI-compatible function tools | Implemented |
| Rename/delete request from natural language | `rename_file` and `delete_file` agent tools | Implemented |
| ZIP tool | Native java.util.zip under private exports directory | Implemented |
| Capability switches authoritative | Tool omission + execution-time blocking | Implemented |
| Eight-round safety cap | Agent loop exits after eight rounds | Implemented |
| No silent failures | UI error state + activity failure + snackbar | Implemented |
| Workspace source of truth | Disk-backed file tree refreshed after operations | Implemented |
| Session-scoped diff | In-memory diff list reset on new/load session | Implemented |
| Real line deltas | Prefix/suffix line comparison | Implemented |
| File preview / rename / delete | Workspace inspector actions | Implemented |
| Share ZIP safely | FileProvider content URI | Implemented |
| Conversation survives app restart | Local conversation store | Implemented |
| Tool-using history can resume | Stored OpenAI wire history in each session | Implemented |
| Provider switch without wiping chat | Session metadata stores provider/base URL/model; provider profiles keep keys/models | Implemented |
| API keys encrypted | Android Keystore AES/GCM | Implemented |
| Blank API-key field preserves existing key | Nullable save argument; only nonblank field replaces key | Implemented |
| Workspace/export excluded from backup | Backup and data-extraction rules | Implemented |
| Local Ollama support | Cleartext allowlist + emulator URL preset | Implemented |
| Gemini / DeepSeek / arbitrary compatible gateways | Provider presets plus custom Base URL | Implemented |
| Wider-screen dual-pane | Chat + Workspace side-by-side at 700dp+ | Implemented |
| Paste-whole-project organizer mode | Chat's sibling Paste & Organize mode, streaming JSON manifest, review, write | Implemented |
| Useful project dotfiles preserved | Import skips generated/meta directories, not all dotfiles | Implemented |
| CI validation | GitHub Actions debug build + unit test workflow | Implemented |
| Release automation | GitHub Actions release workflow with optional signing | Implemented |
| Sandbox path policy is testable | Dedicated WorkspacePathPolicy + JUnit tests | Implemented |

## Deliberate reconciliation of source branches

The supplied materials contained multiple app generations. The unified implementation keeps the stronger private-sandbox model from the Nexus branch and the richer agent/history/organizer ideas from the Code Organizer branch. The operation script's four-tab model remains authoritative; Paste & Organize is exposed as a sibling mode inside Chat instead of becoming a fifth navigation tab.

The core network contract remains streaming OpenAI-compatible `/chat/completions`, as specified by the Nexus operation script. Provider presets therefore target compatible gateways; the alternate branch's separate native Anthropic/Gemini client stack was not mixed into the core wire protocol because doing so would introduce a second request/history/tool schema without being required by the operation script.
