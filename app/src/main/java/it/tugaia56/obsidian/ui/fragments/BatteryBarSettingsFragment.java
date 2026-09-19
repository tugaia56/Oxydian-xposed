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
 * Real OC Battery Bar options (BatteryBarView), matched to what StatusbarPadding /
 * BatteryBar.java actually read at runtime.
 */
public class BatteryBarSettingsFragment extends Fragment {

    private static final String PREF_COLORFUL        = "BBarColorful";
    private static final String PREF_ONLY_CHARGING    = "BBOnlyWhileCharging";
    private static final String PREF_ON_BOTTOM        = "BBOnBottom";
    private static final String PREF_CENTERED         = "BBSetCentered";
    private static final String PREF_ANIMATE_CHARGING = "BBAnimateCharging";
    private static final String PREF_OPACITY          = "BBOpacity";
    private static final String PREF_HEIGHT           = "BBarHeight";
    private static final String PREF_TRANSIT_COLORS   = "BBarTransitColors";
    private static final String PREF_CRITICAL_LEVEL   = "battery_bar_critical_level";
    private static final String PREF_WARNING_LEVEL    = "battery_bar_warning_level";
    private static final String PREF_CRITICAL_COLOR   = "batteryCriticalColor";
    private static final String PREF_WARNING_COLOR    = "batteryWarningColor";
    private static final String PREF_INDICATE_CHARGING     = "indicateCharging";
    private static final String PREF_CHARGING_COLOR        = "batteryChargingColor";
    private static final String PREF_INDICATE_FAST_CHARGING = "indicateFastCharging";
    private static final String PREF_FAST_CHARGING_COLOR    = "batteryFastChargingColor";
    private static final String PREF_INDICATE_POWER_SAVE    = "indicatePowerSave";
    private static final String PREF_POWER_SAVE_COLOR       = "batteryPowerSaveColor";

    private static final int DEF_CRITICAL_COLOR     = 0xFFFF0000;
    private static final int DEF_WARNING_COLOR       = 0xFFFFFF00;
    private static final int DEF_CHARGING_COLOR      = 0xFF00FF00;
    private static final int DEF_FAST_CHARGING_COLOR = 0xFF00FF00;
    private static final int DEF_POWER_SAVE_COLOR    = 0xFF00FF00;

    private static final int DLG_CRITICAL     = PREF_CRITICAL_COLOR.hashCode();
    private static final int DLG_WARNING      = PREF_WARNING_COLOR.hashCode();
    private static final int DLG_CHARGING     = PREF_CHARGING_COLOR.hashCode();
    private static final int DLG_FAST_CHARGING = PREF_FAST_CHARGING_COLOR.hashCode();
    private static final int DLG_POWER_SAVE   = PREF_POWER_SAVE_COLOR.hashCode();

    private RecyclerView mRv;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude l'opzione colore sottostante — stesso pattern di QsTilesCustomizeFragment.
    private boolean mChargingExpanded     = false;
    private boolean mFastChargingExpanded = false;
    private boolean mPowerSaveExpanded    = false;

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

