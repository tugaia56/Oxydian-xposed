package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Illuminazione Bordi AOD — UI port of OC's edge_light_settings.xml (minus the animated
 * EdgeLightView preview, replaced here with a static glowing-border illustration). UI/prefs
 * only for now, no Xposed hook wired — visible so the controls exist, wiring is a separate
 * future pass.
 */
public class AodEdgeLightFragment extends Fragment {

    private static final String KEY_ENABLED        = "edge_light_enabled";
    private static final String KEY_ALWAYS_PULSE   = "edge_light_always_trigger_on_pulse";
    private static final String KEY_RETICK         = "edge_light_retick";
    private static final String KEY_RETICK_DURATION= "edge_light_retick_duration"; // "0".."4"
    private static final String KEY_STYLE          = "edge_light_style";           // "0".."4"
    private static final String KEY_SHOW_BLUR      = "edge_light_show_blur";
    private static final String KEY_BLUR_MODE      = "edge_light_blur_mode";       // "0".."1"
    private static final String KEY_BLUR_TYPE      = "edge_light_blur_type";       // "0".."3"
    private static final String KEY_COLOR_MODE     = "edge_light_color_mode";      // "0".."4"
    private static final String KEY_CUSTOM_COLOR   = "edge_light_custom_color";
    private static final String KEY_WIDTH          = "edge_light_width";           // 8-20 dp

    private static final int COLOR_MODE_CUSTOM = 4; // index in edge_light_color_mode_entries
    private static final int CUSTOM_COLOR_DIALOG_ID = KEY_CUSTOM_COLOR.hashCode();

    private RecyclerView mRv;
    private PreviewAdapter mPreviewAdapter;

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

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        mPreviewAdapter = new PreviewAdapter();
        chain.add(mPreviewAdapter);
        chain.add(new TextRowAdapter(getString(R.string.edge_light_advice)));

