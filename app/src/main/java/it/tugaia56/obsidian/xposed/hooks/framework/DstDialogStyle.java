package it.tugaia56.obsidian.xposed.hooks.framework;

import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

import it.tugaia56.obsidian.utils.ColorUtils;
import android.content.res.XResources;

/**
 * Xposed hook: replaces android:drawable/dialog_background_material with one
 * of 8 DST dialog style presets (identical to OC's DSTD**** RRO overlays).
 *
 * Uses a preload-from-disk pattern because:
 *  - android package resources are initialized in EVERY process
 *  - ContentProvider (Xprefs) is unavailable in system_server / early boot
 *  - Always re-reads to avoid stale zygote fork statics
 *
 * Preset codes (stored in DST_DLG_PRESET_NAME):
 *   DSTDHT  – Dialog Higher Transparent
 *   DSTDHTO – Dialog Higher Transparent Outlined
 *   DSTDLT  – Dialog Lower Transparent
 *   DSTDLYO – Dialog Lower Transparent Outlined
 *   DSTDMT  – Dialog Medium Transparent
 *   DSTDMTO – Dialog Medium Transparent Outlined
 *   DSTDS   – Dialog Solid
 *   DSTDSO  – Dialog Solid Outlined
 */
public class DstDialogStyle {

    private static final String PKG_ANDROID   = "android";
    private static final String DRAWABLE_NAME = "dialog_background_material";
    private static final String PREF_PRESET   = "DST_DLG_PRESET_NAME";
    private static final String PREF_ACCENT1  = "DST_ACCENT1";
    private static final String PREF_BG       = "DST_BACKGROUND";
    private static final String PREF_CORNER   = "DST_DLG_CORNER";
    private static final String PREFS_FILE    =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";
    private static final int DEFAULT_CORNER_DP = 14;

    // ── Preloaded statics — refreshed on every applyPreloaded() call ─────────
    private static volatile boolean sPreloaded = false;
    private static volatile String  sDlgPreset = null;  // "DSTDHT" … "DSTDSO"
    private static volatile int     sAccent    = 0xFFFFFFFF;
    private static volatile int     sBg        = 0xFF1B2029;
    private static volatile int     sCornerDp  = DEFAULT_CORNER_DP;

    // ── SystemProperties helper ───────────────────────────────────────────────

