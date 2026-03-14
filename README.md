# AllySong – Decentralized Edge-AI Disaster Response Agent

A Kotlin Multiplatform (KMP) prototype that combines **on-device machine learning** with **BLE mesh networking** to create an offline, peer-to-peer survival network for multi-hazard disaster response (earthquake + typhoon).

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        :androidApp                              │
│  MainActivity            Single-Activity host + permissions     │
│  AllySongApplication     App-scoped DI + service bridge         │
│  AllySongService         Foreground service (sensor keepalive)  │
│  DisasterOverlayManager  Floating chat-head disaster alert      │
├─────────────────────────────────────────────────────────────────┤
│                     :shared (KMP Module)                        │
│                                                                 │
│  ┌─────────────── commonMain ──────────────────────────────┐    │
│  │  domain/model/     AccelerometerReading, BarometerReading│    │
│  │                    DisasterEvent, MeshMessage, PeerDevice│    │
│  │                    NotificationEvent                     │    │
│  │  domain/sensor/    SensorController, BarometerController │    │
│  │  domain/mesh/      MeshNetworkController interface       │    │
│  │  domain/ai/        DisasterDetectionEngine interface     │    │
│  │                    TyphoonDetectionEngine interface       │    │
│  │  domain/crypto/    PayloadEncryptor interface             │    │
│  │  viewmodel/        AllySongViewModel (state machine)      │    │
│  │  ui/screens/       MainDashboard, HITL, Communication,   │    │
│  │                    ManualSOS screens                      │    │
│  │  ui/navigation/    StateFlow-driven navigation            │    │
│  │  ui/theme/         Dark emergency theme                   │    │
│  └──────────────────────────────────────────────────────────┘    │
│  ┌─────────────── androidMain ──────────────────────────────┐    │
│  │  AndroidSensorController    (SensorManager + callbackFlow)│   │
│  │  AndroidBarometerController (pressure sensor + callbackFlow)│ │
│  │  AndroidMeshController      (Nearby Connections API)      │   │
│  │  AndroidDisasterDetection   (TFLite + STA/LTA heuristic)  │  │
│  │  HeuristicTyphoonDetection  (linear regression + PAGASA)   │  │
│  │  AesGcmPayloadEncryptor     (AES-256-GCM)                │   │
│  │  AndroidServiceLocator      (Manual DI)                   │   │
│  └──────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│                    DUAL SENSOR PIPELINE                           │
├──────────────────────────┬───────────────────────────────────────┤
│   Accelerometer (50 Hz)  │   Barometer (5 Hz, if available)     │
│          │               │          │                            │
│          ▼               │          ▼                            │
│   SensorController       │   BarometerController                │
│   .startMonitoring()     │   .startMonitoring()                 │
│          │               │          │                            │
│   Flow<Accelerometer     │   Flow<BarometerReading>             │
│         Reading>         │          │                            │
│          │               │          ▼                            │
│   Sliding Window         │   Sliding Window                     │
│   (50 samples, 50%       │   (60 samples, 50%                   │
│    overlap)              │    overlap)                           │
│          │               │          │                            │
│          ▼               │          ▼                            │
│   DisasterDetection      │   TyphoonDetection                   │
│   Engine.analyze()       │   Engine.analyze()                   │
│   ├─ TFLite model        │   └─ Linear regression               │
│   └─ STA/LTA heuristic   │      (PAGASA threshold)              │
│          │               │          │                            │
└──────────┴───────┬───────┴──────────┘                            │
                   │                                               │
                   ▼                                               │
         DisasterEvent detected? ──No──→ Continue monitoring       │
                   │ Yes                                           │
                   ▼                                               │
         ┌─ Activity visible? ─────────────────────────────┐       │
         │ YES                          NO                  │       │
         ▼                              ▼                   │       │
  HITL Validation Screen     Floating Overlay (chat-head)   │       │
  (60-second countdown)      + Notification via Service     │       │
         │                              │                   │       │
         ├── "I'M SAFE"  → Dismiss      │                   │       │
         └── "NEED HELP" / Timer        │                   │       │
                   │ ◄──────────────────┘                   │       │
                   ▼                                               │
         MeshNetworkController.broadcastSos()                      │
                   │                                               │
                   ▼                                               │
         BLE Mesh Cascade (Nearby Connections, P2P_CLUSTER)        │
          ├── Peers re-broadcast with hopCount++ (max 5 hops)      │
          └── Receiving devices unlock Communication Mode          │
