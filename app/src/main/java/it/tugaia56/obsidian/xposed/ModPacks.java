package it.tugaia56.obsidian.xposed;

import java.util.ArrayList;
import java.util.List;

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
