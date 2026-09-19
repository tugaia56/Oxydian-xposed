package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.res.Resources;
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

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Fingerprint icon preset picker — lists every fingerprint_N drawable found (same set/order
 * as OC, 0..60), each with a live preview. Tap to apply.
 */
public class FingerprintPresetFragment extends Fragment {

    /** fingerprint_74..89: preset da un pacchetto animazioni impronta PUI, 2026-09-18. */
    private static final java.util.Set<Integer> FINGERPRINT_CREDIT_RANGE =
            new java.util.HashSet<>(java.util.Arrays.asList(74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89));

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

        mCurrentStyle = ObsidianPrefs.getBoolean("lockscreen_fp_custom_icon", false)
                ? ObsidianPrefs.getInt("lockscreen_fp_icon_custom", 0) : -2;

        List<Integer> styles = new ArrayList<>();
        Resources res = requireContext().getResources();
        String pkg = requireContext().getPackageName();
        for (int i = 0; ; i++) {
            if (res.getIdentifier("fingerprint_" + i, "drawable", pkg) == 0) break;
            styles.add(i);
        }

        mAdapter = new Adapter(styles);
        ((RecyclerView) view).setAdapter(mAdapter);
    }

    private void applyStyle(int index) {
        ObsidianPrefs.putBoolean("lockscreen_fp_custom_icon", true);
        ObsidianPrefs.putInt("lockscreen_fp_icon_custom", index);
        mCurrentStyle = index;
        mAdapter.notifyDataSetChanged();
        AppUtils.showRestartReminder(requireContext());
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Integer> items;
        Adapter(List<Integer> items) { this.items = items; }

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
            int size = ObsidianTheme.dp(ctx, 48);
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
            int index = items.get(pos);
            boolean active = index == mCurrentStyle;

            int resId = h.itemView.getResources().getIdentifier(
                    "fingerprint_" + index, "drawable", h.itemView.getContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(h.itemView.getContext(), resId);
            h.preview.setImageDrawable(d);

            String label = getString(R.string.lockscreen_fp_style, index);
            // Preset 74-89: estratti da un pacchetto animazioni impronta PUI (2026-09-18, su
            // richiesta esplicita dell'utente) — credito visibile accanto al nome.
            if (FINGERPRINT_CREDIT_RANGE.contains(index)) {
                label += "\nby PUI (天伞桜&PanL)";
            }
            h.label.setText(label);
            h.label.setTextColor(active
                    ? ContextCompat.getColor(requireContext(), R.color.obs_primary)
                    : ObsidianTheme.textColor());

            h.itemView.setOnClickListener(v -> applyStyle(index));
        }

        @Override public int getItemCount() { return items.size(); }

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
