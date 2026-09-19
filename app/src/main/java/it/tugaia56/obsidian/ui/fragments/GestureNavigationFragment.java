package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.DualSliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Navigazione con Gesture — porting reale di OC's gesture_prefs.xml (zona gesto
 * Indietro con doppio cursore min/max per lato, sostituzione hold-back, pillola
 * di navigazione). Collegato al hook GestureNavZones + HoldBackGesture.
 */
public class GestureNavigationFragment extends Fragment {

    private static final String PREF_GESTURE_LEFT             = "OBS_NAV_GESTURE_LEFT";
    private static final String PREF_GESTURE_LEFT_HEIGHT_MIN  = "OBS_NAV_GESTURE_LEFT_HEIGHT_MIN";
    private static final String PREF_GESTURE_LEFT_HEIGHT_MAX  = "OBS_NAV_GESTURE_LEFT_HEIGHT_MAX";
    private static final String PREF_GESTURE_RIGHT            = "OBS_NAV_GESTURE_RIGHT";
    private static final String PREF_GESTURE_RIGHT_HEIGHT_MIN = "OBS_NAV_GESTURE_RIGHT_HEIGHT_MIN";
    private static final String PREF_GESTURE_RIGHT_HEIGHT_MAX = "OBS_NAV_GESTURE_RIGHT_HEIGHT_MAX";
    private static final String PREF_GESTURE_ON_ROTATE        = "OBS_NAV_GESTURE_ON_ROTATE";

    private static final String PREF_HOLDBACK_ON     = "OBS_NAV_HOLDBACK_ON";
    private static final String PREF_HOLDBACK_MODE   = "OBS_NAV_HOLDBACK_MODE";   // "0".."1"
    private static final String PREF_HOLDBACK_LEFT   = "OBS_NAV_HOLDBACK_LEFT";   // "0".."10"
    private static final String PREF_HOLDBACK_RIGHT  = "OBS_NAV_HOLDBACK_RIGHT";  // "0".."10"

    private static final String PREF_PILL_ACCENT = "OBS_NAV_PILL_ACCENT";
    private static final String PREF_PILL_WIDTH  = "OBS_NAV_PILL_WIDTH";

    private RecyclerView mRv;
    private boolean mHoldbackExpanded = false;

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

