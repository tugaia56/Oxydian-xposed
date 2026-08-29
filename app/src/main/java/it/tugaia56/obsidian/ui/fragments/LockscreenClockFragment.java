package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

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
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Orologio Schermata di Blocco — UI port of OC's LockscreenClockFragment (ClockPickerFragment
 * + LockscreenClockPrefs / lockscreen_clock.xml). UI/prefs only for now, no Xposed hook wired.
 *
 * Mirrors OC's dependency-gated visibility: rows only appear once their controlling switch
 * is on (font picker needs "Font Personalizzato", colours need "Colore Personalizzato", image
 * pickers need their own switch) — the whole list rebuilds on every gating switch change,
 * same idea as Android's Preference android:dependency, just done by hand since this screen
 * uses a plain RecyclerView instead of PreferenceFragmentCompat.
 */
public class LockscreenClockFragment extends Fragment {

    private static final String KEY_SWITCH       = "lockscreen_custom_clock_switch";
    private static final String KEY_STYLE        = "lockscreen_custom_clock_style";       // "0".."4"
    private static final String KEY_CUSTOM_FONT  = "lockscreen_custom_font";
    private static final String KEY_COLOR_SWITCH = "lockscreen_custom_color_switch";
    private static final String KEY_LINE_HEIGHT  = "lockscreen_clock_line_height";        // -120..120 dp
    private static final String KEY_TEXT_SCALING = "lockscreen_text_scaling";             // 50-150 %
    private static final String KEY_FORMAT       = "lockscreen_clock_custom_format";
    private static final String KEY_TOP_MARGIN   = "lockscreen_top_margin";               // 0..600 dp
    private static final String KEY_BOTTOM_MARGIN= "lockscreen_bottom_margin";            // -200..600 dp
    private static final String KEY_BOTTOM_MARGIN_AOD = "lockscreen_bottom_margin_aod";   // -200..600 dp

    private static final String COLOR_ACCENT1 = "lockscreen_clock_color_code_accent1";
    private static final String COLOR_ACCENT2 = "lockscreen_clock_color_code_accent2";
    private static final String COLOR_ACCENT3 = "lockscreen_clock_color_code_accent3";
    private static final String COLOR_TEXT1   = "lockscreen_clock_color_code_text1";
    private static final String COLOR_TEXT2   = "lockscreen_clock_color_code_text2";

    private RecyclerView mRv;
    private final List<DarkShadowItem> mColorItems = new ArrayList<>();
    private DarkShadowColorListener mClockColorsAdapter;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude l'opzione sottostante — stesso pattern di QsTilesCustomizeFragment.
    private boolean mColorExpanded = ObsidianPrefs.getBoolean(KEY_COLOR_SWITCH, false);
    private boolean mFontExpanded  = ObsidianPrefs.getBoolean(KEY_CUSTOM_FONT, false);

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
        mRv = (RecyclerView) view;
        rebuild();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh after returning from the clock style picker (pref may have changed while
        // this fragment was paused underneath it on the back stack).
        if (mRv != null) rebuild();
    }

