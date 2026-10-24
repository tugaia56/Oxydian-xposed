package it.tugaia56.obsidian.xposed.hooks.logfox;

import android.content.Context;
import android.content.res.Resources;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * LogFox (com.f0x1d.logfox). Le schermate disegnate con Compose (Registrazioni, Log, Crash...) usano una tavolozza
 * Material marrone scritta nel codice (m8.a, valori come Color(0xFF19120C)): gli overlay di Oxydian Theme non la
 * raggiungono. Si interviene sulla funzione che crea i colori di Compose (ColorKt.Color(long), offuscata in questa
 * versione come y1.y.d): prima di crearli i marroni della modalita' scura diventano la famiglia del tema scelto in
 * Oxydian Theme (sfondo Blue Gray Dark e sue tonalita', accento). La tavolozza chiara non viene toccata.
 *
 * Nome delle classi legato alla versione di LogFox installata (vedi XposedBridge.log): se l'app cambia va ritrovato.
 * Mod nascosta, sempre attiva, nessuna impostazione. Serve la spunta su LogFox nell'ambito di Oxydian in LSPosed.
 */
public class LogFoxMod extends XposedMods {

    public static final String PKG = "com.f0x1d.logfox";
    private static final int BLUE_GRAY_DARK = 0xFF1B2029;

    private volatile int mAccent = 0xFF908DFF;
    private volatile int mBg = BLUE_GRAY_DARK;
    private volatile long mAt = -10000;

    public LogFoxMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName)) return;
        try {
            Class<?> cls = XposedHelpers.findClass("y1.y", lp.classLoader);   // ColorKt (versione corrente di LogFox)
            XposedHelpers.findAndHookMethod(cls, "d", long.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        long v = (Long) p.args[0];
                        if ((v >>> 32) != 0) return;               // Color(0xFFRRGGBB) passa 0x00000000FFRRGGBB
                        p.args[0] = (long) map((int) v) & 0xFFFFFFFFL;
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log("[ Obsidian ] LogFoxMod: hooked y1.y.d(long) in " + lp.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LogFoxMod: colour class y1.y not found (app version changed?): " + t);
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
        int out;
        switch (argb & 0xFFFFFF) {
            // superfici della tavolozza scura (marrone) -> famiglia dello sfondo di sistema
            case 0x140D08: refresh(); out = shade(mBg, -3); break;      // surfaceContainerLowest
            case 0x19120C: refresh(); out = mBg; break;                 // background / surface / surfaceDim
            case 0x221A14: refresh(); out = shade(mBg, 5); break;       // surfaceContainerLow
            case 0x261E18: refresh(); out = shade(mBg, 9); break;       // surfaceContainer
            case 0x312822: refresh(); out = shade(mBg, 14); break;      // surfaceContainerHigh
            case 0x382F28:                                              // inverseOnSurface
            case 0x3C332C: refresh(); out = shade(mBg, 18); break;      // surfaceContainerHighest
            case 0x413731: refresh(); out = shade(mBg, 22); break;      // surfaceBright
            case 0x51443B: refresh(); out = shade(mBg, 30); break;      // surfaceVariant / outlineVariant
            // contenitori colorati -> accento sul fondo
            case 0x5A422E: refresh(); out = mix(mBg, mAccent, 0.30f); break;   // secondaryContainer
            case 0x6C3A06: refresh(); out = mix(mBg, mAccent, 0.45f); break;   // primaryContainer
            case 0x88511E: refresh(); out = mix(mAccent, 0xFF000000, 0.25f); break; // inversePrimary
            // accento
            case 0xFFB77C: refresh(); out = mAccent; break;                    // primary
            case 0xE3C0A6: refresh(); out = mix(mAccent, 0xFFFFFFFF, 0.35f); break; // secondary
            case 0xFFDCC2: refresh(); out = mix(mAccent, 0xFFFFFFFF, 0.75f); break; // onPrimaryContainer / onSecondaryContainer
            case 0x4D2600: out = 0xFFFFFFFF; break;                            // onPrimary (pallino degli interruttori)
            case 0x412C1A: refresh(); out = mBg; break;                        // onSecondary
            // testi e bordi
            case 0xEFE0D6: out = 0xFFE4E8F0; break;                            // onSurface / onBackground / inverseSurface
            case 0xD6C3B6: out = 0xFFC2C8D6; break;                            // onSurfaceVariant
            case 0x9E8E82: out = 0xFF8A92A6; break;                            // outline
            default: return argb;
        }
        return (argb & 0xFF000000) | (out & 0xFFFFFF);
    }
}
