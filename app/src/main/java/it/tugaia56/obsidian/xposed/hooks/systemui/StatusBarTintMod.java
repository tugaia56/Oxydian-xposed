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
    private static final String PROP_CLOCK = "persist.oxytheme.clock_color";
    private static final String PROP_PCT = "persist.oxytheme.battery_pct_color";
    private static volatile Integer sColor = null, sClock = null, sPct = null;
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

    private static Integer readProp(String prop) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String v = (String) XposedHelpers.callStaticMethod(sp, "get", prop, "");
            if (v != null && !v.trim().isEmpty()) return (int) Long.parseLong(v.trim(), 16);
        } catch (Throwable ignored) {}
        return null;
    }

    private static void refresh() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sRead < 2000) return;
        sRead = now;
        sColor = readProp(PROP);
        sClock = readProp(PROP_CLOCK);
        sPct = readProp(PROP_PCT);
    }

    /** Colore scelto per le icone (null = predefinito); letto al piu' ogni 2 secondi. */
    private static Integer color() {
        refresh();
        return sColor;
    }

    /** Colore del numero della batteria: quello scelto per lui, altrimenti lo stesso delle icone (null = predefinito). */
    private static Integer pctColor() {
        refresh();
        return sPct != null ? sPct : sColor;
    }

    /** Colore dell'orologio: quello scelto per lui, altrimenti lo stesso delle icone (null = predefinito). */
    private static Integer clockColor() {
        refresh();
        return sClock != null ? sClock : sColor;
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
        hookClock(lpparam);
        hookBatteryPercent(lpparam);
    }

    /** L'orologio riceve la tinta da Clock.onDarkChanged(areas, darkIntensity, tint): con barra scura si usa il colore dell'orologio. */
    private static void hookClock(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clock = XposedHelpers.findClass("com.android.systemui.statusbar.policy.Clock", lpparam.classLoader);
            XposedBridge.hookAllMethods(clock, "onDarkChanged", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Integer c = clockColor();
                        if (c == null || param.args.length < 3) return;
                        if (((Float) param.args[1]) < 0.5f) param.args[2] = c | 0xFF000000;
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("StatusBarTintMod: clock hook failed: " + t);
        }
    }

    /**
     * Android 17: il numero della batteria (testo "100%") e' Compose e prende un Color fisso (bianco su sfondo scuro) dal
     * ColorProducer di BatteryWithPercent, non dalla tinta del DarkIconDispatcher. Se il risultato e' il bianco lo si sostituisce
     * con il colore scelto. (Il fulmine e' un'emoji nel testo: non si puo' colorare.)
     */
    /** Valore (ULong impacchettato) di un androidx Color: e' il campo long "value" della value class. */
    private static long packedOf(Object color) {
        try {
            return XposedHelpers.getLongField(color, "value");
        } catch (Throwable t) {
            for (java.lang.reflect.Field f : color.getClass().getDeclaredFields()) {
                if (f.getType() == long.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    try { f.setAccessible(true); return f.getLong(color); } catch (Throwable ignored) {}
                }
            }
            throw new IllegalStateException("no long field in Color");
        }
    }

    private static void hookBatteryPercent(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> producer = XposedHelpers.findClass("com.android.systemui.statusbar.pipeline.battery.ui.composable.BatteryWithPercentKt$BatteryWithPercent$colorProducer$1$1", lpparam.classLoader);
            final Class<?> colorClass = XposedHelpers.findClass("androidx.compose.ui.graphics.Color", lpparam.classLoader);
            XposedBridge.hookAllMethods(producer, "invoke", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Integer c = pctColor();
                        if (c == null || param.getResult() == null) return;
                        long packed = packedOf(param.getResult());
                        if (packed != 0xFFFFFFFF00000000L) return; // solo il bianco di sfondo scuro
                        long mine = (((long) (c | 0xFF000000)) & 0xFFFFFFFFL) << 32;
                        param.setResult(XposedHelpers.callStaticMethod(colorClass, "box-impl", mine));
                    } catch (Throwable t) {
                        XposedBridge.log("StatusBarTintMod: producer error " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("StatusBarTintMod: battery percent hook failed: " + t);
        }
        // i colori della batteria (riempimento, simbolo, testo) di tema scuro sono Color fissi: quando sono bianchi si usa il colore scelto
        final String base = "com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors$DarkTheme$";
        for (String variant : new String[]{"Default", "Charging", "PowerSave", "Error"}) {
            for (String getter : new String[]{"getFill-0d7_KjU", "getGlyph-0d7_KjU", "getAttribution-0d7_KjU"}) {
                try {
                    final String tag = variant + "." + getter;
                    XposedBridge.hookAllMethods(XposedHelpers.findClass(base + variant, lpparam.classLoader), getter, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Integer c = color();
                                if (c == null || !(param.getResult() instanceof Long)) return;
                                long packed = (Long) param.getResult();
                                if (packed != 0xFFFFFFFF00000000L) return;
                                param.setResult((((long) (c | 0xFF000000)) & 0xFFFFFFFFL) << 32);
                            } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable e) { XposedBridge.log("StatusBarTintMod: hook FAIL " + variant + "." + getter + " " + e); }
            }
        }
    }
}
