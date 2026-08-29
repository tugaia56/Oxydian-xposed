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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter.SwitchItem;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Sub-screen for Clock "Ora & data" settings (opened from ClockDateFragment):
 *  – Nascondi orologio: auto-hide when launcher visible / on interval
 *  – Impostazioni formato: seconds, AM/PM style, date display + position
 *  – Avanzate: custom text before / after clock + smaller toggle
 */
public class ClockOraDataFragment extends Fragment {

    // ── Pref keys ──────────────────────────────────────────────────────────────
    private static final String PREF_AUTO_HIDE_LAUNCHER = "status_bar_clock_auto_hide_launcher";
    private static final String PREF_AUTO_HIDE          = "status_bar_clock_auto_hide";
    private static final String PREF_SHOW_SECONDS       = "status_bar_clock_seconds";
    private static final String PREF_AM_PM              = "status_bar_am_pm";
    private static final String PREF_DATE_DISPLAY       = "status_bar_clock_date_display";
    private static final String PREF_DATE_POS           = "status_bar_clock_date_position";
    private static final String PREF_DATE_FORMAT        = "status_bar_clock_date_format";
    private static final String PREF_BEFORE_TEXT        = "sbc_before_clock_format";
    private static final String PREF_BEFORE_SMALL       = "sbc_before_small";
    private static final String PREF_AFTER_TEXT         = "sbc_after_clock_format";
    private static final String PREF_AFTER_SMALL        = "sbc_after_small";

