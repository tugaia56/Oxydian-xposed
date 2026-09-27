package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.hookMethod;
import static de.robv.android.xposed.XposedBridge.log;
import de.robv.android.xposed.XposedBridge;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.ResourceManager.resparams;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import it.tugaia56.obsidian.xposed.ResourceManager;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import android.content.res.XResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Status bar icon mods:
 *  - hide_bluetooth_when_disconnected
 *  - hide_inout_wifi
 *  - hide_inout_mobile
 *  - double_tap_sleep_statusbar
 *  - statusbar_brightness  (slide status bar to adjust brightness)
 */
public class StatusbarIcons extends XposedMods {

    // ── Prefs ──────────────────────────────────────────────────────────────────
    private boolean mHideBluetooth      = false;
    private boolean mHideWifiActivity   = false;
    private boolean mHideMobileActivity = false;
    private boolean mDoubleTapToSleep   = false;
    private boolean mBrightnessControl  = false;

    // ── Double-tap state ───────────────────────────────────────────────────────
    private Object mNotifPanelVC = null;
    private GestureDetector mDtSleepDetector;

    // ── Brightness state ───────────────────────────────────────────────────────
    private Object         mOplusBrightnessCtrl = null;
    private float          mMinBrightness       = 0f;
    private float          mMaxBrightness       = 255f;
    private int            mInitialTouchX, mInitialTouchY, mLinger;
    private boolean        mJustPeeked          = false;
    private int            mQuickQsOffsetHeight = 0;
    private DisplayMetrics mDisplayMetrics;
    private DisplayManager mDisplayManager;

    private final Handler  mHandler   = new Handler(Looper.getMainLooper());
    private final Runnable mLongPress = this::onLongPressBrightnessChange;

    private static final int   BRIGHTNESS_LINGER     = 20;
    private static final int   BRIGHTNESS_LONG_PRESS = 750; // ms
    private static final float BRIGHTNESS_PADDING    = 0.15f;

    // ─────────────────────────────────────────────────────────────────────────

    public StatusbarIcons(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mHideBluetooth      = Xprefs.getBoolean("hide_bluetooth_when_disconnected", false);
        mHideWifiActivity   = Xprefs.getBoolean("hide_inout_wifi",                  false);
        mHideMobileActivity = Xprefs.getBoolean("hide_inout_mobile",                false);
        mDoubleTapToSleep   = Xprefs.getBoolean("double_tap_sleep_statusbar",       false);
        mBrightnessControl  = Xprefs.getBoolean("statusbar_brightness",             false);
        // Keep static flags in sync so DrawableLoaders always see the latest value
        sHideWifiActivity   = mHideWifiActivity;
        sHideMobileActivity = mHideMobileActivity;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        // Pre-load prefs so activity hooks see correct state from the very first invocation.
        // updatePrefs() is a no-op if Xprefs isn't ready yet (safe to call early).
        try { updatePrefs(); } catch (Throwable ignored) {}
        XposedBridge.log("[ Obsidian ] StatusbarIcons.handleLoadPackage SDK=" + Build.VERSION.SDK_INT + " Xprefs=" + (Xprefs != null) + " hideWifi=" + mHideWifiActivity);

        mDisplayMetrics = mContext.getResources().getDisplayMetrics();
        mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);

        // Quick-QS height via resource (fallback; overridden dynamically in hookBrightnessCtrl)
        try {
            mQuickQsOffsetHeight = mContext.getResources().getDimensionPixelSize(
                    mContext.getResources().getIdentifier(
                            "notification_quick_qs_offset_height", "dimen", SYSTEM_UI));
        } catch (Throwable ignored) {}

