package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;
import static it.tugaia56.obsidian.xposed.hooks.systemui.BatteryDataProvider.getCurrentLevel;
import static it.tugaia56.obsidian.xposed.hooks.systemui.BatteryDataProvider.isCharging;
import static it.tugaia56.obsidian.xposed.hooks.systemui.BatteryDataProvider.isPowerSaving;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.xposed.ResourceManager;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.batterystyles.BatteryDrawable;
import it.tugaia56.obsidian.xposed.batterystyles.CircleBattery;
import it.tugaia56.obsidian.xposed.batterystyles.CircleFilledBattery;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBattery;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryA;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryB;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryC;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryColorOS;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryD;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryE;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryF;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryG;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryH;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryI;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryJ;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryK;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryKim;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryL;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryM;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryMIUIPill;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryN;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryO;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryOneUI7;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatterySmiley;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryStyleA;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryStyleB;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryiOS15;
import it.tugaia56.obsidian.xposed.batterystyles.LandscapeBatteryiOS16;
import it.tugaia56.obsidian.xposed.batterystyles.PortraitBatteryAiroo;
import it.tugaia56.obsidian.xposed.batterystyles.PortraitBatteryCapsule;
import it.tugaia56.obsidian.xposed.batterystyles.PortraitBatteryLorn;
import it.tugaia56.obsidian.xposed.batterystyles.PortraitBatteryMx;
import it.tugaia56.obsidian.xposed.batterystyles.PortraitBatteryOrigami;
import it.tugaia56.obsidian.xposed.batterystyles.RLandscapeBattery;
import it.tugaia56.obsidian.xposed.batterystyles.RLandscapeBatteryColorOS;
import it.tugaia56.obsidian.xposed.batterystyles.RLandscapeBatteryStyleA;
import it.tugaia56.obsidian.xposed.batterystyles.RLandscapeBatteryStyleB;

/**
 * Replaces the stock battery icon with a custom drawable.
 *
 * Pref keys (all in Obsidian prefs):
 *   battery_icon_enabled      – master enable
 *   battery_icon_style        – int (0 = default/disabled, see STYLE_* consts)
 *   battery_hide_percentage   – boolean
 *   battery_inside_percentage – boolean
 *   battery_hide_battery      – boolean
 *   battery_width             – int dp  (default 20)
 *   battery_height            – int dp  (default 20)
 */
public class BatteryStyleManager extends XposedMods {

    // ── Style constants (mirror OC's BatteryPrefs int values) ─────────────────
    public static final int STYLE_DEFAULT          = 0;
    public static final int STYLE_RLANDSCAPE       = 3;   // custom RLandscape
    public static final int STYLE_LANDSCAPE        = 4;   // custom Landscape
    public static final int STYLE_CAPSULE          = 5;
    public static final int STYLE_LORN             = 6;
    public static final int STYLE_MX               = 7;
    public static final int STYLE_AIROO            = 8;
    public static final int STYLE_RLANDSCAPE_A     = 9;
    public static final int STYLE_LANDSCAPE_A      = 10;
    public static final int STYLE_RLANDSCAPE_B     = 11;
    public static final int STYLE_LANDSCAPE_B      = 12;
    public static final int STYLE_IOS15            = 13;
    public static final int STYLE_IOS16            = 14;
    public static final int STYLE_ORIGAMI          = 15;
    public static final int STYLE_SMILEY           = 16;
    public static final int STYLE_MIUI_PILL        = 17;
    public static final int STYLE_COLOROS          = 18;
    public static final int STYLE_RLANDSCAPE_COLOROS = 19;
    public static final int STYLE_A                = 20;
    public static final int STYLE_B                = 21;
    public static final int STYLE_C                = 22;
    public static final int STYLE_D                = 23;
    public static final int STYLE_E                = 24;
    public static final int STYLE_F                = 25;
    public static final int STYLE_G                = 26;
    public static final int STYLE_H                = 27;
    public static final int STYLE_I                = 28;
    public static final int STYLE_J                = 29;
    public static final int STYLE_K                = 30;
    public static final int STYLE_L                = 31;
    public static final int STYLE_M                = 32;
    public static final int STYLE_N                = 33;
    public static final int STYLE_O                = 34;
    public static final int STYLE_CIRCLE           = 35;
    public static final int STYLE_DOTTED_CIRCLE    = 36;
    public static final int STYLE_FILLED_CIRCLE    = 37;
    public static final int STYLE_KIM              = 38;
    public static final int STYLE_ONE_UI7          = 39;

