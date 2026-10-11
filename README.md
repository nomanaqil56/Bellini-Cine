# Bellini Cine 🎬

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![OpenGL ES](https://img.shields.io/badge/Graphics-OpenGL%20ES%203.2-5586A4?logo=opengl&logoColor=white)](https://khronos.org/opengles)

**Bellini Cine** is a desktop-grade, mobile-optimized professional motion graphics, compositing, visual effects, and video editing application for Android — directly inspired by Adobe After Effects.

---

## ⚡ Vision & Status Overview

Bellini Cine aims to deliver After Effects-grade compositing and motion graphics on mobile touchscreens. To maintain complete engineering integrity, the codebase distinguishes between **implemented & unit-tested capabilities**, **partially implemented modules**, and **planned architectural milestones**.

### Current Implementation Status

| Subsystem | Status | Details |
| :--- | :--- | :--- |
| **Workspace & UI** | ✅ Implemented | Jetpack Compose multi-tab workspace (Timeline, Graph Editor, Effects Library, Project Assets, Render Queue), Property Inspector, Canvas Viewport. |
| **Project Persistence** | ✅ Implemented & Unit-Tested | Multi-composition atomic JSON persistence with `.bak` crash recovery, schema versioning (`v0 -> v1` migrations), debounced & lifecycle-triggered non-blocking autosave. |
| **Software Compositor** | ✅ Implemented & Unit-Tested | Skia Android Canvas software pipeline with affine 2D transform cascades ($M_{world} = M_{parent} \times M_{local}$), vector masks, and straight-color track mattes. Primary authoritative renderer. |
| **Track Mattes** | ✅ Implemented & Unit-Tested | Offscreen Alpha, Alpha Inverted, Luma, and Luma Inverted mattes using ITU-R BT.709 coefficients and straight color preservation. |
| **Effect Pipeline** | ✅ Implemented & Unit-Tested | Central `EffectRegistry` with 7 registered pixel effects: Fast Gaussian Blur, Glow (with Threshold isolation), Brightness/Contrast, Tint, Invert, Hue/Saturation (Euler matrix), Solid Composite. Rejects unknown effects. |
| **Media Import (SAF)** | ✅ Implemented | Storage Access Framework (`content://` URI) document picker, persistable URI permissions, project media bin, footage relinking, missing media alerts. |
| **Video Decoding** | 🟡 Partially Implemented | `MediaMetadataRetriever` extraction with bounded 64 MB LRU cache, hardware rotation handling, and time-stretch/reverse mapping (`OPTION_CLOSEST`). Exact frame-accurate NDK decoding is planned. |
| **Video Export** | 🟡 Partially Implemented | `MediaCodec` H.264 MP4 export with strict color negotiation (NV12 / I420), plane-aware buffer feeding, backpressure draining, guaranteed EOS acknowledgment, and container/playback validation. Physical device verification pending. |
| **Expression Engine** | 🟡 Partially Implemented | Recursive-descent expression parser supporting basic mathematical expressions, `time`, `wiggle()`, `linear()`, `ease()`, `loopOut()`. Complete JS runtime is planned. |
| **GPU Compositor** | 🟡 Scaffolded | Experimental OpenGL ES 3.2 FBO shaders and `GlRenderer` scaffolded for solids and background compositing; primary composition rendering authoritative through software pipeline. |
| **3D Scene Graph** | ⚪ Planned | 3D layer models, Camera, and Light data structures defined; true 3D projection, depth sorting, and GLTF mesh import scheduled for Phase 7. |
| **Audio Engine** | ⚪ Planned | Audio layer models defined; waveform visualization, multi-track mixing, and muxed audio export scheduled for Phase 6. |

---

## 🏗️ Architecture

```
com.bellinicine
├── core
│   ├── model          # Composition, Layer, Transform, Keyframe, Mask, Effect, BlendMode, Project
│   ├── engine         # Scene graph evaluation, Bezier spline solver, Expression engine, Graph math
│   ├── render         # CompositorPipeline (software), EffectProcessor, VideoFrameDecoder, ExportEngine, GlRenderer (experimental)
│   └── storage        # ProjectRepository (atomic file staging, .bak recovery, schema migrations)
└── ui
    ├── theme          # Pro dark theme (After Effects zinc, neon violet, keyframe amber)
    ├── workspace      # Multi-panel workspace layout & ViewModel (playback clock, selection, autosave)
    ├── viewport       # Composition canvas with pan, pinch-zoom, and 8-point transform gizmos
    ├── timeline       # Multi-track timeline, time ruler scrubbing, CTI playhead, in/out trimming
    ├── grapheditor    # Interactive touch Bezier speed and value curve editor
    └── inspector      # Layer properties, blend mode dropdown, opacity, effect parameter sliders
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- Android SDK Platform 35 (minSdk 26, targetSdk 35)
- JDK 17 / JDK 21

### Building & Running
```bash
# Clone the repository
git clone https://github.com/nomanaqil56/Bellini-Cine.git

# Navigate to project
cd "Bellini Cine"

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## 📋 Roadmap Phases

1. **Phase 0 (Current)**: Reliability & Correctness — Export pipeline, multi-composition atomic persistence, schema migrations, track mattes, effect parameters, SAF media bin.
2. **Phase 1**: Media & Project Management — Audio import, media thumbnails, folder bins, portable project packaging.
3. **Phase 2**: Animation Engine — Generic keyframing, Bezier curve tangents, Speed/Value Graph Editor.
4. **Phase 3**: Advanced Compositing & Effects — Bezier vertex mask editing, expansion/feathering, expanded VFX library.
5. **Phase 4**: Vector Graphics & Typography — Trim Paths, repeaters, text animators, typography layout.
6. **Phase 5**: Footage VFX — Chroma keying, point tracking, planar tracking, rotoscoping.
7. **Phase 6**: Audio Finishing — Waveform generation, gain/pan fades, audio-reactive motion, MP4 audio muxing.
8. **Phase 7**: Genuine 3D — Perspective camera, lights, shadows, GLTF/GLB models, depth compositing.
9. **Phase 8 & 9**: Mobile Optimization & Production — Adaptive preview quality, disk caching, thermal management.

