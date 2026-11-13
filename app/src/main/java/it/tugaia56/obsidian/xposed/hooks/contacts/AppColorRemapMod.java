package it.tugaia56.obsidian.xposed.hooks.contacts;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * App di Google scritte in Compose: i colori non passano dalle risorse, quindi un overlay non li cambia. Qui si sostituiscono, solo in
 * queste app, alcuni colori esatti nel momento in cui l'app li assegna a un pennello (Paint.setColor) o a un filtro colore
 * (tinta delle icone). ACC = accento scelto nel Theme (persist.oxytheme.accent).
 *
 *  - Meteo (com.google.android.apps.weather): il pulsante di ricerca della localita' (lilla #dbcafb) diventa accento; la card rossa
 *    (filtro #871f21) prende lo sfondo del Theme con bordo accento e il suo testo (#ff9993) diventa bianco.
 *    La lente (#4d4169) non si riesce a raggiungere (non passa da nessuna chiamata Java di disegno): resta scura.
 */
public class AppColorRemapMod extends XposedMods {

    private static final int ACC = 0;   // segnaposto: accento
    private static final Map<String, int[][]> MAP = new HashMap<>();
    // card riempite con un filtro colore di questo colore: sfondo del Theme + bordo accento
    private static final Map<String, Integer> CARD = new HashMap<>();
    static {
        MAP.put("com.google.android.apps.weather", new int[][]{{0xFFDBCAFB, ACC}, {0xFF4D4169, 0xFFFFFFFF}, {0xFFFF9993, 0xFFFFFFFF}});
        CARD.put("com.google.android.apps.weather", 0xFF871F21);
    }

    public static boolean handles(String pkg) { return MAP.containsKey(pkg); }

    private volatile int mAccent = 0xFFE05D00;
    private volatile int mBg = 0xFF1B2029;
    private long mAt = 0;
    private int[][] mRules = new int[0][];

    public AppColorRemapMod(Context context) { super(context); }

    @Override public void updatePrefs(String... Key) {}

    @Override public boolean listensTo(String packageName) { return handles(packageName); }

    private void refreshAccent() {
        long now = SystemClock.elapsedRealtime();
        if (now - mAt < 2000) return;
        mAt = now;
        try {
            android.app.Application app0 = android.app.AndroidAppHelper.currentApplication();
            Resources r0 = app0 != null ? app0.getResources() : Resources.getSystem();
            int id0 = r0.getIdentifier("system_background_dark", "color", "android");
            if (id0 != 0) mBg = 0xFF000000 | (r0.getColor(id0, null) & 0xFFFFFF);
        } catch (Throwable ignored) {}
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = String.valueOf(XposedHelpers.callStaticMethod(sp, "get", "persist.oxytheme.accent", "")).trim();
            if (v.length() >= 6) { mAccent = 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16); return; }
        } catch (Throwable ignored) {}
        try {
            android.app.Application app = android.app.AndroidAppHelper.currentApplication();
            Resources r = app != null ? app.getResources() : Resources.getSystem();
            int id = r.getIdentifier("accent_material_dark", "color", "android");
            if (id != 0) mAccent = 0xFF000000 | (r.getColor(id, null) & 0xFFFFFF);
        } catch (Throwable ignored) {}
    }

    private int remap(int c) {
        for (int[] rule : mRules) {
            if ((c & 0xFFFFFF) == (rule[0] & 0xFFFFFF)) {
                refreshAccent();
                int to = rule[1] == ACC ? mAccent : rule[1];
                return (c & 0xFF000000) | (to & 0xFFFFFF);
            }
        }
        return c;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        mRules = MAP.get(lpparam.packageName);
        if (mRules == null) return;
        XC_MethodHook first = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length > 0 && param.args[0] instanceof Integer) param.args[0] = remap((Integer) param.args[0]);
                } catch (Throwable ignored) {}
            }
        };
        try { XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class, first); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] AppColorRemapMod setColor: " + t); }
        try { XposedBridge.hookAllConstructors(android.graphics.PorterDuffColorFilter.class, first); } catch (Throwable ignored) {}
        try { XposedBridge.hookAllConstructors(android.graphics.BlendModeColorFilter.class, first); } catch (Throwable ignored) {}
        // Paint.setColor(long): Compose assegna i colori come "color long" (API 26+); si converte in ARGB, si sostituisce e si riconverte
        try {
            XposedBridge.hookAllMethods(Paint.class, "setColor", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 1 || !(param.args[0] instanceof Long)) return;
                        long l = (Long) param.args[0];
                        int argb = android.graphics.Color.toArgb(l);
                        int mapped = remap(argb);
                        if (mapped != argb) param.args[0] = android.graphics.Color.pack(mapped);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) { XposedBridge.log("[ Obsidian ] AppColorRemapMod setColor(long): " + t); }
        // Paint.setColorFilter: la tinta delle icone puo' essere un filtro gia' creato; si legge il suo colore e, se serve, si sostituisce
        try {
            XposedBridge.hookAllMethods(Paint.class, "setColorFilter", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 1 || param.args[0] == null) return;
                        Object f = param.args[0];
                        if (f instanceof android.graphics.BlendModeColorFilter) {
                            android.graphics.BlendModeColorFilter bf = (android.graphics.BlendModeColorFilter) f;
                            int c = bf.getColor(), m = remap(c);
                            if (m != c) param.args[0] = new android.graphics.BlendModeColorFilter(m, bf.getMode());
                        } else if (f instanceof android.graphics.PorterDuffColorFilter) {
                            int c = (Integer) XposedHelpers.callMethod(f, "getColor");
                            int m = remap(c);
                            if (m != c) param.args[0] = new android.graphics.PorterDuffColorFilter(m, (android.graphics.PorterDuff.Mode) XposedHelpers.callMethod(f, "getMode"));
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) { XposedBridge.log("[ Obsidian ] AppColorRemapMod setColorFilter: " + t); }
        // Card che il Meteo riempie con un filtro colore (es. avviso rosso): sfondo del Theme + bordo accento
        final Integer cardFrom = CARD.get(lpparam.packageName);
        if (cardFrom != null) {
            try {
                XposedBridge.hookAllMethods(Class.forName("android.graphics.BaseRecordingCanvas"), "drawRoundRect", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int pi = param.args.length - 1;
                            if (!(param.args[pi] instanceof Paint)) return;
                            Paint P = (Paint) param.args[pi];
                            android.graphics.ColorFilter cf = P.getColorFilter();
                            if (cf == null) return;
                            int c; Object mode;
                            if (cf instanceof android.graphics.BlendModeColorFilter) { c = ((android.graphics.BlendModeColorFilter) cf).getColor(); mode = ((android.graphics.BlendModeColorFilter) cf).getMode(); }
                            else if (cf instanceof android.graphics.PorterDuffColorFilter) { c = (Integer) XposedHelpers.callMethod(cf, "getColor"); mode = XposedHelpers.callMethod(cf, "getMode"); }
                            else return;
                            if ((c & 0xFFFFFF) != (cardFrom & 0xFFFFFF)) return;
                            refreshAccent();
                            Paint np = new Paint(P);
                            np.setColorFilter(mode instanceof android.graphics.BlendMode
                                    ? new android.graphics.BlendModeColorFilter(mBg, (android.graphics.BlendMode) mode)
                                    : new android.graphics.PorterDuffColorFilter(mBg, (android.graphics.PorterDuff.Mode) mode));
                            param.args[pi] = np;
                            param.setObjectExtra("card", Boolean.TRUE);
                        } catch (Throwable ignored) {}
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (param.getObjectExtra("card") == null) return;
                            Object[] a = param.args;
                            float l, t, r, b, rx, ry;
                            if (a[0] instanceof android.graphics.RectF) { android.graphics.RectF q = (android.graphics.RectF) a[0]; l = q.left; t = q.top; r = q.right; b = q.bottom; rx = (Float) a[1]; ry = (Float) a[2]; }
                            else { l = (Float) a[0]; t = (Float) a[1]; r = (Float) a[2]; b = (Float) a[3]; rx = (Float) a[4]; ry = (Float) a[5]; }
                            float w = 1.5f * Resources.getSystem().getDisplayMetrics().density;
                            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
                            sp.setStyle(Paint.Style.STROKE);
                            sp.setStrokeWidth(w);
                            sp.setColor(mAccent);
                            ((android.graphics.Canvas) param.thisObject).drawRoundRect(l + w / 2, t + w / 2, r - w / 2, b - w / 2, Math.max(0, rx - w / 2), Math.max(0, ry - w / 2), sp);
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable t) { XposedBridge.log("[ Obsidian ] AppColorRemapMod card: " + t); }
        }
        XposedBridge.log("[ Obsidian ] AppColorRemapMod: rules for " + lpparam.packageName);
    }
}
