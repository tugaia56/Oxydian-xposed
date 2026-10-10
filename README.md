# Oxydian

🌐 **English** · [Italiano](README.it.md)

**Oxydian** is an Xposed/LSPosed module for OnePlus/OPPO devices (OxygenOS / ColorOS) with root. It turns the system into a complete, consistent dark theme — not just a colour filter — and adds many system mods.

Much of the system modding comes from the work of **Oxygen Customizer** by Luigi (@LC9889), integrated, adapted and extended here.

## Companion app: Oxydian Theme

The real **overlay themes** (themes for apps, Dark Shadow for Settings / SystemUI / Launcher / OnePlus apps, Wi-Fi / signal / navigation icons, PIN keypad, progress bar, accent and background colours) live in a separate app that does **not** need Xposed:

➡️ **[Oxydian Theme](https://github.com/tugaia56/Oxydian-Theme/releases/latest)**

Oxydian reads the accent and background chosen in Oxydian Theme, so the two apps are best installed together.

## Features

- **Status bar** — icons, clock, notification options, hide a SIM icon, battery styles
- **Lock screen** — widgets, clock, weather, album art, fingerprint icon
- **Always-On Display** — edge lighting, clock, weather
- **Quick Settings** — tile background, header image and clock, widgets, transparency and blur
- **Notification panel** — styles, textures, borders, adjustable corners
- **Volume panel** — colours and style
- **Power menu** — colours, backgrounds, advanced reboot with authentication
- **Launcher** — columns, folders, dock, home layout
- **Miscellaneous** — gesture navigation, screenshot enabler, CorePatch, and more
- Automatic **update check**

## Tutorial

New to Oxydian? Follow the step-by-step **[Tutorial](docs/TUTORIAL.md)** (installation, app tour, first customisation, backup, troubleshooting).

## Nothing changes by itself

Installing Oxydian changes nothing. The mods start only after you enable the module in LSPosed, and only on **OxygenOS / ColorOS**: on any other ROM (AOSP, LineageOS, Matrixx…) Oxydian does nothing at all. On OxygenOS, once enabled, it applies its default dark look, and every option can be changed or turned off in the app.

## Requirements

- OnePlus / OPPO device with **OxygenOS** (tested on OnePlus 12, OxygenOS 16.1), Android 12+
- **Root** (KernelSU / KernelSU Next or Magisk)
- **LSPosed** (or another Xposed framework) with Oxydian enabled for the system scopes

## Theme compatibility

Oxydian is 100% compatible with the dark theme. The light theme is supported for the app's own interface, but a few system-look mods (notification styles, toast, dialog) are designed and tested for the dark theme only: with the system in light mode they stay switched off and the stock OxygenOS look is left untouched.

For the best experience, use Oxydian with Android in dark theme.

## Build

```
./gradlew assembleDebug
```

A release is built by the GitHub workflow when a tag `vX.Y.Z` is pushed (it also posts the changelog and the APK to Telegram when configured).

## Credits

- **Luigi (@LC9889)** — Oxygen Customizer, the base of most mods
- **LuckyTool**, **CorePatch**, **DisableFlagSecure** — features integrated from these projects
- **Claude** (Anthropic) — AI assistant used throughout development
