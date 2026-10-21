package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

import static it.tugaia56.obsidian.ui.fragments.BatteryChargingIconFragment.PREF_STYLE;

/**
 * Charging icon style picker — same 21 icons OC ships (battery.xposed's
 * ic_charging_* set), each row with a live preview tinted like the real status
 * bar icon. Tap to apply.
 */
public class BatteryChargingIconStyleFragment extends Fragment {

    public static final String[] NAMES = {
            "ic_charging_bold", "ic_charging_asus", "ic_charging_buddy", "ic_charging_evplug",
            "ic_charging_idc", "ic_charging_ios", "ic_charging_koplak", "ic_charging_miui",
            "ic_charging_mmk", "ic_charging_moto", "ic_charging_nokia", "ic_charging_plug",
            "ic_charging_powercable", "ic_charging_powercord", "ic_charging_powerstation",
            "ic_charging_realme", "ic_charging_soak", "ic_charging_stres", "ic_charging_strip",
            "ic_charging_usbcable", "ic_charging_xiaomi"
    };

    private int mCurrentStyle;
    private Adapter mAdapter;

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
        mCurrentStyle = ObsidianPrefs.getInt(PREF_STYLE, 0);
        String[] labels = getResources().getStringArray(R.array.battery_charging_icon_style_entries);
        mAdapter = new Adapter(labels);
        ((RecyclerView) view).setAdapter(mAdapter);
    }

    private void applyStyle(int index) {
        ObsidianPrefs.putInt(PREF_STYLE, index);
        mCurrentStyle = index;
        mAdapter.notifyDataSetChanged();
        AppUtils.showRestartReminder(requireContext());
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final String[] labels;
        Adapter(String[] labels) { this.labels = labels; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            int pad = ObsidianTheme.dp(ctx, 12);
            int radius = ObsidianTheme.dp(ctx, 12);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(pad, ObsidianTheme.dp(ctx, 4), pad, ObsidianTheme.dp(ctx, 4));
            row.setLayoutParams(rowLp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(radius);
            row.setBackground(bg);

            ImageView preview = new ImageView(ctx);
            preview.setTag("preview");
            int size = ObsidianTheme.dp(ctx, 32);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(size, size);
            preview.setLayoutParams(previewLp);
            row.addView(preview);

            TextView label = new TextView(ctx);
            label.setTag("label");
            label.setTextColor(ObsidianTheme.textColor());
            label.setTextSize(15);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(ObsidianTheme.dp(ctx, 16));
            label.setLayoutParams(labelLp);
            row.addView(label);

            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            boolean active = pos == mCurrentStyle;

            int resId = h.itemView.getResources().getIdentifier(
                    NAMES[pos], "drawable", h.itemView.getContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(h.itemView.getContext(), resId);
            if (d != null) {
                d = d.mutate();
                boolean useAccent = ObsidianPrefs.getBoolean(BatteryChargingIconFragment.PREF_USE_ACCENT, true);
                int color = useAccent ? ObsidianTheme.overlayAccent()
                        : ObsidianPrefs.getInt(BatteryChargingIconFragment.PREF_CUSTOM_COLOR, 0xFFFFFFFF);
                d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
            h.preview.setImageDrawable(d);

            h.label.setText(pos < labels.length ? labels[pos] : NAMES[pos]);
            h.label.setTextColor(active ? ObsidianTheme.accentColor() : ObsidianTheme.textColor());

            h.itemView.setOnClickListener(v -> applyStyle(pos));
        }

        @Override public int getItemCount() { return NAMES.length; }

        class VH extends RecyclerView.ViewHolder {
            ImageView preview;
            TextView  label;
            VH(View v) {
                super(v);
                preview = v.findViewWithTag("preview");
                label   = v.findViewWithTag("label");
            }
        }
    }
}
