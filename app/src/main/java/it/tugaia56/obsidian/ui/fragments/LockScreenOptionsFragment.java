package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Opzioni Schermata di Blocco — was the standalone "Varie" section reached from
 * Schermata di Blocco (LockScreenButtonsFragment, now deleted); its content moved here:
 * lock icon + affordance buttons, SOS/carrier switches, carrier text replacement,
 * statusbar/power-menu switches (OC's lockscreen misc toggles, minus random-PIN shuffle),
 * Sfondo con Effetto Profondità, plus Copertina Album — same order as OC's lockscreen_prefs.xml
 * (DWCategory sits right before lockscreen_album_art_category there too).
 *
 * Sfondo con Effetto Profondità: OOS16 ha già un editor sfondi nativo (com.oplus.wallpapers)
 * con una scheda "Profondità" che genera l'effetto parallasse su qualunque foto scelta
 * dall'utente — verificato sul dispositivo (Sfondi → Scegli da album → foto → scheda
 * "Profondità"). Niente da ricostruire: questa riga apre direttamente quell'editor (vedi
 * AppUtils.openDepthWallpaperEditor). Copertina Album è invece un hook vero e proprio (vedi
 * AlbumArtLockscreenMod).
 */
public class LockScreenOptionsFragment extends Fragment {

    private static final String PREF_CARRIER_REPLACEMENT = "lockscreen_carrier_replacement";

    private static final String PREF_BLUR_ON     = "OBS_QS_BLUR_ON";
    private static final String PREF_BLUR_RADIUS = "OBS_QS_BLUR_RADIUS";
    private static final String PREF_BLUR_MAX    = "OBS_QS_BLUR_MAX";

    private static final String KEY_ALBUM_ART        = "lockscreen_album_art";
    private static final String KEY_ALBUM_ART_FILTER = "lockscreen_album_art_filter"; // "0".."4"
    private static final String KEY_MEDIA_BLUR       = "lockscreen_media_blur";       // 0-100

    private RecyclerView mRv;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude le opzioni sottostanti — stesso pattern di QsTilesCustomizeFragment.
    private boolean mLockBlurExpanded = false;
    private boolean mAlbumArtExpanded = false;

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
        List<RecyclerView.Adapter<?>> chain = new java.util.ArrayList<>();

