"""
train_seismic_model.py — AllySong Seismic Detection Model Training
==================================================================
Trains a 1D CNN on synthetic accelerometer data to classify four classes:
  - background      (phone at rest, minor vibrations)
  - p_wave          (earthquake onset: high-freq compressional wave)
  - s_wave          (earthquake body: sustained low-freq lateral shaking)
  - human_activity  (comprehensive set of everyday phone motions)

Why synthetic data instead of an existing pre-trained model?
------------------------------------------------------------
No publicly available, pre-trained TFLite model exists for smartphone
earthquake detection. Key prior work:

  - **MyShake** (UC Berkeley, 2016): Uses an ANN trained on phone
    accelerometer data with feature-engineered inputs (CAV, zero-crossing
    rate, power spectral density ratios). The algorithm is published in
    Science Advances (Kong et al., 2016) but the trained weights and
    source code are **proprietary and not publicly released**.

  - **Google Android Earthquake Alerts** (Stogaitis et al., 2020):
    Embedded in Google Play Services on billions of devices. Uses an
    on-device trigger with **server-side crowd-consensus** for
    confirmation. Fully proprietary — no model weights or API available.

  - **EQTransformer** (Mousavi et al., 2020, Nature Communications):
    Open-source with MIT license, but designed for professional
    seismometers (broadband 3-component data at 100 Hz), NOT smartphone
    accelerometers. The model is too large for mobile inference.

  - **STEAD** (Stanford Earthquake Dataset): 1.2M waveforms from
    professional seismometers. Open and free but recorded at much higher
    sensitivity than phone sensors — direct use would require significant
    domain adaptation.

Our approach: custom-trained lightweight 1D CNN on synthetic data that
models realistic phone sensor characteristics (random orientation,
gravity baseline, MEMS noise). The human_activity class covers 13
distinct everyday activities to minimize false positives.

References:
  [1] Kong et al., "MyShake: A smartphone seismic network for earthquake
      early warning and beyond," Science Advances 2(2), 2016.
  [2] Mousavi et al., "Earthquake Transformer — an attentive deep-
      learning model for simultaneous earthquake detection and phase
      picking," Nature Communications 11, 2020.
  [3] Mousavi et al., "STanford EArthquake Dataset (STEAD): A Global
      Data Set of Seismic Signals for AI," IEEE Access, 2019.

Output: seismic_detector.tflite
  Input:  [1, 50, 3]  float32  (batch, timesteps, xyz in m/s^2)
  Output: [1, 4]      float32  (softmax: background, p_wave, s_wave,
                                 human_activity)

Usage:
  pip install tensorflow numpy
  python train_seismic_model.py
"""

import os
import numpy as np

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"  # Suppress TF info logs

import tensorflow as tf
from tensorflow import keras

# ── Configuration ────────────────────────────────────────────────────────────

WINDOW_SIZE = 50          # 50 samples = 1 second at 50 Hz
NUM_AXES = 3              # x, y, z
NUM_CLASSES = 4           # background, p_wave, s_wave, human_activity
SAMPLES_PER_CLASS = 10000  # More samples for better generalization
EPOCHS = 60
BATCH_SIZE = 64
VALIDATION_SPLIT = 0.2
RANDOM_SEED = 42

# Output path (relative to project root)
OUTPUT_DIR = os.path.join("shared", "src", "androidMain", "assets")
OUTPUT_FILE = "seismic_detector.tflite"

GRAVITY = 9.81  # m/s^2

np.random.seed(RANDOM_SEED)
tf.random.set_seed(RANDOM_SEED)


# ── Synthetic Data Generation ────────────────────────────────────────────────

def random_gravity_vector():
    """Generate a random 3D gravity vector (phone in arbitrary orientation)."""
    theta = np.arccos(2 * np.random.uniform() - 1)
    phi = 2 * np.pi * np.random.uniform()
    gx = GRAVITY * np.sin(theta) * np.cos(phi)
    gy = GRAVITY * np.sin(theta) * np.sin(phi)
    gz = GRAVITY * np.cos(theta)
    return np.array([gx, gy, gz], dtype=np.float32)


