package it.tugaia56.obsidian.xposed.hooks.framework;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.widget.ProgressBar;

import android.content.res.XResources;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

import it.tugaia56.obsidian.xposed.ResourceManager;

/**
 * Xposed hook: replaces android progress/spinner drawables with one of 5 DST CPB presets.
 *
 * Targets: android package
 *   — spinner_{16|48|76}_{inner|outer}_holo  (legacy Holo spinners — OOS16 still uses these)
 *   — progress_{large|medium|small}_material (Material progress drawables — belt-and-suspenders)
 *
 * Presets:  DSTCPB1 = OOS spiral arc   (outer: white dots, inner: accent arc)
 *           DSTCPB2 = Refresh icon      (outer: accent, inner: transparent)
 *           DSTCPB3 = Speed/face icon   (outer: accent, inner: transparent)
 *           DSTCPB4 = Two quarter-arcs  (outer+inner: accent tinted)
 *           DSTCPB5 = Star circle       (outer: accent, inner: transparent)
 *
 * KEY DESIGN NOTE — why newDrawable() accesses modRes at call time:
 *   applyPreloaded() is called from handleInitPackageResources(), which fires BEFORE
 *   XPLauncher.initContext() sets ResourceManager.modRes. So modRes IS null at registration
 *   time. By the time any ProgressBar is shown (and newDrawable() fires), initContext() has
 *   already run and modRes IS set. Accessing it at call time (not capturing it in the closure
 *   at registration time) is therefore correct and mirrors OC's approach.
 *
 *   This is the bug fix for the previous XModuleResources.createInstance() approach,
 *   which captured a broken XModuleResources object (tied to the early-init XResources).
 */
public class DstCpbStyle {

    private static final String PKG_ANDROID  = "android";
    private static final String PKG_OBS      = "it.tugaia56.obsidian";
    private static final String PREF_PRESET  = "DST_PRESET_CPB";
    private static final String PREF_ACCENT1 = "DST_ACCENT1";
    private static final String PREFS_FILE   =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";

    private static volatile boolean sPreloaded = false;
    private static volatile String  sCpbPreset = null;   // "DSTCPB1" .. "DSTCPB5"
    private static volatile int     sAccent    = 0xFF9C27B0;

    // ── Boot-time preload ────────────────────────────────────────────────────

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

            sCpbPreset = parseStringContent(xml, PREF_PRESET);
            sAccent    = parseInt(parseAttr(xml, PREF_ACCENT1, "value"), 0xFF9C27B0);
            sPreloaded = true;
            XposedBridge.log("[ Obsidian ] DstCpbStyle.preload(file): preset=" + sCpbPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstCpbStyle.preload(file) ERROR: " + t + " — trying props");
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            String preset = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.cpb_preset", "");
            String a1Str  = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.a1", "");
            XposedBridge.log("[ Obsidian ] DstCpbStyle.preloadFromProps: preset='" + preset + "'");
            if (preset.isEmpty()) return;
            sCpbPreset = preset;
            if (!a1Str.isEmpty()) {
                try { sAccent = Integer.parseInt(a1Str); } catch (NumberFormatException ignored) {}
            }
            sPreloaded = true;
            XposedBridge.log("[ Obsidian ] DstCpbStyle.preload(props): preset=" + sCpbPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstCpbStyle.preload(props) ERROR: " + t);
        }
    }

    // ── Called from ResourceManager.handleInitPackageResources (early) ───────

    /**
     * Registers XResources setReplacement callbacks for all accumulated resparams.
     * Called early (during handleInitPackageResources) so every process that loads
     * android framework resources gets the replacement registered before any UI shows.
     *
     * The newDrawable() closures DO NOT capture modRes — they access ResourceManager.modRes
     * at call time. This is correct because modRes is null here (initContext hasn't run yet)
     * but is guaranteed to be set before any ProgressBar drawable is requested.
     */
    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        preloadFromFile();
        if (!sPreloaded || sCpbPreset == null) return;

        int n = presetIndex(sCpbPreset);
        if (n == 0) return;

        final int accent = sAccent;

