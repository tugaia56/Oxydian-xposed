package it.tugaia56.obsidian.xposed.hooks.systemui;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Android puro (crDroid, ecc.): colore unico per le icone della barra di stato e per l'orologio, scelto in Oxydian Theme
 * (scheda "Colore icone barra di stato", proprieta' persist.oxytheme.battery_icon_color). SystemUI da' a tutte le icone e
 * all'orologio la stessa tinta tramite DarkIconDispatcherImpl: basta cambiare quella. Con la barra chiara (sfondo chiaro dietro,
 * icone scure) si lascia la tinta di sistema, per non rendere le icone invisibili. Su OxygenOS non viene caricata.
 */
public class StatusBarTintMod extends XposedMods {

    private static final String PROP = "persist.oxytheme.battery_icon_color";
    private static volatile Integer sColor = null;
    private static volatile long sRead = 0;

    public StatusBarTintMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }

    /** Colore scelto (null = predefinito); letto al piu' ogni 2 secondi. */
    private static Integer color() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sRead < 2000) return sColor;
        sRead = now;
        Integer c = null;
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) XposedHelpers.callStaticMethod(sp, "get", PROP, "");
            if (v != null && !v.trim().isEmpty()) c = (int) Long.parseLong(v.trim(), 16);
        } catch (Throwable ignored) {}
        sColor = c;
        return c;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> dispatcher = XposedHelpers.findClass("com.android.systemui.statusbar.phone.DarkIconDispatcherImpl", lpparam.classLoader);
        // Android 17: la tinta nasce in applyDarkIntensity (mescola mLightModeIconColorSingleTone e mDarkModeIconColorSingleTone in
        // mIconTint) e arriva a icone e orologio da applyIconTint (flusso) e applyDark (nuovi ricevitori): si cambia il colore di
        // base "chiaro" e mIconTint prima di ognuno
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Integer c = color();
                    if (c == null) return;
                    int tint = c | 0xFF000000;
                    XposedHelpers.setIntField(param.thisObject, "mLightModeIconColorSingleTone", tint);
                    float dark = XposedHelpers.getFloatField(param.thisObject, "mDarkIntensity");
                    if (dark < 0.5f) XposedHelpers.setIntField(param.thisObject, "mIconTint", tint);
                } catch (Throwable t) {
                    // mai far cadere SystemUI: si lascia la tinta di sistema
                }
            }
        };
        for (String m : new String[]{"applyDarkIntensity", "applyIconTint", "applyDark", "addDarkReceiver"}) {
            try { XposedBridge.hookAllMethods(dispatcher, m, hook); } catch (Throwable ignored) {}
        }
    }
}
