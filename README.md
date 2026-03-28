<div align="center">

<br/>

```
░█████╗░███╗░░░███╗███╗░░██╗██╗██╗  ██╗░░██╗░█████╗░██╗░░░██╗░█████╗░░█████╗░██╗░░██╗
██╔══██╗████╗░████║████╗░██║██║██║  ██║░██╔╝██╔══██╗██║░░░██║██╔══██╗██╔══██╗██║░░██║
██║░░██║██╔████╔██║██╔██╗██║██║██║  █████═╝░███████║╚██╗░██╔╝███████║██║░░╚═╝███████║
██║░░██║██║╚██╔╝██║██║╚████║██║██║  ██╔═██╗░██╔══██║░╚████╔╝░██╔══██║██║░░██╗██╔══██║
╚█████╔╝██║░╚═╝░██║██║░╚███║██║██║  ██║░╚██╗██║░░██║░░╚██╔╝░░██║░░██║╚█████╔╝██║░░██║
░╚════╝░╚═╝░░░░░╚═╝╚═╝░░╚══╝╚═╝╚═╝  ╚═╝░░╚═╝╚═╝░░╚═╝░░░╚═╝░░░╚═╝░░╚═╝░╚════╝░╚═╝░░╚═╝
```

# 🛡️ OmniKavach — India's First Fully Offline Personal Safety Agent

### *Your Digital Bodyguard. Zero Cloud. Zero Compromise. Zero Fear.*

