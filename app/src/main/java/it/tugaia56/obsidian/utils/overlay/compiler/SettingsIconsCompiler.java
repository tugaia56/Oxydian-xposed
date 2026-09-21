package it.tugaia56.obsidian.utils.overlay.compiler;

import static it.tugaia56.obsidian.utils.Constants.Packages.SETTINGS;
import static it.tugaia56.obsidian.utils.FileUtil.copyAssets;
import static it.tugaia56.obsidian.utils.RootUtil.setPermissions;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRO;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRW;
import static it.tugaia56.obsidian.utils.helper.BinaryInstaller.symLinkBinaries;
import static it.tugaia56.obsidian.utils.helper.Logger.writeLog;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlay;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlays;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.enableOverlays;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.utils.ModuleConstants;

/**
 * Porting di OC's SettingsIconsCompiler — orchestra la build completa dell'overlay icon
 * pack Impostazioni: crea due APK overlay affiancati (uno per com.android.settings, uno
 * per Obsidian stesso — SIP1/SIP2, stesso schema di OC), li compila, allinea, firma,
 * installa e abilita.
 */
public class SettingsIconsCompiler {

    private final static String TAG = SettingsIconsCompiler.class.getSimpleName();
    private static final String PREFIX = "ObsidianComponent";

    private static int mIconSet;

    private static final List<String> mPackages = new ArrayList<>() {{
        add(SETTINGS);
        add(BuildConfig.APPLICATION_ID.replace(".debug", ""));
    }};

    public static boolean buildOverlay(int iconSet, String resources) throws IOException {
        mIconSet = iconSet;

        preExecute();
        moveOverlaysToCache();

        for (int i = 0; i < mPackages.size(); i++) {
            String overlayName = "SIP" + (i + 1);
            String source = ModuleConstants.TEMP_CACHE_DIR + "/" + mPackages.get(i) + "/" + overlayName;

            if (OverlayCompiler.createManifest(overlayName, mPackages.get(i), source)) {
                Log.e(TAG, "Failed to create Manifest for " + overlayName + "! Exiting...");
                postExecute(true);
                return true;
            }

            if (!resources.isEmpty() && writeResources(source, resources)) {
                Log.e(TAG, "Failed to write resource for " + overlayName + "! Exiting...");
                postExecute(true);
                return true;
            }

            if (OverlayCompiler.runAapt(source, mPackages.get(i))) {
                Log.e(TAG, "Failed to build " + overlayName + "! Exiting...");
                postExecute(true);
                return true;
            }

            if (OverlayCompiler.zipAlign(ModuleConstants.UNSIGNED_UNALIGNED_DIR + "/" + overlayName + "-unsigned-unaligned.apk")) {
                Log.e(TAG, "Failed to align " + overlayName + "-unsigned-unaligned.apk! Exiting...");
                postExecute(true);
                return true;
            }

            if (OverlayCompiler.apkSigner(ModuleConstants.UNSIGNED_DIR + "/" + overlayName + "-unsigned.apk")) {
                Log.e(TAG, "Failed to sign " + overlayName + "-unsigned.apk! Exiting...");
                postExecute(true);
                return true;
            }
        }

        postExecute(false);
        return false;
    }

    private static void preExecute() throws IOException {
        symLinkBinaries();

        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand").exec();

        for (String packageName : mPackages) {
            copyAssets("CompileOnDemand/" + packageName + "/ICS" + mIconSet);
        }

        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR + "; mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_OVERLAY_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_UNALIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.UNSIGNED_DIR).exec();
        Shell.cmd("mkdir -p " + ModuleConstants.SIGNED_DIR).exec();

        for (String packageName : mPackages) {
            Shell.cmd("mkdir -p " + ModuleConstants.TEMP_CACHE_DIR + "/" + packageName + "/").exec();
        }

        disableOverlay(PREFIX + "SIP1.overlay");
        disableOverlay(PREFIX + "SIP2.overlay");
    }

    private static void moveOverlaysToCache() {
        for (int i = 0; i < mPackages.size(); i++) {
            Shell.cmd("mv -f \"" + ModuleConstants.DATA_DIR + "/CompileOnDemand/" + mPackages.get(i)
                    + "/ICS" + mIconSet + "\" \"" + ModuleConstants.TEMP_CACHE_DIR + "/" + mPackages.get(i)
                    + "/SIP" + (i + 1) + "\"").exec();
        }
    }

