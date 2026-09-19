package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Pannello Impostazioni Rapide — porting reale della struttura OC quick_settings_mods.xml.
 *
 * "Quick Settings Tiles": "Numero Tile" (QsTilesFragment) e "Personalizza Riquadri"
 * (QsTilesCustomizeFragment, 22 chiavi pref da OC's QuickSettingsCustomization) sono UI/prefs
 * reali; hook Xposed non ancora scritto per "Personalizza Riquadri" (richiede
 * QsBaseTiles/QsHighlightTiles di OC come riferimento). "Widget QS" ha un hook reale
 * (QsWidgetsMod) con limiti noti (max 4 widget, vedi memoria di progetto). "QS Separati"
 * (QsSeparateModsFragment/QsSeparateMod) porta solo la metà funzionante su SDK36/OOS16 di OC's
 * SeparateQsCustomization — nascondi pulsante modifica/menù + larghezza zona di trascinamento;
 * l'editor a griglia dei riquadri personalizzati di OC resta fuori, disabilitato da OC stesso
 * (mCustomLayout forzato false) su ogni SDK diverso da 35, stesso vicolo cieco di QSTiles.
 *
 * "Sfondo Solido" è tornato in "Intestazione Impostazioni Rapide" (QsFragment) — era stato
 * spostato qui per errore.
 *
 * "Quick Pulldown" e "Il mio dispositivo" sono UI/prefs reali portate da OC, senza hook
 * ancora collegato (come molte altre schermate di questo progetto).
 */
public class QuickSettingsFragment extends Fragment {

    // ── Trasparenza (reale OC: qs_transparency) ──────────────────────────────────
    private static final String PREF_TRANSP_ON     = "OBS_QS_TRANSPARENCY_ON";
    private static final String PREF_TRANSP_VALUE  = "OBS_QS_TRANSPARENCY_VALUE";

    // ── Quick Pulldown (reale OC) ────────────────────────────────────────────────
    private static final String PREF_PULLDOWN_ON     = "OBS_QS_PULLDOWN_ON";
    private static final String PREF_PULLDOWN_LENGTH = "OBS_QS_PULLDOWN_LENGTH";
    private static final String PREF_PULLDOWN_SIDE   = "OBS_QS_PULLDOWN_SIDE"; // "0"=sx "1"=dx
    private static final String PREF_PULLDOWN_COLLAPSE = "OBS_QS_PULLDOWN_COLLAPSE";

    // ── Il mio dispositivo (reale OC: my_device) ────────────────────────────────
    private static final String PREF_MY_DEVICE = "OBS_QS_MY_DEVICE";

    private RecyclerView mRv;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude le opzioni sottostanti — stesso pattern di QsTilesCustomizeFragment.
    private boolean mTranspExpanded   = false;
    private boolean mPulldownExpanded = false;

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
        mRv = (RecyclerView) view;
        rebuild();
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        // ── Intestazione Impostazioni Rapide (QsFragment, annidata qui) ──────────
        chain.add(new NavAdapter(List.of(new NavAdapter.NavItem(
                R.drawable.ic_qs,
                getString(R.string.nav_quick_settings),
                getString(R.string.nav_quick_settings_summary),
                () -> navigate(new QsFragment(), getString(R.string.nav_quick_settings)),
                0xFFE91E63)))); // pink, colore categoria "Pannello Impostazioni Rapide"

        // ── Quick Settings Tiles ──────────────────────────────────────────────
        // "Numero di riquadri" reale (porting di OC's QSTiles); il resto resta
        // segnaposto — sottosistemi grandi a sé in OC.
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.quick_settings_tiles_title))));
        chain.add(new NavAdapter(List.of(new NavAdapter.NavItem(
                R.drawable.ic_qs,
                getString(R.string.quick_settings_tiles_number),
                getString(R.string.qs_tiles_customize),
                () -> navigate(new QsTilesFragment(), getString(R.string.quick_settings_tiles_number)),
                0xFFE91E63))));
        chain.add(new NavAdapter(List.of(new NavAdapter.NavItem(
                R.drawable.ic_qs,
                getString(R.string.quick_settings_tiles_main),
                getString(R.string.qs_tiles_customize_summary),
                () -> navigate(new QsTilesCustomizeFragment(), getString(R.string.quick_settings_tiles_main)),
                0xFFE91E63))));
        chain.add(new NavAdapter(List.of(new NavAdapter.NavItem(
                R.drawable.ic_qs,
                getString(R.string.quick_settings_widgets),
                getString(R.string.qs_widgets_nav_summary),
                () -> navigate(new QsWidgetsFragment(), getString(R.string.quick_settings_widgets)),
                0xFFE91E63))));
        // "Impostazioni Rapide Separati" (QsSeparateMod) spostato dentro "Personalizza
        // Riquadri" il 2026-08-20 come sezione inline (solo pulsanti/larghezza tendina
        // rimasti davvero esclusivi di quello stile) — niente più card qui.

        // ── Trasparenza (reale OC) — la Sfocatura si è spostata in Schermata di
        // Blocco → Opzioni SdB, dato che il blur nativo OOS agisce su entrambe le
        // superfici e lì è più visibile/utile ────────────────────────────────────
        SwitchWidgetAdapter.SwitchItem transpSwitch = prefSwitch(getString(R.string.qs_transparency_title), null, PREF_TRANSP_ON);
        transpSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_TRANSP_ON, transpSwitch.checked);
            mTranspExpanded = transpSwitch.checked;
            rebuild();
        };
        transpSwitch.onRowClick = () -> { mTranspExpanded = !mTranspExpanded; rebuild(); };
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(transpSwitch));
        if (mTranspExpanded) {
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(
                    sliderItem(getString(R.string.qs_transparency_value), PREF_TRANSP_VALUE, 0, 100, 40, "%")));
        }

        // ── Quick Pulldown (reale OC) ────────────────────────────────────────────
        SwitchWidgetAdapter.SwitchItem pulldownSwitch = prefSwitch(getString(R.string.quick_pulldown), null, PREF_PULLDOWN_ON);
        pulldownSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_PULLDOWN_ON, pulldownSwitch.checked);
            mPulldownExpanded = pulldownSwitch.checked;
            rebuild();
        };
        pulldownSwitch.onRowClick = () -> { mPulldownExpanded = !mPulldownExpanded; rebuild(); };
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(pulldownSwitch));
        if (mPulldownExpanded) {
            List<Object> pulldownRows = new ArrayList<>();
            pulldownRows.add(sliderItem(getString(R.string.quick_pulldown_length), PREF_PULLDOWN_LENGTH, 0, 100, 25, "%"));
            pulldownRows.add(singleChoiceItem(getString(R.string.quick_settings_side), PREF_PULLDOWN_SIDE, R.array.quick_pulldown_side_entries));
            pulldownRows.add(prefSwitch(getString(R.string.quick_collapse), null, PREF_PULLDOWN_COLLAPSE));
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, pulldownRows);
        }

        // ── Il mio dispositivo (reale OC) ────────────────────────────────────────
        chain.add(new SwitchWidgetAdapter(List.of(prefSwitch(
                getString(R.string.my_device_title), getString(R.string.my_device_summary), PREF_MY_DEVICE))));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Generic row helpers ──────────────────────────────────────────────────────

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    /** A switch that also controls the visibility of other rows — rebuilds the list on change. */
    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes));
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
