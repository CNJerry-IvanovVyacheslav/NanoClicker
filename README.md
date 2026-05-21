# NanoClicker

A hyper-optimized, zero-dependency cosmic incremental game built entirely on low-level Android APIs, manual physics, and runtime procedural generation. The final compiled and optimized Release APK occupies exactly **28.1 KB** of storage space.

## Engineering Philosophy & Constraints

Modern mobile development often suffers from severe framework overhead. `NanoClicker` serves as a proof of concept demonstrating that deep graphical, auditory, and persistence mechanics can be achieved within extreme binary size boundaries by bypassing high-level abstraction layers (such as Jetpack Compose, XML layouts, Room, or MediaPlayer) and interacting directly with core platform subsystems.

## Technical Deep Dive

### 1. Zero-Dependency Build Configuration
The `dependencies` block within `app/build.gradle.kts` is completely empty. The project relies strictly on the core Android SDK platform APIs and the minimal Kotlin standard library. Size minimization is enforced at the compiler level via aggressive R8 shrinking, resource stripping (`isShrinkResources = true`), and optimization rules (`proguard-android-optimize.txt`).

### 2. Math-Driven Procedural Graphics Engine
The application embeds **zero image assets** (no PNGs, WebPs, or Vector Drawables). The UI and game entities are drawn deterministically on a single custom `View` during the hardware-accelerated `onDraw(canvas: Canvas)` pass:
* **Twinkling Starfield:** 150 independent stars are tracked in memory. Twinkling effects are driven via time-variant sinusoids modifying individual alpha channels (`(sin(time * speed + phase) * 100 + 155)`).
* **Planetary Evolution:** 9 evolution stages (from an irregular Asteroid to a Black Hole featuring an accretion disk and a white photon ring) are drawn entirely via vector primitives (`drawCircle`, `drawRect`, `drawOval`, `drawLine`).
* **Orbital Mechanics & Ray-Casting:** Automated probes track orbital paths utilizing real-time trigonometry (`cos(angle)` and `sin(angle)`) and cast synchronized neon laser beams toward the planet using interleaved frame timing modulos.
* **Particle Physics System:** Tap events instantiate localized particle arrays governed by high-velocity delta-time physics, gravity constants (`vy += 500f * dt`), and progressive alpha decays.

### 3. Real-Time Raw PCM Audio Synthesis
Traditional audio containers (.mp3, .ogg) are too heavy for an ultra-low-footprint binary. `NanoClicker` integrates a custom runtime software synthesizer via Android's low-level `AudioTrack` API configured for static, zero-latency playback (`AudioTrack.MODE_STATIC` with `ENCODING_PCM_16BIT`).
* Pure sinusoidal sound waves are generated algorithmically into short arrays in the RAM.
* Frequency sweeps handle different auditory cues: a sharp down-sweep ($800\text{Hz} \rightarrow 400\text{Hz}$) for standard clicks, an up-sweep ($300\text{Hz} \rightarrow 1200\text{Hz}$) for upgrades, and a deep log-sweep ($1000\text{Hz} \rightarrow 50\text{Hz}$) to simulate cosmic collapse during prestige resets.

### 4. Exponential Moving Average Sensor Smoothing (3D Parallax)
The application leverages the hardware `Sensor.TYPE_ACCELEROMETER` to create a perspective 3D depth layout. Because raw hardware sensor data streams are highly volatile and suffer from high-frequency noise, the engine pipes the vectors through a mathematical **Low-Pass Filter (Exponential Moving Average)**:

$$\text{Smoothed}_t = \text{Smoothed}_{t-1} + \alpha \times (\text{Raw}_t - \text{Smoothed}_{t-1})$$

Where $\alpha = 0.1$. This converts erratic hand micro-tremors into incredibly stable, organic camera panning, with background objects shifting slower than foreground layers based on simulated Z-index depth values.

### 5. Lightweight State Serialization & Delta-Time Idle Engine
* **Storage:** State persistence avoids heavy SQLite wrappers, operating instead through atomic native `SharedPreferences` serialization.
* **Idle Mechanics:** Rather than spawning a persistent background service or worker thread that wastes system resources and battery life, the application logs an epoch timestamp (`System.currentTimeMillis()`) to storage upon suspension. When a resume lifecycle target is hit, the engine evaluates the exact delta-time gap mathematically, processes the offline earnings multiplier, and triggers a singular UI announcement call.
