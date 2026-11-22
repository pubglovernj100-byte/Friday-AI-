# FRIDAY AI — Android Starter Project

FRIDAY is a personal Android assistant starter app (Kotlin + Jetpack Compose).

## Included in this starter
- Dark futuristic chat UI
- Hindi speech recognition through Android's recognizer (tap mic)
- Text-to-speech replies (Hindi locale when available)
- Chat history stored locally on device
- API key settings dialog
- AI chat through OpenAI Chat Completions API (`gpt-4o-mini`)
- Basic intents: Camera, Dialer, Settings, YouTube

## Requirements
- Android Studio (recent version)
- Android SDK 35 installed
- Android device or emulator, Android 8.0+ (API 26)
- An OpenAI API key and API billing/credits for AI chat

## Build / install
1. Extract this ZIP.
2. Open the `FRIDAY_AI` folder in Android Studio.
3. Let Gradle sync finish. If prompted, install Android SDK 35.
4. Connect an Android phone with USB debugging enabled (or start an emulator).
5. Press **Run**.
6. In the app, tap Settings and enter your API key. Save it.
7. Tap the microphone and speak Hindi, or type a message.

## API key safety
The key is stored in app preferences on the device. This is a personal-use prototype only. Do not distribute this APK with your API key saved. For a public release, move API calls behind a secure backend and add authentication/rate limits.

## Limitations (current prototype)
- No always-on wake word/background listening yet.
- No guaranteed live web search; this version uses a chat endpoint only.
- Phone actions are limited to launching supported intents and require user interaction/Android permissions as applicable.
- Android speech recognition may require Google speech services and internet.
- This environment does not have Android SDK/Gradle available to compile and test an APK. The source project is packaged for opening/building in Android Studio.

## Local memory commands (Build 2 prototype)
- Say/type `Yaad rakho ...` or `Remember that ...` to save a note locally.
- `Meri memory dikhao` shows saved notes.
- `Memory clear` removes the saved notes.
- Saved notes are stored in app preferences on this phone and are added to the AI context when chatting. They are not synced to a cloud account.

## Important
This update is source-only and has not been compiled/tested in an Android SDK environment. Live web search, wake-word always-on listening, and full device-control automations remain future features; do not treat them as implemented.
