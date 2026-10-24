package it.tugaia56.obsidian.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Data e ora nel nome del file di backup (scelte nelle impostazioni, niente ":" cosi' va bene su ogni file system). */
public final class BackupName {

    public static final String PREF_DATE = "backup_name_date";
    public static final String PREF_TIME = "backup_name_time";

    public static final String[] DATES = {"dd-MM-yyyy", "dd-MM-yy", "yyyy-MM-dd", "yyyyMMdd"};
    /** "" = solo data. */
    public static final String[] TIMES = {"HH:mm", "HH:mm:ss", "HHmm", "hh:mm a", ""};

    private BackupName() {}

    /** Nel nome del file i due punti normali non sono ammessi (Windows): si usano quelli gemelli, identici alla vista. */
    public static String fileSafe(String s) { return s.replace(':', '∶'); }

    public static String date() {
        return ObsidianPrefs.getString(PREF_DATE, Locale.getDefault().getLanguage().equals("it") ? "dd-MM-yyyy" : "yyyy-MM-dd");
    }

    public static String time() {
        String t = ObsidianPrefs.getString(PREF_TIME, TIMES[0]);
        switch (t) {   // valori delle prime versioni, con il trattino
            case "HH-mm": return "HH:mm";
            case "HH-mm-ss": return "HH:mm:ss";
            case "hh-mm-a": return "hh:mm a";
            default: return t;
        }
    }

    /** Esempio con l'ora di adesso; "" per il formato "nessuna ora" non esiste per la data. */
    public static String example(String pattern) {
        if (pattern == null || pattern.isEmpty()) return "";
        return fileSafe(new SimpleDateFormat(pattern, Locale.US).format(new Date()));
    }

    /** Esempio di adesso + spiegazione del formato (giorno-mese-anno ecc.). */
    public static String label(android.content.Context c, String p) {
        int d;
        switch (p) {
            case "dd-MM-yyyy": d = it.tugaia56.obsidian.R.string.bf_dmy; break;
            case "dd-MM-yy": d = it.tugaia56.obsidian.R.string.bf_dmy_short; break;
            case "yyyy-MM-dd": d = it.tugaia56.obsidian.R.string.bf_ymd; break;
            case "yyyyMMdd": d = it.tugaia56.obsidian.R.string.bf_ymd_compact; break;
            case "HH:mm": d = it.tugaia56.obsidian.R.string.bf_hm; break;
            case "HH:mm:ss": d = it.tugaia56.obsidian.R.string.bf_hms; break;
            case "HHmm": d = it.tugaia56.obsidian.R.string.bf_hm_compact; break;
            case "hh:mm a": d = it.tugaia56.obsidian.R.string.bf_hm12; break;
            default: return p.isEmpty() ? c.getString(it.tugaia56.obsidian.R.string.settings_backup_time_none) : example(p);
        }
        return example(p) + "\n(" + c.getString(d) + ")";
    }

    public static String stamp() {
        Date now = new Date();
        String s = new SimpleDateFormat(date(), Locale.US).format(now);
        String t = time();
        if (!t.isEmpty()) s += "_" + fileSafe(new SimpleDateFormat(t, Locale.US).format(now));
        return s;
    }
}
