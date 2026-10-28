---
name: android-emulator
description: >-
  Starts the MileageTracker Android emulator (Medium_Phone_API_36) and places
  its window fully on the primary screen. Use whenever the user asks to start,
  launch, open, or boot the emulator, or whenever the emulator window is open.
  Always position the window; the user does not need to ask.
---

# Android emulator

From the repo root. Do both steps every time. Positioning is part of starting the emulator.

1. If `adb devices` shows no emulator, start it in the background:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd Medium_Phone_API_36 -netdelay none -netspeed full -gpu host -no-boot-anim
```

2. As soon as the window exists, run:

```powershell
powershell -NoProfile -File scripts/position-emulator.ps1
```

That puts the window on the right side of the primary work area, above the taskbar, and shrinks it if it is taller than the screen. It also writes `window.x` and `window.y` into that AVD's `emulator-user.ini`.

If the script prints `NO_EMULATOR`, wait and run it again until it prints `PLACED`.

Leave the window fully on screen. Do not leave it above the display, under the taskbar, or on a monitor that is not connected.
