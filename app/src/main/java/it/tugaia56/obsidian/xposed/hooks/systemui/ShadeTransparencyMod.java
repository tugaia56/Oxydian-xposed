package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (crDroid, ecc.): lo sfondo del pannello delle notifiche e dei tasti rapidi e' un "velo" (ScrimView) quasi
 * nero e pieno. Qui il velo dietro il pannello e quello sotto le notifiche vengono resi piu' trasparenti, cosi' si vede
 * quello che sta dietro (sfondo del telefono). La percentuale (100 = di serie) la pubblica Oxydian Theme in
 * persist.oxytheme.shade_alpha. Su OxygenOS non viene caricata.
 */
public class ShadeTransparencyMod extends XposedMods {

    private static final String PROP = "persist.oxytheme.shade_alpha";
    private static final String PROP_TINT = "persist.oxytheme.shade_tint";   // "1": sfondo del pannello con il colore dell'accento
    private static final String PROP_ACCENT = "persist.oxytheme.accent";      // accento del Theme, esadecimale
    private static volatile int sTint = 0;                                    // ARGB opaco dell'accento, 0 = spento
    private static volatile float sFactor = 1f;
    private static volatile long sRead = 0;

    public ShadeTransparencyMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    /** Percentuale pubblicata dal Theme (letta al piu' ogni 2 secondi: setViewAlpha viene chiamato ad ogni fotogramma). */
    private static float factor() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sRead < 2000) return sFactor;
        sRead = now;
        float f = 1f;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) XposedHelpers.callStaticMethod(sp, "get", PROP, "100");
            int p = Integer.parseInt(v.trim());
            if (p >= 5 && p < 100) f = p / 100f;
        } catch (Throwable ignored) {}
        sFactor = f;
        // sfondo con l'accento (Theme > Pannello rapido e notifiche > Colore accento): l'opacita' e' sempre la percentuale scelta
        int tint = 0;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            if ("1".equals(XposedHelpers.callStaticMethod(sp, "get", PROP_TINT, "0"))) {
                String h = String.valueOf(XposedHelpers.callStaticMethod(sp, "get", PROP_ACCENT, "")).trim();
                if (!h.isEmpty()) tint = 0xFF000000 | ((int) Long.parseLong(h, 16) & 0xFFFFFF);
            }
        } catch (Throwable ignored) {}
        sTint = tint;
        return f;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Android 17 (crDroid, ecc.): BackgroundBlurAlphaSync.onPreDraw lancia "Cannot round NaN value" quando lo sfondo sfocato e'
        // spento e fa cadere SystemUI ad ogni avvio (due crash per ogni riavvio: LSPosed entra in modalita' sicura). Si ignora l'errore.
        try {
            Class<?> sync = XposedHelpers.findClass("com.android.systemui.common.ui.view.BackgroundBlurAlphaSync", lpparam.classLoader);
            XposedBridge.hookAllMethods(sync, "onPreDraw", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        param.setResult(XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args));
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Throwable c = e.getCause();
                        if (c instanceof IllegalArgumentException || c instanceof ArithmeticException) {
                            param.setResult(Boolean.TRUE);   // ignorato: il disegno prosegue
                        } else {
                            throw c != null ? c : e;
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
            // classe assente su altre versioni: niente da fare
        }
        Class<?> scrimView = XposedHelpers.findClass("com.android.systemui.scrim.ScrimView", lpparam.classLoader);
        // Sfondo con l'accento: dopo che ScrimView ha calcolato il suo colore (blend con la tinta di sistema) si mette l'accento nel
        // suo disegno; solo per i veli dietro il pannello e sotto le notifiche (quello davanti, blocco e PIN, resta com'e')
        XposedBridge.hookAllMethods(scrimView, "updateColorWithTint", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    factor();
                    int tint = sTint;
                    if (tint == 0) return;
                    Object nameObj = XposedHelpers.getObjectField(param.thisObject, "mScrimName");
                    String name = nameObj == null ? "" : String.valueOf(nameObj);
                    if (!(name.contains("behind") || name.contains("notifications"))) return;
                    Object d = XposedHelpers.getObjectField(param.thisObject, "mDrawable");
                    if (d != null) XposedHelpers.callMethod(d, "setColor", tint, false);
                } catch (Throwable t) {
                    // mai far cadere SystemUI
                }
            }
        });
        XposedBridge.hookAllMethods(scrimView, "setViewAlpha", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    float f = factor();
                    if (f >= 1f || param.args.length != 1 || !(param.args[0] instanceof Float)) return;
                    Object nameObj = XposedHelpers.getObjectField(param.thisObject, "mScrimName");
                    String name = nameObj == null ? "" : String.valueOf(nameObj);
                    // "behind_scrim" (dietro il pannello) e "notifications_scrim" (sotto le notifiche); quello davanti (blocco, PIN) resta
                    if (name.contains("behind") || name.contains("notifications")) {
                        param.args[0] = ((Float) param.args[0]) * f;
                    }
                } catch (Throwable t) {
                    // mai far cadere SystemUI: se qualcosa non torna si lascia il valore di serie
                }
            }
        });
    }
}
