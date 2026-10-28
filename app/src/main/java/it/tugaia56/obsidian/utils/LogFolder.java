package it.tugaia56.obsidian.utils;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Cartella "Log Oxydian" nella memoria interna: tutti i log che l'utente puo' mandare per una segnalazione finiscono qui,
 * con nome "<tipo>_<data>_<ora>.<estensione>" (bootcap_ per il log di avvio, modhealth_ per lo stato dei mod, xposed_ per i
 * messaggi di Oxydian). Si tengono gli ultimi 10 di ogni tipo. Scrive con il root: la cartella e' fuori dalle aree
 * consentite alle app normali.
 */
public final class LogFolder {

    public static final String DIR = "/sdcard/Log Oxydian";
    private static final int KEEP = 10;

    private LogFolder() {}

    /** Percorso di un nuovo file di log di quel tipo. */
    public static String newPath(String type, String ext) {
        return DIR + "/" + type + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "." + ext;
    }

    /** Toglie i file piu' vecchi dello stesso tipo, lasciando gli ultimi KEEP. */
    private static String pruneCmd(String type) {
        return "ls -t \"" + DIR + "\"/" + type + "_* 2>/dev/null | tail -n +" + (KEEP + 1)
                + " | while read -r f; do rm -f \"$f\"; done";
    }

    /** Salva un testo: ritorna il percorso del file, o null se non e' riuscito. */
    public static String saveText(Context ctx, String type, String ext, String text) {
        try {
            File tmp = new File(ctx.getCacheDir(), "logtmp." + ext);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            String dest = newPath(type, ext);
            boolean ok = Shell.cmd("mkdir -p \"" + DIR + "\"", "cp \"" + tmp.getAbsolutePath() + "\" \"" + dest + "\"",
                    pruneCmd(type)).exec().isSuccess();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return ok ? dest : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Messaggi di Oxydian nel log di LSPosed (quelli di "Log aggiuntivi" e le segnalazioni dei mod), con in testa un
     * riepilogo del telefono. Ritorna il percorso del file, o null.
     */
    public static String saveXposedLog(Context ctx, String header) {
        try {
            File tmp = new File(ctx.getCacheDir(), "logtmp_head.txt");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(header.getBytes(StandardCharsets.UTF_8));
            }
            String dest = newPath("xposed", "log");
            String src = "/data/adb/lspd/log.old/modules_*.log /data/adb/lspd/log/modules_*.log";   // dal piu' vecchio al piu' recente
            boolean ok = Shell.cmd("mkdir -p \"" + DIR + "\"",
                    "{ cat \"" + tmp.getAbsolutePath() + "\"; echo; echo '=== LSPosed: messaggi di Oxydian (ultime 20000 righe) ==='; "
                            + "cat " + src + " 2>/dev/null | grep -a -E 'Obsidian|oxydian' | tail -n 20000; } > \"" + dest + "\"",
                    pruneCmd("xposed")).exec().isSuccess();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return ok ? dest : null;
        } catch (Exception e) {
            return null;
        }
    }
}
