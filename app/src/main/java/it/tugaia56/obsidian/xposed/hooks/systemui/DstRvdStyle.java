package it.tugaia56.obsidian.xposed.hooks.systemui;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

import android.content.res.XModuleResources;
import android.content.res.XResources;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;

import it.tugaia56.obsidian.utils.ColorUtils;
import it.tugaia56.obsidian.xposed.ResourceManager;

/**
 * Xposed hook: replaces SystemUI volume panel drawables with one of 8 DST RVD presets.
 *
 * Targets: com.android.systemui
 * Resources replaced:
 *   - more_row_stream_app / more_row_stream_system  (3-dots icon, shared by RVD+SVD)
 *   - systemui_icon_volume_double_ear_light          (ear icon, shared)
 *   - systemui_icon_volume_odi_captions / _disabled  (captions icons, shared)
 *   - systemui_icon_volume_app_adjust_bg             (bg shape, per-preset, programmatic)
 *   - systemui_icon_volume_single_app_adjust_bg      (bg shape, same color, programmatic)
 *   - volume_vertical_row_radius dimen               (12dp RVD / 8dp SVD)
 *
 * RVD preset ovals (DSTRVD*):
 *   DSTRVDAccent        → accent color solid
 *   DSTRVDAccentShade   → accent darkened by 20%
 *   DSTRVDDarkGray      → button_material_dark equivalent (bg + 30%)
 *   DSTRVDLightGray     → background_holo_dark equivalent (#FF1b1b1b)
 *   DSTRVDWhite         → white solid
 *   DSTRVDOutlined      → bg solid + accent stroke
 *   DSTRVDSemiTrasp     → #80000000 solid
 *   DSTRVDOutlinedTrasp → #80000000 + accent stroke
 *
 * SVD preset squares (DSTSVD*) — same 8 color variants, RECTANGLE + 8dp corners.
 */
public class DstRvdStyle {

    private static final String PKG_SYSTEMUI  = "com.android.systemui";
    private static final String PREF_RVD     = "DST_PRESET_RVD";
    private static final String PREF_SVD     = "DST_PRESET_SVD";
    private static final String PREF_ACCENT1 = "DST_ACCENT1";
    private static final String PREF_BG      = "DST_BACKGROUND";
    private static final String PREFS_FILE   =
        "/data/user_de/0/it.tugaia56.oxydian/shared_prefs/it.tugaia56.oxydian_preferences.xml";

    private static volatile String  sRvdPreset = null;
    private static volatile String  sSvdPreset = null;
    private static volatile int     sAccent    = 0xFF9C27B0;
    private static volatile int     sBg        = 0xFF1B2029;

    // ── Shared icon drawables (module res/ names → systemui target names) ────

    private static final String[][] SHARED_ICONS = {
        {"obs_rvd_more_row_stream_app",                  "more_row_stream_app"},
        {"obs_rvd_more_row_stream_system",               "more_row_stream_system"},
        {"obs_rvd_systemui_icon_volume_double_ear_light","systemui_icon_volume_double_ear_light"},
        {"obs_rvd_systemui_icon_volume_odi_captions",    "systemui_icon_volume_odi_captions"},
        {"obs_rvd_systemui_icon_volume_odi_captions_disabled",
                                                         "systemui_icon_volume_odi_captions_disabled"},
    };

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

