package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

import static it.tugaia56.obsidian.xposed.hooks.systemui.BatteryStyleManager.PREF_ENABLED;
import static it.tugaia56.obsidian.xposed.hooks.systemui.BatteryStyleManager.PREF_PERCENT_SIZE;

/**
 * "Stile Icone Batteria" — menu principale, solo 4 comandi.
 * Ogni comando è uno switch che, se attivo, mostra UNA riga per aprire la relativa
 * sotto-pagina — niente altro visibile finché non si attiva, per evitare la
 * confusione di avere tutte le scelte sempre in vista.
 */
public class BatteryIconFragment extends Fragment {

    public static final String PREF_BATTERY_BAR          = "BBarEnabled";
    public static final String PREF_PERCENT_SIZE_ENABLED = "battery_text_size_switch";
    public static final String PREF_CHARGING_ICON_ENABLED = "battery_icon_change_charging_icon";

    private String[] mSizeEntries;
    private int[]    mSizeValues;
    private RecyclerView mRv;

    private boolean mBatteryStyleExpanded  = false;
    private boolean mBatteryBarExpanded    = false;
    private boolean mPercentSizeExpanded   = false;
    private boolean mChargingIconExpanded  = false;

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
        mSizeEntries = requireContext().getResources().getStringArray(R.array.battery_percent_size_entries);
        mSizeValues  = requireContext().getResources().getIntArray(R.array.battery_percent_size_values);
        rebuild();
    }

    @Override
    public void onResume() {
        super.onResume();
        rebuild(); // refresh switches/rows after returning from a sub-page
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.battery_icon_section))));

        addGatedCommand(chain,
                getString(R.string.battery_icon_enabled),
                PREF_ENABLED,
                getString(R.string.battery_style_prefs_page),
                BatteryStylePrefsFragment::new,
                mBatteryStyleExpanded, v -> mBatteryStyleExpanded = v);

        addGatedCommand(chain,
                getString(R.string.battery_bar_enabled),
                PREF_BATTERY_BAR,
                getString(R.string.battery_bar_settings),
                BatteryBarSettingsFragment::new,
                mBatteryBarExpanded, v -> mBatteryBarExpanded = v);

        addPercentSizeCommand(chain);

        addGatedCommand(chain,
                getString(R.string.battery_charging_icon_enable),
                PREF_CHARGING_ICON_ENABLED,
                getString(R.string.battery_charging_icon_page),
                BatteryChargingIconFragment::new,
                mChargingIconExpanded, v -> mChargingIconExpanded = v);

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    /** Switch (abilita soltanto) + tocco sul nome per mostrare la riga di navigazione. */
    private void addGatedCommand(List<RecyclerView.Adapter<?>> chain, String title, String key,
                                  String pageTitle, java.util.function.Supplier<Fragment> pageSupplier,
                                  boolean expanded, java.util.function.Consumer<Boolean> setExpanded) {
        boolean on = ObsidianPrefs.getBoolean(key, false);
        SwitchWidgetAdapter.SwitchItem sw = new SwitchWidgetAdapter.SwitchItem(title, null, on, null);
        sw.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, sw.checked);
            setExpanded.accept(sw.checked);
            rebuild();
        };
        sw.onRowClick = () -> { setExpanded.accept(!expanded); rebuild(); };
        chain.add(new SwitchWidgetAdapter(List.of(sw)));
        if (expanded) {
            ListWidgetAdapter.ListItem navItem = new ListWidgetAdapter.ListItem(
                    pageTitle, null,
                    () -> { if (getActivity() instanceof MainActivity)
                        ((MainActivity) getActivity()).navigateTo(pageSupplier.get(), pageTitle); });
            chain.add(new ListWidgetAdapter(List.of(navItem)));
        }
    }

    /** Switch (abilita soltanto) + tocco sul nome per mostrare "Dimensione font batteria". */
    private void addPercentSizeCommand(List<RecyclerView.Adapter<?>> chain) {
        boolean on = ObsidianPrefs.getBoolean(PREF_PERCENT_SIZE_ENABLED, false);
        SwitchWidgetAdapter.SwitchItem sw = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.battery_percent_size_enable), null, on, null);
        sw.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_PERCENT_SIZE_ENABLED, sw.checked);
            mPercentSizeExpanded = sw.checked;
            rebuild();
        };
        sw.onRowClick = () -> { mPercentSizeExpanded = !mPercentSizeExpanded; rebuild(); };
        chain.add(new SwitchWidgetAdapter(List.of(sw)));
        if (mPercentSizeExpanded) {
            ListWidgetAdapter.ListItem sizeItem = new ListWidgetAdapter.ListItem(
                    getString(R.string.battery_percent_size), getCurrentSizeLabel(), this::showSizeDialog);
            GroupUtils.addGroup(chain, List.of((Object) sizeItem));
        }
    }

    private void showSizeDialog() {
        int savedSize = ObsidianPrefs.getInt(PREF_PERCENT_SIZE, 12);
        int currentIdx = 0;
        for (int i = 0; i < mSizeValues.length; i++) {
            if (mSizeValues[i] == savedSize) { currentIdx = i; break; }
        }
        final int[] selected = {currentIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.battery_percent_size)
                .setSingleChoiceItems(mSizeEntries, currentIdx, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_PERCENT_SIZE, mSizeValues[selected[0]]);
                    rebuild();
                    Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.close, null)
                .show());
    }

    private String getCurrentSizeLabel() {
        int size = ObsidianPrefs.getInt(PREF_PERCENT_SIZE, 12);
        if (mSizeEntries == null) return size + " sp";
        for (int i = 0; i < mSizeValues.length; i++) {
            if (mSizeValues[i] == size) return mSizeEntries[i];
        }
        return size + " sp";
    }
}
