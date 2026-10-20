package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.views.BatteryBarView;

/**
 * Real OC BatteryBar mechanism (com.android.systemui.statusbar.phone.fragment.
 * CollapsedStatusBarFragment → mStatusBar's parent FrameLayout), trimmed to
 * the single "BBarEnabled" pref Obsidian currently exposes in the UI.
 */
public class BatteryBar extends XposedMods {

    private static final String PREF_ENABLED = "BBarEnabled";
    private static final String PREF_COLORFUL        = "BBarColorful";
    private static final String PREF_ONLY_CHARGING    = "BBOnlyWhileCharging";
    private static final String PREF_ON_BOTTOM        = "BBOnBottom";
    private static final String PREF_CENTERED         = "BBSetCentered";
    private static final String PREF_ANIMATE_CHARGING = "BBAnimateCharging";
    private static final String PREF_OPACITY          = "BBOpacity";
    private static final String PREF_HEIGHT           = "BBarHeight";
    private static final String PREF_TRANSIT_COLORS   = "BBarTransitColors";
    private static final String PREF_CRITICAL_LEVEL   = "battery_bar_critical_level";
    private static final String PREF_WARNING_LEVEL    = "battery_bar_warning_level";
    private static final String PREF_CRITICAL_COLOR   = "batteryCriticalColor";
    private static final String PREF_WARNING_COLOR    = "batteryWarningColor";
    private static final String PREF_INDICATE_CHARGING      = "indicateCharging";
    private static final String PREF_CHARGING_COLOR         = "batteryChargingColor";
    private static final String PREF_INDICATE_FAST_CHARGING = "indicateFastCharging";
    private static final String PREF_FAST_CHARGING_COLOR    = "batteryFastChargingColor";
    private static final String PREF_INDICATE_POWER_SAVE    = "indicatePowerSave";
    private static final String PREF_POWER_SAVE_COLOR       = "batteryPowerSaveColor";

    private boolean mEnabled = false;
    private ViewGroup mStatusBar;
    private FrameLayout mFullStatusbar;
    private Object mCollapsedStatusBarFragment = null;

    public BatteryBar(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mEnabled = Xprefs.getBoolean(PREF_ENABLED, false);
        applyOptions();

        if (mEnabled) {
            placeBatteryBar();
        } else if (BatteryBarView.hasInstance()) {
            BatteryBarView.getInstance().setVisibility(View.GONE);
        }
    }

    private void applyOptions() {
        if (!BatteryBarView.hasInstance()) return;
        BatteryBarView bar = BatteryBarView.getInstance();
        bar.setColorful(Xprefs.getBoolean(PREF_COLORFUL, false));
        bar.setOnlyWhileCharging(Xprefs.getBoolean(PREF_ONLY_CHARGING, false));
        bar.setOnTop(!Xprefs.getBoolean(PREF_ON_BOTTOM, false));
        bar.setCentered(Xprefs.getBoolean(PREF_CENTERED, false));
        bar.setAnimateCharging(Xprefs.getBoolean(PREF_ANIMATE_CHARGING, true));
        bar.setOpacityPct(Xprefs.getSliderInt(PREF_OPACITY, 100));
        bar.setHeightRaw(Xprefs.getSliderInt(PREF_HEIGHT, 50));
        bar.setTransitColors(Xprefs.getBoolean(PREF_TRANSIT_COLORS, false));
        bar.setLevelColors(
                Xprefs.getSliderInt(PREF_CRITICAL_LEVEL, 15),
                Xprefs.getSliderInt(PREF_WARNING_LEVEL, 40),
                resolveColor(PREF_CRITICAL_COLOR, 0xFFFF0000),
                resolveColor(PREF_WARNING_COLOR, 0xFFFFFF00));
        bar.setStateColors(
                Xprefs.getBoolean(PREF_INDICATE_CHARGING, true),
                resolveColor(PREF_CHARGING_COLOR, 0xFF00FF00),
                Xprefs.getBoolean(PREF_INDICATE_FAST_CHARGING, false),
                resolveColor(PREF_FAST_CHARGING_COLOR, 0xFF00FF00),
                Xprefs.getBoolean(PREF_INDICATE_POWER_SAVE, false),
                resolveColor(PREF_POWER_SAVE_COLOR, 0xFF00FF00));
        bar.refreshLayout();
    }

    private int resolveColor(String key, int def) {
        if (Xprefs.getBoolean(key + "_use_accent", false)) return appAccentColor();
        return Xprefs.getInt(key, def);
    }

    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.systemAccent();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        Class<?> collapsedStatusBarFragmentClass = findClassIfExists(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                lp.classLoader);
        if (collapsedStatusBarFragmentClass == null) return;
        Class<?> phoneStatusBarViewClass = findClass(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView", lp.classLoader);

        hookAllConstructors(collapsedStatusBarFragmentClass, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                mCollapsedStatusBarFragment = param.thisObject;
            }
        });

        findAndHookMethod(collapsedStatusBarFragmentClass, "onViewCreated",
                View.class, Bundle.class, new XC_MethodHook() {
                    @SuppressLint("DiscouragedApi")
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            mStatusBar = (ViewGroup) getObjectField(mCollapsedStatusBarFragment, "mStatusBar");
                            mFullStatusbar = (FrameLayout) mStatusBar.getParent();
                            if (mEnabled) placeBatteryBar();
                        } catch (Throwable ignored) {}
                    }
                });

        try {
            hookAllMethods(phoneStatusBarViewClass, "onConfigurationChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (BatteryBarView.hasInstance()) {
                        BatteryBarView.getInstance().postDelayed(
                                () -> BatteryBarView.getInstance().refreshLayout(), 500);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void placeBatteryBar() {
        if (mFullStatusbar == null) return;
        try {
            BatteryBarView bar = BatteryBarView.getInstance(mContext);
            ViewGroup parent = (ViewGroup) bar.getParent();
            if (parent != null) parent.removeView(bar);
            mFullStatusbar.addView(bar);
            applyOptions();
        } catch (Throwable ignored) {}
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
