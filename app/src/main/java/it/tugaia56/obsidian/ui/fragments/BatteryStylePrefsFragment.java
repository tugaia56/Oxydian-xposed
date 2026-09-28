package it.tugaia56.obsidian.ui.fragments;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.batterystyles.BatteryDrawable;
import it.tugaia56.obsidian.xposed.hooks.systemui.BatteryStyleManager;

import static it.tugaia56.obsidian.xposed.hooks.systemui.BatteryStyleManager.*;

/**
 * "Preferenze icona batteria" — style picker (griglia con anteprima reale) +
 * un'unica sezione con le sole opzioni confermate presenti in OC.
 */
public class BatteryStylePrefsFragment extends Fragment {

    /** Style int → display-name index in R.array.battery_style_names */
    private static final int[] STYLE_VALUES = {
        STYLE_RLANDSCAPE, STYLE_LANDSCAPE, STYLE_CAPSULE, STYLE_LORN, STYLE_MX, STYLE_AIROO,
        STYLE_RLANDSCAPE_A, STYLE_LANDSCAPE_A, STYLE_RLANDSCAPE_B, STYLE_LANDSCAPE_B,
        STYLE_IOS15, STYLE_IOS16, STYLE_ORIGAMI, STYLE_SMILEY, STYLE_MIUI_PILL, STYLE_COLOROS,
        STYLE_RLANDSCAPE_COLOROS, STYLE_A, STYLE_B, STYLE_C, STYLE_D, STYLE_E, STYLE_F, STYLE_G,
        STYLE_H, STYLE_I, STYLE_J, STYLE_K, STYLE_L, STYLE_M, STYLE_N, STYLE_O, STYLE_CIRCLE,
        STYLE_DOTTED_CIRCLE, STYLE_FILLED_CIRCLE, STYLE_KIM, STYLE_ONE_UI7,
    };

