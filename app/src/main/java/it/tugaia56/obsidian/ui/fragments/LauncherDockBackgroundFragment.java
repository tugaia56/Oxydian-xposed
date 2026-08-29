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
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Dock Background sub-screen — mirrors OC's launcher_dock_background.xml.
 * Real hook in LauncherMod.java (hookDockBackground), Android 15+ only.
 */
public class LauncherDockBackgroundFragment extends Fragment {

    private static final String KEY_DOCK_BG          = "dockBackground";
    private static final String KEY_DOCK_BG_MATERIAL = "dockBackgroundMaterial";
    private static final String KEY_DOCK_BG_AMOUNT   = "dockBackgroundMaterialAmount";
    private static final String KEY_DOCK_BG_RADIUS   = "dockBackgroundRadius";

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

        List<Object> rows = new ArrayList<>(List.of(
                boolItem(getString(R.string.dock_background), getString(R.string.dock_background_summary), KEY_DOCK_BG),
                boolItem(getString(R.string.dock_background_material), getString(R.string.dock_background_material_summary), KEY_DOCK_BG_MATERIAL)));

        // Quantità/Raggio angolo si applicano solo a "Materiale" — visibili solo con quella attiva.
        if (ObsidianPrefs.getBoolean(KEY_DOCK_BG_MATERIAL, false)) {
            rows.add(sliderItem(getString(R.string.dock_background_amount), KEY_DOCK_BG_AMOUNT, 0, 4, 0));
            rows.add(sliderItem(getString(R.string.dock_background_radius), KEY_DOCK_BG_RADIUS, 0, 100, 30));
        }
        GroupUtils.addGroup(chain, rows);

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private SwitchWidgetAdapter.SwitchItem boolItem(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            if (KEY_DOCK_BG_MATERIAL.equals(key)) rebuild();
        };
        return item;
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, "", def,
                value -> ObsidianPrefs.putInt(key, value));
    }
}
