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

/**
 * Stile Orologio — split out of ClockDateFragment: position / size / padding of the
 * status bar clock (matching StatusbarClock hook prefs), più Chip di sfondo (reale OC
 * status_bar_clock_background_chip — spostato qui da Stile Data, dove non c'entrava).
 */
public class ClockStyleFragment extends Fragment {

    // ── Pref keys (matching StatusbarClock hook) ───────────────────────────────
    private static final String PREF_POSITION   = "status_bar_clock";
    private static final String PREF_SIZE       = "status_bar_clock_size";
    private static final String PREF_PADDING    = "status_bar_clock_padding";
    private static final String PREF_CHIP_PREFIX = "status_bar_clock_background_chip";
    private static final String PREF_BG_CHIP_ON = PREF_CHIP_PREFIX + "_switch";
    private static final String PREF_CHIP_STYLE = PREF_CHIP_PREFIX + "_style";
    // 2026-09-25: switch diagnostico richiesto dall'utente dopo il troncamento "09:..." mai
    // risolto del tutto (vedi 09-20) — spegne rapidamente TUTTO questo hook (posizione,
    // dimensione, padding, chip) senza dover disattivare l'intero modulo Oxydian.
    private static final String PREF_STYLE_ON = "status_bar_clock_style_enabled";

    private RecyclerView mRv;
    private boolean mChipExpanded = false;

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
        mRv = (RecyclerView) view;
        rebuild();
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.nav_clock_style))));

        boolean styleOn = ObsidianPrefs.getBoolean(PREF_STYLE_ON, true);
        SwitchWidgetAdapter.SwitchItem styleSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.status_bar_clock_style_on_title),
                getString(R.string.status_bar_clock_style_on_summary),
                styleOn, null);
        styleSwitch.onChanged = () -> ObsidianPrefs.putBoolean(PREF_STYLE_ON, styleSwitch.checked);
        chain.add(new SwitchWidgetAdapter(List.of(styleSwitch)));

        GroupUtils.addGroup(chain, List.of(
                new ListWidgetAdapter.ListItem(
                        getString(R.string.clock_position_title),
                        positionLabel(),
                        this::showPositionDialog),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.clock_size_title),
                        sizeLabel(),
                        this::showSizeDialog),
                new ListWidgetAdapter.ListItem(
                        getString(R.string.clock_padding_title),
                        paddingLabel(),
                        this::showPaddingDialog)));

        // ── Chip di sfondo (reale OC status_bar_clock_background_chip) ──────────
        boolean chipOn = ObsidianPrefs.getBoolean(PREF_BG_CHIP_ON, false);
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.status_bar_clock_chip_category))));
        SwitchWidgetAdapter.SwitchItem chipSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.status_bar_clock_background_chip_title), null,
                chipOn, null);
        chipSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BG_CHIP_ON, chipSwitch.checked);
            mChipExpanded = chipSwitch.checked;
            rebuild();
        };
        chipSwitch.onRowClick = () -> { mChipExpanded = !mChipExpanded; rebuild(); };
        chain.add(new SwitchWidgetAdapter(List.of(chipSwitch)));
        if (mChipExpanded) {
            chain.add(new ListWidgetAdapter(List.of(
                    new ListWidgetAdapter.ListItem(
                            getString(R.string.status_bar_clock_background_chip_style_title),
                            chipStyleLabel(),
                            () -> navigate(ClockChipStyleFragment.newInstance(PREF_CHIP_PREFIX),
                                    getString(R.string.status_bar_clock_background_chip_style_title))))));
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Chip di sfondo ────────────────────────────────────────────────────────

    private String chipStyleLabel() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_chip_style_entries);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(PREF_CHIP_STYLE, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }

    // ── Position dialog ───────────────────────────────────────────────────────

    private void showPositionDialog() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_position_entries);
        String[] values  = requireContext().getResources().getStringArray(R.array.clock_position_values);
        String   cur     = ObsidianPrefs.getString(PREF_POSITION, "2");
        int curIdx = indexOf(values, cur, 0);

        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_position_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(PREF_POSITION, values[sel[0]]);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Size dialog ───────────────────────────────────────────────────────────

    private void showSizeDialog() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_size_entries);
        int[]    values  = requireContext().getResources().getIntArray(R.array.clock_size_values);
        int      cur     = ObsidianPrefs.getInt(PREF_SIZE, 12);
        int curIdx = indexOfInt(values, cur, 0);

        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_size_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_SIZE, values[sel[0]]);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Padding dialog ────────────────────────────────────────────────────────

    private void showPaddingDialog() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_padding_entries);
        int[]    values  = requireContext().getResources().getIntArray(R.array.clock_padding_values);
        int      cur     = ObsidianPrefs.getInt(PREF_PADDING, 0);
        int curIdx = indexOfInt(values, cur, 0);

        final int[] sel = {curIdx};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clock_padding_title)
                .setSingleChoiceItems(entries, curIdx, (d, w) -> sel[0] = w)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_PADDING, values[sel[0]]);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Summary label helpers ─────────────────────────────────────────────────

    private String positionLabel() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_position_entries);
        String[] values  = requireContext().getResources().getStringArray(R.array.clock_position_values);
        return entries[indexOf(values, ObsidianPrefs.getString(PREF_POSITION, "2"), 0)];
    }

    private String sizeLabel() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_size_entries);
        int[]    values  = requireContext().getResources().getIntArray(R.array.clock_size_values);
        return entries[indexOfInt(values, ObsidianPrefs.getInt(PREF_SIZE, 12), 0)];
    }

    private String paddingLabel() {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_padding_entries);
        int[]    values  = requireContext().getResources().getIntArray(R.array.clock_padding_values);
        return entries[indexOfInt(values, ObsidianPrefs.getInt(PREF_PADDING, 0), 0)];
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static int indexOf(String[] arr, String val, int def) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return def;
    }

    private static int indexOfInt(int[] arr, int val, int def) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == val) return i;
        return def;
    }
}
