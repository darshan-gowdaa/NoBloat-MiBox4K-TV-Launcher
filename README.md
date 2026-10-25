# NoBloat-MiBox4K-TV-Launcher

> **Ultra-Lightweight, Zero-Bloat, tvOS & Material 3 Expressive Launcher for Android TV & Mi Box 4K**

A high-performance Android TV launcher inspired by Apple TV tvOS liquid frosted glass aesthetics, engineered specifically for resource-constrained hardware such as the **Xiaomi Mi Box 4K** (Amlogic S905X, ARM Cortex-A53, ARM Mali-450 GPU) running Android 9 Pie (API 28).

---

## ✨ Key Features

- **Liquid Glass & Apple tvOS Aesthetics**: Pure white frosted glass pills, caustic outline reflections, and fluid depth without heavy blurs.
- **Dynamic Chromatic Ambient Glow**: Real-time 4-orb radial shader canvas that continuously drifts and extracts dominant accent colors from focused apps and album artwork.
- **Ultra-Smooth Media Player**:
  - Live MediaSession tracking for background playback (Spotify, BitChord, YouTube Music, etc.).
  - Butter-smooth scale-fade album artwork transitions and vertical slide-fade metadata typography on song changes.
  - Seek-safe embedded animated visualizer equalizer.
- **Modularized Architecture**:
  - `BluetoothDialogManager`: Smart TV accessory recognition (gamepads, headphones, soundbars, remotes) with integrated modal focus trapping.
  - `SettingsDialogManager`: Instant access to essential Android TV settings (Display, Network, Bluetooth, Apps).
  - `AppMenuDialogManager`: Fluid contextual modal (Open, Pin/Unpin Favorites, Move, Force Stop, Uninstall).
  - `RecentsOverlayManager`: Multi-tasking carousel with single-click RAM clearing and animated feedback.
  - `TileReorderManager`: Native interactive D-pad app reordering with boundary bumpers and automatic preference persistence.
- **Extreme Performance**:
  - **APK Size**: ~225 KB (Zero bundled multi-megabyte dependencies, zero bloat).
  - **Frame Rate**: Locked 60 fps on Mali-450 GPU via pre-allocated matrix transformations and zero-allocation rendering loops.
  - **Memory Footprint**: Sub-40MB RAM usage.

---

## 🛠 Project Structure

```
NoBloat-MiBox4K-TV-Launcher/
├── app/
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── fonts/Outfit*.ttf
│           ├── java/com/appletv/launcher/
│           │   ├── MainActivity.java
│           │   ├── AppInfo.java
│           │   ├── ColorExtractor.java
│           │   ├── AppleMusicGradientView.java
│           │   ├── BluetoothTracker.java
│           │   ├── RecentAppsManager.java
│           │   ├── AppPreferencesManager.java
│           │   ├── SystemMetricsTracker.java
│           │   ├── dialog/
│           │   │   ├── BluetoothDialogManager.java
│           │   │   ├── SettingsDialogManager.java
│           │   │   └── AppMenuDialogManager.java
│           │   ├── media/
│           │   │   ├── MediaWidgetManager.java
│           │   │   └── MediaNotificationListener.java
│           │   ├── recents/
│           │   │   └── RecentsOverlayManager.java
│           │   ├── reorder/
│           │   │   └── TileReorderManager.java
│           │   ├── widget/
│           │   │   ├── EqualizerView.java
│           │   │   └── MarqueeTextView.java
│           │   └── util/
│           │       └── UIUtils.java
│           └── res/
│               ├── drawable/
│               ├── layout/
│               └── values/
├── build_launcher.py
├── build.gradle
└── settings.gradle
```

---

## 🚀 Building & Deploying

### Option 1: Standalone Python Toolchain (Recommended, Instant Build)

Requires Android Build Tools (aapt2, d8, apksigner) and JDK 17:

```bash
python build_launcher.py
```

This compiles resources, links them, compiles Java sources, dexes them, and outputs a signed, aligned APK at `appletv-launcher.apk` in under 5 seconds.

### Option 2: Deploy to TV via ADB

```bash
adb connect <TV_IP_ADDRESS>:5555
adb install -r appletv-launcher.apk
adb shell am start -n com.appletv.launcher/.MainActivity
```

---

## 📜 License

MIT License. Designed and optimized for maximum speed, simplicity, and elegance.
