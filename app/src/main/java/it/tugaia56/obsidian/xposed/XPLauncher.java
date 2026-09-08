package it.tugaia56.obsidian.xposed;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.xposed.hooks.framework.CorePatchMod;
import it.tugaia56.obsidian.xposed.hooks.framework.DstCpbStyle;
import it.tugaia56.obsidian.xposed.hooks.framework.DstDialogStyle;
import it.tugaia56.obsidian.xposed.hooks.framework.LuckyExtrasMod;
import it.tugaia56.obsidian.xposed.hooks.framework.LuckyScreenshotMod;
import it.tugaia56.obsidian.xposed.hooks.framework.ScreenshotEnablerMod;

public class XPLauncher {

    public static ArrayList<XposedMods> runningMods = new ArrayList<>();
    @SuppressLint("StaticFieldLeak") public static Context mContext = null;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("[ Obsidian ] handleLoadPackage: " + lpparam.packageName);
        Log.d("ObsidianXP", "handleLoadPackage: " + lpparam.packageName);

        if (lpparam.packageName.equals(Constants.Packages.FRAMEWORK)) {
            hookFramework(lpparam);
        } else {
            hookOtherPackage(lpparam);
        }
    }

    private void hookFramework(XC_LoadPackage.LoadPackageParam lpparam) {
        // Install Dialog style hook in every process — handleLoadPackage("android") fires in
        // each process that loads the android framework, which is every app process.
        DstDialogStyle.hookDialogInProcess();

        // Core Patch / Enable Screenshot hooks target classes that only ever exist inside
        // system_server itself (PackageManagerService, WindowManagerService, DisplayControl…) —
        // unlike DstDialogStyle above, install these ONLY in system_server (processName=="android",
        // not just packageName=="android" which fires in every app process too), otherwise every
        // Class.forName() attempt below would just fail harmlessly in every other app but waste
        // work. Both hooks install once at zygote/system_server start — a code change here needs
        // a full device reboot to take effect, same as the "Raggio Finestre Dialogo" bug fixed
        // earlier today (SystemUI restart alone does NOT reload zygote-cached hooks).
        if ("android".equals(lpparam.processName)) {
            try { CorePatchMod.install(lpparam); } catch (Throwable t) {
                log("[ Obsidian ] CorePatchMod.install failed: " + t);
            }
            try { ScreenshotEnablerMod.install(lpparam); } catch (Throwable t) {
                log("[ Obsidian ] ScreenshotEnablerMod.install failed: " + t);
            }
            try { LuckyExtrasMod.install(lpparam); } catch (Throwable t) {
                log("[ Obsidian ] LuckyExtrasMod.install failed: " + t);
            }
        }

        boolean hooked = false;
        try {
            Class<?> PWM = lpparam.classLoader.loadClass("com.android.server.policy.PhoneWindowManager");
            hookAllMethods(PWM, "init", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mContext == null && p.args[0] instanceof Context) {
                        initContext((Context) p.args[0], lpparam);
                    }
                }
            });
            hooked = true;
            log("[ Obsidian ] hooked PhoneWindowManager.init");
        } catch (Throwable t) {
            log("[ Obsidian ] PhoneWindowManager not found: " + t.getMessage());
        }

        if (!hooked) {
            try {
                findAndHookMethod(Instrumentation.class, "newApplication",
                        ClassLoader.class, String.class, Context.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (mContext == null && p.args[2] instanceof Context) {
                            initContext((Context) p.args[2], lpparam);
                        }
                    }
                });
                log("[ Obsidian ] hooked Instrumentation.newApplication (framework fallback)");
            } catch (Throwable t) {
                log("[ Obsidian ] framework fallback hook failed: " + t.getMessage());
            }
        }
    }

    private void hookOtherPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.android.systemui".equals(lpparam.packageName)) {
            it.tugaia56.obsidian.xposed.hooks.systemui.DstNotifBgView.hook(lpparam);
        }
        if ("com.oplus.appplatform".equals(lpparam.packageName)) {
            try { ScreenshotEnablerMod.installOplusAppPlatform(lpparam); } catch (Throwable t) {
                log("[ Obsidian ] ScreenshotEnablerMod.installOplusAppPlatform failed: " + t);
            }
        }
        if ("com.oplus.multiapp".equals(lpparam.packageName)) {
            try { LuckyExtrasMod.installMultiAppApp(lpparam); } catch (Throwable t) {
                log("[ Obsidian ] LuckyExtrasMod.installMultiAppApp failed: " + t);
            }
        }
        if ("com.oplus.screenshot".equals(lpparam.packageName)) {
            try { LuckyScreenshotMod.install(lpparam); } catch (Throwable t) {
                log("[ Obsidian ] LuckyScreenshotMod.install failed: " + t);
            }
        }
        Log.d("ObsidianXP", "hookOtherPackage: hooking Instrumentation for " + lpparam.packageName);
        try {
            findAndHookMethod(Instrumentation.class, "newApplication",
                    ClassLoader.class, String.class, Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Log.d("ObsidianXP", "newApplication fired, mContext=" + mContext + " pkg=" + lpparam.packageName);
                    if (mContext == null && p.args[2] instanceof Context) {
                        initContext((Context) p.args[2], lpparam);
                    }
                }
            });
            Log.d("ObsidianXP", "hookOtherPackage: hook installed OK for " + lpparam.packageName);
        } catch (Throwable t) {
            Log.d("ObsidianXP", "hookOtherPackage FAILED for " + lpparam.packageName + ": " + t);
            log("[ Obsidian ] hookOtherPackage failed for " + lpparam.packageName + ": " + t.getMessage());
        }
    }

    private void initContext(Context ctx, XC_LoadPackage.LoadPackageParam lpparam) {
        Log.d("ObsidianXP", "initContext: " + lpparam.packageName);
        mContext = ctx;
        try {
            ResourceManager.modRes = ctx.createPackageContext(
                    BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY).getResources();
            ModResHolder.modRes = ResourceManager.modRes;
        } catch (Throwable t) {
            log("[ Obsidian ] modRes init failed: " + t.getMessage());
        }

        // Re-register CPB and WiFi icon XResources replacements now that modRes is set.
        // This mirrors OC's initResources() called from updatePrefs():
        // late registration forces cache invalidation so newDrawable() fires on next request.
        try { DstCpbStyle.initResourcesLate(); } catch (Throwable ignored) {}
        try {
            it.tugaia56.obsidian.xposed.hooks.systemui.DstWifiIconStyle.initResourcesLate();
        } catch (Throwable ignored) {}
        try {
            it.tugaia56.obsidian.xposed.hooks.systemui.DstSignalIconStyle.initResourcesLate();
        } catch (Throwable ignored) {}

        XPrefs.init(ctx);

        // 2026-09-06: richiesta esplicita dell'utente dopo aver notato ritardi al boot (sfondo/
        // accento lenti ad applicarsi, il launcher stock che a volte vince la corsa per lo slot
        // Home su Nova) — con ~70 pacchetti ora in scope, TUTTI questi processi partono e
        // installano i propri hook (reflection, findClass che spesso lancia eccezioni per le
        // build offuscate) nella stessa finestra critica del boot, competendo per CPU con
        // launcher/SystemUI. Le app in EXTRA_OEM_PACKAGES non si aprono mai "appena acceso il
        // telefono" (l'utente stesso: "non sono app che uso appena aperto il cellulare") — per
        // queste, ritardare l'intero avvio dei mod (Fase 1 + Fase 2 insieme, stesso ordine di
        // prima) libera CPU proprio nella finestra che conta di più. Le altre (Settings,
        // WirelessSettings, Battery, Phone... — CORE_PACKAGES) restano immediate: sono quelle il
        // cui ritardo di applicazione tema l'utente vuole vedere sparire, non allungare.
        Runnable startMods = () -> {
            // Phase 1 – install hooks immediately so they fire during SystemUI init.
            // Preference fields use preloaded/static values or Java defaults until Phase 2.
            installHooks(lpparam);

            // Phase 2 – background thread: wait for ContentProvider, then push live prefs.
            if (!ModPacks.getMods(lpparam.packageName).isEmpty()) {
                new Thread(() -> waitAndRefreshPrefs(lpparam)).start();
            }
        };

        if (isDeferredPackage(lpparam.packageName)) {
            new Thread(() -> {
                try { Thread.sleep(DEFERRED_STARTUP_DELAY_MS); } catch (InterruptedException ignored) {}
                startMods.run();
            }, "ObsidianDeferredStart").start();
        } else {
            startMods.run();
        }
    }

    private static final long DEFERRED_STARTUP_DELAY_MS = 20000;

    private static boolean isDeferredPackage(String packageName) {
        for (String p : it.tugaia56.obsidian.xposed.hooks.settings.SettingsCardBackgroundMod.EXTRA_OEM_PACKAGES) {
            if (p.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * Phase 1: create each mod instance and install its hooks immediately.
     * updatePrefs() is NOT called here — Xprefs isn't ready yet.
     */
    private void installHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.d("ObsidianXP", "installHooks: " + lpparam.packageName
                + " mods=" + ModPacks.getMods(lpparam.packageName).size());
        for (Class<? extends XposedMods> mod : ModPacks.getMods(lpparam.packageName)) {
            try {
                Log.d("ObsidianXP", "installing: " + mod.getSimpleName());
                XposedMods inst = mod.getConstructor(Context.class).newInstance(mContext);
                if (!inst.listensTo(lpparam.packageName)) {
                    Log.d("ObsidianXP", mod.getSimpleName() + " listensTo=false, skipped");
                    continue;
                }
                inst.handleLoadPackage(lpparam);
                runningMods.add(inst);
                Log.d("ObsidianXP", "installed OK: " + mod.getSimpleName());
                log("[ Obsidian ] hooks installed: " + mod.getSimpleName()
                        + " for " + lpparam.packageName);
            } catch (Throwable t) {
                Log.d("ObsidianXP", "installHooks FAILED: " + mod.getSimpleName() + ": " + t);
                log("[ Obsidian ] installHooks failed: " + mod.getSimpleName()
                        + ": " + t.getMessage());
            }
        }
    }

    /**
     * Phase 2: waits for Obsidian's ContentProvider then calls updatePrefs() on every
     * running mod so they switch from default/preloaded values to the user's saved prefs.
     * On slow boots the app may start 10-30 s after SystemUI — we wait indefinitely.
     */
    private void waitAndRefreshPrefs(XC_LoadPackage.LoadPackageParam lpparam) {
        // 2026-09-05: il retry "per sempre" originale presumeva che ogni fallimento fosse un
        // problema di TIMING (il provider di Obsidian non ancora partito) — vero per la
        // maggior parte dei processi, ma falso per alcune app (com.oneplus.account,
        // com.oplus.games, confermato via logcat dal vivo: "Failed to find provider info"
        // ripetuto ogni secondo indefinitamente, MAI risolto) dove il vero blocco è il filtro
        // di visibilità pacchetti di Android — permanente, nessuna attesa lo risolve. Dopo
        // FALLBACK_ATTEMPTS tentativi si arrende al provider e passa al fallback file-based di
        // ogni mod, ma CONTINUA a ritentare in background nel caso raro in cui fosse davvero
        // solo lento — se il provider arriva più tardi, updatePrefs() sostituisce comunque i
        // valori approssimati del fallback con quelli reali.
        // 2026-09-06: era 15 (~15s) — con ~70 pacchetti ora in scope, l'utente ha notato un
        // ritardo reale nell'applicazione del tema dopo un riavvio (molti processi che partono
        // insieme al boot, ognuno bloccato fino a 15s prima di mostrare qualcosa). Il fallback
        // via system properties (preloadFallback, oggi solo SettingsCardBackgroundMod — quello
        // più visibile, tinge le card ovunque) è già affidabile quanto il provider reale, non
        // serve aspettargli così tanto prima di fidarsene: ridotto a 5 tentativi (~5s), stesso
        // meccanismo di sicurezza sotto (updatePrefs() corregge comunque con i valori veri non
        // appena il provider risponde, quindi nessun rischio di restare su un valore sbagliato).
        final int FALLBACK_ATTEMPTS = 5;
        boolean providerReady = false;
        for (int attempt = 0; attempt < FALLBACK_ATTEMPTS; attempt++) {
            try {
                Xprefs.getBoolean("LoadTestBooleanValue", false);
                providerReady = true;
                break;
            } catch (Throwable ignored) {
                try { Thread.sleep(1000); } catch (Throwable ignored2) {}
            }
        }
        if (!providerReady) {
            log("[ Obsidian ] Xprefs irraggiungibile dopo " + FALLBACK_ATTEMPTS
                    + " tentativi, uso il fallback file-based → " + lpparam.packageName);
            for (XposedMods mod : new ArrayList<>(runningMods)) {
                if (!mod.listensTo(lpparam.packageName)) continue;
                try { mod.preloadFallback(); } catch (Throwable ignored) {}
            }
            while (!providerReady) {
                try {
                    Xprefs.getBoolean("LoadTestBooleanValue", false);
                    providerReady = true;
                } catch (Throwable ignored) {
                    try { Thread.sleep(5000); } catch (Throwable ignored2) {}
                }
            }
        }
        log("[ Obsidian ] v" + BuildConfig.VERSION_NAME
                + " Xprefs ready → " + lpparam.packageName);
        // Se la primissima registrazione in XPrefs.init() era fallita (provider non ancora
        // vivo, vedi lì) il provider ora risponde davvero — ritenta la registrazione del
        // listener, altrimenti i futuri cambi di preferenza non arriverebbero mai a questo
        // processo pur avendo installato correttamente gli hook.
        XPrefs.ensureListenerRegistered();

        for (XposedMods mod : new ArrayList<>(runningMods)) {
            if (!mod.listensTo(lpparam.packageName)) continue;
            try { mod.updatePrefs(); } catch (Throwable ignored) {}
        }
    }
}