    private static boolean writeResources(String source, String resources) {
        Shell.Result result = Shell.cmd(
                "rm -rf " + source + "/res/values/Obsidian.xml",
                "printf '" + resources + "' > " + source + "/res/values/Obsidian.xml;"
        ).exec();

        if (result.isSuccess()) {
            Log.i(TAG + " - WriteResources", "Successfully written resources for SettingsIcons");
        } else {
            Log.e(TAG + " - WriteResources", "Failed to write resources for SettingsIcons\n" + String.join("\n", result.getOut()));
            writeLog(TAG + " - WriteResources", "Failed to write resources for SettingsIcons", result.getOut());
        }
        return !result.isSuccess();
    }

    private static void postExecute(boolean hasErroredOut) {
        if (hasErroredOut) return;

        // Nota: niente "pm install" — su un dispositivo retail com.android.settings è firmato
        // con la chiave OnePlus, non con la nostra testkey AOSP, quindi l'installazione runtime
        // fallisce sempre per firma non corrispondente ("Scanning Failed"). L'overlay funziona
        // comunque perché è un overlay di PARTIZIONE: copiato sia nel modulo Magisk (persistenza
        // dopo reboot) sia direttamente in /system/product/overlay (per lo scan immediato del
        // PackageManager), che non applica quel controllo di firma. Serve però un riavvio perché
        // PackageManagerService scansiona /system/product/overlay solo all'avvio.
        for (int i = 1; i <= mPackages.size(); i++) {
            String apkName = PREFIX + "SIP" + i + ".apk";
            Shell.cmd("mkdir -p " + ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR,
                    "cp -f " + ModuleConstants.SIGNED_DIR + "/" + apkName + " " + ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR + "/" + apkName).exec();
            setPermissions(644, ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR + "/" + apkName);
        }

        mountRW();
        for (int i = 1; i <= mPackages.size(); i++) {
            String apkName = PREFIX + "SIP" + i + ".apk";
            Shell.cmd("cp -f " + ModuleConstants.SIGNED_DIR + "/" + apkName + " " + ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + apkName).exec();
            setPermissions(644, ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + apkName);
        }
        mountRO();

        // Aggiornamento LIVE: il modulo obsidian_overlayfs monta /product/overlay come overlay con un
        // "upper" su tmpfs (/mnt/obsidian_overlay/upper) e dal boot i file ObsidianComponent*.apk sono
        // bind-mount di quell'upper. La partizione e' in sola lettura (il cp qui sopra spesso fallisce),
        // ma l'upper e' scrivibile: copiandoci l'APK il contenuto nuovo diventa visibile subito,
        // senza riavvio. Se l'upper non esiste (modulo assente) non fa nulla.
        for (int i = 1; i <= mPackages.size(); i++) {
            String apkName = PREFIX + "SIP" + i + ".apk";
            String up = "/mnt/obsidian_overlay/upper/" + apkName;
            Shell.cmd("[ -d /mnt/obsidian_overlay/upper ] && cp -f " + ModuleConstants.SIGNED_DIR + "/" + apkName + " " + up
                    + " && chown 0:0 " + up + " && chmod 0644 " + up
                    + " && (chcon u:object_r:vendor_overlay_file:s0 " + up + " 2>/dev/null || chcon u:object_r:system_file:s0 " + up + " 2>/dev/null)").exec();
        }

        String[] overlayNames = new String[mPackages.size()];
        for (int i = 1; i <= mPackages.size(); i++) overlayNames[i - 1] = PREFIX + "SIP" + i + ".overlay";
        // Se l'overlay era già abilitato da una build precedente, un semplice "enable" può
        // essere un no-op per OMS (nessun cambio di stato) e lasciare l'idmap vecchio in
        // cache anche se il file APK sotto è stato appena sovrascritto con contenuto nuovo.
        // Disabilita e riabilita sempre per forzare la rigenerazione dell'idmap dal file attuale.
        disableOverlays(overlayNames);
        enableOverlays(overlayNames);
        // Impostazioni tiene in cache le risorse dell'overlay: chiuderlo forza il ricaricamento.
        Shell.cmd("am force-stop com.android.settings").exec();
    }
}
