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
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Numero di riquadri — porting reale di OC's QSTiles (chiavi pref identiche, stessi
 * range). Vale solo per lo stile QS "Classico": OC stesso disabilita esplicitamente
 * l'equivalente per QS "Separati" su ogni SDK diverso da 35 (mCustomLayout forzato a
 * false), quindi non c'è un riferimento funzionante da portare per SDK36/OOS16 — nessun
 * hook per "Separati" qui.
 */
public class QsTilesFragment extends Fragment {

    private static final String PREF_CUSTOMIZE   = "quick_settings_tiles_customize";
    private static final String PREF_QUICK_COUNT = "quick_settings_quick_tiles_seek";
    private static final String PREF_ROWS        = "quick_settings_tiles_rows_seek";
    private static final String PREF_COLUMNS     = "quick_settings_tiles_horizontal_columns_seek";
    private static final String PREF_COLUMNS_LS  = "quick_settings_tiles_vertical_columns_seek";

    private boolean mCustomizeExpanded = false;
    private RecyclerView mRv;

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

        chain.add(stubNotice());

        boolean on = ObsidianPrefs.getBoolean(PREF_CUSTOMIZE, false);
        SwitchWidgetAdapter.SwitchItem customizeItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.qs_tiles_customize), null, on, null);
        customizeItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_CUSTOMIZE, customizeItem.checked);
            mCustomizeExpanded = customizeItem.checked;
            rebuild();
        };
        customizeItem.onRowClick = () -> { mCustomizeExpanded = !mCustomizeExpanded; rebuild(); };
        chain.add(new SwitchWidgetAdapter(List.of(customizeItem)));

        if (mCustomizeExpanded) {
            chain.add(sliderRow(getString(R.string.qs_tiles_quick_count),
                    getString(R.string.qs_tiles_quick_count_summary), PREF_QUICK_COUNT, 1, 6, 5));

            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_portrait))));
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(
                    sliderItem(getString(R.string.qs_tiles_rows), PREF_ROWS, 1, 6, 3),
                    sliderItem(getString(R.string.qs_tiles_columns), PREF_COLUMNS, 1, 7, 4)));

            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_tiles_landscape))));
            chain.add(sliderRow(getString(R.string.qs_tiles_columns), null, PREF_COLUMNS_LS, 1, 6, 4));
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private ListWidgetAdapter stubNotice() {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                getString(R.string.qs_tiles_separate_note), null, null);
        item.useAccentColor = false;
        return new ListWidgetAdapter(List.of(item));
    }

    private SliderWidgetAdapter sliderRow(String title, String summary, String key, int min, int max, int def) {
        return new SliderWidgetAdapter(List.of(sliderItem(title, key, min, max, def)));
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, "", def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