    // ── Pref keys ──────────────────────────────────────────────────────────────
    public static final String PREF_ENABLED          = "battery_icon_enabled";
    public static final String PREF_STYLE            = "battery_icon_style";
    public static final String PREF_HIDE_PERCENT     = "battery_hide_percentage";
    public static final String PREF_INSIDE_PERCENT   = "battery_inside_percentage";
    public static final String PREF_HIDE_BATTERY     = "battery_hide_battery";
    public static final String PREF_WIDTH            = "battery_width";
    public static final String PREF_HEIGHT           = "battery_height";
    public static final String PREF_BLEND_COLOR      = "battery_blend_color";
    public static final String PREF_FILL_COLOR       = "battery_fill_color";
    public static final String PREF_FILL_GRAD_COLOR  = "battery_fill_gradient_color";
    public static final String PREF_CHARGING_COLOR   = "battery_charging_fill_color";
    public static final String PREF_FAST_COLOR       = "battery_fast_charging_fill_color";
    public static final String PREF_POWERSAVE_COLOR  = "battery_powersave_icon_color";
    public static final String PREF_POWERSAVE_FILL   = "battery_powersave_fill_color";
    public static final String PREF_RAINBOW          = "battery_rainbow_color";
    public static final String PREF_PERIMETER_ALPHA  = "battery_perimeter_alpha";
    public static final String PREF_FILL_ALPHA       = "battery_fill_alpha";
    public static final String PREF_ANIM_ENABLED     = "battery_icon_animation_enabled";
    public static final String PREF_ROTATE           = "battery_rotate_layout";
    public static final String PREF_PERCENT_SIZE     = "battery_percent_size";
    public static final String PREF_TEXT_ATTACH_BAR       = "battery_text_color_batterybar";
    public static final String PREF_TEXT_IND_CHARGING     = "battery_text_indicate_charging";
    public static final String PREF_TEXT_COLOR_CHARGING   = "battery_text_color_charging";
    public static final String PREF_TEXT_IND_FAST         = "battery_text_indicate_fastcharging";
    public static final String PREF_TEXT_COLOR_FAST       = "battery_text_color_fastcharging";
    public static final String PREF_TEXT_IND_POWERSAVE    = "battery_text_indicate_powersave";
    public static final String PREF_TEXT_COLOR_POWERSAVE  = "battery_text_color_powersave";
    public static final String PREF_CHARGING_ICON_ENABLED = "battery_icon_change_charging_icon";
    public static final String PREF_CHARGING_ICON_STYLE   = "battery_charging_icon_style";
    public static final String PREF_CHARGING_ICON_USE_ACCENT = "battery_charging_icon_use_accent";
    public static final String PREF_CHARGING_ICON_CUSTOM_COLOR = "battery_charging_icon_custom_color";
    public static final String PREF_CHARGING_ICON_ML      = "battery_charging_icon_margin_left";
    public static final String PREF_CHARGING_ICON_MR      = "battery_charging_icon_margin_right";
    public static final String PREF_CHARGING_ICON_SIZE    = "battery_charging_icon_size";

