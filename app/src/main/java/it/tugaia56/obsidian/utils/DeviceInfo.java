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