    private String[] mStyleNames;
    private RecyclerView mRv;

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
        mStyleNames = requireContext().getResources().getStringArray(R.array.battery_style_names);
        rebuild();
    }

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        ListWidgetAdapter.ListItem styleItem = new ListWidgetAdapter.ListItem(
                getString(R.string.battery_style_label), getCurrentStyleName(), this::showStyleDialog);

        // ── Style — riga a sé, fuori dalle categorie (come in OC) ────────────
        GroupUtils.addGroup(chain, List.of((Object) styleItem));

        // ── Preferenze Icona Batteria ─────────────────────────────────────────
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.battery_options_section))));
        GroupUtils.addGroup(chain, List.of(
                slider(getString(R.string.battery_width_label), PREF_WIDTH, 10, 30, 20, "dp"),
                slider(getString(R.string.battery_height_label), PREF_HEIGHT, 10, 30, 20, "dp"),
                sw(getString(R.string.battery_hide_percentage), getString(R.string.battery_hide_percentage_summary), PREF_HIDE_PERCENT, false),
                sw(getString(R.string.battery_inside_percentage), getString(R.string.battery_inside_percentage_summary), PREF_INSIDE_PERCENT, false),
                sw(getString(R.string.battery_hide_battery), getString(R.string.battery_hide_battery_summary), PREF_HIDE_BATTERY, false)
        ));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    private SwitchWidgetAdapter.SwitchItem sw(String title, String summary, String key, boolean def) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, def), null);
        item.onChanged = () -> ObsidianPrefs.putBoolean(key, item.checked);
        return item;
    }

    private SliderWidgetAdapter.SliderItem slider(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }

    // ── Style dialog — griglia con anteprima reale ───────────────────────────

    private void showStyleDialog() {
        int savedStyle = ObsidianPrefs.getInt(PREF_STYLE, STYLE_DEFAULT);

        RecyclerView grid = new RecyclerView(requireContext());
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        int pad = dp(8);
        grid.setPadding(pad, pad, pad, pad);
        grid.setClipToPadding(false);

        AlertDialog[] dlgRef = new AlertDialog[1];
        IntConsumer onPick = style -> {
            ObsidianPrefs.putInt(PREF_STYLE, style);
            rebuild();
            Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
            if (dlgRef[0] != null) dlgRef[0].dismiss();
        };
        grid.setAdapter(new StylePreviewAdapter(savedStyle, onPick));

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.battery_style_label)
                .setView(grid)
                .setNegativeButton(R.string.close, null)
                .show();
        dlgRef[0] = dlg;
        fixButtonCaps(dlg);
        ObsidianTheme.themeDialog(dlg);
    }

    private int getPreviewBatteryLevel() {
        try {
            Intent battery = requireContext().registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return 80;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 80);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            return scale > 0 ? Math.round(level * 100f / scale) : 80;
        } catch (Throwable t) { return 80; }
    }

    private int indexOfStyle(int style) {
        for (int i = 0; i < STYLE_VALUES.length; i++) if (STYLE_VALUES[i] == style) return i;
        return -1;
    }

    private String getCurrentStyleName() {
        int style = ObsidianPrefs.getInt(PREF_STYLE, STYLE_DEFAULT);
        int idx = indexOfStyle(style);
        if (idx < 0 || mStyleNames == null || idx >= mStyleNames.length) {
            return requireContext().getString(R.string.dst_none);
        }
        return mStyleNames[idx];
    }

    private static void fixButtonCaps(AlertDialog d) {
        Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        for (Button b : new Button[]{pos, neg}) {
            if (b == null) continue;
            b.setAllCaps(false);
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setEllipsize(null);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Griglia stile picker: ogni cella renderizza il vero BatteryDrawable dello stile. */
    private class StylePreviewAdapter extends RecyclerView.Adapter<StylePreviewAdapter.VH> {
        private final int mSelected;
        private final IntConsumer mOnPick;
        private final int mLevel = getPreviewBatteryLevel();

        StylePreviewAdapter(int selected, IntConsumer onPick) {
            mSelected = selected;
            mOnPick = onPick;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            int padH = dp(8), padV = dp(10);
            root.setPadding(padH, padV, padH, padV);

            FrameLayout iconFrame = new FrameLayout(parent.getContext());
            ImageView icon = new ImageView(parent.getContext());
            iconFrame.addView(icon, new FrameLayout.LayoutParams(dp(40), dp(24), Gravity.CENTER));
            root.addView(iconFrame, new LinearLayout.LayoutParams(dp(56), dp(36)));

            TextView label = new TextView(parent.getContext());
            label.setTextSize(11);
            label.setTextColor(ObsidianTheme.systemDialogTextColor(parent.getContext()));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(4);
            root.addView(label, labelLp);

            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            rootLp.setMargins(m, m, m, m);
            root.setLayoutParams(rootLp);

            return new VH(root, icon, label);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            int style = STYLE_VALUES[pos];
            boolean selected = style == mSelected;
            int accent = ObsidianTheme.accentColor();

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(dp(10));
            if (selected) bg.setStroke(dp(2), accent);
            h.itemView.setBackground(bg);

            try {
                BatteryDrawable d = BatteryStyleManager.createDrawableForStyle(
                        requireContext(), style, Color.WHITE);
                if (d != null) {
                    d.setBatteryLevel(mLevel);
                    d.setColors(Color.WHITE, Color.WHITE, Color.WHITE);
                    d.setChargingEnabled(false, false);
                    d.setPowerSavingEnabled(false);
                    d.setShowPercentEnabled(false);
                    d.setAnimationEnbled(false);
                    d.invalidateSelf();
                    h.icon.setImageDrawable(d);
                } else {
                    h.icon.setImageDrawable(null);
                }
            } catch (Throwable t) {
                h.icon.setImageDrawable(null);
            }

            h.label.setText(pos < mStyleNames.length ? mStyleNames[pos] : "");
            h.label.setTextColor(selected ? accent : ObsidianTheme.systemDialogTextColor(h.itemView.getContext()));
            h.itemView.setOnClickListener(v -> mOnPick.accept(style));
        }

        @Override public int getItemCount() { return STYLE_VALUES.length; }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            VH(View v, ImageView icon, TextView label) {
                super(v);
                this.icon = icon;
                this.label = label;
            }
        }
    }
}