    private void rebuild() {
        mColorItems.clear();
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        GroupUtils.addGroup(chain, List.of(
                gatingSwitch(getString(R.string.lockscreen_clock_switch), null, KEY_SWITCH),
                clockStylePickerItem()));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.lockscreen_clock_prefs))));
        SwitchWidgetAdapter.SwitchItem colorSwitch = gatingSwitch(getString(R.string.lockscreen_clock_custom_color_title), null, KEY_COLOR_SWITCH);
        colorSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_COLOR_SWITCH, colorSwitch.checked);
            mColorExpanded = colorSwitch.checked;
            rebuild();
        };
        colorSwitch.onRowClick = () -> { mColorExpanded = !mColorExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(colorSwitch));
        if (mColorExpanded) chain.add(clockColorsRow());

        List<Object> restRows = new ArrayList<>();
        restRows.add(sliderItem(getString(R.string.lockscreen_font_line_height_title), KEY_LINE_HEIGHT, -120, 120, 0, "dp", true));
        restRows.add(sliderItem(getString(R.string.lockscreen_clock_text_scaling), KEY_TEXT_SCALING, 50, 150, 100, "%", true));

        SwitchWidgetAdapter.SwitchItem fontSwitch = gatingSwitch(getString(R.string.lockscreen_clock_font_custom_enabled), null, KEY_CUSTOM_FONT);
        fontSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_CUSTOM_FONT, fontSwitch.checked);
            mFontExpanded = fontSwitch.checked;
            rebuild();
        };
        fontSwitch.onRowClick = () -> { mFontExpanded = !mFontExpanded; rebuild(); };
        restRows.add(fontSwitch);
        if (mFontExpanded) restRows.add(stubItem(getString(R.string.pick_font_title), getString(R.string.pick_font_summary)));

        restRows.add(editTextItem(getString(R.string.lockscreen_clock_custom_format_title),
                getString(R.string.lockscreen_clock_custom_format_summary), KEY_FORMAT));
        GroupUtils.addGroup(chain, restRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.lockscreen_clock_custom_margins))));
        GroupUtils.addGroup(chain, List.of(
                sliderItem(getString(R.string.lockscreen_clock_top_margin_title), KEY_TOP_MARGIN, 0, 600, 0, "dp", true),
                sliderItem(getString(R.string.lockscreen_clock_bottom_margin_title), KEY_BOTTOM_MARGIN, -200, 600, 40, "dp", true),
                sliderItem(getString(R.string.lockscreen_clock_bottom_margin_title), KEY_BOTTOM_MARGIN_AOD, -200, 600, 40, "dp")));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Clock colours (5 swatches: accent1/2/3, text1/2) ───────────────────────

    private DarkShadowColorListener clockColorsRow() {
        List<DarkShadowItem> clockColors = List.of(
                colorItem(getString(R.string.general_color_accent1), COLOR_ACCENT1, 0xFF006781),
                colorItem(getString(R.string.general_color_accent2), COLOR_ACCENT2, 0xFF006781),
                colorItem(getString(R.string.general_color_accent3), COLOR_ACCENT3, 0xFF006781),
                colorItem(getString(R.string.general_color_text1), COLOR_TEXT1, 0xFFFFFFFF),
                colorItem(getString(R.string.general_color_text2), COLOR_TEXT2, 0xFF000000));
        mColorItems.addAll(clockColors);
        mClockColorsAdapter = new DarkShadowColorListener(
                clockColors, this::onColorEnabled, this::onColorDisabled, this::onColorSwatch);
        return mClockColorsAdapter;
    }

    private DarkShadowItem colorItem(String label, String key, int def) {
        return new DarkShadowItem(label, key, java.util.Collections.emptyList(),
                java.util.Collections.emptyList(), null,
                ObsidianPrefs.getInt(key, def), ObsidianPrefs.getBoolean(key + "_on", false));
    }

    private void onColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        ObsidianPrefs.putInt(item.getOverlayName(), item.getColor());
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", true);
        rebuild();
    }

    private void onColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", false);
        rebuild();
    }

    private void onColorSwatch(DarkShadowItem item, int dialogId) {
        showClockColorAccentChoice(item, dialogId);
    }

    /** Accento/Personalizzato inserted before the swatch opens the raw picker — Accento resolves
     *  immediately (reuses onColorEnabled's existing save path, which already calls rebuild()),
     *  Personalizzato opens the picker as before. Baked at selection time (no live re-resolve),
     *  same as every other picker. */
    private void showClockColorAccentChoice(DarkShadowItem item, int dialogId) {
        String colorKey = item.getOverlayName();
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(colorKey + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(colorKey + "_use_accent", useAccent);
                    if (useAccent) {
                        item.setColor(ObsidianTheme.accentColor());
                        onColorEnabled(item);
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        for (DarkShadowItem item : mColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            ObsidianPrefs.putBoolean(item.getOverlayName() + "_use_accent", false); // picking implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            rebuild();
            return;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    // ── Generic row helpers (UI/prefs only, no restart reminder — not wired yet) ──

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

    private ListWidgetAdapter.ListItem stubItem(String title, String summary) {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(title, summary,
                () -> Toast.makeText(requireContext(), R.string.section_wip_summary, Toast.LENGTH_SHORT).show());
        item.useAccentColor = false;
        return item;
    }

    private ListWidgetAdapter.ListItem editTextItem(String title, String summary, String key) {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                title, textOrDefault(ObsidianPrefs.getString(key, ""), summary),
                () -> showEditTextDialog(title, summary, key));
        item.useAccentColor = false;
        return item;
    }

    private void showEditTextDialog(String title, String summary, String key) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(ObsidianPrefs.getString(key, ""));
        et.setSingleLine(true);
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.addView(et);

        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(summary)
                .setView(layout)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String text = et.getText().toString().trim();
                    ObsidianPrefs.putString(key, text);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String textOrDefault(String text, String fallback) {
        return text.isEmpty() ? fallback : text;
    }

    /** Riga "Stile orologio" — apre la griglia con anteprima live di tutti i 61 stili invece
     *  di un elenco testuale, così si vede subito quale si sta scegliendo. */
    private ListWidgetAdapter.ListItem clockStylePickerItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.lockscreen_clock_style_title),
                choiceLabel(KEY_STYLE, R.array.lockscreen_clock_style_entries),
                () -> {
                    if (getActivity() instanceof MainActivity mainActivity) {
                        mainActivity.navigateTo(ClockStylePickerFragment.newInstance(
                                        KEY_STYLE, KEY_COLOR_SWITCH, COLOR_ACCENT1, COLOR_TEXT1,
                                        true, ObsidianTheme.cardColor()),
                                getString(R.string.lockscreen_clock_style_title));
                    }
                });
    }

    private ListWidgetAdapter singleChoiceRow(String title, String key, int entriesArrayRes) {
        return singleChoiceRow(title, key, entriesArrayRes, false);
    }

    private ListWidgetAdapter singleChoiceRow(String title, String key, int entriesArrayRes, boolean affectsPreview) {
        final ListWidgetAdapter[] adapterRef = new ListWidgetAdapter[1];
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes, adapterRef[0], affectsPreview));
        ListWidgetAdapter adapter = new ListWidgetAdapter(List.of(item));
        adapterRef[0] = adapter;
        return adapter;
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes, ListWidgetAdapter adapter, boolean affectsPreview) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    if (affectsPreview) {
                        rebuild();
                    } else {
                        adapter.getItems().get(0).valueSummary = choiceLabel(key, entriesArrayRes);
                        adapter.notifyItemChanged(0);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        return sliderItem(title, key, min, max, def, unit, false);
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit, boolean affectsPreview) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> {
                    ObsidianPrefs.putInt(key, value);
                    if (affectsPreview) rebuild();
                });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
