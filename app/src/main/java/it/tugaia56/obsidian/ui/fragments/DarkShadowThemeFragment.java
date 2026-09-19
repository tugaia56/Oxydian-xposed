package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Dark Shadow Theme — Preset Sfondo, Preset Accento, Stile Impostazioni (colors.xml +
 * drawable.xml overlay for the Settings app, experimental), Tastierino PIN, Barra
 * Progresso Circolare. Barra di Stato e Impostazioni rapide sono in Home Mods.
 */
public class DarkShadowThemeFragment extends Fragment {

    private static final String PREF_BG_PRESET = "DST_BG_PRESET_NAME";
    private static final String PREF_AC_PRESET = "DST_AC_PRESET_NAME";
    private static final String PREF_CPB_PRESET = "DST_PRESET_CPB";
    private static final String[] CPB_OVERLAYS = {
        "DSTCPB1", "DSTCPB2", "DSTCPB3", "DSTCPB4", "DSTCPB5"
    };

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

        String bgPreset = ObsidianPrefs.getString(PREF_BG_PRESET, null);
        String acPreset = ObsidianPrefs.getString(PREF_AC_PRESET, null);

        List<NavAdapter.NavItem> items = List.of(
                new NavAdapter.NavItem(
                        R.drawable.ic_drawing,
                        getString(R.string.dst_section_preset_sfondo),
                        bgPreset != null ? bgPreset : getString(R.string.dst_none),
                        () -> navigate(new DstBackgroundFragment(),
                                getString(R.string.dst_section_preset_sfondo)),
                        "sfondo", "background", "colore", "color"),

                new NavAdapter.NavItem(
                        R.drawable.ic_palette,
                        getString(R.string.dst_section_preset_accent),
                        acPreset != null ? acPreset : getString(R.string.dst_none),
                        () -> navigate(new DstAccentFragment(),
                                getString(R.string.dst_section_preset_accent)),
                        "accento", "accent", "colore", "color"),

                // 2026-09-11: le 3 voci separate (Impostazioni/SystemUI/Launcher, ognuna col suo
                // Applica) sono state unite in una sola — stessi compiler, stesso comportamento,
                // solo un tasto invece di tre. I fragment vecchi restano nel progetto (non
                // cancellati) ma non più linkati da qui.
                new NavAdapter.NavItem(
                        R.drawable.ic_drawing,
                        getString(R.string.dst_substratum_style_title),
                        getString(R.string.dst_substratum_style_card_desc),
                        () -> navigate(new SubstratumStyleFragment(),
                                getString(R.string.dst_substratum_style_title)),
                        "impostazioni", "settings", "systemui", "launcher", "dialoghi", "dialog",
                        "toast", "popup", "toolbar", "tema", "theme", "substratum", "impronte", "fingerprint"),

                new NavAdapter.NavItem(
                        R.drawable.ic_lock,
                        getString(R.string.section_pin_style),
                        getString(R.string.section_pin_style_summary),
                        () -> navigate(new PinStyleFragment(),
                                getString(R.string.section_pin_style)),
                        "codice", "password", "numeri", "numbers", "puntini", "dots"),

                new NavAdapter.NavItem(
                        R.drawable.arc_progress,
                        getString(R.string.dark_shadow_preset_cpb),
                        getString(R.string.nav_cpb_summary),
                        this::showCpbPresetDialog,
                        "barra", "caricamento", "loading", "spinner", "progress",
                        "progresso", "animazione", "circolare")
        );

        rv.setAdapter(new NavAdapter(items, 0xFF7C4DFF)); // purple, colore categoria "Obsidian Theme"
    }

    // ── Barra Progresso Circolare ─────────────────────────────────────────────

    private void showCpbPresetDialog() {
        String[] names = getResources().getStringArray(R.array.dst_cpb_preset_names);
        String current = ObsidianPrefs.getString(PREF_CPB_PRESET, null);
        int currentIdx = -1;
        for (int i = 0; i < CPB_OVERLAYS.length; i++) {
            if (CPB_OVERLAYS[i].equals(current)) { currentIdx = i; break; }
        }

        final int[] selected = {currentIdx};
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dark_shadow_preset_cpb))
                .setSingleChoiceItems(names, currentIdx, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] < 0) return;
                    ObsidianPrefs.putString(PREF_CPB_PRESET, CPB_OVERLAYS[selected[0]]);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                })
                .setNeutralButton(R.string.dst_disable, (d, w) -> {
                    ObsidianPrefs.remove(PREF_CPB_PRESET);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                })
                .setNegativeButton(R.string.close, null)
                .show();
        it.tugaia56.obsidian.utils.ObsidianTheme.themeDialog(dialog);
        fixButtonCaps(dialog);
    }

    private static void fixButtonCaps(AlertDialog d) {
        Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neu = d.getButton(AlertDialog.BUTTON_NEUTRAL);
        // Forcing minWidth to 0 squeezed "Disabilita" so hard it broke mid-word instead of
        // wrapping — leave the natural min-width so Android's button bar can stack the
        // buttons vertically when 3 don't fit on one row.
        for (Button b : new Button[]{pos, neg, neu}) {
            if (b == null) continue;
            b.setAllCaps(false);
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setEllipsize(null);
        }
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
