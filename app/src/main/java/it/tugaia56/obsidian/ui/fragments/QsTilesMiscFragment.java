package it.tugaia56.obsidian.ui.fragments;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.hooks.systemui.QsSeparateMod;

/**
 * "Varie Riquadri" (2026-09-26) — estratto da QsTilesCustomizeFragment su richiesta esplicita:
 * una VERA voce di navigazione a sé in "Pannello Impostazioni Rapide" (QuickSettingsFragment),
 * non solo un titolo sezione dentro "Personalizza Riquadri" (quel tentativo, un semplice
 * SectionTitleAdapter condiviso, è stato subito corretto — l'utente intendeva una schermata
 * separata). Contiene Animazione/Transizioni Riquadri, Etichette Impostazioni Rapide,
 * Impostazioni Rapide Separati — le tre sezioni meno centrali di "Personalizza Riquadri",
 * spostate qui per accorciare quella schermata dopo l'aggiunta dei cursori Luminosità/Volume
 * separati (stesso giorno). Tutti gli hook Xposed restano in QsTilesCustomizeMod/QsSeparateMod,
 * invariati — qui è cambiata solo la UI, nessuna chiave di preferenza rinominata.
 */
public class QsTilesMiscFragment extends Fragment {

    // ── Animazione Riquadri ──────────────────────────────────────────────────
    private static final String KEY_ANIM_STYLE       = "qs_tile_animation_style";
    private static final String KEY_ANIM_DURATION    = "qs_tile_animation_duration";
    private static final String KEY_ANIM_INTERPOLATOR = "qs_tile_animation_interpolator";

    // ── Transizioni pagine QS ────────────────────────────────────────────────
    private static final String KEY_TRANSITIONS_ON = "qs_transitions_title_switch";
    private static final String KEY_TRANSITIONS    = "qs_tile_transformations";

    // ── Etichette ────────────────────────────────────────────────────────────
    private static final String KEY_HIDE_LABELS    = "qs_hide_labels";
    private static final String KEY_LABEL_COLOR_ON = "qs_tile_label_enabled";
    private static final String KEY_LABEL_COLOR    = "qs_tile_label";

    // ── Impostazioni Rapide Separati ─────────────────────────────────────────
    private static final String KEY_SEP_HIDE_EDIT  = "OBS_QS_SEPARATE_HIDE_EDIT";
    private static final String KEY_SEP_HIDE_MENU  = "OBS_QS_SEPARATE_HIDE_MENU";
    private static final String KEY_SEP_HIDE_SETTINGS = "OBS_QS_SEPARATE_HIDE_SETTINGS";
    private static final String KEY_SEP_WIDTH_ON   = "OBS_QS_SEPARATE_WIDTH_ON";
    private static final String KEY_SEP_WIDTH_VAL  = "OBS_QS_SEPARATE_WIDTH_VALUE";
    private static final String KEY_SEP_ON         = "OBS_QS_SEPARATE_MASTER_ON";

    private RecyclerView mRv;
    private boolean mSepExpanded = false;
    private boolean mSepBtnBgExpanded = false;

    /** dialogId -> pref key, per instradare onColorSelected() al posto giusto (stesso pattern
     *  di QsTilesCustomizeFragment — ID scelti per non collidere: 208-210 già usati là per gli
     *  stessi tre pulsanti Separati, riusati identici qui visto che sono le stesse righe. */
    private final Map<Integer, String> mSingleColorKeys = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

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

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        // ── Animazione ───────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_animation_section))));
        int animStyle = 0;
        try { animStyle = Integer.parseInt(ObsidianPrefs.getString(KEY_ANIM_STYLE, "0")); } catch (NumberFormatException ignored) {}
        List<Object> animRows = new ArrayList<>();
        animRows.add(singleChoiceRow(getString(R.string.qs_tiles_animation_style_title), KEY_ANIM_STYLE,
                R.array.qs_tile_animation_style_entries));
        if (animStyle != 0) {
            animRows.add(sliderRow(getString(R.string.qs_tiles_animation_duration_title), KEY_ANIM_DURATION, 1, 5, 1, ""));
            animRows.add(singleChoiceRow(getString(R.string.qs_tiles_animation_interpolator_title), KEY_ANIM_INTERPOLATOR,
                    R.array.qs_tile_animation_interpolator_entries));
        }
        // "Disattivata" è l'ultima voce dell'elenco Transizioni invece di uno switch separato,
        // tocco unico apre subito la scelta — stesso gruppo di Animazione.
        animRows.add(transitionsRow());
        GroupUtils.addGroup(chain, animRows);

