package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;

import de.robv.android.xposed.XposedBridge;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Replaces the QS panel blur with a solid DST background colour.
 *
 * Approach (layered, most-to-least reliable):
 *
 *  1. PRIMARY — ScrimController.updateScrimColor (BEFORE hook)
 *     Intercepts every scrim repaint and forces alpha + colour on mScrimBehind
 *     and mNotificationsScrim.  Uses the standard AOSP class
 *     com.android.systemui.statusbar.phone.ScrimController, which is always
 *     present regardless of OOS/Oplus class renaming.  Same technique used by
 *     OC's QSTransparency hook (tasks #149-#155).
 *
 *  2. SECONDARY — setBlurAmount = 0 on ScrimViewExImp
 *     Disables the OOS hardware-blur on the scrim view when enabled.
 *
 *  3. TERTIARY — FrameLayout overlay in OplusQSContainerImpl (best-effort)
 *     Kept as a supplementary in-container layer.  Works only when the OOS
 *     container class is found; silently skipped otherwise.
 */
public class QsBackground extends XposedMods {

    private static final String PREF_ENABLED  = "DST_QS_BG_ENABLED";
    private static final String PREF_BG_COLOR = "OBS_QS_BG_COLOR";
    private static final String PREF_BG_ALPHA = "OBS_QS_BG_ALPHA";  // 0-100 %
    private static final String PREFS_FILE    =
        "/data/user_de/0/it.tugaia56.oxydian/shared_prefs/" +
        "it.tugaia56.oxydian_preferences.xml";

    // ── Preloaded statics ─────────────────────────────────────────────────────
    private static volatile boolean sPreloadEnabled = false;
    private static volatile int     sPreloadBgColor = 0xFF1B2029;
    private static volatile int     sPreloadBgAlpha = 100;

    // ── Instance state ────────────────────────────────────────────────────────
    private boolean mEnabled = false;
    private int     mBgColor = 0xFF1B2029;
    private int     mBgAlpha = 100;

    // Tertiary FrameLayout overlay views
    private final List<FrameLayout> mTintViews  = new ArrayList<>();
    // ScrimViewExImp instances tracked so we can force-zero blur on pref change
    private final List<View>        mScrimViews = new ArrayList<>();

    // ── Boot-time preload ─────────────────────────────────────────────────────