└──────────────────────────────────────────────────────────────────┘
```

## Module Structure

```
AllySong/
├── build.gradle.kts              # Root: plugin versions
├── settings.gradle.kts           # Module declarations
├── gradle.properties             # KMP + Compose flags
├── shared/                       # KMP shared module
│   ├── build.gradle.kts          # KMP + Compose MP + TFLite + Nearby
│   └── src/
│       ├── commonMain/kotlin/com/allysong/
│       │   ├── domain/
│       │   │   ├── model/        # AccelerometerReading, BarometerReading,
│       │   │   │                 # DisasterEvent, MeshMessage, PeerDevice,
│       │   │   │                 # NotificationEvent
│       │   │   ├── sensor/       # SensorController, BarometerController
│       │   │   ├── mesh/         # MeshNetworkController interface
│       │   │   ├── ai/           # DisasterDetectionEngine,
│       │   │   │                 # TyphoonDetectionEngine interfaces
│       │   │   └── crypto/       # PayloadEncryptor interface
│       │   ├── viewmodel/        # AllySongViewModel
│       │   └── ui/
│       │       ├── screens/      # MainDashboardScreen, HITLScreen,
│       │       │                 # CommunicationModeScreen, ManualSOSScreen
│       │       ├── navigation/   # AllySongApp (state-driven router)
│       │       └── theme/        # AllySongTheme (dark emergency palette)
│       └── androidMain/kotlin/com/allysong/
│           ├── domain/
│           │   ├── sensor/       # AndroidSensorController,
│           │   │                 # AndroidBarometerController
│           │   ├── mesh/         # AndroidMeshController
│           │   ├── ai/           # AndroidDisasterDetectionEngine,
│           │   │                 # HeuristicTyphoonDetectionEngine
│           │   └── crypto/       # AesGcmPayloadEncryptor
│           └── di/               # AndroidServiceLocator
└── androidApp/                   # Android shell
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml   # BLE + sensor + overlay permissions
        ├── java/com/allysong/
        │   ├── MainActivity.kt           # Single-Activity host
        │   ├── AllySongApplication.kt    # App-scoped DI + service bridge
        │   ├── AllySongService.kt        # Foreground service
        │   └── DisasterOverlayManager.kt # Floating alert overlay
        └── res/values/themes.xml
```

## Key Technologies

| Layer | Technology | Purpose |
|-------|-----------|---------|
| UI | Compose Multiplatform | Shared declarative UI across platforms |
| Concurrency | Kotlin Coroutines + StateFlow | Reactive sensor/mesh data streams |
| Hardware Abstraction | KMP interfaces + DI | Platform-agnostic sensor/BLE access |
| Edge AI (Earthquake) | TensorFlow Lite + STA/LTA | Seismic P-wave / S-wave detection |
| Edge AI (Typhoon) | Linear regression + PAGASA thresholds | Barometric pressure drop detection |
| Mesh Networking | Nearby Connections API | Offline BLE/Wi-Fi Direct P2P mesh |
| Encryption | AES-256-GCM | SOS payload integrity and confidentiality |
| Background | Foreground Service + WakeLock | Persistent monitoring when app is backgrounded |
| Alerting | SYSTEM_ALERT_WINDOW overlay | Chat-head style floating disaster alert |

## Building

Requires Android SDK 34+ and JDK 17.

### Environment setup

Set these two environment variables before building. Android Studio bundles a JDK you can use for `JAVA_HOME`.

**PowerShell** (default VS Code terminal on Windows):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

**Git Bash**:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
```

