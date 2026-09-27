package it.tugaia56.obsidian.xposed.hooks.systemui;

import android.content.res.XResources;
import android.graphics.drawable.Drawable;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

import it.tugaia56.obsidian.xposed.ResourceManager;

/**
 * Xposed hook: replaces SystemUI mobile signal icon drawables with one of
 * Obsidian's DST signal icon presets (42 presets, mirroring OC SGIC1-SGIC42).
 *
 * Targets: com.android.systemui
 *   — stat_signal_signal_lte_single_0..4  (no signal → full)
 *   — stat_signal_noservice_lte
 *
 * Uses the same ResourceManager.modRes-at-call-time pattern as DstCpbStyle / DstWifiIconStyle.
 */
public class DstSignalIconStyle {

    private static final String PKG_SYSTEMUI = "com.android.systemui";
    private static final String PKG_OBS      = "it.tugaia56.oxydian";
    private static final String PREF_PRESET  = "DST_PRESET_SIGNAL_ICON";
    private static final String PREFS_FILE   =
        "/data/user_de/0/it.tugaia56.oxydian/shared_prefs/it.tugaia56.oxydian_preferences.xml";

    /** Continuous icon scale, 1.0–1.5 (100%–150%). Below 1.0 is stock/original size. */
    public static final String PREF_ICON_SCALE = "signal_icon_scale";

    // Order mirrors OC's SGIC1-SGIC42 overlay folders.
    public static final String[] PRESET_KEYS = {
        "DSTSIG_AQUARIUM",     "DSTSIG_AURORA",       "DSTSIG_BARS",
        "DSTSIG_BUTTERFLY",    "DSTSIG_CIRCLE",       "DSTSIG_DAUN",
        "DSTSIG_DEC",          "DSTSIG_DEEP",         "DSTSIG_DORA",
        "DSTSIG_EQUAL",        "DSTSIG_FAINT_UI",     "DSTSIG_FAN",
        "DSTSIG_FORLORN",      "DSTSIG_GRADICON",     "DSTSIG_HUAWEI",
        "DSTSIG_INSIDE",       "DSTSIG_IOS",          "DSTSIG_MINI",
        "DSTSIG_NOTHING_DOT",  "DSTSIG_ODIN",         "DSTSIG_PILLS",
        "DSTSIG_PLUMPY",       "DSTSIG_PUI",          "DSTSIG_REL",
        "DSTSIG_ROMAN",        "DSTSIG_ROUND",        "DSTSIG_SCROLL",
        "DSTSIG_SEA",          "DSTSIG_SNEAKY",       "DSTSIG_STACK",
        "DSTSIG_STROKE",       "DSTSIG_WANNUI",       "DSTSIG_WAVY",
        "DSTSIG_WINDOWS",      "DSTSIG_WING",         "DSTSIG_XPERIA",
        "DSTSIG_ZIGZAG",       "DSTSIG_HOS",          "DSTSIG_NOTHING_DOT_V2",
        "DSTSIG_IOS_10",       "DSTSIG_IOS_BY",       "DSTSIG_IOS_DOUBLE_BY"
    };
    public static final String[] PRESET_NAMES = {
        "aquarium",    "aurora",      "bars",
        "butterfly",   "circle",      "daun",
        "dec",         "deep",        "dora",
        "equal",       "faint_ui",    "fan",
        "forlorn",     "gradicon",    "huawei",
        "inside",      "ios",         "mini",
        "nothing_dot", "odin",        "pills",
        "plumpy",      "pui",         "rel",
        "roman",       "round",       "scroll",
        "sea",         "sneaky",      "stack",
        "stroke",      "wannui",      "wavy",
        "windows",     "wing",        "xperia",
        "zigzag",      "hos",         "nothing_dot_v2",
        "ios_10",      "ios_by",      "ios_double_by"
    };