        // ── Gesture Indietro ─────────────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gesture_back_title))));
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.gesture_left_title), null, PREF_GESTURE_LEFT),
                dualSliderItem(getString(R.string.gesture_height_title),
                        PREF_GESTURE_LEFT_HEIGHT_MIN, PREF_GESTURE_LEFT_HEIGHT_MAX, 0, 100, "%", true),
                prefSwitch(getString(R.string.gesture_right_title), null, PREF_GESTURE_RIGHT),
                dualSliderItem(getString(R.string.gesture_height_title),
                        PREF_GESTURE_RIGHT_HEIGHT_MIN, PREF_GESTURE_RIGHT_HEIGHT_MAX, 0, 100, "%", false),
                prefSwitch(getString(R.string.gesture_back_on_rotate), null, PREF_GESTURE_ON_ROTATE)));

        // ── Override Hold Back ──────────────────────────────────────────────────
        SwitchWidgetAdapter.SwitchItem holdbackSwitch = gatingSwitch(
                getString(R.string.gesture_override_back_hold), null, PREF_HOLDBACK_ON);
        holdbackSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_HOLDBACK_ON, holdbackSwitch.checked);
            mHoldbackExpanded = holdbackSwitch.checked;
            rebuild();
        };
        holdbackSwitch.onRowClick = () -> { mHoldbackExpanded = !mHoldbackExpanded; rebuild(); };
        List<Object> holdbackRows = new ArrayList<>();
        holdbackRows.add(holdbackSwitch);
        if (mHoldbackExpanded) {
            holdbackRows.add(singleChoiceItem(getString(R.string.gesture_override_back_hold_mode),
                    PREF_HOLDBACK_MODE, R.array.gesture_holdback_mode_entries));
            holdbackRows.add(commandChoiceItem(getString(R.string.gesture_override_back_hold_common),
                    PREF_HOLDBACK_LEFT));
            boolean perSide = "1".equals(ObsidianPrefs.getString(PREF_HOLDBACK_MODE, "0"));
            if (perSide) {
                holdbackRows.add(commandChoiceItem(getString(R.string.gesture_override_back_hold_right),
                        PREF_HOLDBACK_RIGHT));
            }
        }
        GroupUtils.addGroup(chain, holdbackRows);

        // ── Pillola di Navigazione ───────────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gesture_nav_pill_cat))));
        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.colorpill), null, PREF_PILL_ACCENT),
                sliderItem(getString(R.string.gesture_nav_pill_width_title), PREF_PILL_WIDTH, 10, 100, 50, "%")));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Generic row helpers ──────────────────────────────────────────────────────

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
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

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes));
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    // ── Comando tieni premuto — stessa lista di OC, con icona per voce ──────────

    private static final int[] HOLDBACK_COMMAND_ICONS = {
        R.drawable.ic_switch_app,
        R.drawable.ic_kill,
        R.drawable.ic_screenshot,
        R.drawable.ic_screenshot_scroll,
        R.drawable.ic_screenshot_area,
        R.drawable.ic_quick_settings,
        R.drawable.ic_one_hand,
        R.drawable.ic_notifications,
        R.drawable.ic_screen_off,
        R.drawable.ic_circle_search,
        R.drawable.ic_custom_app,
    };

    private ListWidgetAdapter.ListItem commandChoiceItem(String title, String key) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, R.array.gesture_holdback_commands_entries),
                () -> showCommandChoiceDialog(title, key));
    }

    private void showCommandChoiceDialog(String title, String key) {
        String[] entries = getResources().getStringArray(R.array.gesture_holdback_commands_entries);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};

        ArrayAdapter<String> listAdapter = new ArrayAdapter<>(requireContext(), 0, entries) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int padH = dp(16), padV = dp(12);
                row.setPadding(padH, padV, padH, padV);

                RadioButton radio = new RadioButton(requireContext());
                radio.setChecked(position == selected[0]);
                radio.setClickable(false);
                radio.setFocusable(false);

                ImageView icon = new ImageView(requireContext());
                icon.setImageResource(HOLDBACK_COMMAND_ICONS[position]);
                icon.setColorFilter(ObsidianTheme.systemDialogTextColor(requireContext()));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(24), dp(24));
                iconLp.setMarginStart(dp(16));
                iconLp.setMarginEnd(dp(20));
                icon.setLayoutParams(iconLp);

                TextView text = new TextView(requireContext());
                text.setText(entries[position]);
                text.setTextColor(ObsidianTheme.systemDialogTextColor(requireContext()));
                text.setTextSize(16);

                row.addView(radio);
                row.addView(icon);
                row.addView(text);
                return row;
            }
        };

        ListView listView = new ListView(requireContext());
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((parent, v, position, id) -> {
            selected[0] = position;
            listAdapter.notifyDataSetChanged();
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(listView)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }

    // ── Cursore doppio per la zona del gesto Indietro, con anteprima live ──────

    private DualSliderWidgetAdapter.DualSliderItem dualSliderItem(String title, String minKey, String maxKey,
                                                   int rangeMin, int rangeMax, String unit, boolean isLeft) {
        int curMin = ObsidianPrefs.getInt(minKey, rangeMin);
        int curMax = ObsidianPrefs.getInt(maxKey, rangeMax);
        DualSliderWidgetAdapter.DualSliderItem item = new DualSliderWidgetAdapter.DualSliderItem(
                title, curMin, curMax, rangeMin, rangeMax, unit,
                (newMin, newMax) -> {
                    ObsidianPrefs.putInt(minKey, newMin);
                    ObsidianPrefs.putInt(maxKey, newMax);
                });
        item.onDragStart = () -> showZonePreview(isLeft);
        item.onDrag = (mn, mx) -> updateZonePreview(mn, mx);
        item.onDragEnd = this::hideZonePreview;
        return item;
    }

    private View mPreviewOverlay;

    private void showZonePreview(boolean isLeft) {
        if (mPreviewOverlay != null || getActivity() == null) return;
        ViewGroup decor = (ViewGroup) getActivity().getWindow().getDecorView();
        View v = new View(requireContext());
        v.setBackgroundColor((0x66 << 24) | (ObsidianTheme.accentColor() & 0x00FFFFFF));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(28), 0);
        lp.gravity = (isLeft ? Gravity.START : Gravity.END) | Gravity.TOP;
        decor.addView(v, lp);
        mPreviewOverlay = v;
    }

    private void updateZonePreview(int minPct, int maxPct) {
        if (mPreviewOverlay == null) return;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPreviewOverlay.getLayoutParams();
        int top = screenH - Math.round(screenH * maxPct / 100f);
        int bottom = screenH - Math.round(screenH * minPct / 100f);
        lp.height = Math.max(bottom - top, 0);
        lp.topMargin = top;
        mPreviewOverlay.setLayoutParams(lp);
    }

    private void hideZonePreview() {
        if (mPreviewOverlay == null) return;
        ViewGroup parent = (ViewGroup) mPreviewOverlay.getParent();
        if (parent != null) parent.removeView(mPreviewOverlay);
        mPreviewOverlay = null;
    }
}
