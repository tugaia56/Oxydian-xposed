# Oxydian — Tutorial

🌐 **English** · [Italiano](TUTORIAL.it.md) · [← Back to README](../README.md)

A step-by-step guide: from installation to your first customisations.

---

## 1. What you need

- A **OnePlus / OPPO** phone with **OxygenOS** (tested on OnePlus 12, OxygenOS 16.1), Android 12 or newer
- **Root**: KernelSU, KernelSU Next or Magisk
- **LSPosed** (or another Xposed framework) installed and working
- Optional but recommended: the companion app **[Oxydian Theme](https://github.com/tugaia56/Oxydian-Theme/releases/latest)**. It does not need Xposed and handles the real overlay themes (accent and background colours, app themes, icons, PIN keypad…). Oxydian reads the colours chosen there, so the two apps work best together.

> Oxydian is designed for the **dark theme**. In light mode a few system-look mods (notification styles, toast, dialog) stay switched off and the stock look is left untouched. See [Light theme](#7-light-theme).

---

## 2. Installation

1. Download the latest **Oxydian** APK from the [Releases page](https://github.com/tugaia56/Oxydian-xposed/releases/latest) and install it.
2. Open **LSPosed → Modules**, find **Oxydian** and switch it **on**.
3. Open the module and check its **scope** (the apps it works inside). The four essential ones are:
   - `android` (System Framework)
   - `com.android.systemui` (SystemUI)
   - `com.android.settings` (Settings)
   - `com.android.launcher` (Launcher)

   The other apps in the list are optional: they are only used to colour the card backgrounds inside those apps. Leave them as they are unless you know you don't need them.
4. **Reboot** the phone. This is required the first time, and also after every update of the Oxydian APK: a SystemUI restart alone can keep running the old code.
5. Open Oxydian and grant **root** access when asked.

---

## 3. A quick tour of the app

At the bottom you find four tabs.

### Oxydian (first tab)

| Entry | What it is for |
|---|---|
| **Launcher** | Recents and other launcher tweaks: columns and rows, folders (including *auto-close folders*), dock, home layout |
| **Power Menu** | Full power-menu customisation: colours, backgrounds, advanced reboot (recovery, bootloader, SystemUI restart, screenshot…) with optional authentication |
| **Battery Icon Style** | Custom battery icons and charging icon |
| **Notification & Toast Styles** | Notification style, textures, borders, corner radius, toast and navigation bar |
| **Misc** | Gesture navigation, rotation button, USB dialog, screenshot enabler and more |
| **Oxydian Theme** | Shortcut to the companion app (accent, background, icons, PIN keypad, app themes) |

### Mods

| Entry | What it is for |
|---|---|
| **Status Bar** | Icons, clock, notification options, battery styles, hide a SIM icon (SIM 1 and SIM 2 separately) |
| **Quick Settings Panel** | Tile background and shape, header image and clock, widgets, transparency and blur |
| **Lock Screen** | Widgets, clock, weather, album art, fingerprint icon, buttons |
| **Always-On Display** | AOD clock, weather, edge lighting |
| **Volume Panel** | Colours and button presets |

### Search

Type a word (for example *clock* or *blur*) and Oxydian finds every option that matches, across the whole app. Tap a result to jump straight to it.

### Settings

| Entry | What it is for |
|---|---|
| **General** | Language, app theme (light / dark / system), default tab, extra logs |
| **Backup** | Save, restore or clear all your preferences |
| **Update** | Check for a new version (manually or automatically, on Wi-Fi only) |
| **About** | Version and credits |
| **Oxydian Status** | Shows which mods installed correctly at the last start of each process (SystemUI, Settings, Launcher, Framework) |
| **Restart SystemUI** | Applies the changes that need it |

---

## 4. Your first customisation

Let's change something you can see right away.

1. Go to **Mods → Quick Settings Panel**.
2. Open **Customize Quick Settings Tiles** and scroll to **Tile Background**. Pick a colour (you can also link it to the accent colour).
3. Tap the **Restart SystemUI** icon (top bar of the app, or *Settings → Restart SystemUI*).
4. Swipe down the quick panel: the new look is there.

### Applying changes

- Most mods apply after **Restart SystemUI**. The screen goes black for a couple of seconds and comes back: that is normal.
- Some options act on other parts of the system (the **Launcher**, **Settings**, the **framework**) and may need a **reboot**. If an option does not seem to work, reboot once before assuming it is broken.
- Many pages remind you when a restart is needed ("Restart SystemUI to apply").

---

## 5. Colours: Oxydian + Oxydian Theme

Oxydian does not repaint the whole system by itself. The system colours (accent and background) are chosen in **Oxydian Theme**, which builds real overlays; Oxydian then uses the same accent for its own mods (borders, tiles, power menu…).

Suggested order:

1. Install **Oxydian Theme**, choose accent and background, apply, reboot.
2. Open Oxydian and customise the rest: it follows the accent you chose.

---

## 6. Backup and restore

Before experimenting a lot, save your settings.

1. Go to **Settings → Backup**.
2. **Backup** saves a `.json` file in the folder you choose. The file name contains the date and time, so you can tell backups apart.
3. **Restore** opens a file picker: select a backup and your preferences are brought back.
4. **Clear** resets every preference to the default.

Keep a copy of the backup outside the phone (cloud or PC) before a clean ROM install.

---

## 7. Light theme

Oxydian is 100 % compatible with the **dark theme**. With the system in light mode:

- the app's own interface works in light mode;
- the mods that only make sense on dark (notification styles, toast, dialog) stay **off**, and the stock OxygenOS look is left alone.

For the best result keep Android in dark mode.

---

## 8. If something goes wrong

| Problem | What to do |
|---|---|
| A mod does nothing | Reboot once. Then open **Settings → Oxydian Status** and check that the mod is listed as working. |
| After a ROM update some mods stopped | OxygenOS can rename internal parts. Oxydian Status shows which ones are affected; update Oxydian and report the problem with the log. |
| The interface restarts in a loop | In LSPosed, switch **Oxydian** off (or remove its scopes), reboot, then re-enable the mods one group at a time. |
| Options look unchanged after installing a new Oxydian version | Do a full **reboot** (not only a SystemUI restart). |
| The root request never appears | Check that Oxydian is allowed in your root manager (KernelSU / Magisk). |

To report a bug, turn on **Settings → General → Extra Logs**, reproduce the problem and open an [issue](https://github.com/tugaia56/Oxydian-xposed/issues) describing what you did, your device and your OxygenOS version.

---

## 9. Good to know

- Oxydian is built on the work of **Oxygen Customizer** by Luigi (@LC9889); see the credits in the README.
- Every release comes with bilingual (English / Italian) release notes on the Releases page.