> **Note:** On macOS / Linux the typical `ANDROID_HOME` is `~/Android/Sdk` and `JAVA_HOME` is the output of `/usr/libexec/java_home`.

### Build the debug APK

**PowerShell**:

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

**Git Bash**:

```bash
./gradlew.bat :androidApp:assembleDebug
```

The debug APK will be at:

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### Build a release APK

A release APK requires signing. You must generate a keystore **before** building.

#### 1. Generate a keystore (one-time)

Run from the **project root** directory so the `.jks` file is created where `build.gradle.kts` expects it.

**PowerShell**:

```powershell
keytool -genkeypair -v -keystore allysong-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias allysong -storepass "your_password" -keypass "your_password"
```

**Git Bash**:

```bash
keytool -genkeypair -v \
  -keystore allysong-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias allysong \
  -storepass your_password \
  -keypass your_password
```

> **Important:** PowerShell does not support `\` for line continuation. Use the single-line form above, or replace `\` with a backtick (`` ` ``).

You will be prompted for your name, organization, and country. You can press Enter through the prompts or fill them in.

#### 2. Configure signing in build.gradle.kts

`androidApp/build.gradle.kts` is already configured to sign release builds automatically. Update the passwords to match the ones you chose when generating the keystore:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../allysong-release.jks")
            storePassword = "your_password"
            keyAlias = "allysong"
            keyPassword = "your_password"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

#### 3. Build the release APK

**PowerShell**:

```powershell
.\gradlew.bat :androidApp:assembleRelease
```

**Git Bash**:

```bash
./gradlew.bat :androidApp:assembleRelease
```

The signed release APK will be at:

```
androidApp/build/outputs/apk/release/androidApp-release.apk
```

> **Quick option for testing:** The debug APK (`assembleDebug`) is automatically signed with the Android debug keystore and can be installed on any device with USB debugging enabled. Use the debug APK for testing and only create a release APK when you need to distribute it.

## Running on the Emulator

### 1. Start the emulator

List available AVDs, then launch one:

**PowerShell**:

```powershell
# List AVDs
& "$env:ANDROID_HOME\emulator\emulator" -list-avds

# Launch (replace <avd_name> with the name from the list above)
Start-Process -NoNewWindow "$env:ANDROID_HOME\emulator\emulator" -ArgumentList "-avd","<avd_name>"
```

**Git Bash**:

```bash
# List AVDs
"$ANDROID_HOME/emulator/emulator" -list-avds

# Launch (replace <avd_name> with the name from the list above)
"$ANDROID_HOME/emulator/emulator" -avd <avd_name> &
```

Wait for the emulator to finish booting (Android home screen visible).

### 2. Install the APK

**PowerShell**:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb" install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

**Git Bash**:

```bash
"$ANDROID_HOME/platform-tools/adb" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### 3. Launch the app

**PowerShell**:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb" shell am start -n com.allysong/.MainActivity
```

**Git Bash**:

```bash
"$ANDROID_HOME/platform-tools/adb" shell am start -n com.allysong/.MainActivity
```

> **Tip:** Add `%ANDROID_HOME%\platform-tools` (PowerShell: `$env:ANDROID_HOME\platform-tools`) to your system `PATH` so you can type `adb` directly.

### Creating an AVD (if you don't have one)

**PowerShell**:

```powershell
sdkmanager "system-images;android-34;google_apis;x86_64"

avdmanager create avd --name "AllySong_Test" --package "system-images;android-34;google_apis;x86_64" --device "pixel_6"
```

**Git Bash**:

```bash
sdkmanager "system-images;android-34;google_apis;x86_64"

avdmanager create avd \
  --name "AllySong_Test" \
  --package "system-images;android-34;google_apis;x86_64" \
  --device "pixel_6"
```