        // ── Sfocatura Schermata di Blocco (blur nativo OOS — stessi pref del pannello
        // QS, si vede anche qui perché ScrimViewExImp è condiviso tra le due superfici) ──
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_blur_lockscreen_section))));
        SwitchWidgetAdapter.SwitchItem lockBlurSwitch = gatingSwitch(
                getString(R.string.qs_blur_enable_switch), null, PREF_BLUR_ON);
        lockBlurSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BLUR_ON, lockBlurSwitch.checked);
            mLockBlurExpanded = lockBlurSwitch.checked;
            rebuild();
        };
        lockBlurSwitch.onRowClick = () -> { mLockBlurExpanded = !mLockBlurExpanded; rebuild(); };
        List<Object> blurRows = new java.util.ArrayList<>();
        blurRows.add(lockBlurSwitch);
        if (mLockBlurExpanded) {
            blurRows.add(sliderItem(getString(R.string.qs_blur_intentisy), PREF_BLUR_RADIUS, 0, 100, 40, "%"));
            blurRows.add(sliderItem(getString(R.string.qs_blur_max_amount), PREF_BLUR_MAX, 0, 100, 100, "%"));
        }
        GroupUtils.addGroup(chain, blurRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.nav_lock_misc))));
        SwitchWidgetAdapter.SwitchItem lockIconItem = prefSwitch(
                getString(R.string.lockscreen_hide_lock_icon), null, "lockscreen_hide_lock_icon");
        SwitchWidgetAdapter.SwitchItem leftItem = prefSwitch(
                getString(R.string.lockscreen_affordance_remove_left), null, "lockscreen_affordance_remove_left");
        SwitchWidgetAdapter.SwitchItem rightItem = prefSwitch(
                getString(R.string.lockscreen_affordance_remove_right), null, "lockscreen_affordance_remove_right");
        SwitchWidgetAdapter.SwitchItem sosItem = prefSwitch(
                getString(R.string.lockscreen_hide_sos), getString(R.string.lockscreen_hide_sos_summary),
                "lockscreen_hide_sos");
        SwitchWidgetAdapter.SwitchItem carrierItem = prefSwitch(
                getString(R.string.lockscreen_hide_carrier), getString(R.string.lockscreen_hide_carrier_summary),
                "lockscreen_hide_carrier");
        SwitchWidgetAdapter.SwitchItem statusbarItem = prefSwitch(
                getString(R.string.lockscreen_hide_statusbar), getString(R.string.lockscreen_hide_statusbar_summary),
                "lockscreen_hide_statusbar");
        SwitchWidgetAdapter.SwitchItem powerMenuItem = prefSwitch(
                getString(R.string.lockscreen_hide_power_menu), getString(R.string.lockscreen_hide_power_menu_summary),
                "lockscreen_hide_power_menu");
        GroupUtils.addGroup(chain, List.of(
                lockIconItem, leftItem, rightItem, sosItem, carrierItem,
                carrierReplacementItem(), statusbarItem, powerMenuItem));

        // ── Sfondo con Effetto Profondità: apre l'editor nativo di OxygenOS, non un
        // hook nostro — vedi il commento in testa alla classe. ──
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.depth_wallpaper_title))));
        ListWidgetAdapter.ListItem dwItem = new ListWidgetAdapter.ListItem(
                getString(R.string.depth_wallpaper_title), getString(R.string.depth_wallpaper_summary),
                AppUtils::openDepthWallpaperEditor);
        chain.add(new ListWidgetAdapter(List.of(dwItem)));

        // ── Copertina Album ──────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.lockscreen_album_art))));
        SwitchWidgetAdapter.SwitchItem albumArtItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.lockscreen_album_art), getString(R.string.lockscreen_album_art_summary),
                ObsidianPrefs.getBoolean(KEY_ALBUM_ART, false), null);
        albumArtItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ALBUM_ART, albumArtItem.checked);
            mAlbumArtExpanded = albumArtItem.checked;
            rebuild();
        };
        albumArtItem.onRowClick = () -> { mAlbumArtExpanded = !mAlbumArtExpanded; rebuild(); };
        List<Object> albumRows = new java.util.ArrayList<>();
        albumRows.add(albumArtItem);
        if (mAlbumArtExpanded) {
            albumRows.add(filterItem());
            albumRows.add(blurItem());
        }
        GroupUtils.addGroup(chain, albumRows);

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            AppUtils.showRestartReminder(requireContext());
        };
        return item;
    }

    /** A switch that also controls the visibility of other rows — rebuilds the list on change. */
    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }

    private ListWidgetAdapter.ListItem carrierReplacementItem() {
        String current = ObsidianPrefs.getString(PREF_CARRIER_REPLACEMENT, "");
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                getString(R.string.lockscreen_carrier_replacement), textOrSummary(current),
                this::showCarrierReplacementDialog);
        item.useAccentColor = false;
        return item;
    }

    private void showCarrierReplacementDialog() {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(ObsidianPrefs.getString(PREF_CARRIER_REPLACEMENT, ""));
        et.setSingleLine(true);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.addView(et);

        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lockscreen_carrier_replacement)
                .setMessage(R.string.lockscreen_carrier_replacement_summary)
                .setView(layout)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String text = et.getText().toString().trim();
                    ObsidianPrefs.putString(PREF_CARRIER_REPLACEMENT, text);
                    rebuild();
                    AppUtils.showRestartReminder(requireContext());
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String textOrSummary(String text) {
        return text.isEmpty() ? getString(R.string.lockscreen_carrier_replacement_summary) : text;
    }

    // ── Copertina Album filter + blur ────────────────────────────────────────
    // No restart reminder here: AlbumArtLockscreenMod re-renders live on every
    // pref change via XPrefs' cross-process listener (updatePrefs()).

    private ListWidgetAdapter.ListItem filterItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.lockscreen_album_art_filter), filterLabel(), this::showFilterDialog);
    }

    private String filterLabel() {
        String[] entries = getResources().getStringArray(R.array.lockscreen_album_art_filter_entries);
        int idx = parseFilterIndex(entries.length);
        return entries[idx];
    }

    private int parseFilterIndex(int entryCount) {
        try {
            int idx = Integer.parseInt(ObsidianPrefs.getString(KEY_ALBUM_ART_FILTER, "0"));
            return (idx >= 0 && idx < entryCount) ? idx : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showFilterDialog() {
        String[] entries = getResources().getStringArray(R.array.lockscreen_album_art_filter_entries);
        int current = parseFilterIndex(entries.length);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(R.string.lockscreen_album_art_filter)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(KEY_ALBUM_ART_FILTER, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem blurItem() {
        int current = ObsidianPrefs.getInt(KEY_MEDIA_BLUR, 30);
        return new SliderWidgetAdapter.SliderItem(
                getString(R.string.lockscreen_media_blur), current, 0, 100, "%", 30,
                value -> ObsidianPrefs.putInt(KEY_MEDIA_BLUR, value));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
