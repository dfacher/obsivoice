# ObsiVoice

An Android app for frictionless voice notes to Obsidian.

## Features
- **One-Tap Recording**: Use the home screen widget or app button.
- **Background Recording**: Works while phone is locked or app is minimized.
- **AI Transcription**: Uses OpenAI Whisper for high-accuracy text.
- **Auto-Tagging & Summarization**: Uses GPT-4o-mini to add tags (`Product`, `Productivity`, `Self-improvement`) and a topic summary.
- **Direct to Obsidian**: Saves markdown files directly to your `Documents/df/journals` folder.

## Setup
1. **OpenAI API Key**: GET your key from https://platform.openai.com/api-keys
2. **Obsidian Folder**: The app requires access to your `Documents/df/journals` folder.
   - On first run, tap "Settings".
   - Enter your API Key.
   - Tap "Select 'journals' Folder" and navigate to `Documents > df > journals` and tap "Use this folder".

## Requirements
- Android 8.0 (Oreo) or higher.
- OpenAI API account with credits (costs ~$0.01 per note).

## Building
Open this project in Android Studio (Hedgehog or newer) and run on your device.