    public static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) { preloadFromProps(); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();
            sPreloadEnabled = parseBoolAttr(xml, PREF_ENABLED);
            int color = parseInt(parseAttr(xml, PREF_BG_COLOR, "value"), 0);
            if (color != 0) sPreloadBgColor = color;
            int alpha = parseInt(parseAttr(xml, PREF_BG_ALPHA, "value"), -1);
            if (alpha >= 0 && alpha <= 100) sPreloadBgAlpha = alpha;
            XposedBridge.log("[ Obsidian ] QsBackground.preload: enabled=" + sPreloadEnabled
                    + " color=#" + Integer.toHexString(sPreloadBgColor)
                    + " alpha=" + sPreloadBgAlpha);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] QsBackground.preload ERROR: " + t);
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getDeclaredMethod("get", String.class, String.class);
            get.setAccessible(true);
            String onStr = (String) get.invoke(null, "persist.obsidian.dst.qs_bg_on", "");
            String bgStr = (String) get.invoke(null, "persist.obsidian.dst.bg",        "");
            if (!onStr.isEmpty()) sPreloadEnabled = "1".equals(onStr);
            if (!bgStr.isEmpty()) {
                try { int c = Integer.parseInt(bgStr); if (c != 0) sPreloadBgColor = c; }
                catch (NumberFormatException ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public QsBackground(Context context) {
        super(context);
        mEnabled = sPreloadEnabled;
        mBgColor = sPreloadBgColor;
        mBgAlpha = sPreloadBgAlpha;
    }

    // ── XposedMods ────────────────────────────────────────────────────────────

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mEnabled = Xprefs.getBoolean(PREF_ENABLED, false);
        int stored = Xprefs.getInt(PREF_BG_COLOR, 0);
        mBgColor = (stored != 0) ? stored : 0xFF1B2029;
        mBgAlpha = Xprefs.getInt(PREF_BG_ALPHA, 100);
        updateTint();  // refresh tertiary FrameLayout views

        // If just enabled (or colour/alpha changed while enabled), force-zero blur
        // on every ScrimViewExImp we've seen so far.  This handles the race where
        // setBlurAmount fired before Xprefs was ready (mEnabled was still false).
        if (mEnabled) forceZeroBlur();
    }

    /** Forces setBlurAmount(0f) on every tracked ScrimViewExImp instance. */
    private void forceZeroBlur() {
        if (mScrimViews.isEmpty()) return;
        for (View v : new ArrayList<>(mScrimViews)) {
            v.post(() -> {
                try {
                    Method m = v.getClass().getMethod("setBlurAmount", float.class);
                    m.invoke(v, 0f);
                } catch (Throwable t1) {
                    try {
                        // Fallback: search declared methods (in case setBlurAmount is not public)
                        for (Method m2 : v.getClass().getDeclaredMethods()) {
                            if ("setBlurAmount".equals(m2.getName())
                                    && m2.getParameterCount() == 1) {
                                m2.setAccessible(true);
                                m2.invoke(v, 0f);
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        // ── PRIMARY: ScrimController.updateScrimColor ─────────────────────────
        // updateScrimColor(View scrim, float alpha, @ColorInt int tint)
        // On some OOS builds the parameter order for alpha/tint is reversed:
        //   standard  → args[1]=float alpha, args[2]=int tint
        //   alternate → args[1]=int tint,    args[2]=float alpha
        // We detect the actual order at runtime via instanceof checks.
        //
        // The class may be in statusbar.phone (Android ≤15) or shade (Android 16+).
        final Class<?> ScrimControllerClass = findScrimControllerClass(lp.classLoader);
        if (ScrimControllerClass != null) {
            hookAllMethods(ScrimControllerClass, "updateScrimColor", new XC_MethodHook() {
                // Cache field references — resolved lazily on first call
                private volatile Field fBehind;
                private volatile Field fNotif;
                private volatile boolean fCached = false;
                // One-shot diagnostic log
                private volatile boolean fDiagDone = false;

                private void ensureFields() {
                    if (fCached) return;
                    try { fBehind = findField(ScrimControllerClass, "mScrimBehind"); }
                    catch (Throwable t) {
                        dbg("mScrimBehind NOT found: " + t);
                    }
                    try { fNotif  = findField(ScrimControllerClass, "mNotificationsScrim"); }
                    catch (Throwable ignored) {}
                    fCached = true;
                }

                @Override
                protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    // ── One-shot diagnostic on first actual invocation ──
                    if (!fDiagDone) {
                        fDiagDone = true;
                        ensureFields();
                        String argTypes = p.args.length >= 3
                                ? p.args[0].getClass().getSimpleName()
                                  + " | " + p.args[1].getClass().getSimpleName()
                                  + " | " + p.args[2].getClass().getSimpleName()
                                : "args.length=" + p.args.length;
                        dbg("updateScrimColor FIRED"
                                + " mEnabled=" + mEnabled
                                + " args=(" + argTypes + ")"
                                + " fBehind=" + (fBehind != null)
                                + " fNotif=" + (fNotif != null));
                    }

                    if (!mEnabled) return;
                    // Expected: args[0]=View, args[1]=float|int, args[2]=int|float
                    if (p.args.length < 3) return;

                    // Determine which arg is the float alpha
                    int alphaIndex;
                    if      (p.args[1] instanceof Float) alphaIndex = 1;
                    else if (p.args[2] instanceof Float) alphaIndex = 2;
                    else return;  // unexpected signature — bail safely
                    int colorIndex = (alphaIndex == 1) ? 2 : 1;

                    // Skip bouncer / keyguard states to avoid breaking the lock screen
                    try {
                        String state = getObjectField(p.thisObject, "mState").toString();
                        if (state.contains("BOUNCER") || state.contains("KEYGUARD")) return;
                    } catch (Throwable ignored) {}

                    // Identify the scrim: only modify mScrimBehind and mNotificationsScrim
                    ensureFields();
                    boolean isTarget = false;
                    try {
                        if (fBehind != null && p.args[0].equals(fBehind.get(p.thisObject)))
                            isTarget = true;
                    } catch (Throwable ignored) {}
                    if (!isTarget) {
                        try {
                            if (fNotif != null && p.args[0].equals(fNotif.get(p.thisObject)))
                                isTarget = true;
                        } catch (Throwable ignored) {}
                    }
                    if (!isTarget) return;

                    // Guard: only apply when system is making the scrim visible.
                    // If we override a transparent scrim (QS closed) we color the
                    // entire screen. The system hides the scrim either via view-alpha≈0
                    // or via a near-transparent tint color — check both.
                    float origAlpha = (float) p.args[alphaIndex];
                    if (origAlpha < 0.01f) return;
                    if (p.args[colorIndex] instanceof Integer
                            && Color.alpha((int) p.args[colorIndex]) < 3) return;

                    // Force our alpha (float) and solid colour (int)
                    // Alpha is controlled by the float param; tint is fully opaque RGB
                    p.args[alphaIndex] = mBgAlpha / 100f;
                    if (p.args[colorIndex] instanceof Integer) {
                        p.args[colorIndex] = (mBgColor & 0x00FFFFFF) | 0xFF000000;
                    }
                }
            });
        }

        // ── SECONDARY: disable blur on ScrimViewExImp ─────────────────────────
        // Also track instances so we can force-zero blur when mEnabled changes.
        try {
            Class<?> scrimViewExImp = findClass(
                    "com.oplus.systemui.scrim.ScrimViewExImp", lp.classLoader);
            hookAllMethods(scrimViewExImp, "setBlurAmount", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    // Track the View instance so updatePrefs() can force-zero later.
                    if (p.thisObject instanceof View) {
                        View v = (View) p.thisObject;
                        if (!mScrimViews.contains(v)) mScrimViews.add(v);
                    }
                    if (mEnabled && p.args.length > 0 && p.args[0] instanceof Float)
                        p.args[0] = 0f;
                }
            });
            log("[ Obsidian ] QsBackground: setBlurAmount hook installed");
        } catch (Throwable ignored) {
            log("[ Obsidian ] QsBackground: ScrimViewExImp not found, blur hook skipped");
        }

        // ── TERTIARY: FrameLayout overlay in OplusQSContainerImpl (best-effort) ─
        Class<?> containerClass = findContainerClass(lp.classLoader);
        if (containerClass != null) {
            hookAllMethods(containerClass, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof FrameLayout)
                        addTintView((FrameLayout) p.thisObject);
                }
            });
            try {
                hookAllMethods(containerClass, "updateResources", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        updateTint();
                    }
                });
            } catch (Throwable ignored) {}
            log("[ Obsidian ] QsBackground: OplusQSContainerImpl overlay hook installed");
        } else {
            log("[ Obsidian ] QsBackground: OplusQSContainerImpl not found — ScrimController is primary");
        }

        // ── OplusQSRootView (split / landscape QS) ────────────────────────────
        try {
            Class<?> rootView = findClass(
                    "com.oplus.systemui.plugins.qs.OplusQSRootView", lp.classLoader);
            hookAllMethods(rootView, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof FrameLayout)
                        addTintView((FrameLayout) p.thisObject);
                }
            });
        } catch (Throwable ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void dbg(String msg) {
        Log.d("ObsidianQsBG", msg);
        XposedBridge.log("[ Obsidian ] QsBG: " + msg);
    }

    private Class<?> findScrimControllerClass(ClassLoader cl) {
        // Android ≤15: com.android.systemui.statusbar.phone.ScrimController
        try {
            Class<?> c = findClass("com.android.systemui.statusbar.phone.ScrimController", cl);
            dbg("ScrimController found in statusbar.phone");
            return c;
        } catch (Throwable ignored) {}
        // Android 16+: possibly moved to shade package
        try {
            Class<?> c = findClass("com.android.systemui.shade.ScrimController", cl);
            dbg("ScrimController found in shade");
            return c;
        } catch (Throwable ignored) {}
        dbg("ScrimController NOT FOUND in any known package");
        return null;
    }

    private Class<?> findContainerClass(ClassLoader cl) {
        try { return findClass("com.oplus.systemui.qs.OplusQSContainerImpl", cl); }
        catch (Throwable ignored) {}
        try { return findClass("com.oplusos.systemui.qs.OplusQSContainerImpl", cl); }
        catch (Throwable ignored) {}
        return null;
    }

    // ── Tertiary FrameLayout management ───────────────────────────────────────

    private void addTintView(FrameLayout root) {
        FrameLayout tintView = new FrameLayout(mContext);
        tintView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        tintView.setVisibility(View.GONE);
        root.addView(tintView, 0);
        mTintViews.add(tintView);
        updateTint();
    }

    private void updateTint() {
        if (mTintViews.isEmpty()) return;
        if (!mEnabled) {
            for (FrameLayout v : mTintViews) v.post(() -> v.setVisibility(View.GONE));
            return;
        }
        int alpha255 = (int) Math.round(mBgAlpha / 100.0 * 255);
        int color = Color.argb(alpha255,
                Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor));
        for (FrameLayout v : mTintViews) {
            v.post(() -> {
                v.setBackgroundColor(color);
                v.setAlpha(1f);
                v.setVisibility(View.VISIBLE);
            });
        }
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }

    // ── XML parse helpers ─────────────────────────────────────────────────────

    private static boolean parseBoolAttr(String xml, String name) {
        return "true".equalsIgnoreCase(parseAttr(xml, name, "value"));
    }
    private static String parseAttr(String xml, String name, String attr) {
        int idx = xml.indexOf("name=\"" + name + "\"");
        if (idx < 0) return null;
        String key = attr + "=\"";
        int s = xml.indexOf(key, idx);
        if (s < 0) return null;
        s += key.length();
        int e = xml.indexOf("\"", s);
        return e < 0 ? null : xml.substring(s, e);
    }
    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
