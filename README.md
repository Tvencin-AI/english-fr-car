# 🇬🇧 English Drive

**Learn British English hands-free while driving — powered by Google Gemini AI**

---

## What is this?

English Drive is an Android app that turns your driving time into English lessons.
A virtual teacher named **Emma** — a native British English speaker from London — 
teaches you through conversation, exercises, and instant corrections.

The entire experience is **100% voice-controlled**. No touching the screen while driving.

Works on your **phone screen** and on your **car dashboard via Android Auto**.

---

## Features

| Feature | Detail |
|---|---|
| 🎙️ 100% Voice | Speak, listen, learn — no typing ever |
| 🇬🇧 British English | Emma uses British spelling, expressions, and accent |
| 🤖 Gemini AI | Powered by Google's Gemini 1.5 Flash |
| 📶 7 Levels | A1 Beginner → C2 Mastery (full CEFR scale) |
| 🚗 Android Auto | Full dashboard integration |
| 🔁 Adaptive | Emma adjusts to your level and corrects your mistakes |
| 📖 Contextual | Emma remembers your conversation and builds on it |

---

## Levels

| Level | CEFR | Description |
|---|---|---|
| Beginner | A1 | First words — greetings, numbers, colours |
| Elementary | A2 | Daily life — family, food, shopping |
| Pre-Intermediate | B1 | Travel, hobbies, simple opinions |
| Intermediate | B1+ | Fluency focus, phrasal verbs, conditionals |
| Upper-Intermediate | B2 | Complex topics, debates, nuance |
| Advanced | C1 | Sophisticated vocabulary, culture, humour |
| Mastery | C2 | Near-native — rare vocabulary, literary references |

---

## Setup

### Step 1 — Get a Gemini API Key (FREE)

1. Go to **https://aistudio.google.com**
2. Sign in with your Google account
3. Click **"Get API key"** → **"Create API key"**
4. Copy the key (it starts with `AIza...`)

### Step 2 — Build the App

1. Install **Android Studio** (https://developer.android.com/studio)
2. Open the `EnglishDrive` folder in Android Studio
3. Wait for Gradle to sync (first time takes a few minutes)
4. Connect your Android phone via USB (or use an emulator)
5. Click ▶ **Run**

### Step 3 — Enter Your API Key

When the app opens, it will ask for your Gemini API key.
Paste the key you copied in Step 1 and tap **Save**.

Your key is stored **only on your device** — it never leaves your phone.

### Step 4 — Start Learning!

**On your phone:** Tap **▶ Start Lesson** and speak to Emma.

**In your car:** Connect your phone to Android Auto, find **English Drive** in the app list, 
and tap **Start** on the dashboard screen.

---

## How It Works

```
You connect to Android Auto
        ↓
Tap "Start" on dashboard
        ↓
Emma speaks (British TTS) → "Hello! I'm Emma. Let's begin..."
        ↓
Microphone opens automatically 🎤
        ↓
You speak in English
        ↓
Your speech is transcribed
        ↓
Gemini AI processes your reply (checks grammar, vocabulary)
        ↓
Emma corrects you if needed + continues the lesson
        ↓
Loop repeats — fully automatic
```

---

## Android Auto Notes

- Android Auto requires a phone with **Android 6.0+**
- Some car manufacturers require **Android Auto** to be enabled in settings
- The app appears in the **Apps** section of Android Auto
- Voice interaction starts automatically once you press Start

---

## Permissions Required

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | To hear you speak |
| `INTERNET` | To contact the Gemini API |

---

## Project Structure

```
EnglishDrive/
├── app/src/main/java/com/englishdrive/
│   ├── MainActivity.kt              ← Phone screen UI
│   ├── car/
│   │   ├── EnglishDriveCarAppService.kt  ← Android Auto entry point
│   │   ├── LearningCarSession.kt         ← Auto session
│   │   └── LearningCarScreen.kt          ← Dashboard screen
│   ├── learning/
│   │   ├── GeminiManager.kt         ← Gemini API + system prompt
│   │   ├── LevelManager.kt          ← 7 CEFR levels
│   │   └── LearningSession.kt       ← Voice learning loop
│   └── speech/
│       └── SpeechManager.kt         ← British TTS + Speech Recognition
├── res/
│   ├── layout/activity_main.xml
│   └── xml/automotive_app_desc.xml
└── AndroidManifest.xml
```

---

## Troubleshooting

**"Please add your Gemini API key"**
→ Tap the 🔑 Key button and paste your key from aistudio.google.com

**"Speech recognition not available"**
→ Your device needs Google app installed with voice search enabled

**Emma's voice sounds robotic / not British**
→ Install the **Google Text-to-Speech** engine on your phone.
  Go to Settings → Accessibility → Text-to-Speech and select Google TTS with `en-GB`.

**App not showing in Android Auto**
→ Make sure Android Auto is installed and updated.
  Check that "Unknown sources" apps are allowed in Android Auto developer settings.

**"Network error"**
→ Check your internet connection. The Gemini API requires internet access.

---

## Privacy

- Your conversations are sent to Google's Gemini API to generate responses.
- Your API key is stored locally on your device only.
- No data is collected by this app itself.
- See Google's privacy policy for Gemini API usage.

---

*Built with ❤️ — Emma is waiting to teach you!*