def generate_background(n):
    """Background: gravity + sensor noise. No seismic activity."""
    samples = []
    for _ in range(n):
        g = random_gravity_vector()
        noise_sigma = np.random.uniform(0.02, 0.10)
        noise = np.random.normal(0, noise_sigma, (WINDOW_SIZE, NUM_AXES)).astype(np.float32)

        # Occasional micro-vibrations (HVAC, traffic rumble) for realism
        if np.random.random() < 0.3:
            vib_freq = np.random.uniform(1.0, 8.0)
            vib_amp = np.random.uniform(0.05, 0.3)
            t = np.linspace(0, 1, WINDOW_SIZE)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                noise[:, axis] += (vib_amp * np.sin(2 * np.pi * vib_freq * t + phase)).astype(np.float32)

        window = np.tile(g, (WINDOW_SIZE, 1)) + noise
        samples.append(window)
    return np.array(samples, dtype=np.float32)


def generate_p_wave(n):
    """P-wave: background + sudden onset high-freq spike with exponential decay.

    Real P-waves are compressional: they arrive first and produce sharp,
    high-frequency spikes (3-15 Hz) with large amplitudes (>>5 m/s² at
    close range). Key discriminator vs human activity: ALL axes spike
    simultaneously and the onset is extremely abrupt.
    """
    samples = []
    for _ in range(n):
        g = random_gravity_vector()
        noise_sigma = np.random.uniform(0.02, 0.10)
        base = np.tile(g, (WINDOW_SIZE, 1)) + np.random.normal(0, noise_sigma, (WINDOW_SIZE, NUM_AXES)).astype(np.float32)

        onset = np.random.randint(5, 30)
        amplitude = np.random.uniform(4.0, 15.0)
        freq = np.random.uniform(3.0, 15.0)
        decay_rate = np.random.uniform(3.0, 8.0)

        t = np.arange(WINDOW_SIZE, dtype=np.float32) / 50.0

        for axis in range(NUM_AXES):
            phase = np.random.uniform(0, 2 * np.pi)
            axis_amp = amplitude * np.random.uniform(0.3, 1.0)
            envelope = np.zeros(WINDOW_SIZE, dtype=np.float32)
            for i in range(onset, WINDOW_SIZE):
                elapsed = (i - onset) / 50.0
                envelope[i] = axis_amp * np.exp(-decay_rate * elapsed)
            signal = envelope * np.sin(2 * np.pi * freq * t + phase).astype(np.float32)
            base[:, axis] += signal

        samples.append(base)
    return np.array(samples, dtype=np.float32)


def generate_s_wave(n):
    """S-wave: background + sustained lower-freq oscillation with gradual buildup.

    Real S-waves are shear: they arrive after P-waves and produce sustained,
    lower-frequency oscillation (0.5-5 Hz). Key discriminator: the energy
    builds up and stays high for the entire window, unlike transient human
    activities. Multiple axes oscillate with correlated but phase-shifted
    patterns (polarized ground motion).
    """
    samples = []
    for _ in range(n):
        g = random_gravity_vector()
        noise_sigma = np.random.uniform(0.02, 0.10)
        base = np.tile(g, (WINDOW_SIZE, 1)) + np.random.normal(0, noise_sigma, (WINDOW_SIZE, NUM_AXES)).astype(np.float32)

        onset = np.random.randint(0, 15)
        amplitude = np.random.uniform(5.0, 20.0)
        freq = np.random.uniform(0.5, 5.0)
        buildup_rate = np.random.uniform(2.0, 6.0)

        t = np.arange(WINDOW_SIZE, dtype=np.float32) / 50.0

        for axis in range(NUM_AXES):
            phase = np.random.uniform(0, 2 * np.pi)
            axis_amp = amplitude * np.random.uniform(0.3, 1.0)
            envelope = np.zeros(WINDOW_SIZE, dtype=np.float32)
            for i in range(onset, WINDOW_SIZE):
                elapsed = (i - onset) / 50.0
                envelope[i] = axis_amp * (1.0 - np.exp(-buildup_rate * elapsed))
            signal = envelope * np.sin(2 * np.pi * freq * t + phase).astype(np.float32)
            base[:, axis] += signal

        samples.append(base)
    return np.array(samples, dtype=np.float32)


