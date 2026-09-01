package it.tugaia56.obsidian.ui.fragments;

import android.graphics.Color;
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
import java.util.Collections;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstSignalIconStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstWifiIconStyle;

/**
 * Stile icone Segnale — top-level Home DST group (was nested under Barra di Stato). Batteria
 * è una voce top-level separata in Home DST (BatteryIconFragment direttamente), non qui.
 *
 * Layout (in order):
 *   1. Icone Segnale WI-FI (OBS)      → WifiIconsFragment    (coloured nav card)
 *   2. Icone Segnale Mobile (OBS)     → SignalIconsFragment  (coloured nav card)
 *   3. Colore Icona Segnale Wi-Fi     — custom color, Wi-Fi only
 *   4. Colore Icona Segnale Mobile    — custom color, Mobile only
 *   5. Dimensione Icone — one slider (100%-150%) driving both Wi-Fi and Mobile
 *   6. Nascondi In-Out Wi-Fi / Mobile switches
 */
public class SignalStyleFragment extends Fragment {

    // colorItems[0] = Wi-Fi, colorItems[1] = Mobile
    private final List<DarkShadowItem> colorItems = new ArrayList<>();
    private DarkShadowColorListener mColorAdapter;
    private ListWidgetAdapter mScaleAdapter;
    private int mPendingDialogId = -1;
    private int mPendingIndex    = -1;
    private RecyclerView mRecyclerView;
    // Tap sul TITOLO di "Icone Segnale WI-FI"/"Mobile" espande/comprime la riga Colore
    // sottostante (switch+expand, come altrove in app) — l'interruttore sulla riga NON
    // attiva/disattiva niente, apre invece la schermata di scelta stile (WifiIconsFragment/
    // SignalIconsFragment): l'utente lo ha chiesto esplicitamente perché uno switch "salta
    // all'occhio" più di una freccia come punto da toccare, anche se qui non rispecchia un
    // vero stato on/off — resta sempre spento, la richiesta è solo visiva/di scoperta.
    private boolean mWifiColorExpanded   = false;
    private boolean mMobileColorExpanded = false;

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
        mRecyclerView = (RecyclerView) view;
        rebuild();
    }

    /** Ricostruisce l'intera catena — chiamata all'avvio e ogni volta che si espande/comprime
     *  la riga Colore di Wi-Fi o Mobile (stesso pattern usato altrove in app per righe che si
     *  espandono sotto se stesse). */
    private void rebuild() {
        colorItems.clear();
        colorItems.add(new DarkShadowItem(
                getString(R.string.signal_icon_color_wifi_title), "WIFI_ICON_COLOR",
                Collections.emptyList(), Collections.emptyList(), null,
                ObsidianPrefs.getInt(DstWifiIconStyle.PREF_COLOR, Color.WHITE),
                ObsidianPrefs.getBoolean(DstWifiIconStyle.PREF_COLOR_ON, false)));
        colorItems.add(new DarkShadowItem(
                getString(R.string.signal_icon_color_mobile_title), "MOBILE_ICON_COLOR",
                Collections.emptyList(), Collections.emptyList(), null,
                ObsidianPrefs.getInt(DstSignalIconStyle.PREF_COLOR, Color.WHITE),
                ObsidianPrefs.getBoolean(DstSignalIconStyle.PREF_COLOR_ON, false)));
        mColorAdapter = new DarkShadowColorListener(
                colorItems,
                this::onColorEnabled,
                this::onColorDisabled,
                this::onColorSwatch,
                true, ObsidianTheme.GroupPos.SINGLE
        );

        // ── "Icone Segnale WI-FI"/"Mobile" — switch+expand invertito su richiesta utente:
        // tocco sul NOME espande/comprime la riga Colore sotto; l'INTERRUTTORE (non il
        // nome) apre la schermata di scelta stile. Lo switch non riflette un vero stato
        // on/off, resta sempre spento — serve solo come punto di tocco ben visibile. ──────
        SwitchWidgetAdapter.SwitchItem wifiItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.nav_wifi_icons), getString(R.string.nav_wifi_icons_summary),
                R.drawable.obs_wifi_aurora_signal_4, false, null);
        wifiItem.onChanged = () -> {
            wifiItem.checked = false; // momentaneo, non persiste stato
            navigate(new WifiIconsFragment(), getString(R.string.nav_wifi_icons));
        };
        wifiItem.onRowClick = () -> { mWifiColorExpanded = !mWifiColorExpanded; rebuild(); };
        wifiItem.groupPos = mWifiColorExpanded ? ObsidianTheme.GroupPos.TOP : ObsidianTheme.GroupPos.SINGLE;
        SwitchWidgetAdapter wifiAdapter = new SwitchWidgetAdapter(List.of(wifiItem));

        DarkShadowColorListener wifiColorAdapter = mWifiColorExpanded
                ? new DarkShadowColorListener(List.of(colorItems.get(0)),
                        this::onColorEnabled, this::onColorDisabled, this::onColorSwatch,
                        true, ObsidianTheme.GroupPos.BOTTOM)
                : null;

        SwitchWidgetAdapter.SwitchItem mobileItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.nav_signal_icons), getString(R.string.nav_signal_icons_summary),
                R.drawable.obs_signal_bars_3, false, null);
        mobileItem.onChanged = () -> {
            mobileItem.checked = false;
            navigate(new SignalIconsFragment(), getString(R.string.nav_signal_icons));
        };
        mobileItem.onRowClick = () -> { mMobileColorExpanded = !mMobileColorExpanded; rebuild(); };
        mobileItem.groupPos = mMobileColorExpanded ? ObsidianTheme.GroupPos.TOP : ObsidianTheme.GroupPos.SINGLE;
        SwitchWidgetAdapter mobileAdapter = new SwitchWidgetAdapter(List.of(mobileItem));

        DarkShadowColorListener mobileColorAdapter = mMobileColorExpanded
                ? new DarkShadowColorListener(List.of(colorItems.get(1)),
                        this::onColorEnabled, this::onColorDisabled, this::onColorSwatch,
                        true, ObsidianTheme.GroupPos.BOTTOM)
                : null;

        // ── Dimensione Icone — single slider driving both Wi-Fi and Mobile ─────────
        mScaleAdapter = scaleRow();

        // ── Hide in/out arrows switches ─────────────────────────────────────────
        SwitchWidgetAdapter switches = new SwitchWidgetAdapter(buildIconSwitches());

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(wifiAdapter);
        if (wifiColorAdapter != null) chain.add(wifiColorAdapter);
        chain.add(mobileAdapter);
        if (mobileColorAdapter != null) chain.add(mobileColorAdapter);
        chain.add(mScaleAdapter);
        chain.add(switches);
        mRecyclerView.setAdapter(new ConcatAdapter(chain));
    }

    // ── Dimensione Icone ─────────────────────────────────────────────────────

    // Stock icon size across every wifi/signal preset drawable (verified: all are 15dp×15dp).
    // The Xposed hooks still work in terms of a scale factor (base intrinsic × scale), so the
    // dp value picked here is converted to/from that ratio — nothing else needs to change.
    private static final float STOCK_DP = 15f;

    private ListWidgetAdapter scaleRow() {
        float current = ObsidianPrefs.getFloat(DstWifiIconStyle.PREF_ICON_SCALE, 1.0f);
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                getString(R.string.signal_icon_scale_title), formatScale(current),
                this::showScaleDialog);
        return new ListWidgetAdapter(List.of(item));
    }

    private String formatScale(float scale) {
        return Math.round(scale * STOCK_DP) + " dp";
    }

    private void showScaleDialog() {
        String[] entries = requireContext().getResources().getStringArray(R.array.signal_icon_scale_entries);
        int[]    values  = requireContext().getResources().getIntArray(R.array.signal_icon_scale_values);
        int      curDp   = Math.round(ObsidianPrefs.getFloat(DstWifiIconStyle.PREF_ICON_SCALE, 1.0f) * STOCK_DP);
        int curIdx = indexOfInt(values, curDp, 0);

        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.signal_icon_scale_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> applyScale(values[sel[0]] / STOCK_DP))
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void applyScale(float scale) {
        ObsidianPrefs.putFloat(DstWifiIconStyle.PREF_ICON_SCALE, scale);
        ObsidianPrefs.putFloat(DstSignalIconStyle.PREF_ICON_SCALE, scale);
        mScaleAdapter.getItems().get(0).valueSummary = formatScale(scale);
        mScaleAdapter.notifyItemChanged(0);

        // Mirror to boot props (same reliability rationale as saveColorPrefs above).
        try {
            String cmd = "resetprop persist.obsidian.dst.wifi_icon_scale " + scale
                    + " && resetprop persist.obsidian.dst.signal_icon_scale " + scale;
            Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (Throwable ignored) {}

        AppUtils.showRestartReminder(requireContext());
    }

    private static int indexOfInt(int[] arr, int val, int def) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == val) return i;
        return def;
    }

    // ── Colore Icona Segnale callbacks ──────────────────────────────────────────

    private boolean isWifi(DarkShadowItem item) { return colorItems.indexOf(item) == 0; }

    private void onColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        saveColorPrefs(item, true, item.getColor());
    }

    private void onColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        saveColorPrefs(item, false, item.getColor());
    }

    private void onColorSwatch(DarkShadowItem item, int dialogId) {
        boolean wifi = isWifi(item);
        String colKey = wifi ? DstWifiIconStyle.PREF_COLOR : DstSignalIconStyle.PREF_COLOR;
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(colKey + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(colKey + "_use_accent", useAccent);
                    if (useAccent) {
                        item.setColor(ObsidianTheme.accentColor());
                        onColorEnabled(item);
                        if (mColorAdapter != null) mColorAdapter.notifyDataSetChanged();
                    } else if (getActivity() instanceof MainActivity) {
                        mPendingDialogId = dialogId;
                        mPendingIndex    = colorItems.indexOf(item);
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                dialogId, item.getColor(), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() != mPendingDialogId || mPendingIndex < 0) return;
        mPendingDialogId = -1;

        DarkShadowItem item = colorItems.get(mPendingIndex);
        item.setColor(event.color());
        boolean wifi = isWifi(item);
        String colKey = wifi ? DstWifiIconStyle.PREF_COLOR : DstSignalIconStyle.PREF_COLOR;
        ObsidianPrefs.putBoolean(colKey + "_use_accent", false); // picking a colour implies custom
        ObsidianPrefs.putInt(colKey, event.color());
        if (mColorAdapter != null) mColorAdapter.notifyDataSetChanged();
        mPendingIndex = -1;
    }

    private void saveColorPrefs(DarkShadowItem item, boolean on, int color) {
        boolean wifi = isWifi(item);
        String onKey  = wifi ? DstWifiIconStyle.PREF_COLOR_ON : DstSignalIconStyle.PREF_COLOR_ON;
        String colKey = wifi ? DstWifiIconStyle.PREF_COLOR    : DstSignalIconStyle.PREF_COLOR;
        ObsidianPrefs.putBoolean(onKey, on);
        ObsidianPrefs.putInt(colKey, color);

        // Also mirror to a boot prop — same pattern as the icon preset (WifiIconsFragment).
        // The shared_prefs XML file isn't always readable from the SystemUI process (e.g.
        // right after an app reinstall, before the next full reboot re-applies the chmod fix),
        // so the prop is the reliable channel.
        String onProp    = wifi ? "persist.obsidian.dst.wifi_icon_color_on"   : "persist.obsidian.dst.mobile_icon_color_on";
        String colorProp = wifi ? "persist.obsidian.dst.wifi_icon_color"      : "persist.obsidian.dst.mobile_icon_color";
        String colorHex  = Integer.toHexString(color);
        try {
            String cmd = "resetprop " + onProp + " " + on
                    + " && resetprop " + colorProp + " " + colorHex;
            Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (Throwable ignored) {}

        AppUtils.showRestartReminder(requireContext());
    }

    // ── Hide in/out switches ──────────────────────────────────────────────────

    private List<SwitchWidgetAdapter.SwitchItem> buildIconSwitches() {
        return List.of(
                makePrefSwitch(
                        getString(R.string.hide_inout_wifi),
                        getString(R.string.hide_inout_wifi_summary),
                        "hide_inout_wifi"),
                makePrefSwitch(
                        getString(R.string.hide_inout_mobile),
                        getString(R.string.hide_inout_mobile_summary),
                        "hide_inout_mobile")
        );
    }

    private SwitchWidgetAdapter.SwitchItem makePrefSwitch(String title, String summary,
                                                           String prefKey) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary,
                ObsidianPrefs.getBoolean(prefKey, false),
                null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(prefKey, item.checked);
            AppUtils.showRestartReminder(requireContext());
        };
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).navigateTo(fragment, title);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
