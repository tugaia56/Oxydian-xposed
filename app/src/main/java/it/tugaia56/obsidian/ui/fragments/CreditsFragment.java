package it.tugaia56.obsidian.ui.fragments;

import android.content.Intent;
import android.net.Uri;
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
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;

/**
 * Ringraziamenti/Librerie — bozza (2026-08-28), da rivedere più avanti.
 */
public class CreditsFragment extends Fragment {

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {}
    }

    private ListWidgetAdapter.ListItem link(String title, String summary, String url) {
        return new ListWidgetAdapter.ListItem(title, summary, () -> openUrl(url));
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
        RecyclerView rv = (RecyclerView) view;

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.credits_thanks))));
        GroupUtils.addGroup(chain, List.of(
                link("Oxygen Customizer", getString(R.string.credits_oc_summary),
                        "https://github.com/DHD2280/Oxygen-Customizer"),
                link("crDroid", getString(R.string.credits_crdroid_summary),
                        "https://github.com/crdroidandroid"),
                link("LSPosed", getString(R.string.credits_lsposed_summary),
                        "https://github.com/LSPosed/LSPosed")));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.credits_libraries))));
        GroupUtils.addGroup(chain, List.of(
                link("EventBus", "greenrobot", "https://github.com/greenrobot/EventBus"),
                link("ColorPicker", "Jared Rummler", "https://github.com/jaredrummler/ColorPicker"),
                link("libsu", "topjohnwu", "https://github.com/topjohnwu/libsu"),
                link("RemotePreferences", "crossbowffs", "https://github.com/crossbowffs/RemotePreferences"),
                link("Lottie", "Airbnb", "https://github.com/airbnb/lottie-android")));

        rv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }
}