        GroupUtils.addGroup(chain, List.of(
                prefSwitch(getString(R.string.edge_light_enabled_title), getString(R.string.edge_light_summary), KEY_ENABLED),
                prefSwitch(getString(R.string.edge_light_always_trigger_on_pulse_title),
                        getString(R.string.edge_light_always_trigger_on_pulse_summary), KEY_ALWAYS_PULSE),
                prefSwitch(getString(R.string.edge_light_retick_title), getString(R.string.edge_light_retick_summary), KEY_RETICK),
                singleChoiceItem(getString(R.string.edge_light_retick_time), KEY_RETICK_DURATION, R.array.edge_light_retick_entries)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.edge_light_style_title))));
        GroupUtils.addGroup(chain, List.of(
                singleChoiceItem(getString(R.string.edge_light_style_title), KEY_STYLE, R.array.edge_light_style_entries, true),
                prefSwitch(getString(R.string.edge_light_show_blur), null, KEY_SHOW_BLUR),
                singleChoiceItem(getString(R.string.edge_light_blur_mode_title), KEY_BLUR_MODE, R.array.edge_light_blur_mode_entries),
                singleChoiceItem(getString(R.string.edge_light_blur_type_title), KEY_BLUR_TYPE, R.array.edge_light_blur_type_entries),
                colorModeItem(),
                sliderItem(getString(R.string.edge_light_stroke_width_title), KEY_WIDTH, 8, 20, 12, "dp")));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private ListWidgetAdapter.ListItem colorModeItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.edge_light_color_mode_title), choiceLabel(KEY_COLOR_MODE, R.array.edge_light_color_mode_entries),
                this::showColorModeDialog);
    }

    private void showColorModeDialog() {
        String[] entries = getResources().getStringArray(R.array.edge_light_color_mode_entries);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(KEY_COLOR_MODE, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edge_light_color_mode_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(KEY_COLOR_MODE, String.valueOf(selected[0]));
                    rebuild();
                    // "Personalizzato" doesn't get its own persistent row — picking it opens
                    // the colour picker right away, same as tapping a swatch elsewhere.
                    if (selected[0] == COLOR_MODE_CUSTOM && getActivity() instanceof MainActivity) {
                        int current2 = ObsidianPrefs.getInt(KEY_CUSTOM_COLOR, 0xFFFF3B30);
                        ((MainActivity) getActivity()).showColorPickerDialog(CUSTOM_COLOR_DIALOG_ID, current2, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Live preview: static glowing border, colour follows current color-mode choice ──

    private class PreviewAdapter extends RecyclerView.Adapter<PreviewAdapter.VH> {
        class VH extends RecyclerView.ViewHolder { FrameLayout glow; VH(View v) { super(v); } }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout outer = new FrameLayout(requireContext());
            int h = dp(140);
            outer.setLayoutParams(marginLp(h));
            outer.setBackgroundColor(0xFF000000);

            FrameLayout glow = new FrameLayout(requireContext());
            glow.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            outer.addView(glow);

            VH holder = new VH(outer);
            holder.glow = glow;
            return holder;
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            h.glow.removeAllViews();
            int style = 0;
            try { style = Integer.parseInt(ObsidianPrefs.getString(KEY_STYLE, "0")); } catch (NumberFormatException ignored) {}

            if (style == 2) { // Linee Laterali — two vertical bars, not a full border
                h.glow.setBackground(null);
                int width = ObsidianPrefs.getInt(KEY_WIDTH, 12);
                int color = previewColor();
                for (int side = 0; side < 2; side++) {
                    View bar = new View(requireContext());
                    bar.setBackgroundColor(color);
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(width), ViewGroup.LayoutParams.MATCH_PARENT);
                    lp.gravity = side == 0 ? Gravity.START : Gravity.END;
                    bar.setLayoutParams(lp);
                    h.glow.addView(bar);
                }
            } else {
                // Draw Line / Blink / Snake / Echo all animate a full-perimeter glow in OC —
                // indistinguishable in a static preview, so they share this border illustration.
                h.glow.setBackground(buildGlowDrawable());
            }
        }
        @Override public int getItemCount() { return 1; }
    }

    private void refreshGlow() {
        if (mPreviewAdapter == null) return;
        mPreviewAdapter.notifyItemChanged(0);
    }

    private FrameLayout.LayoutParams marginLp(int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.setMargins(dp(16), dp(12), dp(16), dp(12));
        return lp;
    }

    private int previewColor() {
        int mode = 0;
        try { mode = Integer.parseInt(ObsidianPrefs.getString(KEY_COLOR_MODE, "0")); } catch (NumberFormatException ignored) {}
        return switch (mode) {
            case 4 -> ObsidianPrefs.getInt(KEY_CUSTOM_COLOR, 0xFFFF3B30);
            case 3 -> 0xFF00E5FF; // rainbow placeholder swatch
            default -> ObsidianTheme.accentColor();
        };
    }

    private GradientDrawable buildGlowDrawable() {
        int width = ObsidianPrefs.getInt(KEY_WIDTH, 12);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xFF000000);
        gd.setStroke(dp(width), previewColor());
        gd.setCornerRadius(dp(24));
        return gd;
    }

    // ── Simple advice text row ──────────────────────────────────────────────────

    private class TextRowAdapter extends RecyclerView.Adapter<TextRowAdapter.VH> {
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
        private final String text;
        TextRowAdapter(String text) { this.text = text; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(requireContext());
            tv.setText(text);
            tv.setTextColor(0x99FFFFFF);
            tv.setTextSize(13);
            tv.setGravity(Gravity.START);
            int padH = dp(16);
            tv.setPadding(padH, dp(4), padH, dp(12));
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {}
        @Override public int getItemCount() { return 1; }
    }

    // ── Custom color (no persistent row — picked directly from the mode dialog) ────

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() != CUSTOM_COLOR_DIALOG_ID) return;
        ObsidianPrefs.putInt(KEY_CUSTOM_COLOR, event.color());
        refreshGlow();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    // ── Generic row helpers (UI/prefs only, no restart reminder — not wired yet) ──

    private SwitchWidgetAdapter.SwitchItem prefSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes) {
        return singleChoiceItem(title, key, entriesArrayRes, false);
    }

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes, boolean refreshPreviewOnApply) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes, refreshPreviewOnApply));
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes, boolean refreshPreviewOnApply) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                    if (refreshPreviewOnApply) refreshGlow();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> {
                    ObsidianPrefs.putInt(key, value);
                    refreshGlow();
                });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
