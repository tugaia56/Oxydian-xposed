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

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Status Bar sub-screen:
 *  – NavItems: Orologio, Notifiche
 *  – Switch inline: BT, DT-sleep, Luminosità
 *
 * Icone Batteria e Stile Icone Segnale sono stati spostati al livello Home DST
 * (vedi DstTabFragment → "Stile icone Segnale" / SignalStyleFragment).
 */
public class StatusbarFragment extends Fragment {

    private final List<NavAdapter.NavItem> mNavItems = new ArrayList<>();
    private NavAdapter mNavAdapter;

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

        mNavItems.clear();

        // 1. Orologio & Calendario (su Android puro non funziona: nascosto)
        if (!it.tugaia56.obsidian.ui.PureAndroidFilter.active()) mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_clock,
                getString(R.string.section_clock_date),
                getString(R.string.nav_clock_date_summary),
                () -> navigate(new ClockDateFragment(),
                        getString(R.string.section_clock_date))));

        // 2. Notifiche (su Android puro le opzioni funzionanti stanno qui sotto, senza la scheda)
        if (!it.tugaia56.obsidian.ui.PureAndroidFilter.active()) mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_notifications,
                getString(R.string.section_statusbar_notifs),
                getString(R.string.section_statusbar_notifs_summary),
                () -> navigate(new StatusbarNotifsFragment(),
                        getString(R.string.section_statusbar_notifs))));

        mNavAdapter = new NavAdapter(mNavItems, 0xFF7C4DFF); // purple, colore categoria "Barra di Stato"

        // ── Icon switches inline ──────────────────────────────────────────────
        SectionTitleAdapter iconSection = new SectionTitleAdapter(
                List.of(getString(R.string.section_statusbar_icons)));
        SwitchWidgetAdapter iconSwitches = new SwitchWidgetAdapter(buildIconSwitches());

        mRv = rv;
        mIconSection = iconSection;
        mIconSwitches = iconSwitches;
        render();
    }

    private RecyclerView mRv;
    private SectionTitleAdapter mIconSection;
    private SwitchWidgetAdapter mIconSwitches;

    private void render() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(mNavAdapter);
        chain.add(mIconSection);
        chain.add(mIconSwitches);
        if (it.tugaia56.obsidian.ui.PureAndroidFilter.active()) {
            // Android puro: delle opzioni di Notifiche restano quelle con hook su classi Android normali
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.section_statusbar_notifs))));
            List<Object> rows = new ArrayList<>();
            SwitchWidgetAdapter.SwitchItem appIcon = new SwitchWidgetAdapter.SwitchItem(
                    getString(R.string.statusbar_use_app_icon), getString(R.string.statusbar_use_app_icon_summary),
                    ObsidianPrefs.getBoolean("statusbar_notification_app_icon", false), null);
            appIcon.onChanged = () -> ObsidianPrefs.putBoolean("statusbar_notification_app_icon", appIcon.checked);
            rows.add(appIcon);
            rows.add(new it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter.SliderItem(
                    getString(R.string.statusbar_app_icon_scale),
                    ObsidianPrefs.getInt("statusbar_notification_app_icon_scale", 100), 50, 200, "%", 100,
                    value -> ObsidianPrefs.putInt("statusbar_notification_app_icon_scale", value)));
            String[] entries = getResources().getStringArray(R.array.notif_default_expansion_entries);
            int cur = 0;
            try { cur = Integer.parseInt(ObsidianPrefs.getString("notificationDefaultExpansion", "0")); } catch (NumberFormatException ignored) {}
            final int curF = cur;
            rows.add(new it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter.ListItem(
                    getString(R.string.notif_default_expansion_title),
                    entries[(curF >= 0 && curF < entries.length) ? curF : 0],
                    () -> new android.app.AlertDialog.Builder(requireContext())
                            .setTitle(R.string.notif_default_expansion_title)
                            .setSingleChoiceItems(entries, curF, null)
                            .setPositiveButton(R.string.apply, (d, w) -> {
                                int sel = ((android.app.AlertDialog) d).getListView().getCheckedItemPosition();
                                ObsidianPrefs.putString("notificationDefaultExpansion", String.valueOf(sel));
                                render();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show()));
            SwitchWidgetAdapter.SwitchItem expandAccent = new SwitchWidgetAdapter.SwitchItem(
                    getString(R.string.notif_expand_accent), getString(R.string.notif_expand_accent_summary),
                    ObsidianPrefs.getBoolean("notifExpandAccent", false), null);
            expandAccent.onChanged = () -> ObsidianPrefs.putBoolean("notifExpandAccent", expandAccent.checked);
            rows.add(expandAccent);
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, rows);
            // Pulsanti in fondo alle notifiche (Cancella tutto, Gestisci, Cronologia): stesse opzioni di OxygenOS
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.customize_clear_all_button))));
            List<Object> btnRows = new ArrayList<>();
            SwitchWidgetAdapter.SwitchItem custom = new SwitchWidgetAdapter.SwitchItem(
                    getString(R.string.customize_clear_all_button), getString(R.string.notif_buttons_custom_summary),
                    ObsidianPrefs.getBoolean("customizeClearButton", false), null);
            custom.onChanged = () -> {
                ObsidianPrefs.putBoolean("customizeClearButton", custom.checked);
                if (custom.checked && !ObsidianPrefs.getBoolean("notifButtonsInit", false)) {   // prima volta: sfondo accento, testo bianco
                    ObsidianPrefs.putBoolean("notifButtonsInit", true);
                    ObsidianPrefs.putBoolean("linkBackgroundAccent", true);
                    ObsidianPrefs.putBoolean("linkIconAccent", false);
                    ObsidianPrefs.putInt("clearButtonIconColor", 0xFFFFFFFF);
                }
                render();
            };
            btnRows.add(custom);
            if (custom.checked) {
                btnRows.add(colorModeRow(getString(R.string.clear_all_bg_color), "linkBackgroundAccent",
                        "clearButtonBgColor", 0xFF8C8C8C, DIALOG_BG));
                btnRows.add(colorModeRow(getString(R.string.notif_buttons_text_color), "linkIconAccent",
                        "clearButtonIconColor", 0xFFFFFFFF, DIALOG_FG));
            }
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, btnRows);
        }
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    // ── Icon switch items ─────────────────────────────────────────────────────

    private List<SwitchWidgetAdapter.SwitchItem> buildIconSwitches() {
        SwitchWidgetAdapter.SwitchItem s1 = makePrefSwitch(
                getString(R.string.hide_bluetooth_disconnected),
                getString(R.string.hide_bluetooth_disconnected_summary),
                "hide_bluetooth_when_disconnected");
        SwitchWidgetAdapter.SwitchItem s2 = makePrefSwitch(
                getString(R.string.double_tap_sleep_statusbar),
                getString(R.string.double_tap_sleep_statusbar_summary),
                "double_tap_sleep_statusbar");
        SwitchWidgetAdapter.SwitchItem s3 = makePrefSwitch(
                getString(R.string.statusbar_brightness),
                getString(R.string.statusbar_brightness_summary),
                "statusbar_brightness");
        SwitchWidgetAdapter.SwitchItem s4 = makePrefSwitch(
                getString(R.string.block_clipboard_overlay),
                getString(R.string.block_clipboard_overlay_summary),
                "block_clipboard_overlay");
        SwitchWidgetAdapter.SwitchItem s5 = makePrefSwitch(
                getString(R.string.hide_sim1_icon),
                getString(R.string.hide_sim_icon_summary),
                "OBS_STATUSBAR_HIDE_SIM1");
        SwitchWidgetAdapter.SwitchItem s6 = makePrefSwitch(
                getString(R.string.hide_sim2_icon),
                getString(R.string.hide_sim_icon_summary),
                "OBS_STATUSBAR_HIDE_SIM2");
        // Android puro (RisingOS): doppio tocco, luminosita', Bluetooth e finestrella degli appunti li gestisce gia' la ROM
        List<SwitchWidgetAdapter.SwitchItem> all = it.tugaia56.obsidian.ui.PureAndroidFilter.active()
                ? List.of(s5, s6) : List.of(s1, s2, s3, s4, s5, s6);
        for (int i = 0; i < all.size(); i++) {
            all.get(i).groupPos = i == 0 ? it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.TOP
                    : i == all.size() - 1 ? it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.BOTTOM
                    : it.tugaia56.obsidian.utils.ObsidianTheme.GroupPos.MIDDLE;
        }
        return all;
    }

    private SwitchWidgetAdapter.SwitchItem makePrefSwitch(String title, String summary,
                                                           String prefKey) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary,
                ObsidianPrefs.getBoolean(prefKey, false),
                null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(prefKey, item.checked);
        return item;
    }

    // ── Colore dei pulsanti (accento / personalizzato) ─────────────────────────────────────────

    private static final int DIALOG_BG = "clearButtonBgColor".hashCode();
    private static final int DIALOG_FG = "clearButtonIconColor".hashCode();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        org.greenrobot.eventbus.EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        org.greenrobot.eventbus.EventBus.getDefault().unregister(this);
    }

    @org.greenrobot.eventbus.Subscribe(threadMode = org.greenrobot.eventbus.ThreadMode.MAIN)
    public void onColorSelected(it.tugaia56.obsidian.ui.events.ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_BG) { ObsidianPrefs.putInt("clearButtonBgColor", event.color()); render(); }
        else if (event.dialogId() == DIALOG_FG) { ObsidianPrefs.putInt("clearButtonIconColor", event.color()); render(); }
    }

    private it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter.ListItem colorModeRow(String title, String accentKey,
                                                                                    String colorKey, int colorDef, int dialogId) {
        String label = ObsidianPrefs.getBoolean(accentKey, false) ? getString(R.string.color_mode_accent)
                : String.format(java.util.Locale.US, "#%06X", ObsidianPrefs.getInt(colorKey, colorDef) & 0xFFFFFF);
        return new it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter.ListItem(title, label, () -> {
            String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
            int current = ObsidianPrefs.getBoolean(accentKey, false) ? 0 : 1;
            final int[] selected = {current};
            it.tugaia56.obsidian.utils.ObsidianTheme.themeDialog(new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(title)
                    .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                    .setPositiveButton(R.string.apply, (d, w) -> {
                        boolean accent = selected[0] == 0;
                        ObsidianPrefs.putBoolean(accentKey, accent);
                        render();
                        if (!accent && getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(
                                    dialogId, ObsidianPrefs.getInt(colorKey, colorDef), true, true, true);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show());
        });
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