    // Same 21 icons/order as BatteryChargingIconStyleFragment's NAMES.
    private static final String[] CHARGING_ICON_NAMES = {
            "ic_charging_bold", "ic_charging_asus", "ic_charging_buddy", "ic_charging_evplug",
            "ic_charging_idc", "ic_charging_ios", "ic_charging_koplak", "ic_charging_miui",
            "ic_charging_mmk", "ic_charging_moto", "ic_charging_nokia", "ic_charging_plug",
            "ic_charging_powercable", "ic_charging_powercord", "ic_charging_powerstation",
            "ic_charging_realme", "ic_charging_soak", "ic_charging_stres", "ic_charging_strip",
            "ic_charging_usbcable", "ic_charging_xiaomi"
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private static final ArrayList<View> batteryViews = new ArrayList<>();
    private static int mStyle  = STYLE_DEFAULT;
    private static int mOldStyle = STYLE_DEFAULT;
    private static boolean mEnabled    = false;
    private static boolean mHidePercent    = false;
    private static boolean mInsidePercent  = false;
    private static boolean mHideBattery    = false;
    private static int mWidth  = 20;
    private static int mHeight = 20;
    private static boolean mBlendColor    = false;
    private static boolean mRainbow       = false;
    private static boolean mPerimAlpha    = false;
    private static boolean mFillAlpha     = false;
    private static boolean mAnimEnabled   = true;
    private static boolean mRotate        = false;
    private static int mPercentSize       = 12;
    private static boolean mTextAttachBar     = false;
    private static boolean mTextIndCharging   = false;
    private static int mTextColorCharging     = Color.GREEN;
    private static boolean mTextIndFast       = false;
    private static int mTextColorFast         = Color.GREEN;
    private static boolean mTextIndPowerSave  = false;
    private static int mTextColorPowerSave    = Color.GREEN;
    private static int mFillColor         = Color.WHITE;
    private static int mFillGradColor     = Color.WHITE;
    private static int mChargingColor     = Color.TRANSPARENT;
    private static int mFastColor         = Color.TRANSPARENT;
    private static int mPowerSaveColor    = Color.TRANSPARENT;
    private static int mPowerSaveFillColor = Color.TRANSPARENT;

    private static int mFrameColor      = Color.WHITE;
    private static int mBgColor         = Color.WHITE;
    private static int mSingleToneColor = Color.WHITE;

    private static boolean mChargingIconEnabled = false;
    private static int mChargingIconStyle = 0;
    private static boolean mChargingIconUseAccent = true;
    private static int mChargingIconCustomColor = Color.WHITE;
    private static int mChargingIconML   = 1;
    private static int mChargingIconMR   = 0;
    private static int mChargingIconSize = 14;

    private Class<?> DarkIconDispatcher = null;
    private Class<?> DualToneHandler    = null;

    public BatteryStyleManager(Context context) { super(context); }

    // ── updatePrefs ───────────────────────────────────────────────────────────

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        mEnabled      = Xprefs.getBoolean(PREF_ENABLED,        false);
        mStyle        = Xprefs.getInt(PREF_STYLE,              STYLE_DEFAULT);
        mHidePercent  = Xprefs.getBoolean(PREF_HIDE_PERCENT,   false);
        mInsidePercent = Xprefs.getBoolean(PREF_INSIDE_PERCENT, false);
        mHideBattery  = Xprefs.getBoolean(PREF_HIDE_BATTERY,   false);
        mWidth        = Xprefs.getInt(PREF_WIDTH,              20);
        mHeight       = Xprefs.getInt(PREF_HEIGHT,             20);
        mBlendColor   = Xprefs.getBoolean(PREF_BLEND_COLOR,    false);
        mRainbow      = Xprefs.getBoolean(PREF_RAINBOW,        false);
        mPerimAlpha   = Xprefs.getBoolean(PREF_PERIMETER_ALPHA, false);
        mFillAlpha    = Xprefs.getBoolean(PREF_FILL_ALPHA,     false);
        mAnimEnabled  = Xprefs.getBoolean(PREF_ANIM_ENABLED,   true);
        mRotate       = Xprefs.getBoolean(PREF_ROTATE,         false);
        mPercentSize  = Xprefs.getInt(PREF_PERCENT_SIZE,       12);
        mTextAttachBar    = Xprefs.getBoolean(PREF_TEXT_ATTACH_BAR,    false);
        mTextIndCharging  = Xprefs.getBoolean(PREF_TEXT_IND_CHARGING,  false);
        mTextColorCharging= Xprefs.getInt(PREF_TEXT_COLOR_CHARGING,    Color.GREEN);
        mTextIndFast      = Xprefs.getBoolean(PREF_TEXT_IND_FAST,      false);
        mTextColorFast    = Xprefs.getInt(PREF_TEXT_COLOR_FAST,        Color.GREEN);
        mTextIndPowerSave = Xprefs.getBoolean(PREF_TEXT_IND_POWERSAVE, false);
        mTextColorPowerSave = Xprefs.getInt(PREF_TEXT_COLOR_POWERSAVE, Color.GREEN);
        mFillColor    = Xprefs.getInt(PREF_FILL_COLOR,         Color.WHITE);
        mFillGradColor= Xprefs.getInt(PREF_FILL_GRAD_COLOR,    Color.WHITE);
        mChargingColor= Xprefs.getInt(PREF_CHARGING_COLOR,     Color.TRANSPARENT);
        mFastColor    = Xprefs.getInt(PREF_FAST_COLOR,         Color.TRANSPARENT);
        mPowerSaveColor= Xprefs.getInt(PREF_POWERSAVE_COLOR,   Color.TRANSPARENT);
        mPowerSaveFillColor = Xprefs.getInt(PREF_POWERSAVE_FILL, Color.TRANSPARENT);
        mChargingIconEnabled = Xprefs.getBoolean(PREF_CHARGING_ICON_ENABLED, false);
        mChargingIconStyle  = Xprefs.getInt(PREF_CHARGING_ICON_STYLE, 0);
        mChargingIconUseAccent = Xprefs.getBoolean(PREF_CHARGING_ICON_USE_ACCENT, true);
        mChargingIconCustomColor = Xprefs.getInt(PREF_CHARGING_ICON_CUSTOM_COLOR, Color.WHITE);
        mChargingIconML     = Xprefs.getInt(PREF_CHARGING_ICON_ML, 1);
        mChargingIconMR     = Xprefs.getInt(PREF_CHARGING_ICON_MR, 0);
        mChargingIconSize   = Xprefs.getInt(PREF_CHARGING_ICON_SIZE, 14);

        // If style changed, swap drawables on existing views
        if (mEnabled && mOldStyle != mStyle) {
            mOldStyle = mStyle;
            for (View v : batteryViews) {
                BatteryDrawable d = createDrawable(mContext);
                if (d != null) setAdditionalInstanceField(v, "mBatteryDrawable", d);
            }
        }
        refreshAll();
    }

