# Bellini Cine 🎬

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![OpenGL ES](https://img.shields.io/badge/Graphics-OpenGL%20ES%203.2-5586A4?logo=opengl&logoColor=white)](https://khronos.org/opengles)

**Bellini Cine** is a desktop-grade, mobile-optimized professional motion graphics, compositing, visual effects, and video editing application for Android — directly inspired by Adobe After Effects.

---

## ⚡ Vision & Highlights

Traditional mobile video editors are limited to simple timeline trimming and preset filters. **Bellini Cine** brings true compositing power to touchscreens:
- **Hierarchical Layer Graph**: Solid, Footage, Text, Vector Shape, Null, Adjustment, Camera, Light, and Nested Precomposition layers with parent-child transform cascades.
- **Precision Keyframe & Bezier Curves**: Spatial motion paths, sub-frame temporal easing, velocity influence handles, and a dedicated mobile-friendly **Speed & Value Graph Editor**.
- **After Effects Procedural Expressions**: Real-time evaluation of mathematical and procedural motion (`wiggle()`, `linear()`, `ease()`, `loopOut()`, `time`, `value`).
- **Hardware-Accelerated Compositing**: Multi-pass offscreen Framebuffer (FBO) pipeline supporting 20+ After Effects blend modes (Multiply, Screen, Overlay, Color Dodge, Add, Difference, etc.), Bezier vector masks, and Alpha/Luma track mattes.
- **Visual Effects & Shaders**: Separable Gaussian blur, Glow & bloom, chromatic aberration, LUT color grading, and displacement maps running on OpenGL ES 3.2 / Vulkan shaders.
- **Hardware Video Acceleration**: Zero-copy surface rendering and decoding via Android MediaCodec, multi-track audio mixing, and 4K/60fps export.

---

## 🏗️ Architecture

```
com.bellinicine
├── core
│   ├── model          # Composition, Layer, Transform, Keyframe, Mask, Effect, BlendMode
│   ├── engine         # Scene graph evaluation, Bezier spline solver, Expression engine, Graph math
│   └── render         # OpenGL ES 3.2 shaders, FBO ping-pong compositor, Matrix transforms
└── ui
    ├── theme          # Pro dark theme (After Effects zinc, neon violet, keyframe amber)
    ├── workspace      # Dockable multi-panel layout & ViewModel (playback clock, selection)
    ├── viewport       # Composition canvas with pan, pinch-zoom, and 8-point transform gizmos
    ├── timeline       # Multi-track timeline, time ruler scrubbing, CTI playhead, in/out trimming
    ├── grapheditor    # Interactive touch Bezier speed and value curve editor
    └── inspector      # Layer properties, blend mode dropdown, opacity, stopwatch keyframes
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
```

---

## 📋 Comprehensive Feature Map (Parts 1–80)

Bellini Cine is designed to implement the complete 80-part After Effects mobile specification:
1. **Interface & Workspaces**: Dockable panels, viewport canvas, multi-track timeline, inspector, asset bins.
2. **Composition & Time**: Custom resolutions (9:16 vertical, 16:9, square), frame rates (24, 30, 60 fps), work areas.
3. **Layer Hierarchy**: 2D/3D affine cascades ($M_{world} = M_{parent} \times M_{local}$), anchor points, controller nulls.
4. **Keyframing & Graph Editor**: Linear, Bezier, Hold, velocity influence, spatial trajectories.
5. **Procedural Expressions**: JavaScript-compatible expression runtime for algorithmic motion.
6. **Vector Shapes & Typography**: Parametric shapes, trim paths, range selectors, variable fonts.
7. **Masks & Mattes**: Bezier masks (Add, Subtract, Intersect, Feather), Alpha and Luma track mattes.
8. **Visual Effects & Compositing**: Multi-pass GLSL shaders, 20+ blend modes, Gaussian blurs, glow, LUT color grades.
9. **Rendering & Export**: MediaCodec hardware encode, ProRes / MP4 / transparent WebM export.
