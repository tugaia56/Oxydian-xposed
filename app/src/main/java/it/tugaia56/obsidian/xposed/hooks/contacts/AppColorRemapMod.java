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
 *  - Meteo (com.google.android.apps.weather): il pulsante di ricerca della localita' (lilla #dbcafb) diventa accento e la lente
 *    (#4d4169) diventa bianca.
 */
public class AppColorRemapMod extends XposedMods {

    private static final int ACC = 0;   // segnaposto: accento
    private static final Map<String, int[][]> MAP = new HashMap<>();
    static {
        MAP.put("com.google.android.apps.weather", new int[][]{{0xFFDBCAFB, ACC}, {0xFF4D4169, 0xFFFFFFFF}});
    }

    public static boolean handles(String pkg) { return MAP.containsKey(pkg); }

    private volatile int mAccent = 0xFFE05D00;
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
        XposedBridge.log("[ Obsidian ] AppColorRemapMod: rules for " + lpparam.packageName);
    }
}
