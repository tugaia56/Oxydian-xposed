package it.tugaia56.obsidian.utils.overlay.compiler;

import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.utils.FileUtil.copyAssets;
import static it.tugaia56.obsidian.utils.RootUtil.setPermissions;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRO;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRW;
import static it.tugaia56.obsidian.utils.helper.BinaryInstaller.symLinkBinaries;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlay;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlays;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.enableOverlays;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;

import it.tugaia56.obsidian.utils.ModuleConstants;

/**
 * Compila l'overlay "Pack Icone Barra di Navigazione": un solo overlay (NIP1) per
 * com.android.systemui, contenuto scelto tra 9 pack (asset NB1..NB9, stessi file reali di
 * OC — solo res/drawable/ic_sysbar_{back,home,recent}.xml, nessuna variante colore/forma).
 * Stesso schema di SystemUIThemeCompiler, parametrizzato sulla cartella asset sorgente:
 * cambiare pack ricompila e sovrascrive lo stesso overlay NIP1 con contenuto diverso.
 */
public class NavbarIconsCompiler {

    private final static String TAG = NavbarIconsCompiler.class.getSimpleName();
    private static final String PREFIX = "ObsidianComponent";
    private static final String OVERLAY_NAME = "NIP1";

    public static boolean buildOverlay(int pack) throws IOException {
        String assetDir = "NB" + pack;
        preExecute(assetDir);

        String source = ModuleConstants.TEMP_CACHE_DIR + "/" + SYSTEM_UI + "/" + OVERLAY_NAME;

        if (OverlayCompiler.createManifest(OVERLAY_NAME, SYSTEM_UI, source)) {
            Log.e(TAG, "Failed to create Manifest for " + OVERLAY_NAME + "! Exiting...");
            postExecute(true);
            return true;
        }

        if (OverlayCompiler.runAapt(source, SYSTEM_UI)) {
            Log.e(TAG, "Failed to build " + OVERLAY_NAME + "! Exiting...");
            postExecute(true);
            return true;
        }

        if (OverlayCompiler.zipAlign(ModuleConstants.UNSIGNED_UNALIGNED_DIR + "/" + OVERLAY_NAME + "-unsigned-unaligned.apk")) {
            Log.e(TAG, "Failed to align " + OVERLAY_NAME + "-unsigned-unaligned.apk! Exiting...");
            postExecute(true);
            return true;
        }

        if (OverlayCompiler.apkSigner(ModuleConstants.UNSIGNED_DIR + "/" + OVERLAY_NAME + "-unsigned.apk")) {
            Log.e(TAG, "Failed to sign " + OVERLAY_NAME + "-unsigned.apk! Exiting...");
            postExecute(true);
            return true;
        }

        postExecute(false);
        return false;
    }

    private static void preExecute(String assetDir) throws IOException {
        symLinkBinaries();

        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();

        copyAssets("CompileOnDemand/" + SYSTEM_UI + "/" + assetDir);

        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR + "; mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.SIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR + "/" + SYSTEM_UI + "/").exec();

        Shell.cmd("mv -f \"" + ModuleConstants.DATA_DIR + "/CompileOnDemand/" + SYSTEM_UI + "/" + assetDir
                + "\" \"" + ModuleConstants.TEMP_CACHE_DIR + "/" + SYSTEM_UI + "/" + OVERLAY_NAME + "\"").exec();

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
