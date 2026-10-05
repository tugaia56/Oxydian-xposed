package it.tugaia56.obsidian.utils;

import android.view.View;
import android.widget.TextView;

import it.tugaia56.obsidian.R;

public final class DeviceInfo {
    private DeviceInfo() {}

    /** "OOS 16 · OnePlus 12" costruito dalle proprietà reali del telefono — era scritto a
     *  mano nel layout (OP12 dello sviluppatore), su un OnePlus 11 mostrava "OnePlus 12". */
    public static String tagline() {
        String market = sysProp("ro.vendor.oplus.market.name");
        if (market.isEmpty()) market = sysProp("ro.product.marketname");
        if (market.isEmpty()) market = android.os.Build.MODEL;

        String os = "OxygenOS";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)")
                .matcher(sysProp("ro.build.version.oplusrom"));
        if (m.find()) os = "OOS " + m.group(1);
        return os + " · " + market;
    }

    /** Riepilogo per il log di diagnostica: modello, build ROM, Android, versione Oxydian. */
    public static String diagnosticSummary() {
        String nl = System.lineSeparator();
        return "Dispositivo: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " (" + tagline() + ")" + nl
                + "Build ROM: " + sysProp("ro.build.display.id") + nl
                + "OxygenOS/ColorOS: " + sysProp("ro.build.version.oplusrom")
                + "  Android " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")" + nl
                + "Oxydian: v" + it.tugaia56.obsidian.BuildConfig.VERSION_NAME
                + " (" + it.tugaia56.obsidian.BuildConfig.VERSION_CODE + ")" + nl;
    }

    /** Diagnosi root per il log: la shell di Oxydian è davvero root? Servono i comandi usati da
     *  riavvio SystemUI, blocco schermo e screenshot. Da chiamare fuori dal thread UI. */
    public static String rootDiagnostics() {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("Root: shell root=").append(com.topjohnwu.superuser.Shell.getShell().isRoot())
                    .append(" (accesso concesso=").append(com.topjohnwu.superuser.Shell.isAppGrantedRoot()).append(")").append(nl);
            sb.append("  uid: ").append(shellLine("id -u")).append("  su: ").append(shellLine("su -v")).append(nl);
            sb.append("  comandi: ");
            for (String c : new String[]{"killall", "pkill", "pidof", "input", "screencap"})
                sb.append(c).append('=').append(shellLine("command -v " + c).isEmpty() ? "NO" : "ok").append(' ');
            sb.append(nl);
            sb.append("  prova killall inesistente: ").append(shellLine("killall oxydian_nonexistent_probe 2>&1; echo rc=$?")).append(nl);
        } catch (Throwable t) {
            sb.append("Root: errore diagnosi ").append(t.getClass().getSimpleName()).append(nl);
        }
        return sb.toString();
    }

    private static String shellLine(String cmd) {
        try {
            java.util.List<String> out = com.topjohnwu.superuser.Shell.cmd(cmd).exec().getOut();
            return out.isEmpty() ? "" : String.join(" | ", out).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /** Imposta la riga sotto al nome app nell'header home (item_home_header). */
    public static void applyTagline(View header) {
        TextView tv = header.findViewById(R.id.headerTagline);
        if (tv != null) tv.setText(tagline());
    }

    private static String sysProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : v.toString().trim();
        } catch (Throwable t) {
            return "";
        }
    }
}
