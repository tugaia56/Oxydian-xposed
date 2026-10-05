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
 * Home DST — solo le 5 voci elencate dall'utente, nient'altro:
 *  1. Dark Shadow Theme (Preset Sfondo, Preset Accento, Stile Impostazioni)
 *  2. Stili notifica e Toast (Stili Notifica, Notifica Toast, Stili Finestre Dialogo,
 *     Raggio angolo notifiche)
 *  3. Stile Icone Impostazioni (PUI v1/v2/v3, OOS — solo anteprima)
 *  4. Stile icone Segnale (Icone Segnale WI-FI/Mobile (OBS))
 *  5. Stile icone Batteria (OBS)
 *
 * Tutto il resto (Schermata di Blocco, AOD, Pannello Volume, Barra Progresso Circolare,
 * Impostazioni rapide, Tastierino PIN, Barra di Stato) è stato spostato in Home Mods
 * (GestioneModsTabFragment).
 */
public class DstTabFragment extends Fragment {

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
                        R.drawable.ic_drawing,
                        getString(R.string.nav_oxytheme),
                        getString(R.string.nav_oxytheme_summary),
                        () -> it.tugaia56.obsidian.utils.OxydianThemeLauncher.open(requireContext()),
                        0xFF7C4DFF, // purple
                        "substratum", "tema", "theme", "accento", "accent",
                        "sfondo", "background", "colore", "color", "impostazioni", "settings",
                        "pin", "icone", "icons", "overlay"),

                new NavAdapter.NavItem(
                        R.drawable.ic_ui_styles,
                        getString(R.string.nav_theme_style),
                        getString(R.string.nav_theme_style_summary),
                        () -> navigate(new ThemeStyleFragment(),
                                getString(R.string.nav_theme_style)),
                        0xFF00BCD4, // cyan
                        "notifiche", "notifications", "toast", "angoli", "corners",
                        "radius", "dialogo", "dialog", "stile", "style"),

                new NavAdapter.NavItem(
                        R.drawable.ic_battery,
                        getString(R.string.nav_battery_icons),
                        getString(R.string.nav_battery_icons_summary),
                        () -> navigate(new BatteryIconFragment(),
                                getString(R.string.nav_battery_icons)),
                        0xFFFF9800, // orange
                        "batteria", "battery", "icone", "icons")
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