    /**
     * Reads a system property via XposedHelpers.callStaticMethod.
     * This bypasses Android 9+ hidden-API restrictions that break manual
     * reflection (Class.forName + getDeclaredMethod + setAccessible) in
     * regular app processes.  Works in every context: zygote, system_server,
     * SystemUI, and all user-app processes.
     */
    private static String readProp(String key, String def) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object val = XposedHelpers.callStaticMethod(sp, "get", key, def);
            return val != null ? (String) val : def;
        } catch (Throwable t) {
            return def;
        }
    }

    // ── Boot-time preload (called from ResourceManager.initZygote) ───────────

    public static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) return;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();

            sDlgPreset = parseStringContent(xml, PREF_PRESET);
            sAccent    = parseInt(parseAttr(xml, PREF_ACCENT1, "value"), 0xFFFFFFFF);
            sBg        = parseInt(parseAttr(xml, PREF_BG,      "value"), 0xFF1B2029);
            int corner = parseInt(parseAttr(xml, PREF_CORNER, "value"), DEFAULT_CORNER_DP);
            sCornerDp  = (corner > 0) ? corner : DEFAULT_CORNER_DP;
            sPreloaded = true;
            XposedBridge.log("[ Obsidian ] DstDialogStyle.preload: preset=" + sDlgPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstDialogStyle.preload ERROR: " + t);
        }
    }

    // ── System-property fallback (same EACCES workaround as MonetFreeze) ──────

    /**
     * Reads persist.obsidian.dst.dlg_preset (and accent/bg colours) via
     * SystemProperties.  Called when preloadFromFile() fails due to EACCES.
     * Respects the _on flags so a disabled colour reverts to the default.
     */
    public static void preloadFromProps() {
        try {
            String preset = readProp("persist.obsidian.dst.dlg_preset", "");
            if (preset.isEmpty()) return;
            sDlgPreset = preset;
            // Honour _on flag: only apply the colour if the toggle is enabled
            if ("1".equals(readProp("persist.obsidian.dst.a1_on", "0"))) {
                String a1Str = readProp("persist.obsidian.dst.a1", "");
                if (!a1Str.isEmpty()) {
                    try { sAccent = Integer.parseInt(a1Str); } catch (NumberFormatException ignored) {}
                }
            } // else sAccent stays at default 0xFFFFFFFF (white)
            if ("1".equals(readProp("persist.obsidian.dst.bg_on", "0"))) {
                String bgStr = readProp("persist.obsidian.dst.bg", "");
                if (!bgStr.isEmpty()) {
                    try { sBg = Integer.parseInt(bgStr); } catch (NumberFormatException ignored) {}
                }
            } // else sBg stays at default 0xFF1B2029
            String cornStr = readProp("persist.obsidian.dst.dlg_corner", "");
            if (!cornStr.isEmpty()) {
                try {
                    int c = Integer.parseInt(cornStr);
                    sCornerDp = (c > 0) ? c : DEFAULT_CORNER_DP;
                } catch (NumberFormatException ignored) {}
            }
            sPreloaded = true;
            XposedBridge.log("[ Obsidian ] DstDialogStyle.preload(props): preset=" + sDlgPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstDialogStyle.preload(props) ERROR: " + t);
        }
    }

    // ── Called from ResourceManager.handleInitPackageResources ───────────────

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        // Non filtrare per packageName: su Android moderno handleInitPackageResources
        // spara con il packageName dell'app, non "android". Dobbiamo impostare il
        // replacement in OGNI processo perché ogni processo ha la propria resource cache.
        preloadFromFile(); // always re-read — don't rely on stale zygote statics
        if (!sPreloaded || sDlgPreset == null) preloadFromProps(); // EACCES fallback
        if (!sPreloaded || sDlgPreset == null) return;

        try {
            // Rilettura ad ogni chiamata invece di catturare preset/accent/bg come "final" —
            // senza questo il dialogo resterebbe fissato al preset attivo al primo avvio del
            // processo, ignorando i cambi successivi finché non si riavvia SystemUI/l'app.
            rp.res.setReplacement(PKG_ANDROID, "drawable", DRAWABLE_NAME,
                new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        // Sistema in chiaro: lasciamo il dialogo stock di OOS (già corretto in
                        // chiaro) invece di un preset pensato/testato solo per lo scuro — vedi
                        // lo stesso gate in DstNotifStyle/DstToastStyle.
                        boolean isNight = (res.getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        if (!isNight) return null;
                        preloadFromFile();
                        if (!sPreloaded || sDlgPreset == null) preloadFromProps();
                        if (sDlgPreset == null) return new GradientDrawable();
                        return buildDrawable(sDlgPreset, sAccent, sBg,
                                res.getDisplayMetrics().density, sCornerDp);
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstDialogStyle.applyPreloaded ERROR: " + t);
        }
    }

    // ── Drawable factory ──────────────────────────────────────────────────────

    /**
     * Public entry point — called from StatusbarFragment to apply the preset to
     * our own in-app dialog. The Xposed hook uses the private overload below.
     */
    public static Drawable buildDrawable(String preset, int accent, int bg, float density) {
        return buildDrawable(preset, accent, bg, density, DEFAULT_CORNER_DP);
    }

    /** Come sopra, con raggio d'angolo regolabile ("Raggio Finestre Dialogo"). */
    public static Drawable buildDrawable(String preset, int accent, int bg, float density, int cornerDp) {
        float corner = cornerDp * density;
        int   stroke = Math.round(1.5f * density);

        if ("DSTDSO".equals(preset)) {
            int inset = Math.round(16f * density);
            return tintBlockedInset(shapeForPreset(preset, accent, bg, density, corner, stroke), inset);
        }
        return shapeForPreset(preset, accent, bg, density, corner, stroke);
    }

    /**
     * Come {@link #buildDrawable} ma senza l'InsetDrawable a 16dp usato da DSTDSO per il
     * vero dialogo a schermo intero — in un'anteprima piccola (griglia scelta stile) quel
     * margine fisso mangia una frazione enorme dello swatch e sembra "più piccolo" degli
     * altri preset. Qui si vede solo la forma, alla stessa scala di tutti gli altri.
     */
    public static Drawable buildPreviewDrawable(String preset, int accent, int bg, float density) {
        return buildPreviewDrawable(preset, accent, bg, density, DEFAULT_CORNER_DP);
    }

    public static Drawable buildPreviewDrawable(String preset, int accent, int bg, float density, int cornerDp) {
        float corner = cornerDp * density;
        int   stroke = Math.round(1.5f * density);
        return shapeForPreset(preset, accent, bg, density, corner, stroke);
    }

    private static Drawable shapeForPreset(String preset, int accent, int bg, float density,
                                            float corner, int stroke) {
        switch (preset) {
            case "DSTDHT":  return shape(0x331b2029, 0,      0,      corner);
            case "DSTDHTO": return shape(0x331b2029, accent, stroke, corner);
            case "DSTDLT":  return shape(0xcc1b2029, 0,      0,      corner);
            case "DSTDLYO": return shape(0xcc1b2029, accent, stroke, corner);
            case "DSTDMT":  return shape(0x801b2029, 0,      0,      corner);
            case "DSTDMTO": return shape(0x80000000, accent, stroke, corner);
            case "DSTDS":   return shape(ColorUtils.adjustColor(bg, 30), 0, 0, corner);
            case "DSTDSO":  return shape(bg, accent, stroke, corner);
            default:
                // Unknown preset — transparent fallback (stock drawable used instead)
                return shape(0x00000000, 0, 0, corner);
        }
    }

    /** GradientDrawable che blocca il setTint/setColorFilter di OOS — OOS applica un tint di
     *  sistema sul drawable dopo averlo caricato; bloccandolo il nostro colore resta quello
     *  scelto dall'utente. */
    private static GradientDrawable shape(int fill, int strokeColor, int strokeWidth,
                                          float cornerRadius) {
        GradientDrawable d = new GradientDrawable() {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(cornerRadius);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    /** InsetDrawable che blocca il setTint/setColorFilter di OOS a livello di wrapper. */
    private static InsetDrawable tintBlockedInset(Drawable base, int inset) {
        return new InsetDrawable(base, inset) {
            @Override public void setTint(int tintColor) { /* block OOS tint */ }
            @Override public void setTintList(ColorStateList tint) { /* block OOS tint */ }
            @Override public void setTintMode(PorterDuff.Mode tintMode) { /* block */ }
            @Override public void setColorFilter(ColorFilter cf) { /* block OOS colorFilter */ }
            @Override public void setColorFilter(int color, PorterDuff.Mode mode) { /* block */ }
        };
    }

    // ── Zygote-global Dialog.show() hook ─────────────────────────────────────

    /**
     * Installs the Dialog.show() hook in the ZYGOTE process.
     *
     * Because every app process is a fork of zygote, hooks installed here
     * propagate to ALL processes — regardless of the module's LSPosed scope.
     * This is the reliable path for truly global hooks.
     *
     * Called from ResourceManager.initZygote().
     *
     * IMPORTANT: XC_MethodHook must NOT be stored as a static field in this
     * class — it would be resolved at class-load time (in <clinit>), which
     * causes NoClassDefFoundError when the UI calls buildDrawable() in the
     * normal (non-injected) app process where Xposed classes are compileOnly.
     * Keep it as a local/anonymous reference inside each method body so it is
     * only resolved when the method is actually invoked.
     */
    public static void hookDialogGlobal() {
        try {
            XposedHelpers.findAndHookMethod(android.app.Dialog.class, "show",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        applyToDialog(p);
                    }
                });
            XposedBridge.log("[ Obsidian ] DstDialogStyle: Dialog hook installed in zygote (global)");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstDialogStyle.hookDialogGlobal ERROR: " + t);
        }
    }

    // ── Per-process Dialog.show() hook ───────────────────────────────────────

    /**
     * Installs the Dialog.show() hook in the CURRENT process (backup).
     *
     * Called from XPLauncher.hookFramework() when packageName == "android".
     * Serves as a safety net for processes where the zygote hook might not
     * propagate (e.g., isolated processes or if scope is narrow).
     */
    public static void hookDialogInProcess() {
        try {
            XposedHelpers.findAndHookMethod(android.app.Dialog.class, "show",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        applyToDialog(p);
                    }
                });
            XposedBridge.log("[ Obsidian ] DstDialogStyle: Dialog hook installed in-process");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstDialogStyle.hookDialogInProcess ERROR: " + t);
        }
    }

    /**
     * Shared implementation called by both global and per-process hooks.
     * Extracted to avoid duplicating the logic, without using a static
     * XC_MethodHook field (which would trigger class-load resolution of
     * Xposed classes in the normal app process).
     */
    private static void applyToDialog(XC_MethodHook.MethodHookParam p) {
        String cls = p.thisObject.getClass().getName();
        XposedBridge.log("[ Obsidian ] DstDialog: Dialog.show cls=" + cls);

        // Skip bottom sheets (positioned at screen bottom — inset preset looks wrong)
        if (cls.contains("BottomSheet")) return;

        // Skip system overlay dialogs that should not get our background treatment:
        //   - Volume panel (slider when pressing hardware volume keys)
        //   - Power menu / GlobalActions (Riavvio / Spegni screen)
        // Two-pronged check:
        //   1. Class name contains "Volume" (catches inner classes like OplusVolumeDialogImpl$X)
        //   2. Window type == TYPE_VOLUME_OVERLAY (2009) for dialogs created as plain android.app.Dialog
        if (cls.contains("Volume")) return;
        if (cls.contains("GlobalAction")) return;
        try {
            android.app.Dialog dlgCheck = (android.app.Dialog) p.thisObject;
            android.view.Window wCheck = dlgCheck.getWindow();
            if (wCheck != null) {
                // TYPE_VOLUME_OVERLAY = 2009 (hardcoded: the constant is deprecated on SDK36
                // but the value hasn't changed and is reliable across all versions we support)
                if (wCheck.getAttributes().type == 2009) return;
            }
        } catch (Throwable ignored) {}

        // Read preset from sys-prop every call — no statics, no fork-inheritance issue.
        // readProp() uses XposedHelpers which bypasses Android 9+ hidden-API restrictions
        // and works in every context: zygote, system_server, SystemUI, user apps.
        String preset = readProp("persist.obsidian.dst.dlg_preset", "");
        XposedBridge.log("[ Obsidian ] DstDialog: preset='" + preset + "'");
        if (preset.isEmpty()) return;

        // Sistema in chiaro: lasciamo il dialogo stock (già corretto in chiaro) invece di un
        // preset pensato/testato solo per lo scuro — stesso gate di DstNotifStyle/DstToastStyle.
        try {
            android.content.Context ctx = ((android.app.Dialog) p.thisObject).getContext();
            boolean isNight = (ctx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            if (!isNight) return;
        } catch (Throwable ignored) {}

        boolean a1On = "1".equals(readProp("persist.obsidian.dst.a1_on", "0"));
        int accent = a1On
                ? parseInt(readProp("persist.obsidian.dst.a1", ""), 0xFFFFFFFF)
                : 0xFFFFFFFF;
        boolean bgOn = "1".equals(readProp("persist.obsidian.dst.bg_on", "0"));
        int bg = bgOn
                ? parseInt(readProp("persist.obsidian.dst.bg", ""), 0xFF1B2029)
                : 0xFF1B2029;

        android.app.Dialog d = (android.app.Dialog) p.thisObject;
        android.view.Window w = d.getWindow();
        if (w == null) { XposedBridge.log("[ Obsidian ] DstDialog: window null"); return; }

        w.setBackgroundDrawable(buildDrawable(preset, accent, bg,
                d.getContext().getResources().getDisplayMetrics().density));
        XposedBridge.log("[ Obsidian ] DstDialog: applied preset=" + preset + " bg=0x"
                + Integer.toHexString(bg) + " in " + android.os.Process.myProcessName());
    }

    // ── XML parse helpers (duplicated from MonetFreeze for independence) ──────

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
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

    private static String parseStringContent(String xml, String name) {
        String needle = "name=\"" + name + "\">";
        int idx = xml.indexOf(needle);
        if (idx < 0) return null;
        int s = idx + needle.length();
        int e = xml.indexOf("<", s);
        return e <= s ? null : xml.substring(s, e).trim();
    }
}
