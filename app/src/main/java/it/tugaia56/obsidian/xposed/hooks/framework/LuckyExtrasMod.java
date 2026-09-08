package it.tugaia56.obsidian.xposed.hooks.framework;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * Porting di 7 mod a basso rischio dal modulo open-source "LuckyTool" (github.com/luckyzyx/
 * LuckyTool, per ColorOS — stesso codebase di OOS), scelti tra ~200 perché: (a) toccano solo
 * classi oplus-services.jar/services.jar/oplus-framework.jar confermate presenti su questo
 * device via grep diretto sui dex reali (2026-09-02, non per analogia/supposizione — vedi
 * "stop guessing" in memoria), (b) sono toggle singoli senza logica di firma/sicurezza delicata.
 * Scartati: EnablePNGSaveFormat (l'originale usa DexKit, ricerca dinamica runtime — dipendenza
 * pesante per una sola feature, servirebbe decompilare l'app Screenshot reale prima), LTPO
 * Dynamic Refresh Rate (reflection su campi privati di HashMap annidate, molto fragile/dipendente
 * dal firmware del pannello).
 *
 * Tutti i 6 hook "android" (system_server) sono installati una sola volta da
 * XPLauncher.hookFramework() quando processName=="android" — stesso identico pattern e stessa
 * identica avvertenza reboot di CorePatchMod (hook zygote-cached, il valore del pref invece è
 * sempre riletto live). Il 7° (Multi App blacklist) ha ANCHE una metà lato-app in
 * com.oplus.multiapp, installata da installMultiAppApp() via XPLauncher.hookOtherPackage().
 */
public class LuckyExtrasMod {

    private static final String PREF_ADB_NO_CONFIRM     = "DST_LUCKY_ADB_NO_CONFIRM";
    private static final String PREF_FAST_POWER_MENU    = "DST_LUCKY_FAST_POWER_MENU";
    private static final String PREF_VOLUME_FLASHLIGHT  = "DST_LUCKY_VOLUME_FLASHLIGHT";
    private static final String PREF_MULTIAPP_NO_BLACK  = "DST_LUCKY_MULTIAPP_NO_BLACKLIST";
    private static final String PREFS_FILE =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";

    // Tutti opt-in, default OFF — a differenza di Core Patch questi non sono "patch di sicurezza
    // già attive nell'app originale", sono extra facoltativi.
    private static volatile boolean sAdbNoConfirm      = false;
    private static volatile boolean sFastPowerMenu     = false;
    private static volatile boolean sVolumeFlashlight  = false;
    private static volatile boolean sMultiAppNoBlack   = false;

    // ── Live pref read (stesso doppio percorso file+prop di CorePatchMod/DstDialogStyle) ─────

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
            sAdbNoConfirm     = parseBool(xml, PREF_ADB_NO_CONFIRM,    false);
            sFastPowerMenu    = parseBool(xml, PREF_FAST_POWER_MENU,   false);
            sVolumeFlashlight = parseBool(xml, PREF_VOLUME_FLASHLIGHT, false);
            sMultiAppNoBlack  = parseBool(xml, PREF_MULTIAPP_NO_BLACK, false);
        } catch (Throwable t) {
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        sAdbNoConfirm     = "1".equals(readProp("persist.obsidian.dst.lk_adbconfirm", "0"));
        sFastPowerMenu    = "1".equals(readProp("persist.obsidian.dst.lk_powermenu", "0"));
        sVolumeFlashlight = "1".equals(readProp("persist.obsidian.dst.lk_volflash", "0"));
        sMultiAppNoBlack  = "1".equals(readProp("persist.obsidian.dst.lk_multiapp", "0"));
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

    // ── Install (chiamato una volta da XPLauncher, solo in system_server) ────────────────────

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        preloadFromFile();

        // "Nascondi notifica VPN attiva" è stata tolta 2026-09-03: l'utente ha trovato un toggle
        // nativo equivalente in Impostazioni → Notifiche e Impostazioni rapide → Barra di stato
        // → VPN — ridondante, stessa scelta già fatta per "Mostra RAM nei Recenti"/"Layout
        // Recenti impilato".

        // ── Salta conferma installazione ADB — Source (Color/Oplus)PackageInstallInterceptManager
        XC_MethodHook adbHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sAdbNoConfirm) param.setResult(Boolean.FALSE);
            }
        };
        hookAllIfExists(cl, "com.android.server.pm.ColorPackageInstallInterceptManager",
                "allowInterceptAdbInstallInInstallStage", adbHook);
        hookAllIfExists(cl, "com.android.server.pm.OplusPackageInstallInterceptManager",
                "allowInterceptAdbInstallInInstallStage", adbHook);

        // ── Riduci ritardo Menù Accensione — Source SingleKeyGestureDetectorExtImpl.
        // pressType==1 (long press) + keyCode==26 (KEYCODE_POWER): forza il timeout a 800ms
        // invece del default OEM (più lungo), il menù appare prima tenendo premuto il tasto.
        XC_MethodHook powerMenuHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (!sFastPowerMenu) return;
                try {
                    if (param.args.length < 2) return;
                    int pressType = (Integer) param.args[0];
                    android.view.KeyEvent event = (android.view.KeyEvent) param.args[param.args.length - 1];
                    if (pressType == 1 && event.getKeyCode() == 26) param.setResult(800L);
                } catch (Throwable ignored) {}
            }
        };
        hookAllIfExists(cl, "com.android.server.policy.SingleKeyGestureDetectorExtImpl",
                "modifyPressTimeout", powerMenuHook);

        // "Supporto app 32-bit forzato" è stata tolta 2026-09-03: confermato che questo device
        // non ha proprio il runtime a 32-bit (ro.product.cpu.abilist32 vuota, zygote64 senza
        // controparte 32-bit) — nessuna app 32-bit può girare comunque, toggle inutile qui.

        // ── Volume ⇒ Torcia a schermo spento — Source OplusScreenOffTorchHelper.getInstance():
        // su alcune region/config l'helper stesso viene creato solo se una condizione OEM
        // (SKU/mercato) è vera; se getInstance() torna null lo costruiamo comunque a mano.
        hookIfExists(cl, "com.android.server.power.OplusScreenOffTorchHelper", "getInstance",
                new String[]{"android.content.Context"}, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        preloadFromFile();
                        if (!sVolumeFlashlight || param.getResult() != null) return;
                        try {
                            Class<?> cls = Class.forName(
                                    "com.android.server.power.OplusScreenOffTorchHelper", false, cl);
                            param.setResult(XposedHelpers.newInstance(cls, param.args[0]));
                        } catch (Throwable ignored) {}
                    }
                });

        // ── Rimuovi blacklist Multi App (metà system_server) — Source OplusMultiAppDataManager.
        // L'altra metà (lato app com.oplus.multiapp) è in installMultiAppApp() sotto.
        XC_MethodHook multiAppSysHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sMultiAppNoBlack) param.setResult(null);
            }
        };
        hookAllIfExists(cl, "com.android.server.pm.OplusMultiAppDataManager",
                "initBlackAppList", multiAppSysHook);

        // "Disabilita gestione automatica Slider Allerta" (AlertSliderAudioPolicy.setUp/
        // setMiddle/setDown) è stata tolta 2026-09-03: confermato dall'utente live che il cambio
        // di modalità (suoneria/vibrazione/silenzioso, cursori volume) avveniva comunque con
        // l'hook attivo e il device riavviato — la vera applicazione del profilo evidentemente
        // passa da un altro punto non ancora identificato, non da questi 3 metodi.

        XposedBridge.log("[ Obsidian ] LuckyExtrasMod: hooks installed in " + android.os.Process.myProcessName());
    }

    /** Metà lato-app della rimozione blacklist Multi App — chiamata da
     *  XPLauncher.hookOtherPackage() quando lpparam.packageName.equals("com.oplus.multiapp"). */
    public static void installMultiAppApp(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        preloadFromFile();
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sMultiAppNoBlack) param.setResult(null);
            }
        };
        hookAllIfExists(cl, "com.oplus.multiapp.utils.MultiAppBlackListUpdateHelper",
                "loadMultiappBlackListConfig", hook);
        XposedBridge.log("[ Obsidian ] LuckyExtrasMod: multiapp hooks installed");
    }

    // ── Helpers (identici a CorePatchMod) ─────────────────────────────────────────────────────

    private static void hookIfExists(ClassLoader cl, String className, String methodName,
                                       String[] paramTypeNames, XC_MethodHook hook) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            Object[] params = new Object[paramTypeNames.length];
            for (int i = 0; i < paramTypeNames.length; i++) params[i] = paramTypeNames[i];
            Method m = XposedHelpers.findMethodExactIfExists(cls, methodName, params);
            if (m != null) XposedBridge.hookMethod(m, hook);
        } catch (Throwable ignored) {
        }
    }

    private static void hookAllIfExists(ClassLoader cl, String className, String methodName, XC_MethodHook hook) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            XposedBridge.hookAllMethods(cls, methodName, hook);
        } catch (Throwable ignored) {
        }
    }
}