Or create one via Android Studio: **Tools > Device Manager > Create Virtual Device**.

## TFLite Model

A custom-trained seismic detection model is included at `shared/src/androidMain/assets/seismic_detector.tflite`. If the model file is deleted, the engine falls back to the STA/LTA heuristic with full rejection filters.

**Model spec:**
- Architecture: 1D CNN (3 conv blocks with multi-scale kernels: 3, 5, 7)
- Input: `[1, 50, 3]` float32 tensor (1 second of accelerometer data at 50 Hz, raw x/y/z in m/s²)
- Output: `[1, 4]` float32 tensor (softmax: background, P-wave, S-wave, human_activity)
- Training data: 8,000 synthetic samples per class (32,000 total)

### Why a custom-trained model (no pre-trained model exists)

There is **no publicly available, pre-trained TFLite model** for smartphone earthquake detection. We evaluated all major existing work:

| Project | Availability | Why it can't be used directly |
|---------|-------------|-------------------------------|
| **MyShake** (UC Berkeley) | Proprietary | Algorithm described in peer-reviewed paper [1] but trained weights and source code are **closed**. Uses an ANN with feature-engineered inputs (CAV, zero-crossing rate, PSD ratios). |
| **Google Android EQ Alerts** | Proprietary | Embedded in Play Services, fully proprietary. Uses on-device trigger + **server-side crowd consensus** — requires internet, which defeats our offline-only design. |
| **EQTransformer** | Open (MIT) | Designed for professional seismometers, not phone accelerometers. Model is too large for mobile inference and expects 100 Hz broadband 3-component data [2]. |
| **PhaseNet** | Open | Same issue — trained on professional seismometer data with much higher sensitivity and lower noise floor than phone MEMS sensors. |
| **STEAD dataset** | Open (85 GB) | 1.2M waveforms from professional seismometers [3]. Could supplement training via transfer learning, but requires significant domain adaptation to phone-quality data. |

**Our approach:** A lightweight 1D CNN trained on synthetic accelerometer data that models realistic phone sensor characteristics (random orientation, gravity baseline, MEMS noise, 11 human activity patterns). This follows the same general strategy as MyShake but with open training code and a focus on minimizing false positives from everyday phone use.

**References:**
1. Kong, Q., Allen, R.M., Schreier, L., & Kwon, Y.-W. "MyShake: A smartphone seismic network for earthquake early warning and beyond." *Science Advances* 2(2), e1501055, 2016.
2. Mousavi, S.M., et al. "Earthquake Transformer — an attentive deep-learning model for simultaneous earthquake detection and phase picking." *Nature Communications* 11, 2020.
3. Mousavi, S.M., et al. "STanford EArthquake Dataset (STEAD): A Global Data Set of Seismic Signals for AI." *IEEE Access*, 2019.

### Multi-layered detection architecture

The detection engine uses 4 layers to eliminate false positives:

```
 Accelerometer window (50 samples, 1 second)
          │
          ▼
 ┌─── Layer 1: Rejection Filters (rules-based, ~0.1 ms) ──────┐
 │  ✗ Put-down detector    (front-loaded energy + silent tail)  │
 │  ✗ Transient detector   (single spike cluster < 5 samples)   │
 │  ✗ Periodicity detector (autocorrelation at 1.5-3.5 Hz)     │
 └──────────────────────────────────────────────────────────────┘
          │ (passed all filters)
          ▼
 ┌─── Layer 2: STA/LTA Heuristic ──────────────────────────────┐
 │  Demeaned characteristic function (removes gravity)          │
 │  P-wave: STA/LTA > 4.0, spike count ≥ 3, peak > 6 m/s²     │
 │  S-wave: STA/LTA > 2.8, stddev > 2.5, sustained > 8 samples │
 │  Multi-axis check: ≥ 2 axes active (rejects single-axis)    │
 └──────────────────────────────────────────────────────────────┘
          │
          ▼
 ┌─── Layer 3: TFLite 1D CNN (if model file present) ──────────┐
 │  4-class: [background, P-wave, S-wave, human_activity]       │
 │  Reject if human_activity > 25% or is dominant class         │
 │  Require seismic class confidence ≥ 80%                      │
 └──────────────────────────────────────────────────────────────┘
          │
          ▼
 ┌─── Layer 4: Consecutive-window Confirmation ─────────────────┐
 │  Require 2 consecutive positive windows (~1 second)          │
 │  Resets on any negative window — eliminates all transients   │
 └──────────────────────────────────────────────────────────────┘
          │
          ▼
    DisasterEvent (or null)
```