    private RecyclerView rv;

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rv = new RecyclerView(requireContext());
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
        buildRecyclerView();
    }

    // ── Build ──────────────────────────────────────────────────────────────────

    private void buildRecyclerView() {

        // ── Nascondi orologio ─────────────────────────────────────────────────
        SwitchItem hideLauncherItem = new SwitchItem(
                getString(R.string.clock_hide_launcher),
                getString(R.string.clock_hide_launcher_summary),
                ObsidianPrefs.getBoolean(PREF_AUTO_HIDE_LAUNCHER, false), null);
        hideLauncherItem.onChanged = () ->
                ObsidianPrefs.putBoolean(PREF_AUTO_HIDE_LAUNCHER, hideLauncherItem.checked);

        SwitchItem hideAutoItem = new SwitchItem(
                getString(R.string.clock_hide_auto),
                getString(R.string.clock_hide_auto_summary),
                ObsidianPrefs.getBoolean(PREF_AUTO_HIDE, false), null);
        hideAutoItem.onChanged = () ->
                ObsidianPrefs.putBoolean(PREF_AUTO_HIDE, hideAutoItem.checked);

        // ── Impostazioni formato ──────────────────────────────────────────────
        SwitchItem secondsItem = new SwitchItem(
                getString(R.string.clock_show_seconds),
                getString(R.string.clock_show_seconds_summary),
                ObsidianPrefs.getBoolean(PREF_SHOW_SECONDS, false), null);
        secondsItem.onChanged = () ->
                ObsidianPrefs.putBoolean(PREF_SHOW_SECONDS, secondsItem.checked);

        ListWidgetAdapter.ListItem amPmItem = new ListWidgetAdapter.ListItem(
                getString(R.string.clock_ampm_title), amPmLabel(), this::showAmPmDialog);

        ListWidgetAdapter.ListItem dateDisplayItem = new ListWidgetAdapter.ListItem(
                getString(R.string.clock_date_title), dateDisplayLabel(), this::showDateDisplayDialog);

        ListWidgetAdapter.ListItem datePosItem = new ListWidgetAdapter.ListItem(
                getString(R.string.clock_date_position_title), datePosLabel(), this::showDatePosDialog);

        ListWidgetAdapter.ListItem dateFormatItem = new ListWidgetAdapter.ListItem(
                getString(R.string.clock_date_format_title), dateFormatLabel(), this::showDateFormatDialog);

        // ── Avanzate ─────────────────────────────────────────────────────────
        ListWidgetAdapter.ListItem beforeTextItem = new ListWidgetAdapter.ListItem(
                getString(R.string.clock_before_text),
                textOrNone(ObsidianPrefs.getString(PREF_BEFORE_TEXT, "")),
                this::showBeforeTextDialog);

        SwitchItem beforeSmallItem = new SwitchItem(
                getString(R.string.clock_text_small),
                getString(R.string.clock_text_small_summary),
                ObsidianPrefs.getBoolean(PREF_BEFORE_SMALL, false), null);
        beforeSmallItem.onChanged = () ->
                ObsidianPrefs.putBoolean(PREF_BEFORE_SMALL, beforeSmallItem.checked);

        ListWidgetAdapter.ListItem afterTextItem = new ListWidgetAdapter.ListItem(
                getString(R.string.clock_after_text),
                textOrNone(ObsidianPrefs.getString(PREF_AFTER_TEXT, "")),
                this::showAfterTextDialog);

        SwitchItem afterSmallItem = new SwitchItem(
                getString(R.string.clock_text_small),
                getString(R.string.clock_text_small_summary),
                ObsidianPrefs.getBoolean(PREF_AFTER_SMALL, false), null);
        afterSmallItem.onChanged = () ->
                ObsidianPrefs.putBoolean(PREF_AFTER_SMALL, afterSmallItem.checked);

        String dateVal    = ObsidianPrefs.getString(PREF_DATE_DISPLAY, "0");
        boolean dateActive = !"0".equals(dateVal);

        List<RecyclerView.Adapter<?>> chain = new java.util.ArrayList<>();
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_section_hide))));
        GroupUtils.addGroup(chain, List.of(hideLauncherItem, hideAutoItem));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_section_format))));
        List<Object> formatRows = new java.util.ArrayList<>(List.of(secondsItem, amPmItem, dateDisplayItem));
        if (dateActive) {
            formatRows.add(datePosItem);
            formatRows.add(dateFormatItem);
        }
        GroupUtils.addGroup(chain, formatRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_section_advanced))));
        GroupUtils.addGroup(chain, List.of(beforeTextItem, beforeSmallItem, afterTextItem, afterSmallItem));

        rv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    private void showAmPmDialog() {
        String[] entries = getResources().getStringArray(R.array.clock_ampm_entries);
        String[] values  = getResources().getStringArray(R.array.clock_ampm_values);
        int curIdx = indexOf(values, ObsidianPrefs.getString(PREF_AM_PM, "2"), 2);
        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_ampm_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(PREF_AM_PM, values[sel[0]]);
                    buildRecyclerView();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showDateDisplayDialog() {
        String[] entries = getResources().getStringArray(R.array.clock_date_entries);
        String[] values  = getResources().getStringArray(R.array.clock_date_values);
        int curIdx = indexOf(values, ObsidianPrefs.getString(PREF_DATE_DISPLAY, "0"), 0);
        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_date_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(PREF_DATE_DISPLAY, values[sel[0]]);
                    buildRecyclerView(); // show/hide date position row
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showDatePosDialog() {
        String[] entries = getResources().getStringArray(R.array.clock_date_position_entries);
        String[] values  = getResources().getStringArray(R.array.clock_date_position_values);
        int curIdx = indexOf(values, ObsidianPrefs.getString(PREF_DATE_POS, "0"), 0);
        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_date_position_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(PREF_DATE_POS, values[sel[0]]);
                    buildRecyclerView();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showDateFormatDialog() {
        String[] entries = getResources().getStringArray(R.array.clock_date_format_entries);
        String[] values  = getResources().getStringArray(R.array.clock_date_format_values);
        int curIdx = indexOf(values, ObsidianPrefs.getString(PREF_DATE_FORMAT, "EEE, d MMM"), values.length - 1);
        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_date_format_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(PREF_DATE_FORMAT, values[sel[0]]);
                    buildRecyclerView();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void showBeforeTextDialog() {
        showTextInputDialog(R.string.clock_before_text, PREF_BEFORE_TEXT);
    }

    private void showAfterTextDialog() {
        showTextInputDialog(R.string.clock_after_text, PREF_AFTER_TEXT);
    }

    private void showTextInputDialog(int titleRes, String prefKey) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(ObsidianPrefs.getString(prefKey, ""));
        et.setSingleLine(true);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp2px(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.addView(et);

        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setView(layout)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String text = et.getText().toString().trim();
                    ObsidianPrefs.putString(prefKey, text);
                    buildRecyclerView();
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset, (d, w) -> {
                    ObsidianPrefs.putString(prefKey, "");
                    buildRecyclerView();
                })
                .show());
    }

    // ── Label helpers ──────────────────────────────────────────────────────────

    private String amPmLabel() {
        String[] entries = getResources().getStringArray(R.array.clock_ampm_entries);
        String[] values  = getResources().getStringArray(R.array.clock_ampm_values);
        return entries[indexOf(values, ObsidianPrefs.getString(PREF_AM_PM, "2"), 2)];
    }

    private String dateDisplayLabel() {
        String[] entries = getResources().getStringArray(R.array.clock_date_entries);
        String[] values  = getResources().getStringArray(R.array.clock_date_values);
        return entries[indexOf(values, ObsidianPrefs.getString(PREF_DATE_DISPLAY, "0"), 0)];
    }

    private String datePosLabel() {
        String[] entries = getResources().getStringArray(R.array.clock_date_position_entries);
        String[] values  = getResources().getStringArray(R.array.clock_date_position_values);
        return entries[indexOf(values, ObsidianPrefs.getString(PREF_DATE_POS, "0"), 0)];
    }

    private String dateFormatLabel() {
        String[] entries = getResources().getStringArray(R.array.clock_date_format_entries);
        String[] values  = getResources().getStringArray(R.array.clock_date_format_values);
        return entries[indexOf(values, ObsidianPrefs.getString(PREF_DATE_FORMAT, "EEE, d MMM"), values.length - 1)];
    }

    private String textOrNone(String s) {
        return (s == null || s.isEmpty()) ? getString(R.string.none) : s;
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static int indexOf(String[] arr, String val, int def) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return def;
    }

    private int dp2px(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