    // ── handleLoadPackage ─────────────────────────────────────────────────────

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!listensTo(lpparam.packageName)) return;

        BatteryDataProvider.registerInfoCallback(this::refreshAll);

        try {
            DarkIconDispatcher = findClass("com.android.systemui.plugins.DarkIconDispatcher", lpparam.classLoader);
            DualToneHandler    = findClass("com.android.systemui.DualToneHandler",            lpparam.classLoader);
        } catch (Throwable ignored) {}

        View.OnAttachStateChangeListener attachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(@NonNull View v) {
                if (!batteryViews.contains(v)) batteryViews.add(v);
                updateView(v);
            }
            @Override public void onViewDetachedFromWindow(@NonNull View v) {
                batteryViews.remove(v);
            }
        };

        // ── StatBatteryMeterView (OOS 14-16) ──────────────────────────────────
        Class<?> StatBatteryMeterView = null;
        try {
            StatBatteryMeterView = findClass(
                    "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
                    lpparam.classLoader);
        } catch (Throwable ignored) {}

        if (StatBatteryMeterView != null) {
            final Class<?> sBMV = StatBatteryMeterView;

            hookAllConstructors(sBMV, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    ((View) param.thisObject).addOnAttachStateChangeListener(attachListener);
                    registerAccentReceiver(((View) param.thisObject).getContext());
                    if (!mEnabled) return;
                    BatteryDrawable d = createDrawable(mContext);
                    if (d != null) setAdditionalInstanceField(param.thisObject, "mBatteryDrawable", d);
                }
            });

            findAndHookMethod(sBMV, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!mEnabled) return;
                    LinearLayout root = (LinearLayout) param.thisObject;
                    @SuppressWarnings("DiscouragedApi")
                    ImageView iv = root.findViewById(mContext.getResources().getIdentifier(
                            "battery_icon_view", "id", mContext.getPackageName()));
                    if (iv == null) return;
                    BatteryDrawable d = createDrawable(mContext);
                    if (d == null) return;
                    setAdditionalInstanceField(param.thisObject, "mBatteryDrawable", d);
                    iv.setImageDrawable(d);
                    iv.requestLayout();
                }
            });

            // onDarkChanged → update icon colour
            if (DarkIconDispatcher != null && DualToneHandler != null) {
                final Class<?> dth = DualToneHandler;
                final Class<?> did = DarkIconDispatcher;
                hookAllMethods(sBMV, "onDarkChanged", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!mEnabled) return;
                        try {
                            ArrayList<Rect> areas = (ArrayList<Rect>) param.args[0];
                            float dark = (float) param.args[1];
                            boolean inAreas = (boolean) callStaticMethod(did, "isInAreas", areas, param.thisObject);
                            if (!inAreas) dark = 0f;
                            Object dh = dth.getConstructor(Context.class).newInstance(((View) param.thisObject).getContext());
                            mSingleToneColor = (int) callMethod(dh, "getSingleColor",    dark);
                            mFrameColor      = (int) callMethod(dh, "getFillColor",       dark);
                            mBgColor         = (int) callMethod(dh, "getBackgroundColor", dark);
                            updateView((View) param.thisObject);
                        } catch (Throwable ignored) {}
                    }
                });
            }

            // BatteryViewBinder — keep drawable in sync when stock updates view
            try {
                Class<?> BatteryViewBinder = findClass(
                        "com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder",
                        lpparam.classLoader);
                XC_MethodHook syncHook = new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args.length > 0 && param.args[0] instanceof View v) updateView(v);
                    }
                };
                hookAllMethods(BatteryViewBinder, "bind",                        syncHook);
                hookAllMethods(BatteryViewBinder, "bind$updateBatteryContentView", syncHook);

                // battery_charge_icon ImageView — real native slot for the charging bolt,
                // reused here instead of injecting a new view (mirrors OC's approach).
                hookAllMethods(BatteryViewBinder, "bind$updateChargingView", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args.length > 0 && param.args[0] instanceof ImageView iv) applyChargingIcon(iv);
                    }
                });
            } catch (Throwable ignored) {}
        }

        // ── OOS 13 fallback (StatBatteryMeterView in oplusos package) ─────────
        try {
            Class<?> StatBMV13 = findClass(
                    "com.oplusos.systemui.statusbar.widget.StatBatteryMeterView",
                    lpparam.classLoader);
            findAndHookMethod(StatBMV13, "onBatteryLevelChanged",
                    int.class, boolean.class, boolean.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!mEnabled) return;
                    try {
                        ImageView iv = (ImageView)
                                de.robv.android.xposed.XposedHelpers.getObjectField(param.thisObject, "mBatteryIconView");
                        if (iv == null) return;
                        BatteryDrawable d = createDrawable(mContext);
                        if (d == null) return;
                        int level = (int) param.args[0];
                        boolean charging = (boolean) param.args[2];
                        d.setBatteryLevel(level);
                        d.setChargingEnabled(charging, false);
                        d.setPowerSavingEnabled(isPowerSaving());
                        d.setShowPercentEnabled(mInsidePercent && !mHidePercent);
                        d.setAnimationEnbled(mAnimEnabled);
                        d.setColors(mFrameColor, mBgColor, mSingleToneColor);
                        applyCustomization(d);
                        iv.setImageDrawable(d);
                        scaleIcon(iv);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    // ── View update ───────────────────────────────────────────────────────────

    private void refreshAll() {
        for (View v : new ArrayList<>(batteryViews)) {
            v.post(() -> updateView(v));
        }
    }

    /**
     * Percentage-text colour override, priority fast-charging > charging > power-save,
     * or (if "attach to battery bar" is on) the bar's own current colour instead.
     * Returns null when no override applies (leave the system's default text colour).
     * Note: "fast charging" has no dedicated detection signal yet, so it behaves the
     * same as regular charging — indicateFast only wins the priority order if both
     * switches are on at once.
     */
    private Integer resolveTextColor() {
        if (mTextAttachBar && it.tugaia56.obsidian.xposed.views.BatteryBarView.hasInstance()) {
            return it.tugaia56.obsidian.xposed.views.BatteryBarView.getInstance().getLastColor();
        }
        boolean charging = isCharging();
        if (mTextIndFast && charging) return mTextColorFast;
        if (mTextIndCharging && charging) return mTextColorCharging;
        if (mTextIndPowerSave && isPowerSaving()) return mTextColorPowerSave;
        return null;
    }

    @SuppressWarnings("DiscouragedApi")
    private void updateView(View view) {
        // Percent text size — applied regardless of mEnabled (standalone pref)
        try {
            android.widget.TextView pct = view.findViewById(
                    mContext.getResources().getIdentifier("battery_percentage_view", "id", mContext.getPackageName()));
            if (pct != null) {
                pct.post(() -> {
                    if (mEnabled) {
                        boolean hideP = mHidePercent || mInsidePercent;
                        pct.setVisibility(hideP ? View.GONE : View.VISIBLE);
                    }
                    int pctSz;
                    try {
                        pctSz = (Xprefs != null) ? Xprefs.getInt(PREF_PERCENT_SIZE, mPercentSize) : mPercentSize;
                    } catch (Throwable t) {
                        pctSz = mPercentSize;
                    }
                    pct.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, pctSz);

                    Integer textColor = resolveTextColor();
                    if (textColor != null) pct.setTextColor(textColor);
                });
            }
        } catch (Throwable ignored) {}

        if (!mEnabled) return;

        if (!(view instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) view;

        ImageView batteryIcon = null;
        try {
            batteryIcon = root.findViewById(mContext.getResources().getIdentifier(
                    "battery_icon_view", "id", mContext.getPackageName()));
        } catch (Throwable ignored) {}

        if (batteryIcon == null) return;

        BatteryDrawable d = null;
        try { d = (BatteryDrawable) getAdditionalInstanceField(view, "mBatteryDrawable"); } catch (Throwable ignored) {}
        if (d == null) {
            d = createDrawable(mContext);
            if (d == null) return;
            setAdditionalInstanceField(view, "mBatteryDrawable", d);
        }

        d.setBatteryLevel(getCurrentLevel());
        d.setChargingEnabled(isCharging(), false);
        d.setPowerSavingEnabled(isPowerSaving());
        d.setShowPercentEnabled(mInsidePercent && !mHidePercent);
        d.setAnimationEnbled(mAnimEnabled);
        d.setColors(mFrameColor, mBgColor, mSingleToneColor);
        applyCustomization(d);
        d.invalidateSelf();

        batteryIcon.setImageDrawable(d);
        batteryIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        batteryIcon.setVisibility(mHideBattery ? View.GONE : View.VISIBLE);
        scaleIcon(batteryIcon);

        refreshChargingIcons();
    }

    private void applyCustomization(BatteryDrawable d) {
        d.customizeBatteryDrawable(
                mRotate,        // isRotation
                mPerimAlpha,
                mFillAlpha,
                mBlendColor,
                mRainbow,
                mFillColor,
                mFillGradColor,
                mChargingColor,
                mFastColor,
                mPowerSaveColor,
                mPowerSaveFillColor,
                mChargingIconEnabled
        );
    }

    /** Legge l'accento via Xprefs invece di ObsidianTheme.accentColor() — quest'ultima richiede
     *  il Context dell'app Obsidian (Obsidian.get()...), disponibile solo nel processo
     *  Obsidian, non in SystemUI: usarla da un Mod causa ExceptionInInitializerError ad ogni
     *  chiamata (bug pre-esistente trovato per caso in questa sessione, stesso identico difetto
     *  già risolto in QsSeparateMod — vedi feedback_stop_guessing_get_data.md in memoria). */
    private int appAccentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) {
            return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        }
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.systemAccent();
    }

    // le ImageView del fulmine viste finora: si ricolorano quando cambia l'accento (il sistema non le ricollega da solo)
    private final java.util.Set<ImageView> mChargingIconViews =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private boolean mAccentReceiverRegistered;

    /** Cambio overlay/accento (Oxydian Theme): le icone gia' disegnate non si aggiornano da sole, si ricolorano qui. */
    private void registerAccentReceiver(Context ctx) {
        if (mAccentReceiverRegistered || ctx == null) return;
        mAccentReceiverRegistered = true;
        try {
            final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            android.content.IntentFilter f = new android.content.IntentFilter(android.content.Intent.ACTION_CONFIGURATION_CHANGED);
            f.addAction("android.intent.action.OVERLAY_CHANGED");
            ctx.getApplicationContext().registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, android.content.Intent i) {
                    h.postDelayed(BatteryStyleManager.this::refreshChargingIcons, 1500);
                    h.postDelayed(BatteryStyleManager.this::refreshChargingIcons, 6000);
                }
            }, f);
        } catch (Throwable t) { mAccentReceiverRegistered = false; }
    }

    private void refreshChargingIcons() {
        for (ImageView iv : new ArrayList<>(mChargingIconViews)) iv.post(() -> applyChargingIcon(iv));
    }

    @SuppressWarnings("DiscouragedApi")
    private Drawable getNewChargingIcon() {
        if (mChargingIconStyle < 0 || mChargingIconStyle >= CHARGING_ICON_NAMES.length) return null;
        try {
            int resId = ResourceManager.modRes.getIdentifier(
                    CHARGING_ICON_NAMES[mChargingIconStyle], "drawable", BuildConfig.APPLICATION_ID);
            if (resId == 0) return null;
            Drawable d = ResourcesCompat.getDrawable(ResourceManager.modRes, resId, mContext.getTheme());
            if (d != null) {
                d = d.mutate();
                int color = mChargingIconUseAccent ? appAccentColor() : mChargingIconCustomColor;
                d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("DiscouragedApi")
    private void applyChargingIcon(ImageView chargingIcon) {
        if (chargingIcon == null) return;
        if (!mChargingIconEnabled) return;
        mChargingIconViews.add(chargingIcon);
        Drawable icon = getNewChargingIcon();
        if (icon == null) return;
        chargingIcon.setImageDrawable(icon);
        Context ctx = chargingIcon.getContext();
        int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mChargingIconSize, ctx.getResources().getDisplayMetrics());
        int left = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mChargingIconML, ctx.getResources().getDisplayMetrics());
        int right = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mChargingIconMR, ctx.getResources().getDisplayMetrics());
        ViewGroup.LayoutParams lp = chargingIcon.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams llp) {
            llp.width = size;
            llp.height = size;
            llp.setMargins(left, 0, right, llp.bottomMargin);
            chargingIcon.setLayoutParams(llp);
        }
        chargingIcon.requestLayout();
    }

    @SuppressWarnings("DiscouragedApi")
    private void scaleIcon(ImageView iv) {
        try {
            Context ctx = iv.getContext();
            int w = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mWidth,  ctx.getResources().getDisplayMetrics());
            int h = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mHeight, ctx.getResources().getDisplayMetrics());
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            if (lp == null) lp = new LinearLayout.LayoutParams(w, h);
            lp.width  = w;
            lp.height = h;
            iv.setLayoutParams(lp);
            iv.requestLayout();
        } catch (Throwable ignored) {}
    }

    // ── Helper: callMethod ────────────────────────────────────────────────────

    private static Object callMethod(Object obj, String method, Object... args) throws Exception {
        return de.robv.android.xposed.XposedHelpers.callMethod(obj, method, args);
    }

    // ── Drawable factory ──────────────────────────────────────────────────────

    private static BatteryDrawable createDrawable(Context ctx) {
        return createDrawableForStyle(ctx, mStyle, mFrameColor);
    }

    /** Public so the UI (style picker preview grid) can render each style's real drawable. */
    public static BatteryDrawable createDrawableForStyle(Context ctx, int style, int color) {
        return switch (style) {
            case STYLE_RLANDSCAPE         -> new RLandscapeBattery(ctx,       color);
            case STYLE_LANDSCAPE          -> new LandscapeBattery(ctx,         color);
            case STYLE_CAPSULE            -> new PortraitBatteryCapsule(ctx,   color);
            case STYLE_LORN               -> new PortraitBatteryLorn(ctx,      color);
            case STYLE_MX                 -> new PortraitBatteryMx(ctx,        color);
            case STYLE_AIROO              -> new PortraitBatteryAiroo(ctx,     color);
            case STYLE_RLANDSCAPE_A       -> new RLandscapeBatteryStyleA(ctx,  color);
            case STYLE_LANDSCAPE_A        -> new LandscapeBatteryStyleA(ctx,   color);
            case STYLE_RLANDSCAPE_B       -> new RLandscapeBatteryStyleB(ctx,  color);
            case STYLE_LANDSCAPE_B        -> new LandscapeBatteryStyleB(ctx,   color);
            case STYLE_IOS15              -> new LandscapeBatteryiOS15(ctx,    color);
            case STYLE_IOS16              -> new LandscapeBatteryiOS16(ctx,    color);
            case STYLE_ORIGAMI            -> new PortraitBatteryOrigami(ctx,   color);
            case STYLE_SMILEY             -> new LandscapeBatterySmiley(ctx,   color);
            case STYLE_MIUI_PILL          -> new LandscapeBatteryMIUIPill(ctx, color);
            case STYLE_COLOROS            -> new LandscapeBatteryColorOS(ctx,  color);
            case STYLE_RLANDSCAPE_COLOROS -> new RLandscapeBatteryColorOS(ctx, color);
            case STYLE_A                  -> new LandscapeBatteryA(ctx,        color);
            case STYLE_B                  -> new LandscapeBatteryB(ctx,        color);
            case STYLE_C                  -> new LandscapeBatteryC(ctx,        color);
            case STYLE_D                  -> new LandscapeBatteryD(ctx,        color);
            case STYLE_E                  -> new LandscapeBatteryE(ctx,        color);
            case STYLE_F                  -> new LandscapeBatteryF(ctx,        color);
            case STYLE_G                  -> new LandscapeBatteryG(ctx,        color);
            case STYLE_H                  -> new LandscapeBatteryH(ctx,        color);
            case STYLE_I                  -> new LandscapeBatteryI(ctx,        color);
            case STYLE_J                  -> new LandscapeBatteryJ(ctx,        color);
            case STYLE_K                  -> new LandscapeBatteryK(ctx,        color);
            case STYLE_L                  -> new LandscapeBatteryL(ctx,        color);
            case STYLE_M                  -> new LandscapeBatteryM(ctx,        color);
            case STYLE_N                  -> new LandscapeBatteryN(ctx,        color);
            case STYLE_O                  -> new LandscapeBatteryO(ctx,        color);
            case STYLE_CIRCLE, STYLE_DOTTED_CIRCLE -> new CircleBattery(ctx,  color);
            case STYLE_FILLED_CIRCLE      -> new CircleFilledBattery(ctx,      color);
            case STYLE_KIM                -> new LandscapeBatteryKim(ctx,      color);
            case STYLE_ONE_UI7            -> new LandscapeBatteryOneUI7(ctx,   color);
            default                       -> null;
        };
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