### Rejection filters explained

| Filter | What it catches | How it works |
|--------|----------------|--------------|
| **Put-down** | Phone placed on desk/table | Checks if last 30% of window has < 10% of the energy of the first 70%. Earthquakes sustain energy throughout. |
| **Transient impulse** | Door slams, drops, bumps | Counts above-3σ sample clusters. A single cluster shorter than 5 samples is a transient, not seismic. |
| **Periodicity** | Walking, running, stairs, cycling | Autocorrelation at locomotion lags (1.5-3.5 Hz). Periodic signals have autocorrelation > 0.55; earthquakes are aperiodic. |
| **Multi-axis** | Typing, single-hand gestures | Checks that ≥ 2 axes have > 25% of the max axis energy. Walking/typing are often single-axis dominant. |

### Retraining the model

The training script generates synthetic accelerometer data (including randomized phone orientation and gravity) and trains a 1D CNN. No external datasets or internet needed.

```bash
# Install dependencies (one-time)
pip install tensorflow numpy

# Train and export the model (~3-8 minutes on CPU)
python train_seismic_model.py
```

The script outputs to `shared/src/androidMain/assets/seismic_detector.tflite` directly. Rebuild the APK afterward.

### What the synthetic data simulates

| Class | Signal characteristics |
|-------|----------------------|
| Background | Gravity (~9.81 m/s² in random orientation) + Gaussian sensor noise + occasional micro-vibrations |
| P-wave | Sudden onset high-frequency spike (3-15 Hz), exponential decay, amplitude 4-15 m/s² |
| S-wave | Sustained lower-frequency oscillation (0.5-5 Hz), gradual buildup, amplitude 5-20 m/s² |
| Human activity | **11 patterns** (see table below) |

**Human activity sub-patterns (all classified as a single class):**

| Activity | Frequency | Amplitude | Key discriminator vs earthquake |
|----------|-----------|-----------|--------------------------------|
| Walking | ~2 Hz periodic | 1-4 m/s² | Periodic, vertical-axis dominant |
| Running | ~3 Hz periodic | 2-6 m/s² | Periodic with jitter, vertical-axis dominant |
| Phone pickup | Single transient | 3-8 m/s² | Smooth ramp (gravity rotation), not oscillatory |
| Phone put-down | Tilt → impact → quiet | 2-12 m/s² | Energy front-loads then goes silent |
| Pocket sway | 0.5-2 Hz irregular | 0.5-3 m/s² | Low amplitude, irregular, not periodic |
| Taps/bumps | Isolated impulses | 3-7 m/s² | 1-3 spikes, not sustained |
| Driving | Multi-frequency | 0.3-1.5 m/s² | Sustained but very low amplitude |
| Stairs | ~1.5 Hz periodic | 2-6 m/s² | Rectified impacts, vertical dominant |
| Door slam | Single impulse | 6-15 m/s² | One spike then dead silence |
| Typing | 5-10 Hz | 0.3-1.5 m/s² | High-freq, low-amp, one-axis dominant |
| Jumping | Isolated large spikes | 5-15 m/s² | Takeoff + landing pairs with airborne gap |
| Cycling | 1-2 Hz smooth | 0.5-3 m/s² | Smooth pedaling sine + road vibration overlay |

