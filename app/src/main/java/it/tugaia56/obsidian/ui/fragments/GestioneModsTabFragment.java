package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;

/**
 * Home Mods — le voci esplicitamente richieste, in ordine:
 *  1. Barra di stato
 *  2. Pannello Impostazioni Rapide (QuickSettingsFragment — reale OC quick_settings_mods.xml;
 *     "Intestazione Impostazioni Rapide"/QsFragment è annidata qui dentro, non più al livello
 *     principale)
 *  3. Schermata di Blocco
 *  4. Always On Display
 *  5. Pannello Volume
 *  6. Launcher
 *  7. Varie
 *  8. Stile Barra di navigazione (NavbarStyleFragment — in fondo, uso occasionale)
 *
 * Tastierino PIN e Barra Progresso Circolare sono stati spostati in Home DST →
 * Dark Shadow Theme.
 */
public class GestioneModsTabFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 12, 0, 24);
        rv.setClipToPadding(false);
        LayoutAnimationController anim = AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_slide_up);
        rv.setLayoutAnimation(anim);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = (RecyclerView) view;

        // Header (icon + OBSIDIAN + tagline) is now in MainActivity's home_header_container.

        List<NavAdapter.NavItem> items = List.of(

                new NavAdapter.NavItem(
                        R.drawable.ic_notifications,
                        getString(R.string.nav_statusbar),
                        getString(R.string.nav_statusbar_summary),
                        () -> navigate(new StatusbarFragment(), getString(R.string.nav_statusbar)),
                        0xFF7C4DFF, // purple
                        "orologio", "clock", "calendario", "luminosità", "brightness",
                        "bluetooth", "notifiche", "notifications"),

                new NavAdapter.NavItem(
                        R.drawable.ic_qs,
                        getString(R.string.nav_quick_settings_panel),
                        getString(R.string.nav_quick_settings_panel_summary),
                        () -> navigate(new QuickSettingsFragment(), getString(R.string.nav_quick_settings_panel)),
                        0xFFE91E63, // pink
                        "qs", "tile", "riquadri", "trasparenza", "transparency", "sfocatura", "blur",
                        "apertura rapida", "pulldown", "widget", "dispositivo"),

                new NavAdapter.NavItem(
                        R.drawable.ic_mods_tools,
                        getString(R.string.nav_lock_screen),
                        getString(R.string.nav_lock_screen_summary),
                        () -> navigate(new LockScreenFragment(),
                                getString(R.string.nav_lock_screen)),
                        0xFF00BCD4, // cyan
                        "schermata di blocco", "lock screen", "codice", "pin",
                        "password", "numeri", "numbers", "puntini", "dots",
                        "orologio", "clock", "impronta", "fingerprint", "pulsanti", "buttons"),

                new NavAdapter.NavItem(
                        R.drawable.ic_clock,
                        getString(R.string.nav_aod),
                        getString(R.string.nav_aod_summary),
                        () -> navigate(new AodFragment(),
                                getString(R.string.nav_aod)),
                        0xFFFF5722, // deep orange
                        "aod", "always-on display", "orologio", "clock",
                        "meteo", "weather", "bordi", "edge lighting"),

                new NavAdapter.NavItem(
                        R.drawable.ic_sysui_volume,
                        getString(R.string.dark_shadow_preset_rvd),
                        getString(R.string.nav_volume_icon_summary),
                        () -> navigate(new VolumeStyleFragment(),
                                getString(R.string.dark_shadow_preset_rvd)),
                        0xFF536DFE, // indigo accent (più brillante)
                        "volume", "suono", "audio", "slider", "timeout",
                        "posizione", "position", "colore", "color"),

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_misc_power_menu),
                        getString(R.string.nav_misc_power_menu_summary),
                        () -> navigate(new PowerMenuFragment(),
                                getString(R.string.nav_misc_power_menu)),
                        0xFFFF1744, // red accent
                        "power menu", "accensione", "spegnimento", "menù accensione", "riavvio"),

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_misc),
                        getString(R.string.nav_misc_summary),
                        () -> navigate(new MiscFragment(),
                                getString(R.string.nav_misc)),
                        0xFFFF6E40, // deep orange accent (più brillante)
                        "varie", "misc", "rotazione", "rotation", "usb",
                        "accensione", "power menu", "impostazioni", "settings"),

                new NavAdapter.NavItem(
                        R.drawable.ic_recents,
                        getString(R.string.nav_launcher),
                        getString(R.string.nav_launcher_summary),
                        () -> navigate(new LauncherFragment(),
                                getString(R.string.nav_launcher)),
                        0xFF009688, // teal
                        "launcher", "recenti", "recents", "task switcher", "multitasking"),

                new NavAdapter.NavItem(
                        R.drawable.ic_navbar_gesture,
                        getString(R.string.nav_navbar_style),
                        getString(R.string.nav_navbar_style_summary),
                        () -> navigate(new NavbarStyleFragment(), getString(R.string.nav_navbar_style)),
                        0xFFFFD600, // vivid yellow
                        "navbar", "barra di navigazione", "navigation bar", "gesture",
                        "pillola", "pill", "indietro", "back")
        );

        NavAdapter navAdapter = new NavAdapter(items);
        rv.setAdapter(navAdapter);
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
