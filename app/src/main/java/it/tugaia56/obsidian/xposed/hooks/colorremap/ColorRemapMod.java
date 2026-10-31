package it.tugaia56.obsidian.xposed.hooks.colorremap;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;
import it.tugaia56.obsidian.xposed.utils.AccentUtil;

/**
 * App con i colori scritti nel codice (Compose, React Native, tavolozze proprie) che un overlay del Theme non raggiunge:
 * i colori fissi vengono sostituiti nel momento in cui l'app li passa alle view (Paint, testo, sfondi, filtri, liste di
 * colori, risorse). Ogni app ha una tabella "colore originale (rgb) -> ruolo": ACCENT = accento del Theme, PAGE = sfondo
 * (background_dark), TILE = tessere e barre (button_material_dark), CARD = card (holo_light_primary_dark), PILL = pillola
 * della barra di navigazione (system_secondary_container_dark). La trasparenza originale resta. Solo con il tema scuro.
 * Serve la spunta sull'app nell'ambito di Oxydian in LSPosed. Prima di aggiungere un'app controllare che non abbia PairIP.
 */
public class ColorRemapMod extends XposedMods {

    private static final int ACCENT = 0, PAGE = 1, TILE = 2, CARD = 3, PILL = 4;

    private static final Map<String, Map<Integer, Integer>> RULES = new HashMap<>();

    static {
        // Box (com.box.android)
        Map<Integer, Integer> box = new HashMap<>();
        for (int c : new int[]{0x0061D5, 0x7FB0EA, 0x3381DD, 0x0065E3, 0x2972FF, 0x76ACFF, 0x3271EA, 0x4E8FF8, 0x2486FC}) box.put(c, ACCENT);
        box.put(0x222222, PAGE);
        box.put(0x212121, PAGE);
        box.put(0x383838, TILE);
        box.put(0x314358, PILL);
        RULES.put("com.box.android", box);
    }

    public static boolean handles(String pkg) {
        return RULES.containsKey(pkg);
    }

    private static volatile boolean sReady;
    private static final int[] sColors = new int[5];
    /** load() legge colori con getColor, che e' agganciato: senza guardia si richiamerebbe all'infinito. */
    private static final ThreadLocal<Boolean> sBusy = new ThreadLocal<>();

    private Map<Integer, Integer> mRules;
    private static int resColor(Resources res, String name) {
        int id = Resources.getSystem().getIdentifier(name, "color", "android");
        return id == 0 ? -1 : (res.getColor(id, null) & 0xFFFFFF);
    }

    private static void load() {
        if (sReady || sBusy.get() != null) return;
        sBusy.set(Boolean.TRUE);
        try {
            Application app = AndroidAppHelper.currentApplication();
            if (app == null) return;
            Resources res = app.getResources();
            sColors[ACCENT] = AccentUtil.systemAccent() & 0xFFFFFF;
            sColors[PAGE] = resColor(res, "background_dark");
            sColors[TILE] = resColor(res, "button_material_dark");
            sColors[CARD] = resColor(res, "holo_light_primary_dark");
            sColors[PILL] = resColor(res, "system_secondary_container_dark");
            sReady = true;
        } catch (Throwable ignored) {
        } finally {
            sBusy.remove();
        }
    }

    private static boolean night() {
        return (Resources.getSystem().getConfiguration().uiMode & 0x30) == 0x20;
    }

    private int map(int c) {
        Integer role = mRules.get(c & 0xFFFFFF);
        if (role == null) return c;
        if (!sReady) load();
        if (!sReady || sColors[role] < 0) return c;
        return (c & 0xFF000000) | sColors[role];
    }

    private final class First extends XC_MethodHook {
        private final String mName;
        private boolean mSeen;

        First(String name) { mName = name; }

