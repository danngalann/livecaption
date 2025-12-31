# LiveCaption

A simple, real-time transcription app for Android designed to help people with hearing difficulties read what's being said in their surroundings.

## Motivation

LiveCaption was created to help family members with hearing difficulties actively participate in conversations and social gatherings. By providing real-time captions of audio from the surrounding environment, the app enables better communication and inclusion.

## Features

- **Real-time Transcription**: Live transcription of audio using the ElevenLabs API via WebSocket
- **Simple Interface**: Large, easy-to-read text display (40sp font size) with auto-scrolling to the latest caption
- **No History or Data Retention**: All transcriptions are temporary—close the app or restart to clear them
- **No Ads or Tracking**: Completely ad-free with zero analytics or user tracking
- **Always-On Display**: Screen stays on while the app is running for continuous viewing
- **Privacy-Focused**: Audio is sent only to ElevenLabs API; no local storage of transcriptions
- **Minimal Dependencies**: Built with modern Android libraries (Jetpack Compose, OkHttp, Kotlin Coroutines)

## Installation

### Prerequisites

- Android 7.0 (API 24) or higher
- An active internet connection
- An ElevenLabs API key
- Microphone and internet permissions granted

### Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/danngalann/livecaption.git
   cd livecaption
   ```

2. **Configure ElevenLabs API Key**:
   - Create a `local.properties` file in the project root (if it doesn't exist)
   - Add your ElevenLabs API key:
     ```
     ELEVENLABS_API_KEY=your_api_key_here
     ```

3. **Build and run**:
   ```bash
   ./gradlew assembleDebug
   ```
   Or open the project in Android Studio and click **Run**.

## Usage

1. **Launch the app**: Open LiveCaption on your Android device
2. **Grant permissions**: Allow microphone and internet permissions when prompted
3. **Start listening**: Tap the "Escuchar" (Listen) button to begin real-time transcription
4. **View captions**: Watch transcriptions appear in real-time on the screen
   - Partial (in-progress) text appears as you speak
   - Final (committed) text is added to the history when a sentence is complete
5. **Stop listening**: Tap the "Detener" (Stop) button to end transcription

## Building and Deployment

### Debug Build

```bash
./gradlew assembleDebug
```

The APK will be available at `app/build/outputs/apk/debug/app-debug.apk`.

### Release Build

```bash
./gradlew assembleRelease
```

### Requirements

- Android Gradle Plugin 8.13.2
- Kotlin 2.0.21
- Gradle 8.0 or higher
- Java 11+

## Permissions

The app requires the following permissions (declared in `AndroidManifest.xml`):

- `RECORD_AUDIO` - Access to the device microphone
- `INTERNET` - Connection to ElevenLabs API

## License

This project is licensed under the MIT License—see the [LICENSE](LICENSE) file for details.

## Disclaimer

This app is provided as-is for accessibility purposes. Please note that:

- Real-time transcription depends on ElevenLabs API quality and availability
- Network latency may affect transcription speed
- Background noise and audio quality will impact transcription accuracy
