package it.tugaia56.obsidian.xposed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.xposed.hooks.framework.MonetFreeze;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstNotifStyleMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsBackground;
import it.tugaia56.obsidian.xposed.hooks.systemui.VolumePanelMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderImage;
import it.tugaia56.obsidian.xposed.hooks.systemui.StatusbarClock;
import it.tugaia56.obsidian.xposed.hooks.systemui.BatteryDataProvider;
import it.tugaia56.obsidian.xposed.hooks.systemui.BatteryStyleManager;
import it.tugaia56.obsidian.xposed.hooks.systemui.BatteryBar;
import it.tugaia56.obsidian.xposed.hooks.systemui.StatusbarPadding;
import it.tugaia56.obsidian.xposed.hooks.systemui.NotificationMods;
import it.tugaia56.obsidian.xposed.hooks.systemui.StatusbarIcons;
import it.tugaia56.obsidian.xposed.hooks.systemui.StatusbarMods;
import it.tugaia56.obsidian.xposed.hooks.systemui.MiscMods;
import it.tugaia56.obsidian.xposed.hooks.systemui.FingerprintIconMods;
import it.tugaia56.obsidian.xposed.hooks.systemui.LockScreenButtonsMods;
import it.tugaia56.obsidian.xposed.hooks.systemui.LockScreenMiscMods;
import it.tugaia56.obsidian.xposed.hooks.systemui.AlbumArtLockscreenMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.LockscreenClockMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.AodClockMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.HoldBackGesture;
import it.tugaia56.obsidian.xposed.hooks.systemui.GestureNavZones;
import it.tugaia56.obsidian.xposed.hooks.systemui.NavbarIconColorMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsTransparencyMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsPulldownMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsMyDeviceMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsTilesMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsTilesCustomizeMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.AodWeather;
import it.tugaia56.obsidian.xposed.hooks.systemui.LockscreenWeather;
import it.tugaia56.obsidian.xposed.hooks.systemui.LockscreenWidgetsMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsWidgetsMod;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsSeparateMod;
import it.tugaia56.obsidian.xposed.hooks.framework.LockScreenPowerMenuMod;
import it.tugaia56.obsidian.xposed.hooks.settings.CustomShortcut;
import it.tugaia56.obsidian.xposed.hooks.settings.SettingsCardBackgroundMod;
import it.tugaia56.obsidian.xposed.hooks.launcher.LauncherMod;
import it.tugaia56.obsidian.xposed.hooks.launcher.LauncherCardBackgroundMod;

public class ModPacks {
    // 2026-09-05: elenco lungo fornito dall'utente (app di sistema già temate via Substratum),
    // verificate TUTTE una per una via script su device (unzip -p + grep sul dex reale) prima di
    // aggiungerle — 58 su 61 avevano davvero le classi COUI card. Vedi SettingsCardBackgroundMod
    // per l'elenco commentato e i 3 esclusi (nessuna classe COUI: cellbroadcastreceiver,
    // providers.media, engineermode).
    private static final Set<String> CARD_BG_PACKAGES = new HashSet<>(Arrays.asList(
            "com.oplus.wirelesssettings", "com.oplus.cast", "com.oplus.linker",
            "com.oplus.wallpapers", "com.oplus.notificationmanager", "com.oplus.uxdesign",
            "com.oplus.sos", "com.oplus.battery", "com.oplus.pantanal.ums",
            "com.coloros.operationManual", "com.heytap.mydevices", "com.android.phone",
            "com.oplus.cota", "com.oplus.ota", "com.oplus.multiapp", "com.oplus.games",
            "com.coloros.smartsidebar", "com.oplus.beaconlink", "com.oplus.appbooster",
            "com.oneplus.calculator", "com.oplus.safecenter", "com.oplus.keyguard.clock.base",
            "com.coloros.systemclone", "com.oplus.eyeprotect", "com.heytap.accessory",
            "com.oplus.remotecontrol", "com.oplus.aiwriter", "com.oplus.melody",
            "com.oneplus.gallery", "com.oplus.camera", "com.oplus.gesture",
            "com.oplus.securitypermission", "com.oplus.phonemanager", "com.android.server.telecom",
            "com.heytap.browser", "net.oneplus.weather", "com.oplus.aimemory",
            "com.coloros.scenemode", "com.oplus.aiunit", "com.oplus.uiengine",
            "com.oplus.pscanvas", "com.oneplus.account", "com.oneplus.oshare",
            "com.oneplus.deskclock", "com.oplus.contentportal", "com.oplus.securepay",
            "com.oplus.apprecover", "com.coloros.bootreg", "com.oplus.screenrecorder",
            "com.oppo.quicksearchbox", "com.coloros.colordirectservice", "com.oplus.screenshot",
            "com.heytap.pictorial", "com.oplus.aod", "com.android.wallpaper.livepicker",
            "com.coloros.floatassistant", "com.coloros.assistantscreen",
            "com.coloros.accessibilityassistant", "com.oplus.trafficmonitor", "com.oplus.vdc",
            "com.coloros.video"
    ));

