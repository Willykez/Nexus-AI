# Security

Please do not report security vulnerabilities in public issues.

Remove API keys, access tokens, private URLs, signing credentials, and
keystore material from bug reports and logs before sharing them.

## How Nexus Forge handles sensitive data

- Provider API keys are stored using Android Keystore-backed AES/GCM
  encryption (`SettingsStore`), and the settings file they live in is
  explicitly excluded from cloud backup and device transfer
  (`data_extraction_rules.xml`, `backup_rules.xml`).
- **Sandbox mode** confines every file operation to a private,
  app-only directory (`SandboxWorkspaceEngine`) with canonical-path
  checks against directory traversal.
- **Attached-folder mode** grants the agent write access to a real,
  user-picked folder via the Storage Access Framework. This is a real,
  permanent change surface by design — do not treat path-safety checks
  in `RealFolderWorkspaceEngine` as a sandbox; they prevent traversal
  outside the attached tree, not damage within it. Do not hard-code
  provider credentials into source code or Gradle files.
