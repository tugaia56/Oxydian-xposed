package it.tugaia56.obsidian.xposed.hooks.framework;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

/**
 * Xposed hook: reimplementation of the 3 lower-risk toggles from the well-known "Core Patch"
 * LSPosed module (com.coderstory.toolkit), ported from its real decompiled source (2026-09-02,
 * user supplied Core Patch_4.9.apk). Only the toggles the user actually asked for and that don't
 * touch the delicate cross-app SharedUserSetting signing-lineage-merging logic (deliberately left
 * out — genuinely risky to hand-port without extensive live testing; the real Core Patch app can
 * still be used standalone for those).
 *
 * All hooks live in system_server (packageName == processName == "android") — install() is
 * called once from XPLauncher.hookFramework() when lpparam.processName.equals("android").
 *
 * Same "always re-read live" pattern as DstDialogStyle/DstNotifStyle: every hook re-checks the
 * pref via preloadFromFile() (with a SystemProperties fallback for the EACCES-before-unlock
 * window) on each call, so toggling a switch in the app takes effect on the next relevant call
 * without needing to reinstall hooks — but since these hooks patch classes that only exist in
 * system_server (PackageManagerService, PackageManagerServiceUtils, VerifyingSession…), a full
 * device reboot is still required after FIRST installing/updating this code, exactly like the
 * "Raggio Finestre Dialogo" bug fixed earlier today — the hook itself is zygote/system_server-
 * installed once at boot, only the pref VALUE it reads is live.
 *
 * Preset codes stored directly as booleans (no preset system needed, just on/off):
 *   DST_COREPATCH_DOWNGRADE      – Consenti Downgrade
 *   DST_COREPATCH_BYPASS_BLOCK   – Bypassa blocchi (OEM install/launch blocklist, e.g. Nothing Phone)
 *   DST_COREPATCH_DISABLE_VERIFY – Disabilita agente di verifica pacchetti (Play Protect / package verifier)
 */
public class CorePatchMod {

    private static final String PREF_DOWNGRADE      = "DST_COREPATCH_DOWNGRADE";
    private static final String PREF_BYPASS_BLOCK   = "DST_COREPATCH_BYPASS_BLOCK";
    private static final String PREF_DISABLE_VERIFY = "DST_COREPATCH_DISABLE_VERIFY";
    private static final String PREFS_FILE =
        "/data/user_de/0/it.tugaia56.obsidian/shared_prefs/it.tugaia56.obsidian_preferences.xml";

    // Screenshot da cui l'utente ha chiesto il porting: tutti e 3 partono ON di default,
    // come nell'app Core Patch originale.
    private static volatile boolean sDowngradeOn      = true;
    private static volatile boolean sBypassBlockOn    = true;
    private static volatile boolean sDisableVerifyOn  = true;