def generate_human_activity(n):
    """Human activity: 13 distinct activities that could trigger false positives.

    Each activity has unique characteristics that differ from earthquakes:
    - Walking/running: periodic, narrowband, one dominant axis (vertical)
    - Pickup/putdown: single transient with quiet before/after
    - Pocket sway: low-frequency drift, low amplitude
    - Taps: isolated impulses (1-3), not sustained
    - Driving: continuous broadband vibration but low amplitude
    - Stairs: lower-frequency periodic (~1.5 Hz), heavier impacts
    - Door slam: single sharp impulse then complete silence
    - Typing: rapid low-amplitude taps (~5-8 Hz), mostly one axis
    - Jumping: large single spike + landing, clear periodicity if repeated
    - Cycling: smooth medium-frequency oscillation (~1-2 Hz)
    - Fidgeting: aperiodic multi-axis 1-5 m/s², irregular timing
    - Pocket shift: slow gravity drift + intermittent bumps
    """
    samples = []
    activities = [
        "walking", "running", "pickup", "pocket", "tap", "putdown",
        "driving", "stairs", "door_slam", "typing", "jumping", "cycling",
        "fidgeting", "pocket_shift"
    ]

    for _ in range(n):
        g = random_gravity_vector()
        noise_sigma = np.random.uniform(0.02, 0.10)
        base = np.tile(g, (WINDOW_SIZE, 1)) + np.random.normal(0, noise_sigma, (WINDOW_SIZE, NUM_AXES)).astype(np.float32)

        activity = np.random.choice(activities)
        t = np.arange(WINDOW_SIZE, dtype=np.float32) / 50.0

        if activity == "walking":
            # ~2 Hz regular step pattern, amplitude 1-4 m/s²
            # Key: periodic, mostly vertical, narrowband
            step_freq = np.random.uniform(1.5, 2.5)
            step_amp = np.random.uniform(1.0, 4.0)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                axis_scale = [0.3, 0.3, 1.0][axis] if np.random.random() < 0.6 else np.random.uniform(0.3, 1.0)
                base[:, axis] += (step_amp * axis_scale * np.sin(2 * np.pi * step_freq * t + phase)).astype(np.float32)

        elif activity == "running":
            # ~3 Hz higher-amplitude step pattern, 2-6 m/s²
            # Key: periodic with jitter, mostly vertical
            step_freq = np.random.uniform(2.5, 3.5)
            step_amp = np.random.uniform(2.0, 6.0)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                axis_scale = [0.4, 0.4, 1.0][axis] if np.random.random() < 0.6 else np.random.uniform(0.3, 1.0)
                jitter = np.random.normal(0, 0.3, WINDOW_SIZE).astype(np.float32)
                base[:, axis] += (step_amp * axis_scale * np.sin(2 * np.pi * step_freq * t + phase) + jitter).astype(np.float32)

        elif activity == "pickup":
            # Phone pickup: smooth rotation causing gravity vector shift
            # Key: single slow transient, NOT oscillatory
            pickup_start = np.random.randint(5, 25)
            pickup_duration = np.random.randint(15, 30)
            pickup_amp = np.random.uniform(3.0, 8.0)
            for axis in range(NUM_AXES):
                for i in range(pickup_start, min(pickup_start + pickup_duration, WINDOW_SIZE)):
                    progress = (i - pickup_start) / pickup_duration
                    base[i, axis] += pickup_amp * np.random.uniform(0.2, 1.0) * np.sin(np.pi * progress)

        elif activity == "pocket":
            # Phone in pocket: irregular low-frequency sway
            # Key: low amplitude, irregular, not periodic
            sway_freq = np.random.uniform(0.5, 2.0)
            sway_amp = np.random.uniform(0.5, 3.0)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                modulation = np.random.uniform(0.3, 1.0, WINDOW_SIZE).astype(np.float32)
                base[:, axis] += (sway_amp * modulation * np.sin(2 * np.pi * sway_freq * t + phase)).astype(np.float32)

        elif activity == "tap":
            # Taps/bumps: 1-3 sharp isolated impulses, very short duration
            # Key: isolated, NOT sustained like earthquake shaking
            num_taps = np.random.randint(1, 4)
            tap_positions = sorted(np.random.choice(range(5, WINDOW_SIZE - 3), size=num_taps, replace=False))
            for pos in tap_positions:
                tap_amp = np.random.uniform(3.0, 7.0)
                tap_len = np.random.randint(2, 5)
                for axis in range(NUM_AXES):
                    axis_amp = tap_amp * np.random.uniform(0.2, 1.0)
                    for j in range(min(tap_len, WINDOW_SIZE - pos)):
                        decay = np.exp(-3.0 * j / tap_len)
                        base[pos + j, axis] += axis_amp * decay

        elif activity == "putdown":
            # Phone put-down: tilt → single impact → silence
            # Key: energy profile front-heavy then dead quiet
            tilt_start = np.random.randint(3, 15)
            tilt_duration = np.random.randint(8, 20)
            tilt_amp = np.random.uniform(2.0, 6.0)
            impact_pos = tilt_start + tilt_duration
            impact_amp = np.random.uniform(4.0, 12.0)

            for axis in range(NUM_AXES):
                axis_scale = np.random.uniform(0.3, 1.0)
                for i in range(tilt_start, min(tilt_start + tilt_duration, WINDOW_SIZE)):
                    progress = (i - tilt_start) / tilt_duration
                    base[i, axis] += tilt_amp * axis_scale * np.sin(np.pi * progress)

            if impact_pos < WINDOW_SIZE:
                impact_len = np.random.randint(3, 7)
                for axis in range(NUM_AXES):
                    axis_amp = impact_amp * np.random.uniform(0.3, 1.0)
                    for j in range(min(impact_len, WINDOW_SIZE - impact_pos)):
                        decay = np.exp(-4.0 * j / impact_len)
                        base[impact_pos + j, axis] += axis_amp * decay

            quiet_start = min(impact_pos + 5, WINDOW_SIZE)
            for i in range(quiet_start, WINDOW_SIZE):
                base[i] = base[i] * 0.3

        elif activity == "driving":
            # Vehicle: continuous broadband engine + road vibration
            # Key: sustained but LOW amplitude, multi-frequency
            base_freq = np.random.uniform(5.0, 20.0)  # Engine RPM harmonics
            road_freq = np.random.uniform(1.0, 5.0)    # Road surface
            engine_amp = np.random.uniform(0.3, 1.5)
            road_amp = np.random.uniform(0.2, 1.0)
            for axis in range(NUM_AXES):
                phase1 = np.random.uniform(0, 2 * np.pi)
                phase2 = np.random.uniform(0, 2 * np.pi)
                engine = engine_amp * np.sin(2 * np.pi * base_freq * t + phase1)
                road = road_amp * np.sin(2 * np.pi * road_freq * t + phase2)
                # Add some random bumps (potholes, speed bumps)
                if np.random.random() < 0.3:
                    bump_pos = np.random.randint(10, 40)
                    bump_amp = np.random.uniform(2.0, 5.0)
                    for j in range(min(4, WINDOW_SIZE - bump_pos)):
                        base[bump_pos + j, axis] += bump_amp * np.exp(-2.0 * j / 4)
                base[:, axis] += (engine + road).astype(np.float32)

        elif activity == "stairs":
            # Climbing/descending stairs: heavy periodic impacts
            # Key: ~1.5 Hz (slower than walking), each step is heavier impact
            step_freq = np.random.uniform(1.0, 1.8)
            step_amp = np.random.uniform(2.0, 6.0)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                # Stairs: stronger vertical, step-like waveform (rectified sine)
                raw = np.sin(2 * np.pi * step_freq * t + phase)
                rectified = np.abs(raw)  # Impact-like pattern
                axis_scale = [0.3, 0.3, 1.0][axis] if np.random.random() < 0.7 else np.random.uniform(0.3, 1.0)
                base[:, axis] += (step_amp * axis_scale * rectified).astype(np.float32)

        elif activity == "door_slam":
            # Door slam near the phone: single massive impulse then dead silence
            # Key: one spike, extremely short, then nothing
            slam_pos = np.random.randint(5, 35)
            slam_amp = np.random.uniform(6.0, 15.0)
            slam_len = np.random.randint(2, 5)
            for axis in range(NUM_AXES):
                axis_amp = slam_amp * np.random.uniform(0.3, 1.0)
                for j in range(min(slam_len, WINDOW_SIZE - slam_pos)):
                    decay = np.exp(-5.0 * j / slam_len)
                    base[slam_pos + j, axis] += axis_amp * decay
                    # Oscillating decay (ringing)
                    if j > 0:
                        base[slam_pos + j, axis] *= (-1) ** j * 0.7
            # Silence after slam
            quiet_after = slam_pos + slam_len + 2
            for i in range(min(quiet_after, WINDOW_SIZE), WINDOW_SIZE):
                base[i] = base[i] * 0.2

        elif activity == "typing":
            # Typing on desk: rapid low-amplitude taps
            # Key: high frequency (~5-10 Hz), very low amplitude, one dominant axis
            typing_freq = np.random.uniform(4.0, 10.0)
            typing_amp = np.random.uniform(0.3, 1.5)
            # Typing is mostly on the Z axis (desk surface)
            dominant_axis = np.random.randint(0, 3)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                scale = 1.0 if axis == dominant_axis else np.random.uniform(0.1, 0.3)
                # Irregular typing rhythm (amplitude modulation)
                modulation = np.random.uniform(0.2, 1.0, WINDOW_SIZE).astype(np.float32)
                signal = typing_amp * scale * modulation * np.abs(np.sin(2 * np.pi * typing_freq * t + phase))
                base[:, axis] += signal.astype(np.float32)

        elif activity == "jumping":
            # Jumping: large spikes at takeoff and landing
            # Key: 1-3 very large but isolated impulses with clear gap between
            num_jumps = np.random.randint(1, 4)
            for jump_i in range(num_jumps):
                jump_pos = np.random.randint(5 + jump_i * 15, min(5 + (jump_i + 1) * 15, WINDOW_SIZE - 5))
                if jump_pos >= WINDOW_SIZE - 5:
                    break
                # Takeoff
                takeoff_amp = np.random.uniform(5.0, 12.0)
                for axis in range(NUM_AXES):
                    axis_amp = takeoff_amp * np.random.uniform(0.3, 1.0)
                    base[jump_pos, axis] += axis_amp
                    if jump_pos + 1 < WINDOW_SIZE:
                        base[jump_pos + 1, axis] += axis_amp * 0.5
                # Airborne (brief quiet period)
                air_time = np.random.randint(3, 8)
                # Landing
                land_pos = min(jump_pos + air_time, WINDOW_SIZE - 2)
                landing_amp = np.random.uniform(6.0, 15.0)
                for axis in range(NUM_AXES):
                    axis_amp = landing_amp * np.random.uniform(0.3, 1.0)
                    base[land_pos, axis] += axis_amp
                    if land_pos + 1 < WINDOW_SIZE:
                        base[land_pos + 1, axis] += axis_amp * 0.4
                    if land_pos + 2 < WINDOW_SIZE:
                        base[land_pos + 2, axis] += axis_amp * 0.1

        elif activity == "cycling":
            # Cycling: smooth, low-to-medium amplitude pedaling rhythm
            # Key: ~1-2 Hz, smooth sine-like, lower amplitude than running
            pedal_freq = np.random.uniform(0.8, 2.0)
            pedal_amp = np.random.uniform(0.5, 3.0)
            # Road vibration overlay
            road_freq = np.random.uniform(8.0, 15.0)
            road_amp = np.random.uniform(0.1, 0.5)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                # Pedaling is mostly vertical
                axis_scale = [0.3, 0.5, 1.0][axis] if np.random.random() < 0.6 else np.random.uniform(0.3, 1.0)
                pedal_signal = pedal_amp * axis_scale * np.sin(2 * np.pi * pedal_freq * t + phase)
                road_signal = road_amp * np.sin(2 * np.pi * road_freq * t + np.random.uniform(0, 2 * np.pi))
                base[:, axis] += (pedal_signal + road_signal).astype(np.float32)

        elif activity == "fidgeting":
            # Fidgeting: aperiodic multi-axis motion, 1-5 m/s², irregular timing
            # Key: appears multi-axis and energetic like an earthquake, but
            # energy comes in short irregular bursts, not sustained
            num_fidgets = np.random.randint(3, 8)
            for _ in range(num_fidgets):
                fidget_start = np.random.randint(0, WINDOW_SIZE - 5)
                fidget_len = np.random.randint(3, 10)
                fidget_amp = np.random.uniform(1.0, 5.0)
                fidget_freq = np.random.uniform(2.0, 8.0)
                for axis in range(NUM_AXES):
                    axis_amp = fidget_amp * np.random.uniform(0.3, 1.0)
                    for i in range(fidget_start, min(fidget_start + fidget_len, WINDOW_SIZE)):
                        elapsed = (i - fidget_start) / 50.0
                        envelope = axis_amp * np.exp(-2.0 * elapsed)
                        base[i, axis] += envelope * np.sin(2 * np.pi * fidget_freq * elapsed + np.random.uniform(0, 2 * np.pi))

        elif activity == "pocket_shift":
            # Pocket shift: slow gravity drift from phone sliding/rotating in pocket
            # + intermittent bumps from leg movement
            # Key: slow low-amplitude baseline drift, occasional isolated bumps
            drift_speed = np.random.uniform(0.5, 2.0)  # Hz
            drift_amp = np.random.uniform(0.5, 2.0)
            for axis in range(NUM_AXES):
                phase = np.random.uniform(0, 2 * np.pi)
                drift = drift_amp * np.random.uniform(0.3, 1.0) * np.sin(2 * np.pi * drift_speed * t + phase)
                base[:, axis] += drift.astype(np.float32)
            # Add 2-5 intermittent bumps from body movement
            num_bumps = np.random.randint(2, 6)
            for _ in range(num_bumps):
                bump_pos = np.random.randint(3, WINDOW_SIZE - 3)
                bump_amp = np.random.uniform(1.5, 4.0)
                bump_len = np.random.randint(2, 5)
                for axis in range(NUM_AXES):
                    axis_amp = bump_amp * np.random.uniform(0.2, 1.0)
                    for j in range(min(bump_len, WINDOW_SIZE - bump_pos)):
                        decay = np.exp(-3.0 * j / bump_len)
                        base[bump_pos + j, axis] += axis_amp * decay

        samples.append(base)
    return np.array(samples, dtype=np.float32)


