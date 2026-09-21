package it.tugaia56.obsidian.utils.overlay.compiler;

import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;
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
 * Compila l'overlay "Stile Impostazioni OOS": bundle fisso di risorse (colori, drawable,
 * stili — incluse le animazioni impronta) estratto dal tema Substratum type3_OxygenOS_16
 * dell'utente, che tema com.android.settings per intero. A differenza di
 * SettingsIconsCompiler non ci sono varianti/opzioni né un secondo pacchetto da temare
 * (Obsidian stesso non è coinvolto qui): un solo overlay statico, contenuto già completo
 * negli asset, stesso schema aapt2/zipalign/firma.
 */
public class SettingsThemeCompiler {

    private final static String TAG = SettingsThemeCompiler.class.getSimpleName();
    private static final String PREFIX = "ObsidianComponent";
    private static final String OVERLAY_NAME = "SST1";
    private static final String ASSET_DIR = "SST";

    public static boolean buildOverlay() throws IOException {
        preExecute();

        String source = ModuleConstants.TEMP_CACHE_DIR + "/" + SETTINGS + "/" + OVERLAY_NAME;

        if (OverlayCompiler.createManifest(OVERLAY_NAME, SETTINGS, source)) {
            Log.e(TAG, "Failed to create Manifest for " + OVERLAY_NAME + "! Exiting...");
            postExecute(true);
            return true;
        }

        if (OverlayCompiler.runAapt(source, SETTINGS)) {
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

    private static void preExecute() throws IOException {
        symLinkBinaries();

        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();

        copyAssets("CompileOnDemand/" + SETTINGS + "/" + ASSET_DIR);

        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR + "; mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.SIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR + "/" + SETTINGS + "/").exec();

        Shell.cmd("mv -f \"" + ModuleConstants.DATA_DIR + "/CompileOnDemand/" + SETTINGS + "/" + ASSET_DIR
                + "\" \"" + ModuleConstants.TEMP_CACHE_DIR + "/" + SETTINGS + "/" + OVERLAY_NAME + "\"").exec();

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
        // Vedi SettingsIconsCompiler: disabilita+riabilita sempre per forzare la
        // rigenerazione dell'idmap dal file appena sovrascritto.
        disableOverlays(overlayName);
        enableOverlays(overlayName);
    }
}
