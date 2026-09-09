# Contributing to Nexus Forge

## Development

1. Open the project in Android Studio (Koala or newer) and let Gradle sync.
2. Use JDK 17.
3. Use the Gradle version declared by CI (8.9) — the wrapper already pins this.
4. Never commit API keys, keystores, passwords, or `local.properties`.
5. Keep Sandbox-mode workspace operations confined to `WorkspaceEngine`'s own
   path-safety checks — don't bypass `SandboxWorkspaceEngine.safe()` with raw
   `File` access elsewhere in the codebase.
6. Any new native tool the agent can call must be added in both
   `ToolRegistry.activeTools()` (what the model is told about) and
   `AppViewModel.executeTool()` (what actually runs), and must respect the
   relevant `CapabilityFlags` toggle in both places.

## Validation

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The GitHub Actions workflow (`.github/workflows/ci.yml`) performs the same
build validation on every push and pull request. There are no unit tests
included yet — `testDebugUnitTest` will currently just pass trivially; adding
real coverage for `WorkspaceEngine` path-safety and the agent loop's
tool-call parsing would be a good first contribution.
