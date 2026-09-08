# Contributing to Nexus AI

## Development

1. Open the project in Android Studio.
2. Use JDK 17.
3. Use the Gradle version declared by CI (8.9).
4. Never commit API keys, keystores, passwords, or `local.properties`.
5. Keep workspace operations confined to the app's private workspace directory.

## Validation

Run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The GitHub Actions workflow performs the same build and test validation.