    public static List<Class<? extends XposedMods>> getMods(String packageName) {
        List<Class<? extends XposedMods>> mods = new ArrayList<>();
        if (Constants.Packages.SYSTEM_UI.equals(packageName)) {
            mods.add(MonetFreeze.class);
            mods.add(QsBackground.class);
            mods.add(QsHeaderImage.class);
            mods.add(QsHeaderClock.class);
            mods.add(StatusbarMods.class);
            mods.add(StatusbarIcons.class);
            mods.add(StatusbarClock.class);
            mods.add(DstNotifStyleMod.class);
            mods.add(VolumePanelMod.class);
            mods.add(BatteryDataProvider.class);
            mods.add(BatteryStyleManager.class);
            mods.add(BatteryBar.class);
            mods.add(AodWeather.class);
            mods.add(StatusbarPadding.class);
            mods.add(NotificationMods.class);
            mods.add(MiscMods.class);
            mods.add(FingerprintIconMods.class);
            mods.add(LockScreenButtonsMods.class);
            mods.add(LockScreenMiscMods.class);
            mods.add(AlbumArtLockscreenMod.class);
            mods.add(LockscreenClockMod.class);
            mods.add(AodClockMod.class);
            mods.add(LockscreenWeather.class);
            mods.add(LockscreenWidgetsMod.class);
            mods.add(QsWidgetsMod.class);
            mods.add(QsSeparateMod.class);
            mods.add(HoldBackGesture.class);
            mods.add(GestureNavZones.class);
            mods.add(NavbarIconColorMod.class);
            mods.add(QsTransparencyMod.class);
            mods.add(QsPulldownMod.class);
            mods.add(QsMyDeviceMod.class);
            mods.add(QsTilesMod.class);
            mods.add(QsTilesCustomizeMod.class);
        }
        if (Constants.Packages.SETTINGS.equals(packageName)) {
            mods.add(CustomShortcut.class);
            mods.add(SettingsCardBackgroundMod.class);
        }
        // 2026-09-04/05: card "invisibili" + sfondo pagina estesi alle app OEM raggiunte da
        // dentro Impostazioni — verificate PRIMA di aggiungerle (stesse classi COUI di Settings
        // nel dex reale, vedi CARD_BG_PACKAGES sopra).
        if (CARD_BG_PACKAGES.contains(packageName)) {
            mods.add(SettingsCardBackgroundMod.class);
        }
        if (Constants.Packages.FRAMEWORK.equals(packageName)) {
            mods.add(LockScreenPowerMenuMod.class);
        }
        if (Constants.Packages.LAUNCHER.equals(packageName)) {
            mods.add(LauncherMod.class);
            mods.add(LauncherCardBackgroundMod.class);
        }
        return mods;
    }
}
