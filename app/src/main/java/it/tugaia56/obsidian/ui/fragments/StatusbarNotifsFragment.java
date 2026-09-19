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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * Status bar notification sub-screen — porting reale di OC statusbar_notifications.xml
 * (le rimozioni notifica esistenti — USB/batteria/carica/torcia/dev-mode — restano qui
 * invariate) più gli 8 campi mancanti:
 *  - Usa icone app (+ scala)
 *  - Espansione predefinita notifiche
 *  - Mostra pulsante espandi/comprimi
 *  - Personalizza pulsante Cancella tutto → Collega sfondo/icona all'accento + colori
 */
public class StatusbarNotifsFragment extends Fragment {

    private static final String PREF_APP_ICON        = "statusbar_notification_app_icon";
    private static final String PREF_APP_ICON_SCALE  = "statusbar_notification_app_icon_scale";
    private static final String PREF_DEFAULT_EXPANSION = "notificationDefaultExpansion"; // "0".."2"
    private static final String PREF_SHOW_BUTTONS    = "statusbar_notification_buttons";

    private static final String PREF_CUSTOMIZE_CLEAR = "customizeClearButton";
    private static final String PREF_LINK_BG_ACCENT   = "linkBackgroundAccent";
    private static final String PREF_CLEAR_BG_COLOR   = "clearButtonBgColor";
    private static final String PREF_LINK_ICON_ACCENT  = "linkIconAccent";
    private static final String PREF_CLEAR_ICON_COLOR  = "clearButtonIconColor";

    private static final int DEFAULT_CLEAR_BG_COLOR   = 0xFF8C8C8C;
    private static final int DEFAULT_CLEAR_ICON_COLOR  = 0xFFFFFFFF;
    private static final int CLEAR_BG_DIALOG_ID   = PREF_CLEAR_BG_COLOR.hashCode();
    private static final int CLEAR_ICON_DIALOG_ID = PREF_CLEAR_ICON_COLOR.hashCode();

    private RecyclerView mRv;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude le opzioni sottostanti — stesso pattern di QsTilesCustomizeFragment.
    private boolean mAppIconExpanded  = false;
    private boolean mClearBtnExpanded = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 8, 0, 8);
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

        // ── Rimozioni notifica (esistenti, invariate) ────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.section_statusbar_notifs))));
        GroupUtils.addGroup(chain, buildRemovalSwitches());

        // ── Usa icone app / Espansione / pulsanti ─────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.notif_expansion_section))));
        SwitchWidgetAdapter.SwitchItem appIconSwitch = gatingSwitch(
                getString(R.string.statusbar_use_app_icon), null, PREF_APP_ICON);
        appIconSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_APP_ICON, appIconSwitch.checked);
            mAppIconExpanded = appIconSwitch.checked;
            rebuild();
        };
        appIconSwitch.onRowClick = () -> { mAppIconExpanded = !mAppIconExpanded; rebuild(); };
        List<Object> expansionRows = new ArrayList<>();
        expansionRows.add(appIconSwitch);
        if (mAppIconExpanded) {
            expansionRows.add(sliderItem(getString(R.string.statusbar_app_icon_scale), PREF_APP_ICON_SCALE, 50, 200, 100, "%"));
        }
        expansionRows.add(singleChoiceItem(getString(R.string.notif_default_expansion_title),
                PREF_DEFAULT_EXPANSION, R.array.notif_default_expansion_entries));
        expansionRows.add(prefSwitch(
                getString(R.string.notif_show_buttons), getString(R.string.notif_show_buttons_summary), PREF_SHOW_BUTTONS));
        GroupUtils.addGroup(chain, expansionRows);

        // ── Personalizza pulsante Cancella tutto ────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.customize_clear_all_button))));
        SwitchWidgetAdapter.SwitchItem clearBtnSwitch = gatingSwitch(
                getString(R.string.customize_clear_all_button), null, PREF_CUSTOMIZE_CLEAR);
        clearBtnSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_CUSTOMIZE_CLEAR, clearBtnSwitch.checked);
            mClearBtnExpanded = clearBtnSwitch.checked;
            rebuild();
        };
        clearBtnSwitch.onRowClick = () -> { mClearBtnExpanded = !mClearBtnExpanded; rebuild(); };
        List<Object> clearBtnRows = new ArrayList<>();
        clearBtnRows.add(clearBtnSwitch);
        if (mClearBtnExpanded) {
            clearBtnRows.add(colorModeItem(getString(R.string.clear_all_bg_color),
                    PREF_LINK_BG_ACCENT, PREF_CLEAR_BG_COLOR, DEFAULT_CLEAR_BG_COLOR, CLEAR_BG_DIALOG_ID));
            clearBtnRows.add(colorModeItem(getString(R.string.clear_all_icon_color),
                    PREF_LINK_ICON_ACCENT, PREF_CLEAR_ICON_COLOR, DEFAULT_CLEAR_ICON_COLOR, CLEAR_ICON_DIALOG_ID));
        }
        GroupUtils.addGroup(chain, clearBtnRows);

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private List<SwitchWidgetAdapter.SwitchItem> buildRemovalSwitches() {
        return List.of(
                prefSwitch(
                        getString(R.string.remove_usb_dialog),
                        getString(R.string.remove_usb_dialog_summary),
                        "remove_usb_dialog"),
                prefSwitch(
                        getString(R.string.remove_low_battery),
                        getString(R.string.remove_low_battery_summary),
                        "remove_low_battery_notification"),
                prefSwitch(
                        getString(R.string.remove_charging_complete),
                        getString(R.string.remove_charging_complete_summary),
                        "remove_charging_complete_notification"),
                prefSwitch(
                        getString(R.string.remove_flashlight_notif),
                        getString(R.string.remove_flashlight_notif_summary),
                        "remove_flashlight_notification"),
                prefSwitch(
                        getString(R.string.remove_dev_mode_notif),
                        getString(R.string.remove_dev_mode_notif_summary),
                        "remove_dev_mode")
        );
    }

    // ── Riga unica "Accento / Personalizzato" — tocco apre un dialog a scelta singola
    // invece di uno switch + riga separata, stesso pattern di ClockChipStyleFragment ──

    private ListWidgetAdapter.ListItem colorModeItem(String title, String accentKey, String colorKey, int colorDef, int dialogId) {
        return new ListWidgetAdapter.ListItem(title,
                ObsidianPrefs.getBoolean(accentKey, false) ? getString(R.string.color_mode_accent)
                        : colorHex(ObsidianPrefs.getInt(colorKey, colorDef)),
                () -> showColorModeDialog(title, accentKey, colorKey, colorDef, dialogId));
    }

    private void showColorModeDialog(String title, String accentKey, String colorKey, int colorDef, int dialogId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        int current = ObsidianPrefs.getBoolean(accentKey, false) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean accent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(accentKey, accent);
                    rebuild();
                    if (!accent && getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                dialogId, ObsidianPrefs.getInt(colorKey, colorDef), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String colorHex(int c) {
        return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == CLEAR_BG_DIALOG_ID) {
            ObsidianPrefs.putInt(PREF_CLEAR_BG_COLOR, event.color());
            rebuild();
        } else if (event.dialogId() == CLEAR_ICON_DIALOG_ID) {
            ObsidianPrefs.putInt(PREF_CLEAR_ICON_COLOR, event.color());
            rebuild();
        }
    }

    // ── Generic row helpers ──────────────────────────────────────────────────────

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String prefKey) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(prefKey, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(prefKey, item.checked);
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
