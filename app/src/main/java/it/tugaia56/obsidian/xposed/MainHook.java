package it.tugaia56.obsidian.xposed;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.hooks.framework.MonetFreeze;
import java.io.File;

public class MainHook implements IXposedHookLoadPackage, IXposedHookInitPackageResources, IXposedHookZygoteInit {

    private final XPLauncher     launcher         = new XPLauncher();
    private final ResourceManager resourceManager = new ResourceManager();

    @Override
    public void initZygote(StartupParam p) throws Throwable {
        resourceManager.initZygote(p);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if ("it.tugaia56.oxydian".equals(lp.packageName)) {
            // Chmod: run as owner UID (10629) — always succeeds
            try {
                boolean dirOk  = new File("/data/user_de/0/it.tugaia56.oxydian").setExecutable(true, false);
                boolean fileOk = new File("/data/user_de/0/it.tugaia56.oxydian/shared_prefs/it.tugaia56.oxydian_preferences.xml").setReadable(true, false);
                XposedBridge.log("[ Obsidian ] handleLoadPackage(obsidian): chmod dir=" + dirOk + " file=" + fileOk);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] handleLoadPackage(obsidian): chmod FAILED: " + t);
            }

            // Preload prefs come owner UID (unico processo che può leggere il proprio XML)
            MonetFreeze.preloadFromFile();
        }
        launcher.handleLoadPackage(lp);
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam rp) throws Throwable {
        resourceManager.handleInitPackageResources(rp);
    }
}
