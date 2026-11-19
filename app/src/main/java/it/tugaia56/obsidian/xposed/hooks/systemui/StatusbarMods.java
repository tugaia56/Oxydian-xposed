package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Status bar / notification removal mods.
 *
 * Features (each controlled by its own pref):
 *  - remove_usb_dialog               → UsbService
 *  - remove_low_battery_notification → OplusPowerNotificationWarnings
 *  - remove_charging_complete_notification → OplusPowerNotificationWarnings (arg==7)
 *  - remove_flashlight_notification  → FlashlightNotification
 *  - remove_dev_mode                 → SystemPromptController + StatusBarFeatureOption
 */
public class StatusbarMods extends XposedMods {

    private boolean mRemoveUsb           = false;
    private boolean mRemoveLowBattery    = false;
    private boolean mMuteLowBatterySound = false;   // da OC: mute_low_battery_sound
    private boolean mRemoveChargingDone  = false;
    private boolean mRemoveFlashlight    = false;
    private boolean mRemoveDevMode       = false;

    public StatusbarMods(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mRemoveUsb          = Xprefs.getBoolean("remove_usb_dialog",                       false);
        mRemoveLowBattery   = Xprefs.getBoolean("remove_low_battery_notification",          false);
        mMuteLowBatterySound = Xprefs.getBoolean("mute_low_battery_sound",                  false);
        mRemoveChargingDone = Xprefs.getBoolean("remove_charging_complete_notification",    false);
        mRemoveFlashlight   = Xprefs.getBoolean("remove_flashlight_notification",           false);
        mRemoveDevMode      = Xprefs.getBoolean("remove_dev_mode",                          false);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        hookUsb(lp);
        hookPowerWarnings(lp);
        hookFlashlight(lp);
        hookDevMode(lp);
    }

    // ── USB dialog ────────────────────────────────────────────────────────────

    private void hookUsb(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> UsbService;
        try {
            UsbService = findClass("com.oplus.systemui.usb.UsbService", lp.classLoader);
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarMods: UsbService not found");
            return;
        }

        hookAllMethods(UsbService, "onUsbConnected", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                if (!mRemoveUsb) return;
                Context c = (Context) p.args[0];
                p.setResult(null);
                callMethod(p.thisObject, "onUsbSelect", 1);
                callMethod(p.thisObject, "updateAdbNotification", c);
                callMethod(p.thisObject, "updateUsbNotification", c, 1);
                callMethod(p.thisObject, "changeUsbConfig", c, 1);
            }
        });

        hookAllMethods(UsbService, "helpUpdateUsbNotification", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                if (!mRemoveUsb) return;
                setBooleanField(p.thisObject, "mNeedShowUsbDialog", false);
            }
        });
    }

    // ── Low battery + charging complete ──────────────────────────────────────

    private void hookPowerWarnings(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> PowerWarnings = tryFindClass(lp,
                "com.oplus.systemui.statusbar.notification.power.OplusPowerNotificationWarnings",  // OOS 15-14
                "com.oplusos.systemui.notification.power.OplusPowerNotificationWarnings"            // OOS 13
        );
        if (PowerWarnings == null) return;

        hookAllMethods(PowerWarnings, "showChargeErrorDialog", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (mRemoveChargingDone && (int) p.args[0] == 7) p.setResult(null);
            }
        });

        hookAllMethods(PowerWarnings, "showLowBatteryDialog", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (mRemoveLowBattery) p.setResult(null);
            }
        });

        hookAllMethods(PowerWarnings, "showLowBatteryWarning", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (mRemoveLowBattery) p.setResult(false);
            }
        });

        hookAllMethods(PowerWarnings, "createNotification", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (mRemoveLowBattery) p.setResult(null);
            }
        });

        // Silenzia il suono di batteria scarica / critica (da OC): playPowerUISound(flag, ...) con flag 0 (scarica) e 2 (critica)
        try {
            Class<?> soundUtil = tryFindClass(lp, "com.oplus.systemui.statusbar.notification.power.OplusPowerUISoundUtil");
            if (soundUtil != null) {
                hookAllMethods(soundUtil, "playPowerUISound", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mMuteLowBatterySound || p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
                        int flag = (Integer) p.args[0];
                        if (flag == 0 || flag == 2) p.setResult(null);
                    }
                });
            }
        } catch (Throwable t) {
            log("[ Obsidian ] StatusbarMods: mute low battery sound hook failed: " + t);
        }
    }

    // ── Flashlight notification ───────────────────────────────────────────────

    private void hookFlashlight(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> FlashlightNotif = tryFindClass(lp,
                "com.oplus.systemui.notification.flashlight.FlashlightNotification",                   // OOS 15.0.1+
                "com.oplus.systemui.statusbar.notification.flashlight.FlashlightNotification",          // OOS 15-14
                "com.oplusos.systemui.flashlight.FlashlightNotification"                                // OOS 13
        );
        if (FlashlightNotif == null) return;

        hookAllMethods(FlashlightNotif, "sendNotification", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (mRemoveFlashlight) p.setResult(null);
            }
        });
    }

    // ── Developer mode notification ───────────────────────────────────────────

    private void hookDevMode(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> SysPrompt = tryFindClass(lp,
                "com.oplus.systemui.statusbar.controller.SystemPromptController",   // OOS 15-14
                "com.oplusos.systemui.statusbar.policy.SystemPromptController"      // OOS 13
        );
        if (SysPrompt != null) {
            hookAllMethods(SysPrompt, "updateDeveloperMode", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mRemoveDevMode) p.setResult(null);
                }
            });
        }

        // OOS 13 fallback
        Class<?> FeatureOption = tryFindClass(lp,
                "com.oplusos.systemui.common.feature.StatusBarFeatureOption");
        if (FeatureOption != null) {
            hookAllMethods(FeatureOption, "getSendDeveloperModeNotification", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mRemoveDevMode) p.setResult(false);
                }
            });
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    /** Try each class name in order; return the first found, or null. */
    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); }
            catch (Throwable ignored) {}
        }
        log("[ Obsidian ] StatusbarMods: none of " + java.util.Arrays.toString(names) + " found");
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