    // ── Live pref read (stesso doppio percorso file+prop di DstDialogStyle) ──────────────────

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
            sDowngradeOn     = parseBool(xml, PREF_DOWNGRADE,      true);
            sBypassBlockOn   = parseBool(xml, PREF_BYPASS_BLOCK,   true);
            sDisableVerifyOn = parseBool(xml, PREF_DISABLE_VERIFY, true);
        } catch (Throwable t) {
            preloadFromProps();
        }
    }

    private static void preloadFromProps() {
        sDowngradeOn     = "1".equals(readProp("persist.obsidian.dst.cp_downgrade", "1"));
        sBypassBlockOn   = "1".equals(readProp("persist.obsidian.dst.cp_bypassblock", "1"));
        sDisableVerifyOn = "1".equals(readProp("persist.obsidian.dst.cp_disableverify", "1"));
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

        // ── Consenti Downgrade — checkDowngrade su ogni variante nota per SDK 30-36+.
        // Ogni hook è indipendente e a prova di ClassNotFoundException/NoSuchMethodException:
        // sulle versioni dove quella particolare firma non esiste semplicemente non scatta,
        // stesso pattern difensivo dell'app originale (findMethodExactIfExists + try/catch).
        XC_MethodHook downgradeHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sDowngradeOn) param.setResult(null);
            }
        };
        XC_MethodHook downgradeHookBool = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sDowngradeOn) param.setResult(Boolean.TRUE);
            }
        };
        hookIfExists(cl, "com.android.server.pm.PackageManagerService", "checkDowngrade",
                new String[]{"com.android.server.pm.parsing.pkg.AndroidPackage", "android.content.pm.PackageInfoLite"}, downgradeHook);
        hookIfExists(cl, "com.android.server.pm.PackageManagerService", "checkDowngrade",
                new String[]{"android.content.pm.PackageInfoLite", "android.content.pm.PackageInfoLite"}, downgradeHookBool);
        hookIfExists(cl, "com.android.server.pm.PackageManagerServiceUtils", "checkDowngrade",
                new String[]{"com.android.server.pm.parsing.pkg.AndroidPackage", "android.content.pm.PackageInfoLite"}, downgradeHook);
        hookIfExists(cl, "com.android.server.pm.PackageManagerServiceUtils", "checkDowngrade",
                new String[]{"com.android.server.pm.pkg.AndroidPackage", "android.content.pm.PackageInfoLite"}, downgradeHook);
        hookIfExists(cl, "com.android.server.pm.PackageManagerServiceUtils", "checkDowngrade",
                new String[]{"com.android.server.pm.PackageSetting", "android.content.pm.PackageInfoLite"}, downgradeHook);

        // ── Bypassa blocchi — solo dispositivi Nothing Phone (com.nothing.server.ex…), su
        // OnePlus/Oplus questa classe semplicemente non esiste e l'hook non scatta mai: innocuo,
        // portato solo per completezza/compatibilità futura.
        XC_MethodHook bypassBlockHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sBypassBlockOn) param.setResult(Boolean.FALSE);
            }
        };
        hookIfExists(cl, "com.nothing.server.ex.NtConfigListServiceImpl", "isInstallingAppForbidden",
                new String[]{"java.lang.String"}, bypassBlockHook);
        hookIfExists(cl, "com.nothing.server.ex.NtConfigListServiceImpl", "isStartingAppForbidden",
                new String[]{"java.lang.String"}, bypassBlockHook);

        // ── Disabilita agente di verifica pacchetti — isVerificationEnabled, la classe che lo
        // ospita è cambiata nome nel tempo (PackageManagerService → VerificationParams →
        // VerifyingSession): proviamo tutte e 3, solo quella giusta per questa build scatta.
        XC_MethodHook disableVerifyHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                preloadFromFile();
                if (sDisableVerifyOn) param.setResult(Boolean.FALSE);
            }
        };
        hookAllIfExists(cl, "com.android.server.pm.PackageManagerService", "isVerificationEnabled", disableVerifyHook);
        hookAllIfExists(cl, "com.android.server.pm.VerificationParams", "isVerificationEnabled", disableVerifyHook);
        hookAllIfExists(cl, "com.android.server.pm.VerifyingSession", "isVerificationEnabled", disableVerifyHook);

        XposedBridge.log("[ Obsidian ] CorePatchMod: hooks installed in " + android.os.Process.myProcessName());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    private static void hookIfExists(ClassLoader cl, String className, String methodName,
                                       String[] paramTypeNames, XC_MethodHook hook) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            Object[] params = new Object[paramTypeNames.length];
            for (int i = 0; i < paramTypeNames.length; i++) params[i] = paramTypeNames[i];
            Method m = XposedHelpers.findMethodExactIfExists(cls, methodName, params);
            if (m != null) XposedBridge.hookMethod(m, hook);
        } catch (Throwable ignored) {
            // Classe/metodo non presente su questa build Android — atteso e innocuo, esattamente
            // come nell'app originale (ogni variante SDK-specifica è opzionale).
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
