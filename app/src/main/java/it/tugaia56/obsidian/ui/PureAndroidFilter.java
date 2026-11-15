package it.tugaia56.obsidian.ui;

import android.content.Context;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.utils.DeviceInfo;

/**
 * Su Android puro (RisingOS, crDroid...) il modulo carica solo poche mod (vedi ModPacks): le pagine
 * delle mod di OxygenOS (Launcher, AOD, pannello, schermata di blocco, volume, impronta) non
 * farebbero niente. Qui si nascondono, nella home, nelle schede e nella ricerca.
 */
public final class PureAndroidFilter {
    private PureAndroidFilter() {}

    /** Titoli delle voci di navigazione da nascondere. */
    private static final int[] HIDDEN_TITLES = {
            R.string.nav_launcher, R.string.nav_aod, R.string.nav_quick_settings,
            R.string.nav_quick_settings_panel, R.string.nav_lock_clock_new, R.string.nav_lock_weather,
            R.string.nav_lock_widgets, R.string.nav_lock_screen_options,
            R.string.nav_misc_power_menu, R.string.nav_battery_icons, R.string.dark_shadow_preset_rvd /* volume */
    };

    /** Prefissi dei nomi delle pagine (Fragment) da nascondere. */
    private static final String[] HIDDEN_PAGE_PREFIXES = {
            "Launcher", "Aod", "Qs", "Clock", "PowerMenu", "Battery", "QuickSettings", "LockScreenOptions", "Lockscreen",
            "FingerprintPreset", "Volume", "DeviceWidget"
    };

    public static boolean active() {
        return DeviceInfo.isPureAndroid();
    }

    public static List<NavAdapter.NavItem> filter(Context ctx, List<NavAdapter.NavItem> items) {
        if (!active()) return items;
        List<String> hidden = new ArrayList<>();
        for (int id : HIDDEN_TITLES) hidden.add(ctx.getString(id));
        List<NavAdapter.NavItem> out = new ArrayList<>();
        for (NavAdapter.NavItem it : items) if (!hidden.contains(it.title)) out.add(it);
        return out;
    }

    /** Per la ricerca: true se la voce porta a una pagina nascosta su Android puro. */
    public static boolean hiddenPage(Supplier<Fragment> page) {
        if (!active() || page == null) return false;
        try {
            String n = page.get().getClass().getSimpleName();
            for (String p : HIDDEN_PAGE_PREFIXES) if (n.startsWith(p)) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
