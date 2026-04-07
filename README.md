# Minecraft AntiCheat - Android

**SS Tools AntiCheat** — Minecraft cheat scanner for Android. Detects cheat clients, macros, disguised mods, and suspicious activity on mobile Minecraft launchers.

## Features

- **Auto-detect all Minecraft launchers** — Zalith, Zalith 2, Mojo, PojavLauncher, FCL, HMCL-PE
- **Deep JAR inspection** — reads every `.class` file, extracts Java bytecode string constants
- **Mod authenticity verification** — catches cheats disguised as legitimate mods (e.g. fake sodium)
- **63 cheat signatures** — Crystal PvP, Sword PvP, 20+ cheat clients, macros
- **Log scanning** — scans latest.log for cheat client traces
- **Deleted file scanner** — Downloads, Temp, Cache, suspicious APKs
- **Chrome history scanner** — cheat-related URLs and downloads
- **Shizuku integration** — access `/Android/data/` without root
- **Zero false flags** — whitelist + fingerprint verification system
- **Modern dark UI** — Jetpack Compose Material 3

## Supported Launchers

| Launcher | Package |
|----------|---------|
| Zalith Launcher | `com.movtery.zalithlauncher` |
| Zalith Launcher 2 | `com.movtery.zalithlauncher2` |
| Mojo Launcher | `git.artdeell.mojo` |
| PojavLauncher | `net.kdt.pojavlaunch` |
| Fold Craft Launcher | `com.tungsten.fcl` |
| HMCL-PE | `com.tungsten.hmclpe` |

## Requirements

- Android 8.0+ (API 26)
- [Shizuku](https://shizuku.rikka.app/) for accessing launcher data directories
- Storage permission for scanning Downloads/Temp

## Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`
