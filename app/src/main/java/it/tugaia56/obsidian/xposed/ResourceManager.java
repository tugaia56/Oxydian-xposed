package it.tugaia56.obsidian.xposed;

import android.content.res.Resources;
import java.io.File;
import java.util.ArrayList;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import it.tugaia56.obsidian.xposed.hooks.framework.DstCpbStyle;
import it.tugaia56.obsidian.xposed.hooks.framework.DstDialogStyle;
import it.tugaia56.obsidian.xposed.hooks.framework.MonetFreeze;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstNotifStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstRvdStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstToastStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstSignalIconStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstWifiIconStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsBackground;
import it.tugaia56.obsidian.xposed.hooks.systemui.StatusbarIcons;

public class ResourceManager implements IXposedHookInitPackageResources, IXposedHookZygoteInit {

    public final static java.util.concurrent.ConcurrentHashMap<String, XC_InitPackageResources.InitPackageResourcesParam> resparams = new java.util.concurrent.ConcurrentHashMap<>();
    public static Resources modRes;
    /** Module APK path — set in initZygote(), used by XModuleResources in hooks. */
    public static String modulePath;

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
        // chmod PRIMA del tentativo di lettura, sempre, in ogni boot.
        // Se initZygote ha i permessi (root), i file diventano leggibili subito
        // e preloadFromFile() riesce gia in questo boot.
        try {
            boolean dirOk = new File("/data/user_de/0/it.tugaia56.oxydian")
                    .setExecutable(true, false);  // 700 -> 711
            boolean fileOk = new File("/data/user_de/0/it.tugaia56.oxydian/shared_prefs/it.tugaia56.oxydian_preferences.xml")
                    .setReadable(true, false);    // 660 -> 664
            XposedBridge.log("[ Obsidian ] initZygote: chmod dir=" + dirOk + " file=" + fileOk);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] initZygote: chmod FAILED: " + t);
        }

        XposedBridge.log("[ Obsidian ] ResourceManager.initZygote: preloading prefs from disk");
        MonetFreeze.preloadFromFile();
        // Dialog: file → props fallback so zygote statics are set even on EACCES boot
        DstDialogStyle.preloadFromFile();
        DstDialogStyle.preloadFromProps();   // no-op if already preloaded; sets if file failed
        DstCpbStyle.preloadFromFile();
        DstRvdStyle.preloadFromFile();
        DstToastStyle.preloadFromFile();
        DstNotifStyle.preloadFromFile();
        DstWifiIconStyle.preloadFromFile();
        DstSignalIconStyle.preloadFromFile();
        StatusbarIcons.preloadFromFile();
        QsBackground.preloadFromFile();

        // Hook Dialog.show() here in zygote so it propagates to ALL forked processes
        // (regardless of LSPosed module scope). readProp() uses XposedHelpers so it
        // works without restriction in every app context after the fork.
        DstDialogStyle.hookDialogGlobal();

        // Hook ProgressBar.setIndeterminateDrawable() in zygote — same rationale.
        // On SDK36/OOS16, XResources replacements for the android package drawable do NOT fire
        // when ProgressBar loads its indeterminate drawable (confirmed by log analysis).
        // This method hook intercepts at the Java level and fires in every app process.
        DstCpbStyle.hookProgressBarGlobal();
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        XposedBridge.log("[ Obsidian ] ResourceManager.handleInitPackageResources: " + resparam.packageName);
        resparams.put(resparam.packageName, resparam);
        try { MonetFreeze.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] MonetFreeze.apply ERROR: " + t); }
        try { DstDialogStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstDialogStyle.apply ERROR: " + t); }
        try { DstCpbStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstCpbStyle.apply ERROR: " + t); }
        try { DstRvdStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstRvdStyle.apply ERROR: " + t); }
        try { DstToastStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstToastStyle.apply ERROR: " + t); }
        try { DstNotifStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstNotifStyle.apply ERROR: " + t); }
        try { DstWifiIconStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstWifiIconStyle.apply ERROR: " + t); }
        try { DstSignalIconStyle.applyPreloaded(resparam); } catch (Throwable t) { XposedBridge.log("[ Obsidian ] DstSignalIconStyle.apply ERROR: " + t); }
        if (!XPLauncher.runningMods.isEmpty()) {
            for (XposedMods mod : new ArrayList<>(XPLauncher.runningMods)) {
                if (!mod.listensTo(resparam.packageName)) continue;
                XposedBridge.log("[ Obsidian ] ResourceManager: initResources -> " + resparam.packageName);
                try {
                    mod.initResources();
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] initResources error in "
                            + mod.getClass().getSimpleName() + ": " + t.getMessage());
                }
            }
        }
    }
}
