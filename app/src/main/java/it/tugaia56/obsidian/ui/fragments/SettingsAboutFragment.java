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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Info" — GitHub/Support Group open the real links; Compatibilità Tema explains the
 * light/dark mod behaviour; Credits apre CreditsFragment (bozza, 2026-08-28).
 */
public class SettingsAboutFragment extends Fragment {

    private static final String GITHUB_URL = "https://github.com/tugaia56/Oxydian-xposed";
    private static final String SUPPORT_GROUP_URL = "https://t.me/OnePlus_Mods_Theme";
    private static final String WEBSITE_URL = "https://mythemedarkandmore.altervista.org/";

    private void openUrl(String url) {
        if (url == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {}
    }

    private void showThemeCompatDialog() {
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme_compat)
                .setMessage(R.string.settings_theme_compat_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 8, 0, 24);
        rv.setClipToPadding(false);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = (RecyclerView) view;

        rv.setAdapter(new ListWidgetAdapter(List.of(
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_github),
                        getString(R.string.settings_github_summary),
                        () -> openUrl(GITHUB_URL)),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_support_group),
                        getString(R.string.settings_support_group_summary),
                        () -> openUrl(SUPPORT_GROUP_URL)),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_website),
                        getString(R.string.settings_website_summary),
                        () -> openUrl(WEBSITE_URL)),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_theme_compat),
                        getString(R.string.settings_theme_compat_summary),
                        this::showThemeCompatDialog),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_credits),
                        getString(R.string.settings_credits_summary),
                        () -> {
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).navigateTo(
                                        new CreditsFragment(), getString(R.string.settings_credits));
                            }
                        })
        )));
    }
}
