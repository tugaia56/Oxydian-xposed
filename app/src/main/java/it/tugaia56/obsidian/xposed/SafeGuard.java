package it.tugaia56.obsidian.xposed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import it.tugaia56.obsidian.BuildConfig;

/**
 * Protezione contro i cicli di riavvio di SystemUI causati dagli hook del modulo.
 *
 * Livelli (v2): 1 = spente le mod con hook su metodi molto usati; 2 = spente tutte le mod di SystemUI E i ritocchi alle risorse
 * (handleInitPackageResources); 3 = il modulo non fa niente (nemmeno gli hook iniziali di initZygote).
 * Su alcuni telefoni (OnePlus 13 indiano, OxygenOS 16.0.1.304) SystemUI cade ogni 2-3 secondi con un errore di memoria (ART/GC)
 * appena il modulo e' attivo in SystemUI. Qui si contano gli avvii di SystemUI: se partono 6 volte in 40 secondi (un ciclo)
 * si sale di livello, e al livello successivo si spengono piu' hook:
 *   livello 0: tutto acceso
 *   livello 1: spente le mod che agganciano metodi molto usati di Android (View.setVisibility, TextView.setTextColor, ...)
 *   livello 2: spente tutte le mod di SystemUI
 *   livello 3: il modulo non fa niente in SystemUI
 * Il livello resta finche' non si cancella il file (o non cambia la versione del modulo). Il file si legge con
 * "su -c cat /data/user_de/0/com.android.systemui/cache/oxydian_guard.txt" quando il telefono e' stabile.
 */
public final class SafeGuard {
    private SafeGuard() {}

    /** Cambiarlo azzera il livello di tutti (nuova prova). */
    private static final String REV = "g2";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final String FILE = "/data/user_de/0/com.android.systemui/cache/oxydian_guard.txt";
    private static final int LOOP_STARTS = 4;
    private static final long LOOP_WINDOW_MS = 40_000;
    private static final long STABLE_AFTER_MS = 120_000;
    private static final int MAX_LEVEL = 3;

    private static boolean sInited;
    private static boolean sSystemUi;
    private static int sLevel;

    public static synchronized void init() {
        if (sInited) return;
        sInited = true;
        try {
            sSystemUi = SYSTEMUI.equals(cmdline());
            if (!sSystemUi) return;
            File f = new File(FILE);
            String build = BuildConfig.VERSION_NAME + "/" + REV;
            int level = 0;
            List<Long> starts = new ArrayList<>();
            if (f.exists()) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                    String line, savedBuild = "";
                    int savedLevel = 0;
                    List<Long> savedStarts = new ArrayList<>();
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("build=")) savedBuild = line.substring(6).trim();
                        else if (line.startsWith("level=")) savedLevel = Integer.parseInt(line.substring(6).trim());
                        else if (line.startsWith("starts=")) {
                            for (String s : line.substring(7).split(",")) if (!s.trim().isEmpty()) savedStarts.add(Long.parseLong(s.trim()));
                        }
                    }
                    if (build.equals(savedBuild)) { level = savedLevel; starts = savedStarts; }
                } catch (Throwable ignored) {}
            }
            long now = System.currentTimeMillis();
            List<Long> recent = new ArrayList<>();
            for (long t : starts) if (now - t < LOOP_WINDOW_MS && now >= t) recent.add(t);
            recent.add(now);
            if (recent.size() >= LOOP_STARTS && level < MAX_LEVEL) {
                level++;
                recent.clear();
                recent.add(now);
                XposedBridge.log("[ Obsidian ] SafeGuard: SystemUI sta riavviando in ciclo, livello di protezione -> " + level);
            }
            sLevel = level;
            write(f, build, level, recent, "start");
            XposedBridge.log("[ Obsidian ] SafeGuard: SystemUI livello " + level + " (avvii recenti " + recent.size() + ")");
            if (level > 0) {
                final int lv = level;
                final String b = build;
                final List<Long> rc = recent;
                Thread t = new Thread(() -> {
                    try { Thread.sleep(STABLE_AFTER_MS); } catch (InterruptedException ignored) {}
                    write(new File(FILE), b, lv, new ArrayList<>(), "stabile al livello " + lv);
                }, "ObsidianGuard");
                t.setDaemon(true);
                t.start();
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SafeGuard.init failed: " + t);
        }
    }

    private static void write(File f, String build, int level, List<Long> starts, String note) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("build=").append(build).append('\n');
            sb.append("level=").append(level).append('\n');
            sb.append("starts=");
            for (int i = 0; i < starts.size(); i++) { if (i > 0) sb.append(','); sb.append(starts.get(i)); }
            sb.append('\n');
            sb.append("note=").append(note).append(" @").append(System.currentTimeMillis()).append('\n');
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(sb.toString().getBytes()); }
        } catch (Throwable ignored) {}
    }

    private static String cmdline() {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream("/proc/self/cmdline")))) {
            String s = r.readLine();
            if (s == null) return "";
            int z = s.indexOf('\0');
            return (z >= 0 ? s.substring(0, z) : s).trim();
        } catch (Throwable t) { return ""; }
    }

    /** True se siamo nel processo di SystemUI. */
    public static boolean isSystemUi() { init(); return sSystemUi; }

    /** Livello di protezione (0 fuori da SystemUI). */
    public static int level() { init(); return sSystemUi ? sLevel : 0; }

    /** Mod con hook su metodi molto usati di Android: si spengono dal livello 1. */
    public static boolean isHotMod(Class<?> mod) {
        String n = mod.getSimpleName();
        return n.equals("SimIconHideMod") || n.equals("LockScreenMiscMods") || n.equals("BatteryStyleManager")
                || n.equals("MiscMods") || n.equals("StatusbarClock") || n.equals("StatusbarIcons")
                || n.equals("StatusBarTintMod") || n.equals("PowerMenuItalianMod") || n.equals("PinKeyPressedMod")
                || n.equals("NotificationMods") || n.equals("CouiSwitchMod") || n.equals("SwitchCompatMod");
    }
}