            sRvdPreset = parseStringContent(xml, PREF_RVD);
            sSvdPreset = parseStringContent(xml, PREF_SVD);
            sAccent    = parseInt(parseAttr(xml, PREF_ACCENT1, "value"), 0xFF9C27B0);
            sBg        = parseInt(parseAttr(xml, PREF_BG,      "value"), 0xFF1B2029);
            XposedBridge.log("[ Obsidian ] DstRvdStyle.preload(file): rvd=" + sRvdPreset + " svd=" + sSvdPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstRvdStyle.preload(file) ERROR: " + t + " — trying props");
            preloadFromProps();
        }
    }

    /**
     * Fallback: reads persist.obsidian.dst.rvd/svd_preset via SystemProperties.
     * Called when the SharedPreferences XML is not accessible (SELinux blocks reads
     * in SystemUI after a UI restart where the Zygote fork inherited null statics).
     */
    private static void preloadFromProps() {
        try {
            Class<?> sp  = XposedHelpers.findClass("android.os.SystemProperties", null);
            String rvd   = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.rvd_preset", "");
            String svd   = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.svd_preset", "");
            String a1Str = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.a1",          "");
            String bgStr = (String) XposedHelpers.callStaticMethod(sp, "get", "persist.obsidian.dst.bg",           "");
            XposedBridge.log("[ Obsidian ] DstRvdStyle.preloadFromProps: rvd='" + rvd + "' svd='" + svd + "'");
            if (rvd.isEmpty() && svd.isEmpty()) return;
            if (!rvd.isEmpty()) sRvdPreset = rvd;
            if (!svd.isEmpty()) sSvdPreset = svd;
            if (!a1Str.isEmpty()) {
                try { sAccent = Integer.parseInt(a1Str); } catch (NumberFormatException ignored) {}
            }
            if (!bgStr.isEmpty()) {
                try { sBg = Integer.parseInt(bgStr); } catch (NumberFormatException ignored) {}
            }
            XposedBridge.log("[ Obsidian ] DstRvdStyle.preload(props): rvd=" + sRvdPreset + " svd=" + sSvdPreset);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] DstRvdStyle.preload(props) ERROR: " + t);
        }
    }

    // ── Called from ResourceManager.handleInitPackageResources ───────────────

    public static void applyPreloaded(XC_InitPackageResources.InitPackageResourcesParam rp) {
        if (!PKG_SYSTEMUI.equals(rp.packageName)) return;
        XposedBridge.log("[ Obsidian ] DstRvdStyle.applyPreloaded: CALLED for systemui");
        preloadFromFile();
        XposedBridge.log("[ Obsidian ] DstRvdStyle.applyPreloaded: after preload rvd=" + sRvdPreset + " svd=" + sSvdPreset);
        if (sRvdPreset == null && sSvdPreset == null) return;

        String modulePath = ResourceManager.modulePath;
        if (modulePath == null) return;

        final int accent = sAccent;
        final int bg     = sBg;

        // 1. Replace shared icon drawables (identical for RVD and SVD)
        XModuleResources modRes =
            XModuleResources.createInstance(modulePath, rp.res);

        for (String[] pair : SHARED_ICONS) {
            final String modName    = pair[0];
            final String targetName = pair[1];
            try {
                final int resId = modRes.getIdentifier(modName, "drawable", "it.tugaia56.oxydian");
                if (resId == 0) continue;
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable", targetName,
                    new XResources.DrawableLoader() {
                        @Override public Drawable newDrawable(XResources res, int id) {
                            try { return modRes.getDrawable(resId, null); }
                            catch (Throwable t) { return null; }
                        }
                    });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstRvdStyle: error replacing " + targetName + ": " + t);
            }
        }

        // 2a. RVD: oval bg + 12dp row radius
        if (sRvdPreset != null) {
            final String preset = sRvdPreset;
            XResources.DrawableLoader ovalLoader = new XResources.DrawableLoader() {
                @Override public Drawable newDrawable(XResources res, int id) {
                    float density = res.getDisplayMetrics().density;
                    if (density <= 0f) density = 3.0f;  // safe default
                    return buildOval(preset, accent, bg, density);
                }
            };
            try {
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable",
                        "systemui_icon_volume_app_adjust_bg",         ovalLoader);
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable",
                        "systemui_icon_volume_single_app_adjust_bg",  ovalLoader);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstRvdStyle: RVD drawable error: " + t);
            }
            try {
                rp.res.setReplacement(PKG_SYSTEMUI, "dimen", "volume_vertical_row_radius",
                    new XResources.DimensionReplacement(12f, TypedValue.COMPLEX_UNIT_DIP));
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstRvdStyle: RVD dimen error: " + t);
            }
        }

        // 2b. SVD: square bg + 8dp row radius (overwrites RVD if both set)
        if (sSvdPreset != null) {
            final String preset = sSvdPreset;
            XResources.DrawableLoader squareLoader = new XResources.DrawableLoader() {
                @Override public Drawable newDrawable(XResources res, int id) {
                    XposedBridge.log("[ Obsidian ] DstRvdStyle: SVD newDrawable called preset=" + preset);
                    float density = res.getDisplayMetrics().density;
                    if (density <= 0f) density = 3.0f;
                    return buildSquare(preset, accent, bg, density);
                }
            };
            try {
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable",
                        "systemui_icon_volume_app_adjust_bg",         squareLoader);
                rp.res.setReplacement(PKG_SYSTEMUI, "drawable",
                        "systemui_icon_volume_single_app_adjust_bg",  squareLoader);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstRvdStyle: SVD drawable error: " + t);
            }
            try {
                rp.res.setReplacement(PKG_SYSTEMUI, "dimen", "volume_vertical_row_radius",
                    new XResources.DimensionReplacement(8f, TypedValue.COMPLEX_UNIT_DIP));
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] DstRvdStyle: SVD dimen error: " + t);
            }
        }
    }

    // ── Oval factory (GradientDrawable) ──────────────────────────────────────

    private static Drawable buildOval(String preset, int accent, int bg, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        // OVAL ignores cornerRadius; the 11dp value is here for reference only

        switch (preset) {
            case "DSTRVDAccent":
                d.setColor(accent | 0xFF000000);
                break;
            case "DSTRVDAccentShade":
                d.setColor(ColorUtils.adjustColor(accent | 0xFF000000, -20));
                break;
            case "DSTRVDDarkGray":
                d.setColor(ColorUtils.adjustColor(bg | 0xFF000000, 30));
                break;
            case "DSTRVDLightGray":
                d.setColor(0xFF1b1b1b);
                break;
            case "DSTRVDWhite":
                d.setColor(0xFFFFFFFF);
                break;
            case "DSTRVDOutlined":
                d.setColor(bg | 0xFF000000);
                d.setStroke(Math.round(density), accent | 0xFF000000);
                break;
            case "DSTRVDSemiTrasp":
                d.setColor(0x80000000);
                break;
            case "DSTRVDOutlinedTrasp":
                d.setColor(0x80000000);
                d.setStroke(Math.round(density), accent | 0xFF000000);
                break;
            default:
                return null;
        }
        return d;
    }

    // ── Square factory (GradientDrawable) ────────────────────────────────────

    private static Drawable buildSquare(String preset, int accent, int bg, float density) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(8f * density);

        switch (preset) {
            case "DSTSVDAccent":
                d.setColor(accent | 0xFF000000);
                break;
            case "DSTSVDAccentShade":
                d.setColor(ColorUtils.adjustColor(accent | 0xFF000000, -20));
                break;
            case "DSTSVDDarkGray":
                d.setColor(ColorUtils.adjustColor(bg | 0xFF000000, 30));
                break;
            case "DSTSVDLightGray":
                d.setColor(0xFF1b1b1b);
                break;
            case "DSTSVDWhite":
                d.setColor(0xFFFFFFFF);
                break;
            case "DSTSVDOutlined":
                d.setColor(bg | 0xFF000000);
                d.setStroke(Math.round(density), accent | 0xFF000000);
                break;
            case "DSTSVDSemiTrasp":
                d.setColor(0x80000000);
                break;
            case "DSTSVDOutlinedTrasp":
                d.setColor(0x80000000);
                d.setStroke(Math.round(density), accent | 0xFF000000);
                break;
            default:
                return null;
        }
        return d;
    }

    // ── XML parse helpers ────────────────────────────────────────────────────

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
