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
        // Android puro: con la sfocatura delle finestre accesa lo sfondo del pannello diventa "vetro" (velo quasi trasparente, 5%: la
        // sfocatura fa il resto, come su RisingOS); con la sfocatura spenta resta quello del sistema. Niente piu' scelta nel Theme.
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            boolean supported = "1".equals(String.valueOf(XposedHelpers.callStaticMethod(sp, "get", "ro.surface_flinger.supports_background_blur", "0")));
            Object app = XposedHelpers.callStaticMethod(Class.forName("android.app.ActivityThread"), "currentApplication");
            // RisingOS ha gia' il suo vetro: li' non si tocca niente
            boolean rising = !String.valueOf(XposedHelpers.callStaticMethod(sp, "get", "ro.rising.build.version", "")).isEmpty();
            if (supported && !rising && app != null) {
                android.content.ContentResolver cr = ((android.content.Context) app).getContentResolver();
                if (android.provider.Settings.Global.getInt(cr, "disable_window_blurs", 0) == 0) f = 0.05f;
            }
        } catch (Throwable ignored) {}
        sFactor = f;
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