# ── Model Definition ─────────────────────────────────────────────────────────

def build_model():
    """Enhanced 1D CNN for [50, 3] -> [4] seismic classification.

    Architecture designed based on MyShake's published insights:
    - Multi-scale convolutions capture both high-freq P-wave spikes
      and low-freq S-wave oscillations
    - BatchNorm + Dropout for robust generalization
    - Deeper than original (3 conv blocks instead of 2) for better
      discrimination between earthquake and human activity patterns
    """
    model = keras.Sequential([
        keras.layers.Input(shape=(WINDOW_SIZE, NUM_AXES)),

        # Block 1: capture high-frequency features (P-wave spikes, taps)
        keras.layers.Conv1D(32, kernel_size=3, padding="same"),
        keras.layers.BatchNormalization(),
        keras.layers.ReLU(),
        keras.layers.MaxPooling1D(pool_size=2),

        # Block 2: capture medium-frequency patterns (walking, running)
        keras.layers.Conv1D(64, kernel_size=5, padding="same"),
        keras.layers.BatchNormalization(),
        keras.layers.ReLU(),
        keras.layers.MaxPooling1D(pool_size=2),

        # Block 3: capture low-frequency patterns (S-wave oscillation)
        keras.layers.Conv1D(64, kernel_size=7, padding="same"),
        keras.layers.BatchNormalization(),
        keras.layers.ReLU(),

        keras.layers.GlobalAveragePooling1D(),

        keras.layers.Dense(64),
        keras.layers.ReLU(),
        keras.layers.Dropout(0.4),

        keras.layers.Dense(32),
        keras.layers.ReLU(),
        keras.layers.Dropout(0.3),

        keras.layers.Dense(NUM_CLASSES, activation="softmax"),
    ])
    return model


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    # --- Generate synthetic data ---
    print("Generating synthetic data...")
    bg = generate_background(SAMPLES_PER_CLASS)
    pw = generate_p_wave(SAMPLES_PER_CLASS)
    sw = generate_s_wave(SAMPLES_PER_CLASS)
    ha = generate_human_activity(SAMPLES_PER_CLASS)

    print(f"  Background:      {bg.shape[0]} samples")
    print(f"  P-wave:          {pw.shape[0]} samples")
    print(f"  S-wave:          {sw.shape[0]} samples")
    print(f"  Human activity:  {ha.shape[0]} samples")

    x = np.concatenate([bg, pw, sw, ha], axis=0)
    y = np.concatenate([
        np.full(SAMPLES_PER_CLASS, 0),  # background
        np.full(SAMPLES_PER_CLASS, 1),  # p_wave
        np.full(SAMPLES_PER_CLASS, 2),  # s_wave
        np.full(SAMPLES_PER_CLASS, 3),  # human_activity
    ])
    y_onehot = keras.utils.to_categorical(y, NUM_CLASSES)

    # Shuffle
    indices = np.random.permutation(len(x))
    x, y_onehot, y = x[indices], y_onehot[indices], y[indices]

    # Split
    split = int(len(x) * (1 - VALIDATION_SPLIT))
    x_train, x_val = x[:split], x[split:]
    y_train, y_val = y_onehot[:split], y_onehot[split:]
    y_val_labels = y[split:]

    print(f"  Train: {x_train.shape[0]}, Val: {x_val.shape[0]}")

    # --- Build and train ---
    print("\nBuilding model...")
    model = build_model()
    model.summary()

    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=0.001),
        loss="categorical_crossentropy",
        metrics=["accuracy"],
    )

    callbacks = [
        keras.callbacks.EarlyStopping(
            monitor="val_accuracy",
            patience=10,
            restore_best_weights=True,
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=5,
            min_lr=1e-6,
        ),
    ]

    print("\nTraining...")
    history = model.fit(
        x_train, y_train,
        validation_data=(x_val, y_val),
        epochs=EPOCHS,
        batch_size=BATCH_SIZE,
        callbacks=callbacks,
        verbose=1,
    )

    # --- Evaluate ---
    val_loss, val_acc = model.evaluate(x_val, y_val, verbose=0)
    print(f"\nValidation accuracy: {val_acc:.4f}")
    print(f"Validation loss:     {val_loss:.4f}")

    # Per-class accuracy
    preds = model.predict(x_val, verbose=0)
    pred_labels = np.argmax(preds, axis=1)
    class_names = ["background", "p_wave", "s_wave", "human_activity"]
    print("\nPer-class results:")
    for i, name in enumerate(class_names):
        mask = y_val_labels == i
        if mask.sum() > 0:
            class_acc = (pred_labels[mask] == i).mean()
            print(f"  {name:16s}: {class_acc:.4f} ({mask.sum()} samples)")

    # Confusion matrix
    print("\nConfusion matrix (rows=true, cols=predicted):")
    print(f"{'':>16s}  {'bg':>6s}  {'p_wave':>6s}  {'s_wave':>6s}  {'human':>6s}")
    for i, name in enumerate(class_names):
        row = []
        for j in range(NUM_CLASSES):
            count = ((y_val_labels == i) & (pred_labels == j)).sum()
            row.append(f"{count:>6d}")
        print(f"  {name:>14s}  {'  '.join(row)}")

    # --- Convert to TFLite ---
    print("\nConverting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()

    # --- Validate TFLite ---
    print("Validating .tflite...")
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    input_shape = input_details[0]["shape"].tolist()
    output_shape = output_details[0]["shape"].tolist()
    input_dtype = input_details[0]["dtype"]
    output_dtype = output_details[0]["dtype"]

    print(f"  Input shape:  {input_shape}  dtype={input_dtype.__name__}")
    print(f"  Output shape: {output_shape}  dtype={output_dtype.__name__}")

    assert input_shape == [1, WINDOW_SIZE, NUM_AXES], f"Expected [1,{WINDOW_SIZE},{NUM_AXES}], got {input_shape}"
    assert output_shape == [1, NUM_CLASSES], f"Expected [1,{NUM_CLASSES}], got {output_shape}"
    assert input_dtype == np.float32, f"Expected float32 input, got {input_dtype}"
    assert output_dtype == np.float32, f"Expected float32 output, got {output_dtype}"
    print("  Shape/dtype checks passed!")

    # Test inference on one sample per class
    print("\n  Test predictions:")
    for i, name in enumerate(class_names):
        mask = y_val_labels == i
        sample = x_val[mask][0:1]  # [1, 50, 3]
        interpreter.set_tensor(input_details[0]["index"], sample)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]["index"])[0]
        pred = class_names[np.argmax(output)]
        probs = ", ".join(f"{p:.3f}" for p in output)
        print(f"    {name:16s} -> [{probs}]  predicted={pred}  sum={output.sum():.4f}")

    # --- Save ---
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    output_path = os.path.join(OUTPUT_DIR, OUTPUT_FILE)
    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_kb = len(tflite_model) / 1024
    print(f"\nSaved: {output_path} ({size_kb:.1f} KB)")
    print("Done!")


if __name__ == "__main__":
    main()