        // Apply to ALL accumulated resparams + current rp.
        java.util.List<XC_InitPackageResources.InitPackageResourcesParam> allRp =
                new java.util.ArrayList<>(ResourceManager.resparams.values());
        if (!allRp.contains(rp)) allRp.add(rp);

        for (XC_InitPackageResources.InitPackageResourcesParam target : allRp) {
            try {
                // Legacy Holo spinners — OOS16 still uses these for ProgressBar
                for (String size : new String[]{"16", "48", "76"}) {
                    replaceSpinner(target, n, size, "inner", accent);
                    replaceSpinner(target, n, size, "outer", accent);
                }
                // Material progress drawables (belt-and-suspenders)
                replaceProgress(target, n, "progress_large_material",  "large",  n == 1, accent);
                replaceProgress(target, n, "progress_medium_material", "medium", n == 1, accent);
                replaceProgress(target, n, "progress_small_material",  "small",  n == 1, accent);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstCpbStyle: error on " + target.packageName + ": " + t);
            }
        }
    }

    /**
     * Late re-registration of CPB drawable replacements.
     * Called from XPLauncher.initContext() AFTER ResourceManager.modRes is set.
     *
     * This mirrors OC's DstCpbStyle.initResources() called from updatePrefs():
     * calling setReplacement() AFTER modRes is ready forces XResources to invalidate
     * any previously cached drawables and go through our loader on the next request.
     * This is the belt-and-suspenders approach that makes it work even if the early
     * registration in applyPreloaded() was somehow missed.
     */
    public static void initResourcesLate() {
        if (!sPreloaded) preloadFromFile();
        if (!sPreloaded || sCpbPreset == null) return;

        int n = presetIndex(sCpbPreset);
        if (n == 0) return;

        java.util.List<XC_InitPackageResources.InitPackageResourcesParam> allRp =
                new java.util.ArrayList<>(ResourceManager.resparams.values());
        if (allRp.isEmpty()) return;

        final int accent = sAccent;
        XposedBridge.log("[ Obsidian ] DstCpbStyle.initResourcesLate: preset=DSTCPB" + n
                + " resparams=" + allRp.size()
                + " modRes=" + (ResourceManager.modRes != null ? "OK" : "NULL"));

        for (XC_InitPackageResources.InitPackageResourcesParam target : allRp) {
            try {
                for (String size : new String[]{"16", "48", "76"}) {
                    replaceSpinner(target, n, size, "inner", accent);
                    replaceSpinner(target, n, size, "outer", accent);
                }
                replaceProgress(target, n, "progress_large_material",  "large",  n == 1, accent);
                replaceProgress(target, n, "progress_medium_material", "medium", n == 1, accent);
                replaceProgress(target, n, "progress_small_material",  "small",  n == 1, accent);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstCpbStyle.initResourcesLate error on "
                        + target.packageName + ": " + t);
            }
        }
    }

    // ── Zygote method hook (additional path for SDK36) ───────────────────────

    /**
     * Hooks ProgressBar.setIndeterminateDrawable() in the Zygote so all forked processes
     * inherit it. Additional path on SDK36/OOS16 to catch any spinner not covered by
     * XResources setReplacement.
     *
     * Called from ResourceManager.initZygote() — same pattern as DstDialogStyle.hookDialogGlobal().
     */
    public static void hookProgressBarGlobal() {
        try {
            XposedHelpers.findAndHookMethod(
                ProgressBar.class, "setIndeterminateDrawable", Drawable.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!sPreloaded || sCpbPreset == null) return;
                        int n = presetIndex(sCpbPreset);
                        if (n == 0) return;

                        Drawable incoming = (Drawable) param.args[0];
                        XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB fired type="
                            + (incoming == null ? "null" : incoming.getClass().getSimpleName())
                            + " preset=" + sCpbPreset);
                        if (incoming == null) return;
                        // Skip if already our LayerDrawable replacement to avoid double-apply
                        if (incoming instanceof LayerDrawable) return;
                        // Barre non circolari (es. "Avvio del telefono" durante il boot, richiesta
                        // esplicita dell'utente 2026-09-06): i 5 preset CPB sono tutti forme
                        // circolari, sostituire il drawable qui sarebbe sbagliato — ci limitiamo a
                        // tingere quella ESISTENTE, forma invariata.
                        if (!isSquareDrawable(incoming)) {
                            try {
                                incoming.mutate().setTint(sAccent);
                            } catch (Throwable t) {
                                XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB tint non-circular error: " + t);
                            }
                            return;
                        }

                        try {
                            ProgressBar pb = (ProgressBar) param.thisObject;
                            android.content.res.Resources modRes = ResourceManager.modRes;
                            if (modRes == null) {
                                XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB modRes=null, skip");
                                return;
                            }
                            String sizeName = getSizeName(pb);
                            String modName = "obs_cpb" + n + "_progress_" + sizeName;
                            int resId = modRes.getIdentifier(modName, "drawable", PKG_OBS);
                            if (resId == 0) {
                                XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB resId=0 for " + modName);
                                return;
                            }
                            Drawable d = modRes.getDrawable(resId, null);
                            if (d == null) {
                                XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB null drawable for " + modName);
                                return;
                            }
                            d = d.mutate();
                            boolean isCpb1 = (n == 1);
                            if (!isCpb1) {
                                d.setTint(sAccent);
                            } else {
                                if (d instanceof LayerDrawable) {
                                    LayerDrawable ld = (LayerDrawable) d;
                                    if (ld.getNumberOfLayers() >= 2) {
                                        applyTintToAnimatedRotateLayer(ld.getDrawable(1), sAccent);
                                    }
                                }
                            }
                            param.args[0] = d;
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB replaced preset=DSTCPB"
                                + n + " size=" + sizeName);
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: hookPB error: " + t);
                        }
                    }
                });
            XposedBridge.log("[ Obsidian ] DstCpbStyle: ProgressBar hooked globally");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstCpbStyle: hookProgressBarGlobal FAILED: " + t);
        }
    }

    /**
     * Circular indeterminate spinners are square (equal intrinsic width/height).
     * Horizontal indeterminate bars (e.g. the boot-time "Avvio telefono" bar) are wide
     * and short, or report no intrinsic size at all — either way, not square.
     */
    private static boolean isSquareDrawable(Drawable d) {
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();
        return w > 0 && h > 0 && w == h;
    }

    // ── Drawable replacement helpers ─────────────────────────────────────────

    /**
     * Registers a setReplacement for one progress_*_material drawable.
     * newDrawable() accesses ResourceManager.modRes at call time (not at registration time).
     */
    private static void replaceProgress(
            XC_InitPackageResources.InitPackageResourcesParam rp,
            int n, String targetName, String sizeName, boolean isCpb1, int accent) {

        final String modName = "obs_cpb" + n + "_progress_" + sizeName;

        try {
            rp.res.setReplacement(PKG_ANDROID, "drawable", targetName,
                new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        // Access modRes at call time — it is null at registration time
                        // (handleInitPackageResources) but set by the time any UI drawable fires.
                        android.content.res.Resources mr = ResourceManager.modRes;
                        if (mr == null) {
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: newDrawable modRes=null for " + modName);
                            return null;
                        }
                        try {
                            int resId = mr.getIdentifier(modName, "drawable", PKG_OBS);
                            if (resId == 0) {
                                XposedBridge.log("[ Obsidian ] DstCpbStyle: newDrawable resId=0 for " + modName);
                                return null;
                            }
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: newDrawable CALLED pkg="
                                    + rp.packageName + " name=" + modName);
                            Drawable d = mr.getDrawable(resId, null);
                            if (d == null) return null;
                            d = d.mutate();
                            if (!isCpb1) {
                                d.setTint(accent);
                            } else {
                                if (d instanceof LayerDrawable) {
                                    LayerDrawable ld = (LayerDrawable) d;
                                    if (ld.getNumberOfLayers() >= 2) {
                                        applyTintToAnimatedRotateLayer(ld.getDrawable(1), accent);
                                    }
                                }
                            }
                            return d;
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: newDrawable ERROR for "
                                    + modName + ": " + t);
                            return null;
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstCpbStyle: setReplacement error for "
                    + targetName + " in " + rp.packageName + ": " + t);
        }
    }

    /**
     * Registers a setReplacement for one spinner_*_holo drawable.
     * newDrawable() accesses ResourceManager.modRes at call time.
     */
    private static void replaceSpinner(
            XC_InitPackageResources.InitPackageResourcesParam rp,
            int n, String size, String type, int accent) {

        final String targetName = "spinner_" + size + "_" + type + "_holo";
        final String modName    = "obs_cpb" + n + "_s" + size + "_" + type;
        final boolean applyTint = shouldTintLayer(n, type);

        try {
            rp.res.setReplacement(PKG_ANDROID, "drawable", targetName,
                new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        android.content.res.Resources mr = ResourceManager.modRes;
                        if (mr == null) return null;
                        try {
                            int resId = mr.getIdentifier(modName, "drawable", PKG_OBS);
                            if (resId == 0) return null;
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: newDrawable HOLO pkg="
                                    + rp.packageName + " name=" + modName);
                            Drawable d = mr.getDrawable(resId, null);
                            if (d != null && applyTint) {
                                d = d.mutate();
                                d.setTint(accent);
                            }
                            return d;
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] DstCpbStyle: newDrawable HOLO error "
                                    + modName + ": " + t);
                            return null;
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstCpbStyle: setReplacement error for "
                    + targetName + " in " + rp.packageName + ": " + t);
        }
    }

    // ── Misc helpers ─────────────────────────────────────────────────────────

    /** Returns "small", "medium", or "large" based on the ProgressBar's minimum width (dp). */
    private static String getSizeName(ProgressBar pb) {
        try {
            float density = pb.getResources().getDisplayMetrics().density;
            int minDp = (int) (pb.getMinimumWidth() / density);
            if (minDp <= 24) return "small";
            if (minDp <= 56) return "medium";
        } catch (Throwable ignored) { }
        return "large";
    }

    /**
     * Applies tint to the drawable wrapped inside an AnimatedRotateDrawable.
     * AnimatedRotateDrawable extends DrawableWrapper (API 23+), so getDrawable() is public.
     */
    private static void applyTintToAnimatedRotateLayer(Drawable animRotate, int accent) {
        try {
            Drawable wrapped = null;
            if (animRotate instanceof DrawableWrapper) {
                wrapped = ((DrawableWrapper) animRotate).getDrawable();
            }
            if (wrapped == null) {
                Object obj = XposedHelpers.callMethod(animRotate, "getDrawable");
                if (obj instanceof Drawable) wrapped = (Drawable) obj;
            }
            if (wrapped != null) {
                wrapped.mutate().setTint(accent);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstCpbStyle: applyTintToAnimatedRotateLayer error: " + t);
        }
    }

    /**
     * Per-preset inner/outer tint map — matches OC's real DstCpbStyle.initResources() exactly
     * (tintOuter/tintInner per case). Previously this was a blanket "tint everything except
     * CPB1 outer" rule, which incorrectly tinted CPB2/CPB3/CPB5's inner layer — OC leaves
     * those transparent/untouched.
     *   CPB1 Aurora:      outer NOT tinted (white stars), inner tinted (accent crescent)
     *   CPB2 Arrow:       outer tinted,                    inner NOT tinted (transparent)
     *   CPB3 Radioactive: outer tinted,                    inner NOT tinted (transparent)
     *   CPB4 Sprite:      outer tinted,                    inner tinted
     *   CPB5 Stars:       outer tinted,                    inner NOT tinted (transparent)
     */
    private static boolean shouldTintLayer(int n, String type) {
        boolean outer = "outer".equals(type);
        switch (n) {
            case 1: return !outer;       // Aurora: only inner tinted
            case 4: return true;         // Sprite: both tinted
            default: return outer;       // Arrow/Radioactive/Stars: only outer tinted
        }
    }

    private static int presetIndex(String preset) {
        switch (preset) {
            case "DSTCPB1": return 1;
            case "DSTCPB2": return 2;
            case "DSTCPB3": return 3;
            case "DSTCPB4": return 4;
            case "DSTCPB5": return 5;
            default:         return 0;
        }
    }

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