## Testing

### Granting the overlay permission

AllySong uses a floating overlay (chat-head style) to alert you when a disaster is detected while you are outside the app. On first launch, the app will open the system Settings page for "Display over other apps". Toggle the permission on for AllySong and press Back to return.

If you skip this, the overlay will not appear -- the app will fall back to a standard notification.

### Testing earthquake detection

The emulator does not have a physical accelerometer, but you can send simulated sensor data:

1. In the app, tap the **Accelerometer** toggle to start sensor monitoring. The status should change to "Monitoring -- analyzing..." and the foreground service notification should appear.
2. Open the emulator's **Extended Controls** (click the `...` button on the emulator toolbar).
3. Go to **Virtual sensors > Accelerometer**.
4. Rapidly drag the 3D phone model back and forth to simulate shaking, or manually set X/Y/Z values with sharp spikes (e.g. set X to 20, then -15, then 25 in quick succession). The STA/LTA algorithm detects sudden energy transients relative to the window baseline.
5. The **HITL validation screen** should appear with a 60-second countdown.
6. Tap **"YES -- I'M SAFE"** to dismiss and resume monitoring, or **"NO -- I NEED HELP"** to broadcast an SOS over the mesh.

### Testing earthquake detection while backgrounded

1. Start sensor monitoring from the dashboard (the foreground notification "AllySong -- Monitoring active" should appear in the notification shade).
2. Press **Home** to send the app to the background.
3. Trigger shaking via Extended Controls as described above.
4. A **floating red alert overlay** should slide down from the top of the screen (over whatever app you are in) with the disaster type, confidence, and a "TAP TO RESPOND" button.
5. Tapping the overlay opens AllySong directly to the HITL validation screen.
6. If the overlay permission was not granted, a standard high-priority notification will appear instead.

### Testing manual SOS

1. From the dashboard, tap the **SOS** button.
2. The **Manual SOS screen** appears with a disaster type picker (Earthquake, Typhoon, Flood, Fire, Other).
3. Select a type and tap **CONFIRM** to broadcast an SOS, or **Cancel** to go back.

### Testing the barometer

The emulator does not have a barometer sensor. On the dashboard, the barometer card will show "Unavailable" if no hardware is detected.

To test barometric typhoon detection, use a **physical device** with a barometer sensor (most modern Android phones have one). The typhoon engine detects sustained pressure drops exceeding 6 hPa/hour using linear regression on a sliding window.

### Peer verification (challenge-response handshake)

AllySong verifies that every connected peer is a genuine AllySong device before exchanging any messages. When two devices connect via Nearby Connections:

1. Both sides send a **HANDSHAKE_CHALLENGE** containing a random 16-byte nonce.
2. The receiver XORs the nonce with a shared pattern and replies with a **HANDSHAKE_RESPONSE**.
3. The challenger verifies the response matches the expected value.
4. If verification succeeds, the peer is marked as **CONNECTED** and can send/receive messages.
5. If verification fails or times out (10 seconds), the peer is **disconnected**.

This prevents non-AllySong devices or ghost peers from injecting messages into the mesh.

### GPS location sharing over BLE mesh

When the mesh is active, AllySong tracks the device's GPS location using `FusedLocationProviderClient` (30-second intervals, balanced power accuracy). Location sharing is **user-initiated**, not automatic:

- **Send Location button** (crosshair icon) in the chat input bar lets users deliberately share their GPS coordinates.
- **Tappable map links** — shared locations and SOS coordinates render as clickable pins that open in the device's default map app (Google Maps, Apple Maps, etc.) via `geo:` URI.
- **SOS messages** always include the sender's current coordinates (emergency override).
- **Regular chat messages** do NOT auto-attach GPS — location sharing is a conscious choice.
- **Shown on peer chips** in Communication Mode so you can see where each peer is.
- **Updated on peer records** when incoming messages contain location data.