        @Override protected void beforeHookedMethod(MethodHookParam p) {
            try {
                if (sBusy.get() != null || !night()) return;
                int c = (Integer) p.args[0], n = map(c);
                if (n != c) {
                    p.args[0] = n;
                    if (!mSeen) {
                        mSeen = true;
                        XposedBridge.log("[ Obsidian ] ColorRemapMod: " + mName + " " + Integer.toHexString(c) + " -> " + Integer.toHexString(n));
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private final class ResultColor extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            try {
                if (sBusy.get() != null || !night()) return;
                int c = (Integer) p.getResult(), n = map(c);
                if (n != c) p.setResult(n);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        mRules = RULES.get(lp.packageName);
        if (mRules == null) return;
        int ok = 0;
        try { XposedHelpers.findAndHookMethod(Paint.class, "setColor", int.class, new First("Paint.setColor")); ok++; } catch (Throwable t) { log("Paint.setColor", t); }
        try { XposedHelpers.findAndHookMethod(TextView.class, "setTextColor", int.class, new First("TextView.setTextColor")); ok++; } catch (Throwable t) { log("TextView.setTextColor", t); }
        try { XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, new First("View.setBackgroundColor")); ok++; } catch (Throwable t) { log("View.setBackgroundColor", t); }
        try { XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", int.class, new First("ColorDrawable.setColor")); ok++; } catch (Throwable t) { log("ColorDrawable.setColor", t); }
        try { XposedHelpers.findAndHookConstructor(ColorDrawable.class, int.class, new First("ColorDrawable()")); ok++; } catch (Throwable t) { log("ColorDrawable()", t); }
        try { XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", int.class, new First("GradientDrawable.setColor")); ok++; } catch (Throwable t) { log("GradientDrawable.setColor", t); }
        try { XposedHelpers.findAndHookMethod(Canvas.class, "drawColor", int.class, new First("Canvas.drawColor")); ok++; } catch (Throwable t) { log("Canvas.drawColor", t); }
        try { XposedHelpers.findAndHookConstructor(PorterDuffColorFilter.class, int.class, PorterDuff.Mode.class, new First("PorterDuffColorFilter()")); ok++; } catch (Throwable t) { log("PorterDuffColorFilter()", t); }
        try {
            XposedHelpers.findAndHookConstructor(android.graphics.BlendModeColorFilter.class, int.class, android.graphics.BlendMode.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (sBusy.get() != null || !night()) return;
                        int c = (Integer) p.args[0];
                        // icona attiva della barra di navigazione: bianca (sulla pillola con l'accento)
                        if ((c & 0xFFFFFF) == 0x7FB0EA && mRules.containsKey(0x7FB0EA)) p.args[0] = (c & 0xFF000000) | 0xFFFFFF;
                        else {
                            int n = map(c);
                            if (n != c) p.args[0] = n;
                        }
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("BlendModeColorFilter()", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setColorFilter", int.class, PorterDuff.Mode.class, new First("Drawable.setColorFilter")); ok++; } catch (Throwable t) { log("Drawable.setColorFilter", t); }
        try { XposedHelpers.findAndHookMethod(Drawable.class, "setTint", int.class, new First("Drawable.setTint")); ok++; } catch (Throwable t) { log("Drawable.setTint", t); }
        try { XposedHelpers.findAndHookMethod(android.widget.ImageView.class, "setColorFilter", int.class, new First("ImageView.setColorFilter")); ok++; } catch (Throwable t) { log("ImageView.setColorFilter", t); }
        try { XposedHelpers.findAndHookConstructor(ForegroundColorSpan.class, int.class, new First("ForegroundColorSpan()")); ok++; } catch (Throwable t) { log("ForegroundColorSpan()", t); }
        try { XposedHelpers.findAndHookMethod(ColorStateList.class, "valueOf", int.class, new First("ColorStateList.valueOf")); ok++; } catch (Throwable t) { log("ColorStateList.valueOf", t); }
        try {
            XposedHelpers.findAndHookConstructor(ColorStateList.class, int[][].class, int[].class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        int[] a = (int[]) p.args[1];
                        if (a == null || sBusy.get() != null || !night()) return;
                        int[] b = null;
                        for (int i = 0; i < a.length; i++) {
                            int n = map(a[i]);
                            if (n != a[i]) {
                                if (b == null) b = a.clone();
                                b[i] = n;
                            }
                        }
                        if (b != null) p.args[1] = b;
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("ColorStateList()", t); }
        try {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor", long.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (sBusy.get() != null || !night()) return;
                        int c = Color.toArgb((Long) p.args[0]), n = map(c);
                        if (n != c) p.args[0] = Color.pack(n);
                    } catch (Throwable ignored) {}
                }
            });
            ok++;
        } catch (Throwable t) { log("Paint.setColor(long)", t); }
        // indicatore attivo ("pillola") della barra di navigazione Material: colore dell'accento
        for (String cn : new String[]{"com.google.android.material.navigation.NavigationBarView",
                "com.google.android.material.navigation.NavigationBarMenuView"}) {
            try {
                Class<?> nc = XposedHelpers.findClassIfExists(cn, lp.classLoader);
                if (nc == null) continue;
                XposedHelpers.findAndHookMethod(nc, "setItemActiveIndicatorColor", ColorStateList.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (p.args[0] == null || sBusy.get() != null || !night()) return;
                            if (!sReady) load();
                            if (sReady) p.args[0] = ColorStateList.valueOf(0xFF000000 | sColors[ACCENT]);
                        } catch (Throwable ignored) {}
                    }
                });
                ok++;
            } catch (Throwable t) { log(cn + ".setItemActiveIndicatorColor", t); }
        }
        ResultColor after = new ResultColor();
        try { XposedHelpers.findAndHookMethod(Resources.class, "getColor", int.class, Resources.Theme.class, after); ok++; } catch (Throwable t) { log("Resources.getColor", t); }
        try { XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", int.class, int.class, after); ok++; } catch (Throwable t) { log("TypedArray.getColor", t); }
        XposedBridge.log("[ Obsidian ] ColorRemapMod: " + ok + " colour hooks installed in " + lp.packageName);
    }

    private static void log(String what, Throwable t) {
        XposedBridge.log("[ Obsidian ] ColorRemapMod: hook " + what + " failed: " + t);
    }
}
