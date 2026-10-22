package it.tugaia56.obsidian.xposed.hooks.garmin;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Garmin Connect. La schermata principale e quasi tutte le nuove sono scritte in Compose, con i colori scritti nel
 * codice (una tavolozza creata con Color(0xFF101010), Color(0xFF1976D2) ecc.): gli overlay di Oxydian Theme non
 * li raggiungono. Si interviene sulla funzione che crea i colori di Compose (ColorKt.Color(long) e Color(int),
 * offuscate in questa versione come o4.f0.d e o4.f0.b): prima di crearli, i pochi colori della grafica
 * dell'app vengono sostituiti:
 *  - blu (#1976D2, #3B97F3, #2A88E6, #54A9FE e i piu' scuri) -> accento scelto in Oxydian Theme;
 *  - grigi di sfondo (#101010 pagina, #1D1D1D card) -> lo sfondo di sistema (Blue Gray Dark).
 * Il nero di base e tutto il resto (grafici, verde/arancio/rosso) restano com'erano.
 *
 * Nome delle classi legato alla versione 5.30.1 dell'app: se Garmin cambia il codice va ritrovato. Mod nascosta,
 * sempre attiva, nessuna impostazione. Serve la spunta su Garmin Connect nell'ambito di Oxydian in LSPosed.
 */
public class GarminConnectMod extends XposedMods {

    public static final String PKG = "com.garmin.android.apps.connectmobile";
    public static final String PKG_IQ = "com.garmin.connectiq";
    private static final int BLUE_GRAY_DARK = 0xFF1B2029;

    private volatile int mAccent = 0xFF908DFF;
    private volatile int mBg = BLUE_GRAY_DARK;
    private volatile long mAt = -10000;

    public GarminConnectMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName) || PKG_IQ.equals(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        // classe offuscata di Compose (ColorKt) con le funzioni Color(long) e Color(int), per app e versione
        final String colorClass, fnLong, fnInt;
        if (PKG.equals(lp.packageName)) { colorClass = "o4.f0"; fnLong = "d"; fnInt = "b"; }          // Garmin Connect 5.30.1
        else if (PKG_IQ.equals(lp.packageName)) { colorClass = "nkb"; fnLong = "e"; fnInt = "d"; }  // Connect IQ 2.50
        else return;
        try {
            Class<?> cls = XposedHelpers.findClass(colorClass, lp.classLoader);
            XC_MethodHook asLong = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        long v = (Long) p.args[0];
                        // ARGB nei 32 bit bassi (Color(0xFF101010) passa 0x00000000FF101010)
                        if ((v >>> 32) != 0) return;
                        p.args[0] = (long) map((int) v) & 0xFFFFFFFFL;
                    } catch (Throwable ignored) {}
                }
            };
            XC_MethodHook asInt = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        p.args[0] = map((Integer) p.args[0]);
                    } catch (Throwable ignored) {}
                }
            };
            XposedHelpers.findAndHookMethod(cls, fnLong, long.class, asLong);
            XposedHelpers.findAndHookMethod(cls, fnInt, int.class, asInt);
            XposedBridge.log("[ Obsidian ] GarminConnectMod: hooked " + colorClass + "." + fnLong + "(long) / ." + fnInt + "(int) in " + lp.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] GarminConnectMod: colour class " + colorClass
                    + " not found (app version changed?): " + t);
        }
    }

    // ── Colori ───────────────────────────────────────────────────────────────

    private static String prop(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            return (String) XposedHelpers.callStaticMethod(sp, "get", key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private void refresh() {
        long now = SystemClock.elapsedRealtime();
        if (now - mAt < 3000) return;
        mAt = now;
        int acc = 0;
        try {
            String a1 = prop("persist.obsidian.dst.a1", "");
            if (!a1.isEmpty()) acc = 0xFF000000 | Integer.parseInt(a1);
        } catch (NumberFormatException ignored) {}
        try {
            Resources r = Resources.getSystem();
            if (acc == 0) {
                int id = r.getIdentifier("accent_material_dark", "color", "android");
                if (id != 0) acc = r.getColor(id, null);
            }
        } catch (Throwable ignored) {}
        // sfondo di sistema scelto in Oxydian Theme (Resources.getSystem() qui darebbe il nero originale del sistema)
        int bg = BLUE_GRAY_DARK;
        try {
            String v = prop("persist.oxytheme.bg", "");
            if (v != null && v.length() >= 6) bg = 0xFF000000 | (int) Long.parseLong(v.substring(v.length() - 6), 16);
        } catch (Throwable ignored) {}
        mBg = bg;
        if (acc != 0) mAccent = acc | 0xFF000000;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int shade(int c, int delta) {
        int r = Math.min(255, Math.max(0, ((c >> 16) & 255) + delta));
        int g = Math.min(255, Math.max(0, ((c >> 8) & 255) + delta));
        int b = Math.min(255, Math.max(0, (c & 255) + delta));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Colore sostituito, con la trasparenza di prima; quelli non riconosciuti restano uguali. */
    private int map(int argb) {
        int rgb = argb & 0xFFFFFF;
        int out;
        switch (rgb) {
            case 0x000000:
                // nero pieno: sfondo della barra in alto e altri sfondi scuri; con trasparenza (ombre) resta com'e'
                // nero trasparente (barra in alto, ombre): prende lo sfondo, cosi' sopra lo sfondo non lo scurisce
                if ((argb >>> 24) == 0) return argb;
                refresh(); out = mBg; break;
            case 0x101010: refresh(); out = mBg; break;
            case 0x1D1D1D: refresh(); out = shade(mBg, 6); break;
            case 0x252525: refresh(); out = shade(mBg, 9); break;
            case 0x1976D2:
            case 0x2A88E6:
            case 0x3B97F3: refresh(); out = mAccent; break;
            case 0x54A9FE: refresh(); out = mix(mAccent, 0xFFFFFFFF, 0.2f); break;
            case 0x1265C2:
            case 0x0A59B2:
            case 0x004BA0: refresh(); out = mix(mAccent, 0xFF000000, 0.3f); break;
            default: return argb;
        }
        return (argb & 0xFF000000) | (out & 0xFFFFFF);
    }
}