<br/>

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![AI Engine](https://img.shields.io/badge/AI-RunAnywhere%20SDK%20On--Device-FF6B35?style=for-the-badge)](https://docs.runanywhere.ai)
[![Connectivity](https://img.shields.io/badge/Network%20Dependency-ZERO-FF0000?style=for-the-badge)](.)
[![Track](https://img.shields.io/badge/Track-PS%20%232%20Offline--First-6C63FF?style=for-the-badge)](.)
[![Privacy](https://img.shields.io/badge/Data%20Sent%20to%20Cloud-NONE-00C853?style=for-the-badge)](.)

<br/>

> **"Safety apps fail the moment you need them most — when the network dies."**
> OmniKavach was built to solve *that* exact problem. Every AI model, every alert, every decision happens entirely on your phone.

<br/>

---

</div>

## 📌 The Problem Nobody Is Solving Correctly

India reports **over 1.5 lakh cyber crimes** and thousands of physical safety incidents annually. Yet every "safety app" on the market has a fatal flaw:

| Common Safety App | OmniKavach |
|---|---|
| ❌ Requires internet to work | ✅ Works in full Airplane Mode |
| ❌ Sends your location to servers | ✅ All GPS stays on-device |
| ❌ Cloud AI = privacy risk | ✅ SmolLM2 runs locally via RunAnywhere SDK |
| ❌ One SOS button = one attack vector | ✅ Voice, shake, volume key, timer — 4 stealth triggers |
| ❌ No network = no help | ✅ Disconnected network = still full functionality |
| ❌ Relies on user action during attack | ✅ Autonomous dead-man switch acts *for* you |

**OmniKavach is not a safety app. It is a safety *agent*.**

---

## 🎯 Hackathon Track

### 🥇 Problem Statement #2 — Offline-First Mobile Experiences

> *"Create a mobile app designed to work flawlessly without internet connectivity, with AI models running entirely on the device."*

**Our answer:** Every single feature — AI chat, SOS dispatch, crime map, cyber scanner, dead-man timer — works with Wi-Fi OFF, Mobile Data OFF, and Airplane Mode ON. The **RunAnywhere SDK** makes this possible — LLM inference runs 100% on-device, zero cloud dependency.

---

## ✨ Feature Deep Dive

### 🤖 1. Kavach AI — The Offline Emergency Advisor

> *The first Hinglish-speaking, fully offline AI safety advisor for Indian users — powered by RunAnywhere SDK.*

```
User  → "Mere phone pe OTP aaya, kisi ne call karke maanga — kya karu?"

Kavach AI → [SmolLM2-360M running locally via RunAnywhere SDK, Wi-Fi OFF] →
           "Yeh 100% scam hai bhai! Koi bhi genuine company/bank 
            OTP nahi maangti. Turant phone kaato. National Cyber 
            Crime helpline 1930 pe call karo. OTP KABHI share mat karo."
           
           → Response generated entirely on-device. Zero network used.
```

**Technical Implementation:**
- **SDK:** RunAnywhere SDK v0.20.6 — `runanywhere-sdk-android` + `runanywhere-llamacpp-android`
- **Model:** `SmolLM2-360M-Instruct-Q8_0` (GGUF format) — downloaded once via WiFi, runs forever offline
- **Backend:** `LlamaCPP.register()` — llama.cpp native backend for GGUF inference
- **Manager:** `KavachAiManager.kt` — Kotlin coroutine-based wrapper, handles download → load → chat lifecycle
- **Download:** `RunAnywhere.downloadModel()` with Kotlin Flow progress tracking
- **Inference:** `RunAnywhere.chat()` — suspend function, runs on `Dispatchers.IO`
- **System Prompt:** Tuned for Indian emergencies — Police (100), Ambulance (108), Women's Helpline (1091), Universal (112), Cyber Crime (1930) always in context
- **Voice Input:** Android `RecognizerIntent` — on-device, no cloud call
- **Privacy:** Model runs in `context.filesDir` — user data never leaves device

📁 *See: [`OfflineAiActivity.java`](app/src/main/java/com/example/rakshak360/OfflineAiActivity.java), [`KavachAiManager.kt`](app/src/main/java/com/example/rakshak360/KavachAiManager.kt)*

---

### ⏱️ 2. Chronos Protocol — Autonomous Dead-Man Switch

> *An offline agent that protects you even when you are incapacitated.*

**The Logic:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                     CHRONOS PROTOCOL FLOW                           │
│                                                                     │
│  User enables timer → Sets safe return window (e.g., 45 min)       │
│         │                                                           │
│         ▼                                                           │
│  Agent monitors silently in background (LockService foreground)     │
│         │                                                           │
│         ├─── User checks in before time ──────────► Timer resets ✅ │
│         │                                                           │
│         └─── Timer expires, no check-in detected                   │
│                     │                                               │
│                     ▼                                               │
│         [AUTONOMOUS SEQUENCE — NO INTERNET NEEDED]                  │
│         ├── 📸 Front camera captures intruder selfie (Camera API)   │
│         ├── 🎤 Background audio recording starts (MediaRecorder)    │
│         ├── 📍 GPS coordinates captured (LocationManager satellite) │
│         ├── 📱 SMS sent to all guardians (SmsManager via carrier)   │
│         └── 🔊 Siren triggers via RingtoneManager                  │
└─────────────────────────────────────────────────────────────────────┘
```

**Why this works fully offline:**
- `CountDownTimer` runs entirely in-process — no server polling
- `LockService` is a foreground service with `START_STICKY` — survives app kill
- `BootReceiver` listens for `ACTION_BOOT_COMPLETED` — auto-restarts after reboot
- `SmsManager` dispatches SMS directly via cellular carrier — no internet required
- GPS via `LocationManager` works on satellite signal — no Wi-Fi or data needed

📁 *See: [`MainActivity.java`](app/src/main/java/com/example/rakshak360/MainActivity.java), [`LockService.java`](app/src/main/java/com/example/rakshak360/LockService.java), [`BootReceiver.java`](app/src/main/java/com/example/rakshak360/BootReceiver.java)*

---

### 🗺️ 3. Safety Genesis — Crime Intelligence Map

> *Navigate by safety, not speed. Offline crime-aware routing for Jaipur.*

**Data Architecture:**
- Pre-bundled SQLite database (`assets/databases/crime_data.db`) loaded via Room ORM singleton
- Schema: `Crime_ID → Date → Area → Latitude → Longitude → Police_Station → Crime_Type → Severity`
- All queries execute on-device via Room DAO — **zero API calls at any point**

**Intelligent Routing Engine:**
```
User sets destination → Room DAO queries local crime DB →
Calculates two route options:
  [FASTEST]  → Standard shortest path
  [SAFEST]   → Avoids Red Zones (Severity ≥ 4) + prioritizes
               routes within 1.5km radius of Police Stations

User sees live crime heatmap on Google Maps:
  🟢 Green circles  → Safe zones  (Severity 1–2)
  🟡 Yellow circles → Caution     (Severity 3)
  🔴 Red circles    → Danger      (Severity 4–5)
```

📁 *See: [`SafetyMapActivity.java`](app/src/main/java/com/example/rakshak360/SafetyMapActivity.java), [`AppDatabase.java`](app/src/main/java/com/example/rakshak360/AppDatabase.java)*

---

### 🔍 4. Aegis Cyber Scanner — 5-Sensor Threat Detection

> *Five hardware sensors running simultaneously. Fully on-device. No cloud.*

| Sensor | Android API Used | What It Detects |
|--------|-----------------|-----------------|
| 👁️ **Spy Lens Detector** | `Camera` API + ML Kit `ImageLabeler` | Hidden cameras via IR glint |
| 📡 **EMF Anomaly Monitor** | `SensorManager` (Magnetic Field) | EM spikes from surveillance hardware |
| 🔵 **BLE Rogue Scanner** | `BluetoothLeScanner` | Unknown Bluetooth LE devices nearby |
| 🌐 **Network Sentinel** | `WifiManager` + `NsdManager` | Rogue access points on local network |
| 🎤 **Audio Anomaly Monitor** | `AudioRecord` (PCM 16-bit) | Ambient sound pattern analysis |

📁 *See: [`CyberScanActivity.java`](app/src/main/java/com/example/rakshak360/CyberScanActivity.java), [`DeviceProfiler.java`](app/src/main/java/com/example/rakshak360/DeviceProfiler.java)*

---

### 🕵️ 5. Ghost Mode — Stealth Identity Switch

> *When your phone must not look like yours.*

`GhostClickService` extends Android `AccessibilityService`. On a single trigger, it autonomously navigates the device UI to a guest/secondary user profile — no internet, no server, pure on-device automation.

📁 *See: [`GhostClickService.java`](app/src/main/java/com/example/rakshak360/GhostClickService.java)*

---

## 🏗️ Technical Architecture

```
┌───────────────────────────────────────────────────────────────────────┐
│                        OmniKavach Architecture                        │
│                                                                       │
│  ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐ │
│  │   Kavach AI      │  │ Chronos Protocol│  │ Safety Genesis Map   │ │
│  │                  │  │                 │  │                      │ │
│  │ RunAnywhere SDK  │  │ CountDownTimer  │  │ Room ORM + SQLite    │ │
│  │ KavachAiManager  │  │ LockService     │  │ Google Maps SDK      │ │
│  │ SmolLM2-360M     │  │ BootReceiver    │  │ Local crime_data.db  │ │
│  │ llama.cpp GGUF   │  │ SmsManager      │  │ FusedLocationProvider│ │
│  └────────┬─────────┘  └───────┬─────────┘  └──────────┬───────────┘ │
│           │                    │                        │             │
│  ┌────────▼────────────────────▼────────────────────────▼──────────┐  │
│  │                      Core Device Layer                          │  │
│  │  Camera API  │  SensorManager  │  AudioRecord  │  WifiManager   │  │
│  │  SmsManager  │  LocationManager│  MediaRecorder│  NsdManager    │  │
│  │  BluetoothLeScanner  │  Vibrator  │  AccessibilityService       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │                  On-Device AI (RunAnywhere SDK)                 │  │
│  │  SmolLM2-360M GGUF Q8    │  LlamaCPP backend (llama.cpp)        │  │
│  │  ONNX Runtime            │  All inference in-process, zero cloud│  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                       │
│  ☁️  CLOUD SERVICES USED FOR CORE FEATURES: NONE                     │
└───────────────────────────────────────────────────────────────────────┘
```

### Complete Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java + Kotlin (Android Native) |
| **Local LLM** | RunAnywhere SDK v0.20.6 — SmolLM2-360M-Instruct GGUF Q8 |
| **LLM Backend** | `runanywhere-llamacpp-android` — llama.cpp native |
| **AI Manager** | `KavachAiManager.kt` — Kotlin Coroutines + Flow |
| **Vision AI** | Google ML Kit `ImageLabeler` — on-device model |
| **Database** | Room ORM + SQLite (pre-bundled `crime_data.db`) |
| **Maps** | Google Maps Android SDK |
| **Background** | Foreground Service (`LockService`) + `START_STICKY` |
| **Boot Survival** | `BroadcastReceiver` — `ACTION_BOOT_COMPLETED` |
| **Location** | `FusedLocationProviderClient` + satellite GPS (offline) |
| **SMS Dispatch** | Android `SmsManager` (carrier-based, no internet) |
| **Sensors** | `SensorManager` — Accelerometer + Magnetic Field |
| **Network Scan** | `WifiManager` + `BluetoothLeScanner` + `NsdManager` |
| **Stealth Switch** | `AccessibilityService` (`GhostClickService`) |

---

## 🚀 The Offline-First Proof

```
Step 1 → Install OmniKavach APK
Step 2 → Open app on WiFi → Kavach AI downloads SmolLM2 model (~360MB, one time only)
Step 3 → Turn ON Airplane Mode — disable ALL connectivity
Step 4 → Open "Kavach AI"
Step 5 → Ask: "Mujhe raat ko akele ghar jaana hai, kya savadhani rakhu?"
Step 6 → SmolLM2 generates a complete Hinglish safety response via RunAnywhere SDK

         ✅ Zero network packets sent
         ✅ Zero API calls made
         ✅ Full AI response generated entirely on-device

Step 7 → Open Crime Map → GPS locks via satellite, crime heatmap loads
          from local Room database. No internet. Full intelligence.

Step 8 → Enable Chronos timer for 1 minute. Don't check in.
          Watch autonomous SOS trigger — SMS dispatched via carrier.

         ✅ All 3 steps work in complete Airplane Mode.
```

---

## 🛠️ Setup & Installation

### Prerequisites
- Android Studio Hedgehog or later
- Android device — API 26+ (Android 8.0+), minimum 2GB RAM
- ~500MB free storage for SmolLM2 model
- WiFi for first-time model download only

### Steps

**1. Clone**
```bash
git clone https://github.com/Aayushi2024/OmniKavach-360.git
cd OmniKavach-360
```

**2. Add keys to `local.properties`**
```properties
MAPS_API_KEY=your_google_maps_api_key
```

**3. Build and install**
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**4. First launch**
- Connect to WiFi
- Open app → Kavach AI screen → Model downloads automatically (~360MB)
- After download: works 100% offline forever

**5. Grant permissions on first launch:** Location, Camera, Microphone, SMS, Contacts

---

## 📁 Project Structure

```
app/src/main/java/com/example/rakshak360/
│
├── MainActivity.java          # Core hub: SOS triggers, Chronos timer,
│                              # shake detection, stealth camera + audio
│
├── OfflineAiActivity.java     # Kavach AI UI: chat interface, voice input,
│                              # progress display, RecyclerView chat
│
├── KavachAiManager.kt         # RunAnywhere SDK wrapper: model download,
│                              # load, chat — fully offline LLM manager
│
├── SafetyMapActivity.java     # Crime map: Room DAO queries, safe/fast
│                              # route logic, CircleOptions heatmap
│
├── CyberScanActivity.java     # 5-sensor scanner: ML Kit, SensorManager,
│                              # BLE, NsdManager, AudioRecord
│
├── LockService.java           # Foreground service: agent persistence,
│                              # screen-unlock relaunch, START_STICKY
│
├── GhostClickService.java     # AccessibilityService: autonomous
│                              # guest mode navigation
│
├── BootReceiver.java          # Reboot survival: ACTION_BOOT_COMPLETED
│
├── AppDatabase.java           # Room singleton: loads crime_data.db asset
│
├── CrimeEntity.java           # @Entity: crime table schema
│
└── DeviceProfiler.java        # RAM tier detection (Low/Mid/High)
```

---

## 🔒 Privacy Architecture

```
Data Category            Where It Goes           Server Involved
──────────────────────────────────────────────────────────────────
AI conversations         Device RAM only          None
LLM model                Device storage only      None (downloaded once)
GPS location             Device only              None
Crime DB queries         Local SQLite             None
SOS photos & audio       Device storage + SMS*    None
Guardian contacts        SharedPreferences         None

* SMS travels phone → carrier → guardian. No app server involved.
```

---

## 🌟 Why RunAnywhere SDK?

Most hackathon projects wrap a cloud API. We run a **real LLM on Android**:

- **RunAnywhere SDK** provides production-grade on-device LLM inference via llama.cpp
- GGUF quantized models keep SmolLM2 under 400MB while maintaining quality
- Kotlin Coroutine + Flow based API — clean async with no UI blocking
- `KavachAiManager.kt` handles the full lifecycle: register → download → load → chat
- Model stored in `context.filesDir` — private, secure, never synced to cloud

---

## 🔮 Roadmap

- [ ] Multilingual — Tamil, Telugu, Bengali, Marathi
- [ ] Offline map tile caching
- [ ] Larger model option (SmolLM2-1.7B) for high-end devices
- [ ] Streaming token output for real-time response display
- [ ] P2P crime report sync via Bluetooth mesh
- [ ] Smartwatch companion — wrist gesture SOS trigger

---

<div align="center">

**🛡️ OmniKavach — Every Indian deserves a bodyguard. Now everyone has one.**

<br/>

*Built for PS #2: Offline-First Mobile Experiences*

*Powered by RunAnywhere SDK — AI runs on your phone, not in the cloud.*

*Built with ❤️ for Bharat. Runs offline. Protects always.*

</div>
