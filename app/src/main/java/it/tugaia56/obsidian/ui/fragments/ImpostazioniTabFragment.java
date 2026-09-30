package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
 * Tab 3 — Impostazioni hub, mirrors OC's own_settings.xml categories (Generale,
 * Backup/Varie, Aggiornamento, Info). Generale/Aggiornamento/Info are UI-only
 * placeholders for now (SettingsGeneralFragment / SettingsUpdateFragment /
 * SettingsAboutFragment) — only Backup (SettingsFragment) is functional.
 */
public class ImpostazioniTabFragment extends Fragment {

    private static final int ACCENT_GENERAL = 0xFF7C4DFF; // purple
    private static final int ACCENT_BACKUP  = 0xFF4CAF50; // green
    private static final int ACCENT_UPDATE  = 0xFF00BCD4; // cyan
    private static final int ACCENT_ABOUT   = 0xFFFF9800; // amber
    private static final int ACCENT_HEALTH  = 0xFFE91E63; // pink

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 12, 0, 24);
        rv.setClipToPadding(false);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = (RecyclerView) view;

        List<NavAdapter.NavItem> items = List.of(
                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_settings_general),
                        getString(R.string.nav_settings_general_summary),
                        () -> navigate(new SettingsGeneralFragment(), getString(R.string.nav_settings_general)),
                        ACCENT_GENERAL,
                        "generale", "general", "lingua", "language", "icona", "icon"),

                new NavAdapter.NavItem(
                        R.drawable.ic_reset,
                        getString(R.string.settings_section),
                        getString(R.string.nav_settings_backup_summary),
                        () -> navigate(new SettingsFragment(), getString(R.string.settings_section)),
                        ACCENT_BACKUP,
                        "backup", "ripristina", "restore", "cancella", "clear", "reset"),

                new NavAdapter.NavItem(
                        R.drawable.ic_restart_systemui,
                        getString(R.string.nav_settings_update),
                        getString(R.string.nav_settings_update_summary),
                        () -> navigate(new SettingsUpdateFragment(), getString(R.string.nav_settings_update)),
                        ACCENT_UPDATE,
                        "aggiornamento", "update", "versione", "version"),

                new NavAdapter.NavItem(
                        R.drawable.ic_settings,
                        getString(R.string.nav_settings_about),
                        getString(R.string.nav_settings_about_summary),
                        () -> navigate(new SettingsAboutFragment(), getString(R.string.nav_settings_about)),
                        ACCENT_ABOUT,
                        "info", "about", "github", "crediti", "credits", "supporto", "traduci"),

                new NavAdapter.NavItem(
                        R.drawable.ic_check,
                        getString(R.string.nav_mod_health),
                        getString(R.string.nav_mod_health_summary),
                        () -> navigate(new ModHealthFragment(), getString(R.string.nav_mod_health)),
                        ACCENT_HEALTH,
                        "stato", "status", "diagnostica", "diagnostics", "rom", "aggiornamento", "update", "rotto", "broken")
        );

        rv.setAdapter(new NavAdapter(items));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
