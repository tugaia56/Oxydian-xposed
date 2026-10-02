package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;

/**
 * Always-On Display hub — mirrors OC's AOD section: clock, weather, edge lighting.
 * UI-only ports for now (AodClockFragment / AodWeatherFragment / AodEdgeLightFragment) —
 * visible with previews, but not wired to a hook yet.
 */
public class AodFragment extends Fragment {

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

        List<NavAdapter.NavItem> items = List.of(

                new NavAdapter.NavItem(
                        R.drawable.ic_clock,
                        getString(R.string.nav_aod_clock),
                        getString(R.string.nav_aod_clock_summary),
                        () -> navigate(new AodClockFragment(),
                                getString(R.string.nav_aod_clock))),

                new NavAdapter.NavItem(
                        R.drawable.ic_palette,
                        getString(R.string.nav_aod_weather),
                        getString(R.string.nav_aod_weather_summary),
                        () -> navigate(new AodWeatherFragment(),
                                getString(R.string.nav_aod_weather))),

                new NavAdapter.NavItem(
                        R.drawable.ic_drawing,
                        getString(R.string.nav_aod_edge_lighting),
                        getString(R.string.nav_aod_edge_lighting_summary),
                        () -> navigate(new AodEdgeLightFragment(),
                                getString(R.string.nav_aod_edge_lighting)))
        );
        // Interruttore: nasconde l'icona impronta solo con lo schermo spento (stessa preferenza
        // della schermata Icona Impronta Digitale).
        it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter.SwitchItem fpItem =
                new it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter.SwitchItem(
                        getString(R.string.lockscreen_fp_hide_aod), getString(R.string.lockscreen_fp_hide_aod_summary),
                        it.tugaia56.obsidian.utils.ObsidianPrefs.getBoolean("lockscreen_fp_hide_aod", false), null);
        fpItem.onChanged = () -> it.tugaia56.obsidian.utils.ObsidianPrefs.putBoolean("lockscreen_fp_hide_aod", fpItem.checked);
        java.util.List<RecyclerView.Adapter<?>> chain = new java.util.ArrayList<>();
        chain.add(new NavAdapter(items, 0xFFFF5722)); // deep orange, colore categoria "Always-On Display"
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(fpItem));
        rv.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }
}
