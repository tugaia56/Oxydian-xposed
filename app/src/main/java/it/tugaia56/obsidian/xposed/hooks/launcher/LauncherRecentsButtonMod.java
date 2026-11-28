package it.tugaia56.obsidian.xposed.hooks.launcher;

import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewTreeObserver;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Pulsante "Chiudi" delle recenti: colore (accento o a scelta) e stile (pieno, oppure scuro con bordo). Il colore si legge
 * ogni volta che il pulsante viene disegnato, cosi' segue l'accento da solo (prima era un overlay con il colore scritto
 * dentro, da rifare a ogni cambio e spesso rimasto col vecchio).
 */
public class LauncherRecentsButtonMod extends XposedMods {

    private static final String KEY = "LAUNCHER_RECENTS_BTN_COLOR";
    private static final String KEY_STYLE = "LAUNCHER_RECENTS_BTN_STYLE";   // 0 pieno, 1 scuro con bordo
    private static final String CLASS = "com.android.launcher.views.PressFeedbackButton";

    private static final String WATCHED = "oxy_recents_watched";
    private static final String SIGNATURE = "oxy_recents_signature";
    private static final String APPLIED = "oxy_recents_applied";
    private static final String ORIGINAL = "oxy_recents_original";

    /**
     * Il pulsante di serie disegna sopra a tutto (sfondo, testo, bordo) un velo bianco al 30% (drawPressedColor, disegnato sempre, non solo al tocco), che schiariva i colori scelti.
     * Sta nel suo gestore del tocco: qui si tengono i gestori dei pulsanti ricolorati, per spegnerlo solo su quelli.
     */
    private static final java.util.Set<Object> sNoHighlight =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final String HANDLER_CLASS = "com.android.launcher.togglebar.animation.PressFeedbackHandler";

    private static void setHighlight(View button, boolean on) {
        try {
            Object handler = XposedHelpers.getObjectField(button, "mPressFeedbackHandler");
            if (on) sNoHighlight.remove(handler);
            else sNoHighlight.add(handler);
            button.invalidate();
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherRecentsButtonMod riflesso: " + t);
        }
    }

    public LauncherRecentsButtonMod(Context context) { super(context); }

    @Override public void updatePrefs(String... Key) {}

    @Override public boolean listensTo(String packageName) { return it.tugaia56.obsidian.utils.Constants.LAUNCHER.equals(packageName); }

    private static int accent(Context ctx) {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) return 0xFF000000 | Xprefs.getInt("DST_ACCENT1", 0xFF908DFF);
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = String.valueOf(XposedHelpers.callStaticMethod(sp, "get", "persist.oxytheme.accent", "")).trim();
            if (v.length() >= 6) return 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (Throwable ignored) {}
        Resources r = ctx.getResources();
        int id = r.getIdentifier("accent_material_dark", "color", "android");
        return id != 0 ? r.getColor(id, null) : 0xFF908DFF;
    }

    private static void style(View button) {
        if (Xprefs == null) return;
        Object saved = XposedHelpers.getAdditionalInstanceField(button, ORIGINAL);
        if (!Xprefs.getBoolean(KEY + "_on", false)) {
            if (saved instanceof Drawable && XposedHelpers.getAdditionalInstanceField(button, APPLIED) != null) {
                button.setBackground((Drawable) saved);
                setHighlight(button, true);
                XposedHelpers.removeAdditionalInstanceField(button, APPLIED);
                XposedHelpers.removeAdditionalInstanceField(button, SIGNATURE);
            }
            return;
        }
        Context ctx = button.getContext();
        int color = Xprefs.getBoolean(KEY + "_use_accent", false) ? accent(ctx) : 0xFF000000 | Xprefs.getInt(KEY, 0xFF6200EE);
        int mode = Xprefs.getInt(KEY_STYLE, 0);
        // sfondo scuro = background_dark (nel Theme e' il colore di sfondo scelto)
        int darkId = ctx.getResources().getIdentifier("background_dark", "color", "android");
        int dark = darkId != 0 ? 0xFF000000 | ctx.getResources().getColor(darkId, null) : 0xFF1B2029;
        String signature = color + ":" + mode + ":" + dark;
        Object applied = XposedHelpers.getAdditionalInstanceField(button, APPLIED);
        if (applied != null && applied == button.getBackground()
                && signature.equals(XposedHelpers.getAdditionalInstanceField(button, SIGNATURE))) return;

        if (saved == null && button.getBackground() != null && button.getBackground() != applied) {
            XposedHelpers.setAdditionalInstanceField(button, ORIGINAL, button.getBackground());
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10000f);   // a pillola, come il pulsante di serie
        if (mode == 1) {
            bg.setColor(dark);
            bg.setStroke(Math.max(1, Math.round(1.5f * ctx.getResources().getDisplayMetrics().density)), color);
        } else {
            bg.setColor(color);
        }
        button.setForceDarkAllowed(false);   // il "tema scuro forzato" di OnePlus schiariva i colori scelti
        button.setBackgroundTintList(null);
        button.setBackground(bg);
        setHighlight(button, false);
        XposedHelpers.setAdditionalInstanceField(button, APPLIED, bg);
        XposedHelpers.setAdditionalInstanceField(button, SIGNATURE, signature);
    }

    /** Il launcher puo' rimettere il suo sfondo: si ricontrolla prima di disegnare (non costa quasi nulla se e' gia' a posto). */
    private static void watch(View button) {
        if (XposedHelpers.getAdditionalInstanceField(button, WATCHED) != null) return;
        XposedHelpers.setAdditionalInstanceField(button, WATCHED, Boolean.TRUE);
        button.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!button.isAttachedToWindow()) {
                    ViewTreeObserver o = button.getViewTreeObserver();
                    if (o.isAlive()) o.removeOnPreDrawListener(this);
                    XposedHelpers.removeAdditionalInstanceField(button, WATCHED);
                    return true;
                }
                try { style(button); } catch (Throwable ignored) {}
                return true;
            }
        });
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        try {
            XposedHelpers.findAndHookMethod(HANDLER_CLASS, lpparam.classLoader, "drawPressedColor", android.graphics.Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (sNoHighlight.contains(param.thisObject)) param.setResult(null);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LauncherRecentsButtonMod: drawPressedColor non agganciato: " + t);
        }
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    View v = (View) param.thisObject;
                    if (!CLASS.equals(v.getClass().getName()) || v.getId() == View.NO_ID
                            || !"btn_clear".equals(v.getResources().getResourceEntryName(v.getId()))) return;
                    style(v);
                    watch(v);
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] LauncherRecentsButtonMod: " + t);
                }
            }
        });
    }
}
