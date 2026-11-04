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
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) XposedHelpers.callStaticMethod(sp, "get", PROP, "100");
            int p = Integer.parseInt(v.trim());
            if (p >= 5 && p < 100) f = p / 100f;
        } catch (Throwable ignored) {}
        sFactor = f;
        return f;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
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