Location tracking only runs while the mesh is active. When the mesh is toggled off, GPS tracking stops (privacy by default).

### Image sharing over BLE mesh

Users can send compressed images through the offline BLE mesh — no internet required:

- **Camera button** (camera icon) in the chat input bar opens a dialog to **Take Photo** or **Choose from Gallery**.
- Images are **compressed to 320px max, JPEG 50%** (~5-20KB) to fit within BLE mesh bandwidth.
- Images render **inline in chat bubbles** with rounded corners (max 240x240dp).
- The entire pipeline is offline: image capture/pick → compress → base64 → encrypted JSON → Nearby Connections BYTES payload → decrypt → decode → display.
- Uses `expect`/`actual` KMP pattern: `rememberImagePicker` and `Base64Image` composables have platform-specific implementations (Android: `ActivityResultContracts` + `BitmapFactory`).

> **Permissions:** The app requires `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` for GPS. These are already needed by Nearby Connections for BLE discovery.

### Testing BLE mesh with physical devices

BLE is **not natively supported** between emulator instances. To test real mesh networking with actual BLE hardware:

| Approach | How |
|----------|-----|
| **Two physical devices** | Install the APK on two phones. Both must grant BLE + location permissions. Mesh discovery uses Nearby Connections P2P_CLUSTER strategy. Peer verification happens automatically. |
| **One emulator + one physical device** | Run the emulator for UI testing and a physical device for mesh. They can discover each other over Wi-Fi Direct if on the same network. |

> The BLE mesh communication pattern is inspired by [bitchat](https://github.com/permissionlesstech/bitchat) -- a decentralized BLE mesh chat protocol that uses TTL-based gossip relay and deduplication. AllySong adapts these ideas using Google's Nearby Connections API with AES-256-GCM encrypted payloads.

### Useful adb commands

```bash
adb devices                                    # Check connected devices / emulators
adb logcat -s AllySong:V                       # View AllySong logs
adb uninstall com.allysong                     # Uninstall
adb exec-out screencap -p > screenshot.png     # Take a screenshot
adb shell am force-stop com.allysong           # Force-stop (also stops foreground service)
```

> If `adb` is not on your `PATH`, prefix each command with the full path: `"$ANDROID_HOME/platform-tools/adb"` (Bash) or `& "$env:ANDROID_HOME\platform-tools\adb"` (PowerShell).

## Notes

- **Prototype scope:** This is a functional prototype demonstrating KMP architecture, reactive sensor pipelines, multi-layered seismic detection (rejection filters + STA/LTA + TFLite CNN + consecutive-window confirmation), barometric typhoon detection, foreground service persistence, floating alert overlays, peer-verified BLE mesh networking with GPS location sharing, offline image sharing over BLE mesh, and challenge-response handshake security.
- **No pre-trained model available:** There is no publicly available, pre-trained TFLite model for smartphone earthquake detection. MyShake (UC Berkeley) and Google Android Earthquake Alerts are both proprietary. EQTransformer and PhaseNet are open-source but designed for professional seismometers, not phone accelerometers. Our custom-trained 1D CNN follows the same general approach as MyShake but with open training code. See the TFLite Model section above for full citations.
- **PSK encryption** is used for development convenience. Production requires per-peer key exchange (X25519 ECDH).
- The earthquake detection uses a 4-layer pipeline: (1) rejection filters for put-down/transient/periodic human motion, (2) STA/LTA heuristic with multi-axis correlation on a demeaned characteristic function, (3) TFLite CNN inference with 4-class discrimination, (4) consecutive-window confirmation. Each layer independently rejects false positives — a signal must pass ALL layers to trigger an alert.
- The typhoon heuristic uses linear regression on barometric pressure readings with a PAGASA-derived threshold of 6 hPa/hour sustained drop rate.
