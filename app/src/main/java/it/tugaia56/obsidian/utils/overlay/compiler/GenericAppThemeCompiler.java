package it.tugaia56.obsidian.utils.overlay.compiler;

import static it.tugaia56.obsidian.utils.FileUtil.copyAssets;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRO;
import static it.tugaia56.obsidian.utils.SystemUtil.mountRW;
import static it.tugaia56.obsidian.utils.helper.BinaryInstaller.symLinkBinaries;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.disableOverlays;
import static it.tugaia56.obsidian.utils.overlay.OverlayUtil.enableOverlays;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.util.List;

import it.tugaia56.obsidian.utils.ModuleConstants;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/** Compilatore overlay unico per le app Google/utente: i sorgenti stanno in
 *  assets/CompileOnDemand/&lt;pacchetto&gt;/APP/res, il nome dell'overlay deriva dal pacchetto.
 *
 *  Per compilarne molte insieme: beginBatch() una volta, buildInBatch(pkg) per ognuna,
 *  endBatch(daAggiornare) una volta. Così il rimontaggio del sistema, i link ad aapt2 e le
 *  chiamate al gestore overlay (lente) si fanno una volta sola e non per ogni app. */
public class GenericAppThemeCompiler {
    private static final String PREFIX = "ObsidianComponent";
    private static final String ASSET_DIR = "APP";

    public static String overlayName(String targetPackage) {
        // "apk" nel nome romperebbe CompilerUtil.getOverlayName (regex ".apk")
        return "GApp_" + targetPackage.replace('.', '_').replace("apk", "ap_k");
    }

    public static String overlayPackage(String targetPackage) {
        return PREFIX + overlayName(targetPackage) + ".overlay";
    }

    // ── Giro completo ────────────────────────────────────────────────────────

    public static void beginBatch() {
        symLinkBinaries();
        Shell.cmd("rm -rf " + ModuleConstants.TEMP_OVERLAY_DIR,
                "rm -rf " + ModuleConstants.DATA_DIR + "/CompileOnDemand",
                "mkdir -p " + ModuleConstants.TEMP_CACHE_DIR + " " + ModuleConstants.UNSIGNED_UNALIGNED_DIR
                        + " " + ModuleConstants.UNSIGNED_DIR + " " + ModuleConstants.SIGNED_DIR
                        + " " + ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR).exec();
        mountRW();
    }

    /** @return true se la compilazione è fallita */
    public static boolean buildInBatch(String targetPackage) throws IOException {
        String name = overlayName(targetPackage);
        String cacheRoot = ModuleConstants.TEMP_CACHE_DIR + "/" + targetPackage;
        String source = cacheRoot + "/" + name;

        copyAssets("CompileOnDemand/" + targetPackage + "/" + ASSET_DIR);
        // @*android:color/accent_material_dark fuori dai processi hookati resta il teal di sistema:
        // si scrive l'accento reale (come per il pack icone di Impostazioni). Il prefisso "@*"
        // può comparire doppio in alcuni sorgenti Substratum.
        String accent = String.format("#%08X", ObsidianTheme.accentColor());
        String moved = ModuleConstants.DATA_DIR + "/CompileOnDemand/" + targetPackage + "/" + ASSET_DIR;
        Shell.cmd("mkdir -p \"" + cacheRoot + "\"",
                "mv -f \"" + moved + "\" \"" + source + "\"",
                "find \"" + source + "/res\" -type f -name '*.xml'"
                        + " -exec sed -i -E 's|(@\\*)+android:color/accent_material_dark|" + accent + "|g' {} +").exec();

        if (OverlayCompiler.createManifest(name, targetPackage, source)) return true;
        if (OverlayCompiler.runAapt(source, targetPackage)) return true;
        if (OverlayCompiler.zipAlign(ModuleConstants.UNSIGNED_UNALIGNED_DIR + "/" + name + "-unsigned-unaligned.apk")) return true;
        if (OverlayCompiler.apkSigner(ModuleConstants.UNSIGNED_DIR + "/" + name + "-unsigned.apk")) return true;

        String apkName = PREFIX + name + ".apk";
        String signed = ModuleConstants.SIGNED_DIR + "/" + apkName;
        String inModule = ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR + "/" + apkName;
        String inSystem = ModuleConstants.SYSTEM_OVERLAY_DIR + "/" + apkName;
        Shell.cmd("cp -f " + signed + " " + inModule,
                "chmod 644 " + inModule,
                "cp -f " + signed + " " + inSystem,
                "chmod 644 " + inSystem).exec();
        return false;
    }

    /** @param refresh pacchetti già abilitati prima: vanno spenti e riaccesi perché il sistema
     *  rilegga l'APK nuovo. Quelli mai registrati non si possono abilitare finché non si
     *  riavvia, quindi non si tocca il gestore overlay per loro. */
    public static void endBatch(List<String> refresh) {
        mountRO();
        if (refresh == null || refresh.isEmpty()) return;
        String[] names = new String[refresh.size()];
        for (int i = 0; i < names.length; i++) names[i] = overlayPackage(refresh.get(i));
        disableOverlays(names);
        enableOverlays(names);
    }

    // ── Una sola app (non usato dall'elenco, comodo per prove) ───────────────

    public static boolean buildOverlay(String targetPackage) throws IOException {
        beginBatch();
        boolean failed = true;
        try {
            failed = buildInBatch(targetPackage);
        } finally {
            endBatch(List.of(targetPackage));
        }
        return failed;
    }

    /** Toglie dal telefono gli APK degli overlay spenti: disabilitarli non basta, Android a ogni
     *  avvio rilegge e prepara comunque tutti gli APK presenti in /product/overlay (circa 90
     *  temi rallentano molto il boot). */
    public static void removeApks(List<String> targetPackages) {
        if (targetPackages == null || targetPackages.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (String pkg : targetPackages) {
            String apk = PREFIX + overlayName(pkg) + ".apk";
            sb.append("rm -f ").append(ModuleConstants.MODULE_SYSTEM_OVERLAY_DIR).append('/').append(apk)
              .append(' ').append(ModuleConstants.SYSTEM_OVERLAY_DIR).append('/').append(apk).append("; ");
        }
        mountRW();
        Shell.cmd(sb.toString().trim()).exec();
        mountRO();
    }

    public static void disable(String targetPackage) {
        disableOverlays(overlayPackage(targetPackage));
    }

    public static void disable(List<String> targetPackages) {
        if (targetPackages.isEmpty()) return;
        String[] names = new String[targetPackages.size()];
        for (int i = 0; i < names.length; i++) names[i] = overlayPackage(targetPackages.get(i));
        disableOverlays(names);
    }
}