        // ── Aspetto ───────────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bb_appearance_section))));
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.bb_colorful), null, PREF_COLORFUL),
                prefSwitch(getString(R.string.bb_transit_colors), null, PREF_TRANSIT_COLORS),
                sliderItem(getString(R.string.bb_opacity), PREF_OPACITY, 0, 100, 100, "%"),
                sliderItem(getString(R.string.bb_height), PREF_HEIGHT, 1, 100, 50, "")
        ));

        // ── Posizione ─────────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bb_position_section))));
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.bb_on_bottom), null, PREF_ON_BOTTOM),
                prefSwitch(getString(R.string.bb_centered), null, PREF_CENTERED)
        ));

        // ── Comportamento ─────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bb_behavior_section))));
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.bb_only_charging), null, PREF_ONLY_CHARGING),
                prefSwitch(getString(R.string.bb_animate_charging), null, PREF_ANIMATE_CHARGING)
        ));

        // ── Colori livello ────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bb_level_colors_section))));
        GroupUtils.addGroup(chain, List.of(
                sliderItem(getString(R.string.bb_critical_level), PREF_CRITICAL_LEVEL, 0, 100, 15, "%"),
                colorItem(getString(R.string.bb_critical_color), PREF_CRITICAL_COLOR, DLG_CRITICAL, DEF_CRITICAL_COLOR),
                sliderItem(getString(R.string.bb_warning_level), PREF_WARNING_LEVEL, 0, 100, 40, "%"),
                colorItem(getString(R.string.bb_warning_color), PREF_WARNING_COLOR, DLG_WARNING, DEF_WARNING_COLOR)
        ));

        // ── Colori stato ──────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.bb_state_colors_section))));

        List<Object> stateRows = new ArrayList<>();
        SwitchWidgetAdapter.SwitchItem chargingSwitch = gatingSwitch(getString(R.string.bb_indicate_charging), PREF_INDICATE_CHARGING, true);
        chargingSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_INDICATE_CHARGING, chargingSwitch.checked);
            mChargingExpanded = chargingSwitch.checked;
            rebuild();
        };
        chargingSwitch.onRowClick = () -> { mChargingExpanded = !mChargingExpanded; rebuild(); };
        stateRows.add(chargingSwitch);
        if (mChargingExpanded) {
            stateRows.add(colorItem(getString(R.string.bb_charging_color), PREF_CHARGING_COLOR, DLG_CHARGING, DEF_CHARGING_COLOR));
        }

        SwitchWidgetAdapter.SwitchItem fastChargingSwitch = gatingSwitch(getString(R.string.bb_indicate_fast_charging), PREF_INDICATE_FAST_CHARGING, false);
        fastChargingSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_INDICATE_FAST_CHARGING, fastChargingSwitch.checked);
            mFastChargingExpanded = fastChargingSwitch.checked;
            rebuild();
        };
        fastChargingSwitch.onRowClick = () -> { mFastChargingExpanded = !mFastChargingExpanded; rebuild(); };
        stateRows.add(fastChargingSwitch);
        if (mFastChargingExpanded) {
            stateRows.add(colorItem(getString(R.string.bb_fast_charging_color), PREF_FAST_CHARGING_COLOR, DLG_FAST_CHARGING, DEF_FAST_CHARGING_COLOR));
        }

        SwitchWidgetAdapter.SwitchItem powerSaveSwitch = gatingSwitch(getString(R.string.bb_indicate_power_save), PREF_INDICATE_POWER_SAVE, false);
        powerSaveSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_INDICATE_POWER_SAVE, powerSaveSwitch.checked);
            mPowerSaveExpanded = powerSaveSwitch.checked;
            rebuild();
        };
        powerSaveSwitch.onRowClick = () -> { mPowerSaveExpanded = !mPowerSaveExpanded; rebuild(); };
        stateRows.add(powerSaveSwitch);
        if (mPowerSaveExpanded) {
            stateRows.add(colorItem(getString(R.string.bb_power_save_color), PREF_POWER_SAVE_COLOR, DLG_POWER_SAVE, DEF_POWER_SAVE_COLOR));
        }
        GroupUtils.addGroup(chain, stateRows);

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        String key = event.dialogId() == DLG_CRITICAL ? PREF_CRITICAL_COLOR
                : event.dialogId() == DLG_WARNING ? PREF_WARNING_COLOR
                : event.dialogId() == DLG_CHARGING ? PREF_CHARGING_COLOR
                : event.dialogId() == DLG_FAST_CHARGING ? PREF_FAST_CHARGING_COLOR
                : event.dialogId() == DLG_POWER_SAVE ? PREF_POWER_SAVE_COLOR : null;
        if (key == null) return;
        ObsidianPrefs.putBoolean(key + "_use_accent", false); // picking a colour implies custom
        ObsidianPrefs.putInt(key, event.color());
        rebuild();
    }

    // ── Row helpers ───────────────────────────────────────────────────────────

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String key, boolean def) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, null, ObsidianPrefs.getBoolean(key, def), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private ListWidgetAdapter.ListItem colorItem(String title, String key, int dialogId, int def) {
        String label = ObsidianPrefs.getBoolean(key + "_use_accent", false)
                ? getString(R.string.color_mode_accent) : colorHex(ObsidianPrefs.getInt(key, def));
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                title, label, () -> showColorAccentChoice(title, key, dialogId, def));
        item.useAccentColor = false;
        return item;
    }

    /** Accento/Personalizzato inserted before the row opens the raw picker — Accento resolves
     *  immediately, Personalizzato opens the picker as before. Baked at selection time (no live
     *  re-resolve), same as every other picker. */
    private void showColorAccentChoice(String title, String key, int dialogId, int def) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(key + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(key + "_use_accent", useAccent);
                    if (useAccent) {
                        ObsidianPrefs.putInt(key, ObsidianTheme.accentColor());
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, ObsidianPrefs.getInt(key, def), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String colorHex(int c) {
        return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