        // GestureDetector must be created on the main Looper
        mDtSleepDetector = new GestureDetector(mContext,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDoubleTap(MotionEvent e) {
                        if (!mDoubleTapToSleep) return false;
                        try {
                            PowerManager pm = (PowerManager)
                                    mContext.getSystemService(Context.POWER_SERVICE);
                            callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
                        } catch (Throwable ignored) {}
                        return true;
                    }
                }, new Handler(Looper.getMainLooper()));

        try { hookWifiActivity(lp);   } catch (Throwable t) { log("[ Obsidian ] SBIcons WiFi: "   + t); }
        try { hookMobileActivity(lp); } catch (Throwable t) { log("[ Obsidian ] SBIcons Mobile: " + t); }
        try { hookBluetooth(lp);      } catch (Throwable t) { log("[ Obsidian ] SBIcons BT: "     + t); }
        try { hookBrightnessCtrl(lp); } catch (Throwable t) { log("[ Obsidian ] SBIcons Bright: " + t); }
        try { hookStatusBarTouch(lp); } catch (Throwable t) { log("[ Obsidian ] SBIcons Touch: "  + t); }
    }

    // ── Bluetooth ──────────────────────────────────────────────────────────────

    private void hookBluetooth(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> cls = tryFindClass(lp,
                "com.oplus.systemui.statusbar.phone.OplusPhoneStatusBarPolicyExImpl", // OOS 15-14
                "com.oplusos.systemui.statusbar.phone.PhoneStatusBarPolicyEx");        // OOS 13
        if (cls == null) return;

        hookAllMethods(cls, "updateBluetoothIcon", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!mHideBluetooth) return;
                try {
                    boolean enabled = (boolean) p.args[3];
                    if (!enabled) return;
                    Object btCtrl = getObjectField(p.thisObject,
                            Build.VERSION.SDK_INT >= 34 ? "bluetoothController" : "mBluetooth");
                    boolean connected = (boolean) callMethod(btCtrl, "isBluetoothConnected");
                    if (!connected) p.setResult(null);
                } catch (Throwable t) {
                    log("[ Obsidian ] SBIcons BT hook: " + t.getMessage());
                }
            }
        });
    }

    // ── WiFi activity arrows ───────────────────────────────────────────────────

    private void hookWifiActivity(XC_LoadPackage.LoadPackageParam lp) {
        // Class priority:
        //   OOS16 (SDK 36) → OplusWifiSignalExImpl  — bindEx(3)
        //   OOS15 (SDK 35) → OplusWifiSignalExImpl  — bindEx$updateActivityIcon
        //   OOS14          → OplusStatusBarSignalPolicyExImpl
        //   OOS13          → StatusBarSignalPolicyEx — getWifiActivityId
        Class<?> cls = tryFindClass(lp,
                "com.oplus.systemui.statusbar.pipeline.OplusWifiSignalExImpl",
                "com.oplus.systemui.statusbar.phone.signal.OplusStatusBarSignalPolicyExImpl",
                "com.oplusos.systemui.statusbar.phone.StatusBarSignalPolicyEx");
        XposedBridge.log("[ Obsidian ] WiFi cls=" + (cls != null ? cls.getName() : "NULL") + " SDK=" + Build.VERSION.SDK_INT);
        if (cls == null) return;

        // ── TEMP diagnostic: dump real method names so we stop guessing ──────────
        try {
            StringBuilder sb = new StringBuilder("[ Obsidian ] WiFi cls methods:");
            for (Method m : cls.getDeclaredMethods())
                sb.append(' ').append(m.getName()).append('(').append(m.getParameterCount()).append(')');
            XposedBridge.log(sb.toString());
        } catch (Throwable t) { XposedBridge.log("[ Obsidian ] WiFi cls methods dump ERROR: " + t); }
        for (int i = 1; i <= 12; i++) {
            try {
                Class<?> inner = Class.forName(cls.getName() + "$bindEx$" + i, false, lp.classLoader);
                StringBuilder sb = new StringBuilder("[ Obsidian ] WiFi inner " + inner.getSimpleName() + ":");
                for (Method m : inner.getDeclaredMethods())
                    sb.append(' ').append(m.getName()).append('(').append(m.getParameterCount()).append(')');
                XposedBridge.log(sb.toString());
            } catch (Throwable ignored) {}
        }
        // ── end TEMP diagnostic ────────────────────────────────────────────────

        if (Build.VERSION.SDK_INT >= 35) {
            XC_MethodHook actHook = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mHideWifiActivity && p.args.length > 1) p.args[1] = 0;
                }
            };
            // Exact Kotlin-lambda method name (confirmed on OOS15).
            int hooked = hookAllMethods(cls, "bindEx$updateActivityIcon", actHook).size();
            if (hooked == 0) {
                // The compiler-generated lambda name can differ per ROM build/SDK (e.g. OOS16) —
                // hookAllMethods with the wrong exact name silently hooks nothing, which is the
                // actual cause of arrows staying visible. Fall back to a signature-shape scan.
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getName().contains("ActivityIcon") && m.getParameterCount() >= 2) {
                        hookMethod(m, actHook);
                        hooked++;
                    }
                }
            }
            XposedBridge.log("[ Obsidian ] WiFi activity-icon methods hooked=" + hooked);
            if (hooked == 0) {
                log("[ Obsidian ] SBIcons: no WiFi ActivityIcon method found in " + cls.getName());
            }

            // Always additionally hook bindEx (initial bind) as a view-tree fallback, so the
            // arrow is hidden on first render even when the per-update method above wasn't
            // found or doesn't follow the args[1]=0 convention on this ROM build (OOS16).
            hookAllMethods(cls, "bindEx", new XC_MethodHook() {
                private boolean mDumped = false;
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mDumped) {
                        mDumped = true;
                        try {
                            StringBuilder sb = new StringBuilder("[ Obsidian ] WiFi bindEx args:");
                            for (Object a : p.args)
                                sb.append(' ').append(a == null ? "null" : a.getClass().getName());
                            XposedBridge.log(sb.toString());
                            if (p.args.length > 0 && p.args[0] != null) {
                                Object view0 = p.args[0];
                                // Walk the full class hierarchy — declared fields only include
                                // the class's OWN fields, the real icon fields likely live in a
                                // superclass (e.g. the base ModernStatusBarView).
                                StringBuilder hb = new StringBuilder("[ Obsidian ] WiFi arg0 hierarchy:");
                                Class<?> c = view0.getClass();
                                while (c != null && c != Object.class) {
                                    hb.append("\n  ").append(c.getName()).append(" fields:");
                                    for (java.lang.reflect.Field f : c.getDeclaredFields())
                                        hb.append(' ').append(f.getName()).append(':').append(f.getType().getSimpleName());
                                    c = c.getSuperclass();
                                }
                                XposedBridge.log(hb.toString());

                                boolean isVG = view0 instanceof android.view.ViewGroup;
                                XposedBridge.log("[ Obsidian ] WiFi arg0 isViewGroup=" + isVG
                                        + (isVG ? " childCount=" + ((android.view.ViewGroup) view0).getChildCount() : ""));
                                if (isVG) {
                                    android.view.ViewGroup vg = (android.view.ViewGroup) view0;
                                    for (int i = 0; i < vg.getChildCount(); i++) {
                                        android.view.View ch = vg.getChildAt(i);
                                        String idName = "NO_ID";
                                        try { idName = ch.getId() != android.view.View.NO_ID
                                                ? ch.getContext().getResources().getResourceEntryName(ch.getId()) : "NO_ID"; } catch (Throwable ignored) {}
                                        XposedBridge.log("[ Obsidian ] WiFi child " + i + ": " + ch.getClass().getName() + " id=" + idName);
                                        if (ch instanceof android.view.ViewGroup) {
                                            android.view.ViewGroup chVg = (android.view.ViewGroup) ch;
                                            for (int j = 0; j < chVg.getChildCount(); j++) {
                                                android.view.View gc = chVg.getChildAt(j);
                                                String gcId = "NO_ID";
                                                try { gcId = gc.getId() != android.view.View.NO_ID
                                                        ? gc.getContext().getResources().getResourceEntryName(gc.getId()) : "NO_ID"; } catch (Throwable ignored) {}
                                                XposedBridge.log("[ Obsidian ]   grandchild " + i + "." + j + ": " + gc.getClass().getName()
                                                        + " id=" + gcId + " vis=" + gc.getVisibility());
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] WiFi bindEx dump ERROR: " + t);
                        }
                    }
                    if (p.args.length == 0 || !(p.args[0] instanceof android.view.ViewGroup)) return;
                    final android.view.ViewGroup vg = (android.view.ViewGroup) p.args[0];

                    // "binding" (on the ModernStatusBarView superclass) is still null at this
                    // point — OOS16 fills it in asynchronously after bindEx returns — so a
                    // one-time hide right here is too early AND gets overwritten later when
                    // the StateFlow first emits. Re-apply on every layout pass instead of
                    // chasing the exact internal setter method.
                    if (mHideWifiActivity) hideWifiActivityViews(vg);
                    applyWifiIconColor(vg);
                    vg.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                        if (mHideWifiActivity) hideWifiActivityViews(vg);
                        applyWifiIconColor(vg);
                    });
                }
            });

        } else {
            // OOS14 and older
            hookAllMethods(cls, "getWifiActivityId", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mHideWifiActivity) p.setResult(0);
                }
            });
        }
    }

    // ── Mobile data activity arrows ────────────────────────────────────────────

    private void hookMobileActivity(XC_LoadPackage.LoadPackageParam lp) {
        // Class priority mirrors OxygenCustomizer exactly:
        //   OOS15-16 → OplusStatusBarMobileViewBinder
        //   OOS14-13 → OplusStatusBarMobileViewExImpl
        Class<?> cls = tryFindClass(lp,
                "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder",
                "com.oplus.systemui.statusbar.phone.signal.OplusStatusBarMobileViewExImpl");
        if (cls == null) return;

        // SystemUI's own bindCustEx$updateTint re-tints the "mobile_signal" icon (e.g. to a
        // semi-transparent white) right after our custom-colour drawable is bound, wiping out
        // the tint baked into it. Win the race by re-applying our colour immediately after —
        // the idempotent check (tint already == ours) stops this from re-entering forever.
        hookAllMethods(android.widget.ImageView.class, "setImageTintList", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!DstSignalIconStyle.sColorOn) return;
                try {
                    android.widget.ImageView iv = (android.widget.ImageView) p.thisObject;
                    if (iv.getId() == View.NO_ID) return;
                    if (!"mobile_signal".equals(iv.getResources().getResourceEntryName(iv.getId()))) return;
                    android.content.res.ColorStateList tint = (android.content.res.ColorStateList) p.args[0];
                    if (tint != null && tint.getDefaultColor() == DstSignalIconStyle.sColor) return;
                    iv.setImageTintList(android.content.res.ColorStateList.valueOf(DstSignalIconStyle.sColor));
                } catch (Throwable ignored) {}
            }
        });
        if (Build.VERSION.SDK_INT >= 35) {
            XC_MethodHook dataHook = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mHideMobileActivity && p.args.length > 1) p.args[1] = 0;
                }
            };
            int hooked = hookAllMethods(cls, "bindCustEx$updateDataActivity", dataHook).size();
            if (hooked == 0) {
                // Exact name mismatch on this ROM build silently hooks nothing — fall back
                // to a signature-shape scan instead of leaving the arrows unhidden.
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getName().contains("DataActivity") && m.getParameterCount() >= 2) {
                        hookMethod(m, dataHook);
                        hooked++;
                    }
                }
            }
            if (hooked == 0) {
                log("[ Obsidian ] SBIcons: no Mobile DataActivity method found in " + cls.getName());
            }
        } else {
            hookAllMethods(cls, "updateState", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mHideMobileActivity) return;
                    try {
                        ((android.widget.ImageView) getObjectField(p.thisObject, "mDataActivity")).setVisibility(View.GONE);
                        ((android.widget.ImageView) getObjectField(p.thisObject, "mIn")).setVisibility(View.GONE);
                        ((android.widget.ImageView) getObjectField(p.thisObject, "mOut")).setVisibility(View.GONE);
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    // ── WiFi activity traversal (SDK 36 fallback) ─────────────────────────────

    /**
     * Walk the view tree of the WiFi indicator and hide any view whose resource-entry
     * name contains "activity", "inout", or "arrow" — those are the data-direction arrows.
     */
    private static void hideWifiActivityViews(android.view.ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            android.view.View child = vg.getChildAt(i);
            int id = child.getId();
            if (id != android.view.View.NO_ID) {
                try {
                    String name = child.getContext().getResources().getResourceEntryName(id);
                    if (name.contains("activity") || name.contains("inout") || name.contains("arrow")) {
                        child.setVisibility(android.view.View.GONE);
                        continue;
                    }
                } catch (Throwable ignored) {}
            }
            if (child instanceof android.view.ViewGroup) {
                hideWifiActivityViews((android.view.ViewGroup) child);
            }
        }
    }

    // ── WiFi icon custom color — applied directly on the ImageView(s) ─────────
    //
    // The XResources drawable-replacement tint (DstWifiIconStyle) is not reliably invoked
    // on this ROM's modern StateFlow-driven wifi view (same root cause as the activity-arrow
    // bug: OOS16 doesn't always refetch the drawable through the classic Resources path).
    // Tinting the actual ImageView is proven reliable here (same technique as arrow-hiding),
    // so use that instead of depending on the drawable loader firing.

    private static void applyWifiIconColor(android.view.ViewGroup vg) {
        android.content.res.ColorStateList tint = DstWifiIconStyle.sColorOn
                ? android.content.res.ColorStateList.valueOf(DstWifiIconStyle.sColor)
                : null;
        tintImageViews(vg, tint);
    }

    private static void tintImageViews(android.view.View v, android.content.res.ColorStateList tint) {
        if (v instanceof android.widget.ImageView) {
            ((android.widget.ImageView) v).setImageTintList(tint);
        } else if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) tintImageViews(vg.getChildAt(i), tint);
        }
    }

    // ── Activity arrows — XResources drawable replacement (boot-safe) ─────────
    //
    // Uses the same preloadFromFile pattern as DstWifiIconStyle so the static
    // flags are set BEFORE Android first renders (and potentially caches) the
    // activity arrow drawables — avoiding the Xprefs-not-yet-loaded timing gap.

    private static final String PREFS_FILE =
        "/data/user_de/0/it.tugaia56.oxydian/shared_prefs/it.tugaia56.oxydian_preferences.xml";
    private static final String PKG_OBS = "it.tugaia56.oxydian";

    private static volatile boolean sHideWifiActivity   = false;
    private static volatile boolean sHideMobileActivity = false;

    public static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) return;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            sHideWifiActivity   = parsePrefBoolean(sb.toString(), "hide_inout_wifi");
            sHideMobileActivity = parsePrefBoolean(sb.toString(), "hide_inout_mobile");
            XposedBridge.log("[ Obsidian ] StatusbarIcons.preload: hideWifi=" + sHideWifiActivity
                    + " hideMobile=" + sHideMobileActivity);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] StatusbarIcons.preload ERROR: " + t);
        }
    }

    private static boolean parsePrefBoolean(String xml, String name) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return false;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return false;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        return ve > vs && "true".equals(xml.substring(vs, ve).trim());
    }

    private static final String[] WIFI_ACTIVITY_DRAWABLES = {
        "stat_signal_activity_wifi_in",
        "stat_signal_activity_wifi_out",
        "stat_signal_activity_wifi_inout",
        "stat_signal_activity_wifi_none",
    };

    private static final String[] MOBILE_ACTIVITY_DRAWABLES = {
        "stat_signal_activity_in_public",
        "stat_signal_activity_out_public",
        "stat_signal_activity_inout_public",
        "stat_signal_activity_default_public",
        "stat_signal_activity_soft_in_public",
        "stat_signal_activity_soft_out_public",
        "stat_signal_activity_soft_inout_public",
        "stat_signal_activity_soft_default_public",
    };

    @Override
    public void initResources() {
        // Ensure statics are loaded from disk before the first DrawableLoader call.
        preloadFromFile();
        var rp = resparams.get(SYSTEM_UI);
        if (rp == null) return;

        XResources.DrawableLoader wifiLoader = new XResources.DrawableLoader() {
            @Override public Drawable newDrawable(XResources res, int id) {
                return (sHideWifiActivity || mHideWifiActivity) ? loadHiddenDrawable(res, id) : null;
            }
        };
        XResources.DrawableLoader mobileLoader = new XResources.DrawableLoader() {
            @Override public Drawable newDrawable(XResources res, int id) {
                return (sHideMobileActivity || mHideMobileActivity) ? loadHiddenDrawable(res, id) : null;
            }
        };

        for (String name : WIFI_ACTIVITY_DRAWABLES)
            try { rp.res.setReplacement(SYSTEM_UI, "drawable", name, wifiLoader);   } catch (Throwable ignored) {}
        for (String name : MOBILE_ACTIVITY_DRAWABLES)
            try { rp.res.setReplacement(SYSTEM_UI, "drawable", name, mobileLoader); } catch (Throwable ignored) {}
    }

    // Our own module already ships a transparent-fill "obs_" clone of every activity-arrow
    // drawable (same vector, invisible fill) — see res/drawable-anydpi/obs_stat_signal_activity_*.
    // Loading that (same pattern as DstWifiIconStyle/DstSignalIconStyle) keeps the icon's real
    // intrinsic size/shape instead of a bare ColorDrawable, which some ROM icon pipelines ignore.
    private static Drawable loadHiddenDrawable(XResources res, int id) {
        try {
            String name = res.getResourceEntryName(id);
            android.content.res.Resources mr = ResourceManager.modRes;
            if (mr != null) {
                int resId = mr.getIdentifier("obs_" + name, "drawable", PKG_OBS);
                if (resId != 0) return mr.getDrawable(resId, null);
            }
        } catch (Throwable ignored) {}
        return new ColorDrawable(Color.TRANSPARENT);
    }

    // ── Brightness controller hook ─────────────────────────────────────────────

    private void hookBrightnessCtrl(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> brightnessClass = tryFindClass(lp,
                "com.oplus.systemui.qs.impl.OplusBrightnessControllerExImpl",  // OOS15-14
                "com.oplus.systemui.qs.OplusBrightnessControllerExImpl");       // OOS13
        if (brightnessClass == null) return;

        hookAllConstructors(brightnessClass, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                mOplusBrightnessCtrl = p.thisObject;
            }
        });
        hookAllMethods(brightnessClass, "setBrightnessMin", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                mMinBrightness = ((Number) p.args[0]).floatValue();
            }
        });
        hookAllMethods(brightnessClass, "setBrightnessMax", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                mMaxBrightness = ((Number) p.args[0]).floatValue();
            }
        });

        // More accurate QuickQS offset height from NSSL
        try {
            Class<?> nsslCls = findClass(
                    "com.oplus.systemui.statusbar.notification.stack.NotificationStackScrollLayoutExtImpl",
                    lp.classLoader);
            hookAllMethods(nsslCls, "initView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int h = (int) getObjectField(p.thisObject, "mQuickQsOffsetHeight");
                        if (h > 0) mQuickQsOffsetHeight = h;
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    // ── Status bar touch — double-tap sleep + brightness ──────────────────────

    private void hookStatusBarTouch(XC_LoadPackage.LoadPackageParam lp) {
        // Capture NPVC for collapse/doze state check
        Class<?> npvcClass = tryFindClass(lp,
                "com.android.systemui.shade.NotificationPanelViewController",
                "com.android.systemui.statusbar.phone.NotificationPanelViewController");
        if (npvcClass != null) {
            hookAllConstructors(npvcClass, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    mNotifPanelVC = p.thisObject;
                }
            });
        }

        Class<?> psbvcClass = tryFindClass(lp,
                "com.android.systemui.statusbar.phone.PhoneStatusBarViewController");
        if (psbvcClass == null) {
            log("[ Obsidian ] SBIcons: PhoneStatusBarViewController not found");
            return;
        }

        XC_MethodHook touchHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                MotionEvent ev = null;
                for (Object arg : p.args) {
                    if (arg instanceof MotionEvent) { ev = (MotionEvent) arg; break; }
                }
                if (ev == null) return;

                // ── Double-tap to sleep ────────────────────────────────────
                if (mDoubleTapToSleep) {
                    try {
                        boolean ok = true;
                        if (mNotifPanelVC != null) {
                            boolean pulsing   = (boolean) getObjectField(mNotifPanelVC, "mPulsing");
                            boolean dozing    = (boolean) getObjectField(mNotifPanelVC, "mDozing");
                            boolean collapsed = (boolean) callMethod(mNotifPanelVC, "isFullyCollapsed");
                            ok = !pulsing && !dozing && collapsed;
                        }
                        if (ok) mDtSleepDetector.onTouchEvent(ev);
                    } catch (Throwable ignored) {}
                }

                // ── Brightness control ─────────────────────────────────────
                if (!mBrightnessControl) return;
                final int action = ev.getAction();
                final int x = (int) ev.getRawX();
                final int y = (int) ev.getRawY();

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        if (y < mQuickQsOffsetHeight) {
                            mLinger        = 0;
                            mInitialTouchX = x;
                            mInitialTouchY = y;
                            mJustPeeked    = true;
                            mHandler.removeCallbacks(mLongPress);
                            mHandler.postDelayed(mLongPress, BRIGHTNESS_LONG_PRESS);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (y < mQuickQsOffsetHeight && mJustPeeked) {
                            if (mLinger > BRIGHTNESS_LINGER) {
                                adjustBrightness(x);
                            } else {
                                int xd   = Math.abs(x - mInitialTouchX);
                                int yd   = Math.abs(y - mInitialTouchY);
                                int slop = ViewConfiguration.get(mContext).getScaledTouchSlop();
                                if (xd > yd) mLinger++;
                                if (xd > slop || yd > slop) mHandler.removeCallbacks(mLongPress);
                            }
                        } else {
                            if (y > mQuickQsOffsetHeight) mJustPeeked = false;
                            mHandler.removeCallbacks(mLongPress);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mHandler.removeCallbacks(mLongPress);
                        break;
                }
            }
        };

        hookAllMethods(psbvcClass, "onTouch",         touchHook);
        hookAllMethods(psbvcClass, "handleTouchEvent", touchHook);
    }

    private void onLongPressBrightnessChange() {
        adjustBrightness(mInitialTouchX);
        mLinger = BRIGHTNESS_LINGER + 1;
    }

    private void adjustBrightness(int x) {
        if (mDisplayMetrics == null || mOplusBrightnessCtrl == null) return;
        float raw    = (float) x / mDisplayMetrics.widthPixels;
        float padded = Math.min(1f - BRIGHTNESS_PADDING, Math.max(BRIGHTNESS_PADDING, raw));
        float value  = (padded - BRIGHTNESS_PADDING) / (1f - 2f * BRIGHTNESS_PADDING);
        float val    = mMinBrightness + value * (mMaxBrightness - mMinBrightness);
        try { callMethod(mDisplayManager, "setTemporaryBrightness", 0, val);            } catch (Throwable ignored) {}
        try { callMethod(mDisplayManager, "setTemporaryAutoBrightnessAdjustment", val); } catch (Throwable ignored) {}
        try { callMethod(mOplusBrightnessCtrl, "setBrightness", (int) val);             } catch (Throwable ignored) {}
    }

    // ── Utility ────────────────────────────────────────────────────────────────

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return findClass(name, lp.classLoader); }
            catch (Throwable ignored) {}
        }
        log("[ Obsidian ] SBIcons: none of " + Arrays.toString(names) + " found");
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
