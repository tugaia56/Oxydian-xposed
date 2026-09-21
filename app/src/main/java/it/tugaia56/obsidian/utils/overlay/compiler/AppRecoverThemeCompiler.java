package it.tugaia56.obsidian.utils.overlay.compiler;

import static it.tugaia56.obsidian.utils.Constants.Packages.APP_RECOVER;
import static it.tugaia56.obsidian.utils.FileUtil.copyAssets;
import static it.tugaia56.obsidian.utils.RootUtil.setPermissions;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRO;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRW;
import static it.tugaia56.obsidian.utils.helper.BinaryInstaller.symLinkBinaries;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlay;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlays;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.enableOverlays;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;

import it.tugaia56.obsidian.utils.ModuleConstants;

public class AppRecoverThemeCompiler {
    private final static String TAG = AppRecoverThemeCompiler.class.getSimpleName();
    private static final String PREFIX = "ObsidianComponent";
    private static final String OVERLAY_NAME = "AR1";
    private static final String ASSET_DIR = "AR";

    public static boolean buildOverlay() throws IOException {
        preExecute();
        String source = ModuleConstants.TEMP_CACHE_DIR + "/" + APP_RECOVER + "/" + OVERLAY_NAME;
        if (OverlayCompiler.createManifest(OVERLAY_NAME, APP_RECOVER, source)) { postExecute(true); return true; }
        if (OverlayCompiler.runAapt(source, APP_RECOVER)) { postExecute(true); return true; }
        if (OverlayCompiler.zipAlign(ModuleConstants.UNSIGNED_UNALIGNED_DIR + "/" + OVERLAY_NAME + "-unsigned-unaligned.apk")) { postExecute(true); return true; }
        if (OverlayCompiler.apkSigner(ModuleConstants.UNSIGNED_DIR + "/" + OVERLAY_NAME + "-unsigned.apk")) { postExecute(true); return true; }
        postExecute(false);
        return false;
    }
    private static void preExecute() throws IOException {
        symLinkBinaries();
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();
        copyAssets("CompileOnDemand/" + APP_RECOVER + "/" + ASSET_DIR);
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR + "; mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.SIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR + "/" + APP_RECOVER + "/").exec();
        Shell.cmd("mv -f \"" + ModuleConstants.DATA_DIR + "/CompileOnDemand/" + APP_RECOVER + "/" + ASSET_DIR
                + "\" \"" + ModuleConstants.TEMP_CACHE_DIR + "/" + APP_RECOVER + "/" + OVERLAY_NAME + "\"").exec();
        disableOverlay(PREFIX + OVERLAY_NAME + ".overlay");
    }
    private static void postExecute(boolean hasErroredOut) {
        if (hasErroredOut) return;
        String apkName = PREFIX + OVERLAY_NAME + ".apk";
        Shell.cmd("mkdir -p " + ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR,
                "cp -f " + ModuleConstants.SIGNED_DIR + "/" + apkName + " " + ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR + "/" + apkName).exec();
        setPermissions(644, ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR + "/" + apkName);
        mountRW();
        Shell.cmd("cp -f " + ModuleConstants.SIGNED_DIR + "/" + apkName + " " + ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + apkName).exec();
        setPermissions(644, ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + apkName);
        mountRO();
        String overlayName = PREFIX + OVERLAY_NAME + ".overlay";
        disableOverlays(overlayName);
        enableOverlays(overlayName);
    }
}
