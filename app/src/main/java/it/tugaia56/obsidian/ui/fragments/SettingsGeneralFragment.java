package it.tugaia56.obsidian.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Generale" — scheda predefinita ("Disposizione") e Tema (Sistema/Chiaro/Scuro) sono
 * reali; il resto (lingua) resta UI-only, mirror di OC's General Settings category.
 */
public class SettingsGeneralFragment extends Fragment {

    private static final String[] TAB_VALUES = {"dst", "mods", "settings"};

    /** Per-app language (AndroidX AppCompatDelegate.setApplicationLocales — persisted
     *  automatically by AppCompat 1.6+, no manual pref needed). null tag = "System default". */
    private static final String[] LANGUAGE_TAGS = {
            null, "en", "it-IT", "ru", "pt-BR", "id", "es", "tr", "de", "zh-CN", "vi", "uk"
    };
    private static final String[] LANGUAGE_NAMES = {
            null, "English", "Italiano", "Русский", "Português (Brasil)", "Bahasa Indonesia",
            "Español", "Türkçe", "Deutsch", "中文（简体）", "Tiếng Việt", "Українська"
    };
    /** Same key/semantics as OC's "moreLogging" — sets XposedMods.mDebug on every running mod
     *  (see XPrefs.loadEverything()), gating the extra XposedBridge.log(...) calls mods can
     *  opt into via the inherited log() helper. Off by default — verbose only when needed. */
    public static final String KEY_MORE_LOGGING = "more_logging";

    private RecyclerView mRv;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 8, 0, 24);
        rv.setClipToPadding(false);
        mRv = rv;
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rebuild();
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.settings_appearance_section))));
        GroupUtils.addGroup(chain, List.of(
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_theme_title),
                        themeModeLabel(),
                        this::showThemeModeDialog),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_language),
                        currentLanguageLabel(),
                        this::showLanguageDialog)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.settings_layout_section))));
        chain.add(new ListWidgetAdapter(List.of(
                new ListWidgetAdapter.ListItem(
                        getString(R.string.settings_default_tab_title),
                        defaultTabLabel(),
                        this::showDefaultTabDialog))));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.settings_debug_section))));
        SwitchWidgetAdapter.SwitchItem moreLoggingItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.more_logging_title),
                getString(R.string.more_logging_summary),
                ObsidianPrefs.getBoolean(KEY_MORE_LOGGING, false),
                null);
        moreLoggingItem.onChanged = () ->
                ObsidianPrefs.putBoolean(KEY_MORE_LOGGING, moreLoggingItem.checked);
        chain.add(new SwitchWidgetAdapter(List.of(moreLoggingItem)));

        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    private String themeModeLabel() {
        return switch (ObsidianTheme.themeMode()) {
            case ObsidianTheme.THEME_LIGHT -> getString(R.string.settings_theme_light);
            case ObsidianTheme.THEME_DARK  -> getString(R.string.settings_theme_dark);
            default -> getString(R.string.settings_theme_system);
        };
    }

    private void showThemeModeDialog() {
        int current = ObsidianTheme.themeMode();
        String[] entries = {
                getString(R.string.settings_theme_system),
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark)
        };
        final int[] selected = {current};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(ObsidianTheme.KEY_THEME_MODE, selected[0]);
                    // refreshThemeMode() calls AppCompatDelegate.setDefaultNightMode(), which
                    // already triggers an automatic recreate() of every active AppCompatActivity
                    // when the effective night mode actually changes — an explicit recreate()
                    // here too raced with it and left the UI in a stuck, dimmed half-drawn state.
                    ObsidianTheme.refreshThemeMode(requireContext());
                    // Keep "Preset Sfondo" (DST custom background) in sync with the Tema just
                    // picked — off in Chiaro so it doesn't keep masking the light palette, on
                    // in Scuro to restore whatever colour was already configured there.
                    ObsidianTheme.syncBackgroundPresetToTheme();
                })
                .setNegativeButton(R.string.close, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    private String currentLanguageLabel() {
        LocaleListCompat applied = AppCompatDelegate.getApplicationLocales();
        if (applied.isEmpty()) return getString(R.string.settings_theme_system);
        String tag = applied.get(0).toLanguageTag();
        for (int i = 1; i < LANGUAGE_TAGS.length; i++) {
            if (LANGUAGE_TAGS[i].equalsIgnoreCase(tag)) return LANGUAGE_NAMES[i];
        }
        return applied.get(0).getDisplayName(applied.get(0));
    }

    private void showLanguageDialog() {
        LocaleListCompat applied = AppCompatDelegate.getApplicationLocales();
        String currentTag = applied.isEmpty() ? null : applied.get(0).toLanguageTag();
        int currentIdx = 0;
        if (currentTag != null) {
            for (int i = 1; i < LANGUAGE_TAGS.length; i++) {
                if (LANGUAGE_TAGS[i].equalsIgnoreCase(currentTag)) { currentIdx = i; break; }
            }
        }

        // Every entry shows "Native name (name translated into the language the dialog is
        // currently displayed in)" — e.g. from an Italian UI: "Українська (Ucraino)" — so the
        // reader can always tell what a language is without recognizing its own script/alphabet.
        String[] localized = getResources().getStringArray(R.array.language_localized_names);
        String[] entries = new String[LANGUAGE_NAMES.length];
        entries[0] = getString(R.string.settings_theme_system);
        for (int i = 1; i < LANGUAGE_NAMES.length; i++) {
            String native_ = LANGUAGE_NAMES[i];
            String loc = (i - 1 < localized.length) ? localized[i - 1] : null;
            entries[i] = (loc == null || loc.equalsIgnoreCase(native_))
                    ? native_ : native_ + " (" + loc + ")";
        }

        final int[] selected = {currentIdx};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(entries, currentIdx, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String tag = LANGUAGE_TAGS[selected[0]];
                    AppCompatDelegate.setApplicationLocales(
                            tag == null ? LocaleListCompat.getEmptyLocaleList()
                                    : LocaleListCompat.forLanguageTags(tag));
                    // setApplicationLocales recreates every active AppCompatActivity on its own —
                    // same reasoning as the Tema dialog above, no explicit recreate() here.
                })
                .setNegativeButton(R.string.close, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    private String defaultTabLabel() {
        String value = ObsidianPrefs.getString(MainActivity.KEY_DEFAULT_TAB, TAB_VALUES[0]);
        return switch (value) {
            case "mods" -> getString(R.string.tab_mods);
            case "settings" -> getString(R.string.tab_settings);
            default -> getString(R.string.tab_dst);
        };
    }

    private void showDefaultTabDialog() {
        String current = ObsidianPrefs.getString(MainActivity.KEY_DEFAULT_TAB, TAB_VALUES[0]);
        int currentIdx = 0;
        for (int i = 0; i < TAB_VALUES.length; i++) if (TAB_VALUES[i].equals(current)) currentIdx = i;

        String[] entries = {
                getString(R.string.tab_dst), getString(R.string.tab_mods), getString(R.string.tab_settings)
        };
        final int[] selected = {currentIdx};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_default_tab_title)
                .setSingleChoiceItems(entries, currentIdx, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(MainActivity.KEY_DEFAULT_TAB, TAB_VALUES[selected[0]]);
                    rebuild();
                })
                .setNegativeButton(R.string.close, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }
}
