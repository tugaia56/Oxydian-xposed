package it.tugaia56.obsidian.xposed.hooks.framework;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * 2 mod dell'app com.oplus.screenshot (Screenshot.apk) portate da LuckyTool (github.com/luckyzyx/
 * LuckyTool: EnablePNGSaveFormat.kt, CustomizeLongScreenshotMaxCapturedPages.kt) — l'originale usa
 * DexKit (ricerca dinamica a runtime nel bytecode dell'app) per trovare le classi giuste, qui
 * invece le classi VERE sono state trovate decompilando con jadx la vera Screenshot.apk installata
 * su questo device (2026-09-03), evitando di aggiungere DexKit come dipendenza per 2 sole feature.
 *
 * Rischio noto: entrambe le classi target sono offuscate (nomi tipo "j4.c", "...utils.h" — R8),
 * quindi il nome esatto potrebbe cambiare a un futuro aggiornamento dell'app Screenshot di sistema
 * (diversamente da "com.oplus.multiapp"/"com.android.launcher3.DeviceProfile" altrove in questo
 * file, i cui nomi di classe sono stabili). Se un aggiornamento OTA rompe l'hook, il toggle
 * semplicemente torna a non avere effetto (ogni hook è già in un try/catch difensivo) — nessun
 * crash, va solo ri-verificato con un nuovo decompile.
 */
public class LuckyScreenshotMod {

    private static final String PREF_PNG_FORMAT     = "DST_LUCKY_PNG_SCREENSHOT";
    private static final String PREF_LONGSHOT_LIMIT  = "DST_LUCKY_LONGSHOT_NO_LIMIT";
    private static final String PREFS_FILE =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";

    private static volatile boolean sPngFormat      = false;
    private static volatile boolean sLongshotNoLimit = false;

    // ── Live pref read (stesso pattern di CorePatchMod/LuckyExtrasMod) ───────────────────────

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object val = XposedHelpers.callStaticMethod(sp, "get", key, def);
            return val != null ? (String) val : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static void preloadFromFile() {
        try {
            java.io.File f = new java.io.File(PREFS_FILE);
            if (!f.exists()) { preloadFromProps(); return; }
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String xml = sb.toString();
            sPngFormat       = parseBool(xml, PREF_PNG_FORMAT,     false);
            sLongshotNoLimit = parseBool(xml, PREF_LONGSHOT_LIMIT, false);
        } catch (Throwable t) {
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        sPngFormat       = "1".equals(readProp("persist.obsidian.dst.lk_pngshot", "0"));
        sLongshotNoLimit = "1".equals(readProp("persist.obsidian.dst.lk_longshot", "0"));
    }

    private static boolean parseBool(String xml, String name, boolean def) {
        int idx = xml.indexOf("name=\"" + name + "\"");
        if (idx < 0) return def;
        int s = xml.indexOf("value=\"", idx);
        if (s < 0) return def;
        s += 7;
        int e = xml.indexOf("\"", s);
        if (e < 0) return def;
        return "true".equals(xml.substring(s, e));
    }

    // ── Install (chiamato da XPLauncher.hookOtherPackage per com.oplus.screenshot) ───────────

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        preloadFromFile();

        // ── Salva screenshot in PNG invece di JPEG — Source j4.c (ImageFileFormat.kt, enum
        // JPEG/PNG/PDF). Invece di mutare l'enum, ridirigiamo le 3 getter (getMimeType/getSuffix/
        // getCompressFormat) chiamate sull'istanza JPEG: quando il chiamante interroga JPEG per
        // sapere mimetype/estensione/formato di compressione, gli restituiamo gli stessi valori
        // dell'istanza PNG — PDF resta intoccato.
        try {
            Class<?> fmtCls = Class.forName("j4.c", false, cl);
            Object jpegConst = XposedHelpers.getStaticObjectField(fmtCls, "JPEG");
            Object pngConst = XposedHelpers.getStaticObjectField(fmtCls, "PNG");
            XC_MethodHook redirectHook = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    preloadFromFile();
                    if (!sPngFormat) return;
                    if (param.thisObject != jpegConst) return;
                    try {
                        param.setResult(((Method) param.method).invoke(pngConst));
                    } catch (Throwable ignored) {}
                }
            };
            hookAllIfExists(cl, "j4.c", "getMimeType", redirectHook);
            hookAllIfExists(cl, "j4.c", "getSuffix", redirectHook);
            hookAllIfExists(cl, "j4.c", "getCompressFormat", redirectHook);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LuckyScreenshotMod: j4.c (PNG format) not found: " + t);
        }

        // ── Più pagine massime per screenshot lunghi — Source com.oplus.screenshot.longshot.
        // stitch.utils.h (StitchLimitUtils.kt). Individuati per FORMA della firma, non per nome
        // (i nomi dei metodi sono offuscati a singola lettera e possono cambiare ad ogni build):
        //   • isCapturedPagesReachLimit(receiver, pagine:Int): Boolean → forziamo sempre false
        //   • trimToStitchLimit(receiver, larghezza:Int, altezza:Int): Int → forziamo sempre -1
        //     (valore sentinella dell'originale per "nessun taglio necessario, continua")
        try {
            Class<?> stitchCls = Class.forName(
                    "com.oplus.screenshot.longshot.stitch.utils.h", false, cl);
            for (Method m : stitchCls.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[1] == int.class && m.getReturnType() == boolean.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            preloadFromFile();
                            if (sLongshotNoLimit) param.setResult(Boolean.FALSE);
                        }
                    });
                } else if (p.length == 3 && p[1] == int.class && p[2] == int.class
                        && m.getReturnType() == int.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            preloadFromFile();
                            if (sLongshotNoLimit) param.setResult(-1);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LuckyScreenshotMod: StitchLimitUtils not found: " + t);
        }

        XposedBridge.log("[ Obsidian ] LuckyScreenshotMod: hooks installed in com.oplus.screenshot");
    }

    // ── Helpers (identici a CorePatchMod/LuckyExtrasMod) ──────────────────────────────────────

    private static void hookAllIfExists(ClassLoader cl, String className, String methodName, XC_MethodHook hook) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            XposedBridge.hookAllMethods(cls, methodName, hook);
        } catch (Throwable ignored) {
        }
    }
}
