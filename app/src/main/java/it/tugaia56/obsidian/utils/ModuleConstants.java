package it.tugaia56.obsidian.utils;

import android.os.Environment;

import it.tugaia56.obsidian.Obsidian;

/**
 * MODULE_DIR/OVERLAY_DIR (sopra) restano quelli usati da FabricatedUtil per gli overlay
 * "fabricated" (colore/dimensione — cmd overlay fabricate). Le costanti sotto servono al
 * sistema separato di compilazione overlay APK reali (icon pack Impostazioni — porting da
 * OC: aapt2/zipalign + firma custom + cmd overlay install/enable), che deve rispecchiare il
 * path /system/product/overlay reale per la persistenza via modulo Magisk/KSU.
 */
public final class ModuleConstants {
    public static final String MODULE_DIR  = "/data/adb/modules/Obsidian";
    public static final String OVERLAY_DIR = MODULE_DIR + "/overlays";

    // ── Compilatore overlay APK (icon pack Impostazioni) ─────────────────────────
    // 2026-09-09: era "/system/product/overlay" — su questo device /product è una partizione
    // SEPARATA e di sola lettura (EROFS, confermato via `mount`; /system/product è solo un
    // symlink a /product). Magisk faceva da solo il redirect "system/product/" → "/product/"
    // dentro i moduli (funzione nota come partition mapping); KernelSU-Next NON lo fa —
    // confermato dal vivo: il modulo restava con mount:true ma le APK non comparivano mai in
    // "cmd overlay list" per nessuno dei 3 pack (Settings/SystemUI/Launcher, tutti bloccati su
    // "Compilato - riavvia il dispositivo"). Path top-level "product/" (non annidato sotto
    // "system/") è la convenzione universale Magisk/KSU per le partizioni separate, senza
    // bisogno di redirect furbo.
    public static final String SYSTEM_OVERLAY_DIR       = "/product/overlay";
    public static final String MODULE_SYSTEM_OVERLAY_DIR = MODULE_DIR + "/product/overlay";
    public static final String DATA_DIR = Obsidian.getAppContext().getFilesDir().getAbsolutePath();
    public static final String BIN_DIR  = Obsidian.getAppContext().getDataDir() + "/bin";
    public static final String TEMP_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.obsidian";
    public static final String TEMP_OVERLAY_DIR      = TEMP_DIR + "/overlays";
    public static final String TEMP_CACHE_DIR        = TEMP_OVERLAY_DIR + "/cache";
    public static final String UNSIGNED_UNALIGNED_DIR = TEMP_OVERLAY_DIR + "/unsigned_unaligned";
    public static final String UNSIGNED_DIR          = TEMP_OVERLAY_DIR + "/unsigned";
    public static final String SIGNED_DIR            = TEMP_OVERLAY_DIR + "/signed";

    // Overlay metadata (meta-data keys nel manifest dell'overlay generato)
    public static final String METADATA_OVERLAY_PARENT = "OVERLAY_PARENT";
    public static final String METADATA_OVERLAY_TARGET  = "OVERLAY_TARGET";
    public static final String METADATA_THEME_VERSION   = "THEME_VERSION";
    public static final String METADATA_THEME_CATEGORY  = "THEME_CATEGORY";
    public static final String OVERLAY_CATEGORY_PREFIX  = "it.tugaia56.obsidian.category.";

    public static final String FRAMEWORK_DIR = "/system/framework/framework-res.apk";

    private ModuleConstants() {}
}