    // Custom color for signal icons — shared with DstWifiIconStyle (same pref keys), set from
    // SignalStyleFragment "Colore Icone Segnale" so Wi-Fi + Mobile get the same custom color.
    public static final String PREF_COLOR_ON = "MOBILE_ICON_CUSTOM_COLOR_ON";
    public static final String PREF_COLOR    = "MOBILE_ICON_CUSTOM_COLOR";

    private static volatile boolean sPreloaded    = false;
    private static volatile String  sSignalPreset = null;
    static volatile float           sIconScale    = 1.0f;
    static volatile boolean         sColorOn      = false;
    static volatile int             sColor        = 0xFFFFFFFF;

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
            sSignalPreset = parseStringContent(sb.toString(), PREF_PRESET);
            sIconScale    = parseFloatAttr(sb.toString(), PREF_ICON_SCALE, 1.0f);
            sColorOn      = parseBoolAttr(sb.toString(), PREF_COLOR_ON, false);
            sColor        = parseIntAttr(sb.toString(), PREF_COLOR, 0xFFFFFFFF);
            sPreloaded    = true;
            XposedBridge.log("[ Obsidian ] DstSignalIconStyle.preload(file): preset=" + sSignalPreset + " scale=" + sIconScale
                    + " colorOn=" + sColorOn + " color=" + Integer.toHexString(sColor));
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstSignalIconStyle.preload(file) ERROR: " + t);
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            String preset = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.signal_icon_preset", "");
            if (!preset.isEmpty()) {
                sSignalPreset = preset;
                sPreloaded    = true;
            }
            String colorOn = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.mobile_icon_color_on", "");
            String colorHex = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.mobile_icon_color", "");
            if (!colorOn.isEmpty())  sColorOn = "true".equals(colorOn);
            if (!colorHex.isEmpty()) {
                try { sColor = (int) Long.parseLong(colorHex, 16); } catch (NumberFormatException ignored) {}
            }
            String scaleStr = (String) XposedHelpers.callStaticMethod(sp, "get",
                    "persist.obsidian.dst.signal_icon_scale", "");
            if (!scaleStr.isEmpty()) {
                try { sIconScale = Float.parseFloat(scaleStr); } catch (NumberFormatException ignored) {}
            }
            XposedBridge.log("[ Obsidian ] DstSignalIconStyle.preload(props): preset='" + preset
                    + "' colorOn=" + sColorOn + " color=" + Integer.toHexString(sColor));
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstSignalIconStyle.preload(props) ERROR: " + t);
        }
    }

    // ── Early registration ────────────────────────────────────────────────────

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        preloadFromFile();
        if (!sPreloaded || sSignalPreset == null) return;

        String presetName = presetDrawableName(sSignalPreset);
        if (presetName == null) return;

        java.util.List<XC_InitPackageResources.InitPackageResourcesParam> allRp =
                new java.util.ArrayList<>(ResourceManager.resparams.values());
        if (!allRp.contains(rp)) allRp.add(rp);

        for (XC_InitPackageResources.InitPackageResourcesParam target : allRp) {
            if (!PKG_SYSTEMUI.equals(target.packageName)) continue;
            try {
                applyAll(target, presetName);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstSignalIconStyle: error on " + target.packageName + ": " + t);
            }
        }
    }

    /** Late re-registration — called from XPLauncher.initContext() after modRes is set. */
    public static void initResourcesLate() {
        if (!sPreloaded) preloadFromFile();
        if (!sPreloaded || sSignalPreset == null) return;

        String presetName = presetDrawableName(sSignalPreset);
        if (presetName == null) return;

        java.util.List<XC_InitPackageResources.InitPackageResourcesParam> allRp =
                new java.util.ArrayList<>(ResourceManager.resparams.values());

        XposedBridge.log("[ Obsidian ] DstSignalIconStyle.initResourcesLate: preset=" + sSignalPreset
                + " resparams=" + allRp.size()
                + " modRes=" + (ResourceManager.modRes != null ? "OK" : "NULL"));

        for (XC_InitPackageResources.InitPackageResourcesParam target : allRp) {
            if (!PKG_SYSTEMUI.equals(target.packageName)) continue;
            try {
                applyAll(target, presetName);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstSignalIconStyle.initResourcesLate error: " + t);
            }
        }
    }

    private static void applyAll(XC_InitPackageResources.InitPackageResourcesParam rp, String presetName) {
        for (int level = 0; level <= 4; level++) {
            replaceOne(rp, "stat_signal_signal_lte_single_" + level,
                       "obs_signal_" + presetName + "_" + level);
        }
        replaceOne(rp, "stat_signal_noservice_lte",
                   "obs_signal_" + presetName + "_noservice");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void replaceOne(
            XC_InitPackageResources.InitPackageResourcesParam rp,
            String target, String modName) {
        try {
            rp.res.setReplacement(PKG_SYSTEMUI, "drawable", target,
                new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources res, int id) {
                        android.content.res.Resources mr = ResourceManager.modRes;
                        if (mr == null) return null;
                        try {
                            int resId = mr.getIdentifier(modName, "drawable", PKG_OBS);
                            if (resId == 0) return null;
                            Drawable d = mr.getDrawable(resId, null);
                            XposedBridge.log("[ Obsidian ] DstSignalIconStyle: newDrawable " + modName
                                    + " class=" + (d == null ? "null" : d.getClass().getName())
                                    + " colorOn=" + sColorOn + " color=" + Integer.toHexString(sColor));
                            if (sColorOn && d != null) {
                                d = d.mutate();
                                d.setTint(sColor);
                                XposedBridge.log("[ Obsidian ] DstSignalIconStyle: tint applied to " + modName
                                        + ", mutated class=" + d.getClass().getName());
                            }
                            return makeScaled(d, sIconScale);
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] DstSignalIconStyle: newDrawable error "
                                    + modName + ": " + t);
                            return null;
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstSignalIconStyle: setReplacement error "
                    + target + " → " + modName + ": " + t);
        }
    }

    /**
     * Wraps {@code base} in a DrawableWrapper that reports scaled intrinsic dimensions.
     * When the status bar ImageView uses wrap_content, it will size itself to those
     * intrinsic dimensions, making the icon visually larger or smaller.
     */
    private static Drawable makeScaled(Drawable base, float scale) {
        if (base == null || Math.abs(scale - 1f) < 0.01f) return base;
        final int w = Math.max(1, Math.round(base.getIntrinsicWidth()  * scale));
        final int h = Math.max(1, Math.round(base.getIntrinsicHeight() * scale));
        return new android.graphics.drawable.DrawableWrapper(base) {
            @Override public int getIntrinsicWidth()  { return w; }
            @Override public int getIntrinsicHeight() { return h; }
        };
    }

    private static String presetDrawableName(String presetKey) {
        for (int i = 0; i < PRESET_KEYS.length; i++) {
            if (PRESET_KEYS[i].equals(presetKey)) return PRESET_NAMES[i];
        }
        return null;
    }

    private static boolean parseBoolAttr(String xml, String name, boolean def) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return def;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return def;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        return ve > vs ? "true".equals(xml.substring(vs, ve).trim()) : def;
    }

    private static int parseIntAttr(String xml, String name, int def) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return def;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return def;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        if (ve <= vs) return def;
        try { return Integer.parseInt(xml.substring(vs, ve).trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static String parseStringContent(String xml, String name) {
        String needle = "name=\"" + name + "\">";
        int idx = xml.indexOf(needle);
        if (idx < 0) return null;
        int s = idx + needle.length();
        int e = xml.indexOf("<", s);
        return e <= s ? null : xml.substring(s, e).trim();
    }

    private static float parseFloatAttr(String xml, String name, float def) {
        String needle = "name=\"" + name + "\"";
        int idx = xml.indexOf(needle);
        if (idx < 0) return def;
        int vi = xml.indexOf("value=\"", idx);
        if (vi < 0) return def;
        int vs = vi + 7;
        int ve = xml.indexOf("\"", vs);
        if (ve <= vs) return def;
        try { return Float.parseFloat(xml.substring(vs, ve).trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