        // ── Etichette ────────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_labels_section))));
        mSingleColorKeys.put(205, KEY_LABEL_COLOR);
        SwitchWidgetAdapter.SwitchItem labelColorSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_tiles_label_color_title), getString(R.string.qs_tiles_label_color_summary),
                ObsidianPrefs.getBoolean(KEY_LABEL_COLOR_ON, false), null);
        labelColorSwitch.onChanged = () -> ObsidianPrefs.putBoolean(KEY_LABEL_COLOR_ON, labelColorSwitch.checked);
        labelColorSwitch.onRowClick = () -> showLabelColorAccentChoice();
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.qs_tiles_hide_labels_title), getString(R.string.qs_tiles_hide_labels_summary), KEY_HIDE_LABELS),
                labelColorSwitch));

        // ── Impostazioni Rapide Separati (pulsanti/larghezza tendina) — uniche opzioni
        // davvero esclusive dello stile "Separati" (tutto il resto — sfondo, colori icone,
        // etichette — si è rivelato universale). Stesso pattern: switch master attiva/
        // disattiva, il tocco sul nome apre/chiude le opzioni sottostanti.
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_separate_mods))));
        SwitchWidgetAdapter.SwitchItem sepSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_separate_mods), getString(R.string.qs_separate_mods_summary),
                ObsidianPrefs.getBoolean(KEY_SEP_ON, true), null);
        sepSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_SEP_ON, sepSwitch.checked);
            mSepExpanded = sepSwitch.checked;
            rebuild();
        };
        sepSwitch.onRowClick = () -> { mSepExpanded = !mSepExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(sepSwitch));
        if (mSepExpanded) {
            SwitchWidgetAdapter.SwitchItem btnBgSwitch = gatingSwitch(
                    getString(R.string.qs_separate_bg_section), getString(R.string.qs_separate_bg_section_summary), QsSeparateMod.PREF_BTN_BG_ON);
            btnBgSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(QsSeparateMod.PREF_BTN_BG_ON, btnBgSwitch.checked);
                mSepBtnBgExpanded = btnBgSwitch.checked;
                rebuild();
            };
            btnBgSwitch.onRowClick = () -> { mSepBtnBgExpanded = !mSepBtnBgExpanded; rebuild(); };
            GroupUtils.addGroup(chain, List.of(btnBgSwitch));
            if (mSepBtnBgExpanded) {
                GroupUtils.addGroup(chain, List.of(
                        bgButtonRow(getString(R.string.qs_separate_bg_edit), QsSeparateMod.PREF_EDIT_BG_ON,
                                QsSeparateMod.PREF_EDIT_BG_ACCENT, QsSeparateMod.PREF_EDIT_BG_COLOR, 208),
                        bgButtonRow(getString(R.string.qs_separate_bg_menu), QsSeparateMod.PREF_MENU_BG_ON,
                                QsSeparateMod.PREF_MENU_BG_ACCENT, QsSeparateMod.PREF_MENU_BG_COLOR, 209),
                        bgButtonRow(getString(R.string.qs_separate_bg_settings), QsSeparateMod.PREF_SETTINGS_BG_ON,
                                QsSeparateMod.PREF_SETTINGS_BG_ACCENT, QsSeparateMod.PREF_SETTINGS_BG_COLOR, 210)));
            }

            GroupUtils.addGroup(chain, List.of(
                    prefSwitch(getString(R.string.qs_separate_hide_edit), getString(R.string.qs_separate_hide_edit_summary), KEY_SEP_HIDE_EDIT),
                    prefSwitch(getString(R.string.qs_separate_hide_menu), getString(R.string.qs_separate_hide_menu_summary), KEY_SEP_HIDE_MENU),
                    prefSwitch(getString(R.string.qs_separate_hide_settings), getString(R.string.qs_separate_hide_settings_summary), KEY_SEP_HIDE_SETTINGS)));
            boolean sepWidthOn = ObsidianPrefs.getBoolean(KEY_SEP_WIDTH_ON, false);
            List<Object> sepWidthRows = new ArrayList<>();
            sepWidthRows.add(gatingSwitch(getString(R.string.qs_separate_width_switch),
                    getString(R.string.qs_separate_width_switch_summary), KEY_SEP_WIDTH_ON));
            if (sepWidthOn) {
                sepWidthRows.add(sliderRow(getString(R.string.qs_separate_width_value), KEY_SEP_WIDTH_VAL, 10, 85, 50, "%"));
            }
            GroupUtils.addGroup(chain, sepWidthRows);
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    /** "Stile Transizioni" — riga sola come "Stile Animazione": "Disattivata" è la prima voce
     *  dell'elenco (indice 0) invece di un secondo switch "Abilita..." separato. L'indice
     *  mostrato/scelto qui è sempre "indice reale + 1" (0 = Disattivata) — quello salvato in
     *  KEY_TRANSITIONS per il Mod resta l'indice reale (indice UI - 1), così TileTransformers.get()
     *  non deve cambiare. KEY_TRANSITIONS_ON resta il vero interruttore letto dal Mod. */
    private ListWidgetAdapter.ListItem transitionsRow() {
        String[] entries = getResources().getStringArray(R.array.qs_tile_transitions_entries);
        return new ListWidgetAdapter.ListItem(getString(R.string.qs_tiles_transitions_style_title),
                entries[currentTransitionUiIndex(entries)], () -> showTransitionsDialog(entries));
    }

    private int currentTransitionUiIndex(String[] entries) {
        if (!ObsidianPrefs.getBoolean(KEY_TRANSITIONS_ON, false)) return 0;
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(KEY_TRANSITIONS, "0")); } catch (NumberFormatException ignored) {}
        int uiIndex = idx + 1;
        return (uiIndex > 0 && uiIndex < entries.length) ? uiIndex : 0;
    }

    private void showTransitionsDialog(String[] entries) {
        int current = currentTransitionUiIndex(entries);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new android.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.qs_tiles_transitions_style_title))
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean off = selected[0] == 0;
                    ObsidianPrefs.putBoolean(KEY_TRANSITIONS_ON, !off);
                    if (!off) ObsidianPrefs.putString(KEY_TRANSITIONS, String.valueOf(selected[0] - 1));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** Come colorModeItem (altri fragment), ma con uno switch acceso/spento indipendente per il
     *  singolo pulsante: lo switch abilita/disabilita lo sfondo colorato di QUESTO pulsante, il
     *  tocco sul nome apre lo stesso dialog Accento/Personalizzato. */
    private SwitchWidgetAdapter.SwitchItem bgButtonRow(String title, String onKey,
                                                        String accentKey, String colorKey, int dialogId) {
        mSingleColorKeys.put(dialogId, colorKey);
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, colorModeLabel(accentKey, colorKey),
                ObsidianPrefs.getBoolean(onKey, true), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(onKey, item.checked);
        item.onRowClick = () -> showColorModeDialog(title, accentKey, colorKey, dialogId);
        return item;
    }

    private String colorModeLabel(String accentKey, String colorKey) {
        if (ObsidianPrefs.getBoolean(accentKey, true)) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(colorKey, 0xFFFFFFFF));
    }

    private void showColorModeDialog(String title, String accentKey, String colorKey, int dialogId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        int current = ObsidianPrefs.getBoolean(accentKey, true) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean accent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(accentKey, accent);
                    rebuild();
                    if (!accent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(colorKey, 0xFFFFFFFF);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showLabelColorAccentChoice() {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(KEY_LABEL_COLOR + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.qs_tiles_label_color_title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(KEY_LABEL_COLOR + "_use_accent", useAccent);
                    if (useAccent) {
                        ObsidianPrefs.putInt(KEY_LABEL_COLOR, ObsidianTheme.accentColor());
                        rebuild();
                    } else {
                        openColorPicker(205, KEY_LABEL_COLOR);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void openColorPicker(int dialogId, String key) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showColorPickerDialog(
                    dialogId, ObsidianPrefs.getInt(key, 0xFFFFFFFF), true, true, true);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        String singleKey = mSingleColorKeys.get(event.dialogId());
        if (singleKey != null) {
            ObsidianPrefs.putBoolean(singleKey + "_use_accent", false); // picking a colour implies custom
            ObsidianPrefs.putInt(singleKey, event.color());
            rebuild();
        }
    }

    // ── Generic row helpers (stesso pattern delle altre schermate QS) ──────────

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private ListWidgetAdapter.ListItem singleChoiceRow(String title, String key, int entriesArrayRes) {
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
        ObsidianTheme.themeDialog(new android.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem sliderRow(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
