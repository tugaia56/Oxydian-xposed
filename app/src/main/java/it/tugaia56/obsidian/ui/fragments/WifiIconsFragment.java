package it.tugaia56.obsidian.ui.fragments;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.ObsidianPrefs;

/**
 * Fragment: Icone Wi-Fi
 *
 * 18 presets (WIFI1-WIFI18).
 * For each preset the row shows name + 4 preview icons.
 * On tap: expands "APPLICA" (or "DISABILITA" if active) + optional "Applica entrambi".
 * Pressing any apply button also restarts SystemUI so the change takes effect immediately.
 */
public class WifiIconsFragment extends Fragment {

    private static final String PREF_KEY        = "DST_PRESET_WIFI_ICON";
    private static final String PREF_KEY_SIGNAL = "DST_PRESET_SIGNAL_ICON";

    // ── Preset definition ─────────────────────────────────────────────────────

    private static class Preset {
        final String key;           // e.g. "DSTWIFI_AURORA"
        final String signalKey;     // matching signal key, or null
        final String displayName;
        final String drawableName;  // e.g. "aurora" for obs_wifi_aurora_signal_*

        Preset(String key, String signalKey, String displayName, String drawableName) {
            this.key          = key;
            this.signalKey    = signalKey;
            this.displayName  = displayName;
            this.drawableName = drawableName;
        }
    }

    private List<Preset> mPresets;
    private String mCurrentPreset;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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
        mCurrentPreset = ObsidianPrefs.getString(PREF_KEY, null);
        mPresets = buildPresets();
        ((RecyclerView) view).setAdapter(new PresetsAdapter());
    }

    // ── Presets (WIFI1-WIFI19) ────────────────────────────────────────────────

    private List<Preset> buildPresets() {
        List<Preset> list = new ArrayList<>();
        list.add(new Preset("DSTWIFI_AURORA",      "DSTSIG_AURORA",      "Aurora",      "aurora"));
        list.add(new Preset("DSTWIFI_BARS",        "DSTSIG_BARS",        "Bars",        "bars"));
        list.add(new Preset("DSTWIFI_DORA",        "DSTSIG_DORA",        "Dora",        "dora"));
        list.add(new Preset("DSTWIFI_FAINT_UI",    "DSTSIG_FAINT_UI",    "Faint UI",    "faint_ui"));
        list.add(new Preset("DSTWIFI_FORLORN",     "DSTSIG_FORLORN",     "Forlorn",     "forlorn"));
        list.add(new Preset("DSTWIFI_GRADICON",    "DSTSIG_GRADICON",    "Gradicon",    "gradicon"));
        list.add(new Preset("DSTWIFI_INSIDE",      "DSTSIG_INSIDE",      "Inside",      "inside"));
        list.add(new Preset("DSTWIFI_NOTHING_DOT", "DSTSIG_NOTHING_DOT", "Nothing Dot", "nothing_dot"));
        list.add(new Preset("DSTWIFI_PLUMPY",      "DSTSIG_PLUMPY",      "Plumpy",      "plumpy"));
        list.add(new Preset("DSTWIFI_PUI",         "DSTSIG_PUI",         "PUI",         "pui"));
        list.add(new Preset("DSTWIFI_ROUND",       "DSTSIG_ROUND",       "Round",       "round"));
        list.add(new Preset("DSTWIFI_SNEAKY",      "DSTSIG_SNEAKY",      "Sneaky",      "sneaky"));
        list.add(new Preset("DSTWIFI_STROKE",      "DSTSIG_STROKE",      "Stroke",      "stroke"));
        list.add(new Preset("DSTWIFI_WAVY",        "DSTSIG_WAVY",        "Wavy",        "wavy"));
        list.add(new Preset("DSTWIFI_WEED",        null,                  "Weed",        "weed"));
        list.add(new Preset("DSTWIFI_XPERIA",      "DSTSIG_XPERIA",      "Xperia",      "xperia"));
        list.add(new Preset("DSTWIFI_ZIGZAG",      "DSTSIG_ZIGZAG",      "ZigZag",      "zigzag"));
        list.add(new Preset("DSTWIFI_HOS",         "DSTSIG_HOS",         "HOS",         "hos"));
        return list;
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class PresetsAdapter extends RecyclerView.Adapter<PresetsAdapter.VH> {

        private int mExpandedPos = -1;

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_icon_preset, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Preset preset     = mPresets.get(pos);
            boolean isActive   = preset.key.equals(mCurrentPreset);
            boolean isExpanded = (pos == mExpandedPos);

            // Name color: accent when active
            h.name.setText(preset.displayName);
            h.name.setTextColor(isActive
                    ? ContextCompat.getColor(requireContext(), R.color.obs_primary)
                    : android.graphics.Color.WHITE);

            // Preview icons obs_wifi_{name}_signal_{1..4}
            int tint = ContextCompat.getColor(requireContext(), R.color.obs_primary);
            Resources res = requireContext().getResources();
            String pkg    = requireContext().getPackageName();
            ImageView[] previews = { h.preview1, h.preview2, h.preview3, h.preview4 };
            for (int i = 0; i < 4; i++) {
                int resId = res.getIdentifier(
                        "obs_wifi_" + preset.drawableName + "_signal_" + (i + 1),
                        "drawable", pkg);
                Drawable d = resId != 0
                        ? ContextCompat.getDrawable(requireContext(), resId) : null;
                if (d != null) { d = d.mutate(); d.setTint(tint); }
                previews[i].setImageDrawable(d);
            }

            // Expand / collapse on row click
            h.row.setOnClickListener(v -> {
                int prev = mExpandedPos;
                mExpandedPos = isExpanded ? -1 : pos;
                if (prev >= 0) notifyItemChanged(prev);
                notifyItemChanged(pos);
            });

            h.actions.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            if (isActive) {
                // Active: show DISABILITA
                h.btnApply.setText(getString(R.string.icon_preset_disable));
                h.btnApply.setOnClickListener(v -> {
                    mCurrentPreset = null;
                    ObsidianPrefs.remove(PREF_KEY);
                    ObsidianPrefs.putBoolean("WIFI_STYLE_OVERLAY", false);
                    saveBootProp("wifi", "");
                    new Thread(() -> it.tugaia56.obsidian.utils.overlay.compiler.GenericAppThemeCompiler.removeNamed("WIFI1")).start();
                    restartSystemUI();
                    mExpandedPos = -1;
                    notifyDataSetChanged();
                });
                h.btnApplyBoth.setVisibility(View.GONE);
            } else {
                // Not active: show APPLICA (+ APPLICA ENTRAMBI if signal match exists)
                h.btnApply.setText(getString(R.string.icon_preset_apply));
                h.btnApply.setOnClickListener(v -> {
                    mCurrentPreset = preset.key;
                    ObsidianPrefs.putString(PREF_KEY, preset.key);
                    // Stile Wi-Fi come overlay di SystemUI (si ricolora come l'icona di sistema)
                    ObsidianPrefs.putBoolean("WIFI_STYLE_OVERLAY", true);
                    saveBootProp("wifi", "");
                    applyWifiOverlay(preset.key);
                    mExpandedPos = -1;
                    notifyDataSetChanged();
                });

                if (preset.signalKey != null) {
                    h.btnApplyBoth.setVisibility(View.VISIBLE);
                    h.btnApplyBoth.setOnClickListener(v -> {
                        mCurrentPreset = preset.key;
                        ObsidianPrefs.putString(PREF_KEY,        preset.key);
                        ObsidianPrefs.putString(PREF_KEY_SIGNAL, preset.signalKey);
                        applyBothAndRestart(preset.key, preset.signalKey);
                        mExpandedPos = -1;
                        notifyDataSetChanged();
                    });
                } else {
                    h.btnApplyBoth.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() { return mPresets.size(); }

        class VH extends RecyclerView.ViewHolder {
            LinearLayout row, actions;
            TextView     name;
            ImageView    preview1, preview2, preview3, preview4;
            Button       btnApply, btnApplyBoth;

            VH(View v) {
                super(v);
                row          = v.findViewById(R.id.icon_preset_row);
                actions      = v.findViewById(R.id.icon_preset_actions);
                name         = v.findViewById(R.id.preset_name);
                preview1     = v.findViewById(R.id.preview_1);
                preview2     = v.findViewById(R.id.preview_2);
                preview3     = v.findViewById(R.id.preview_3);
                preview4     = v.findViewById(R.id.preview_4);
                btnApply     = v.findViewById(R.id.btn_apply);
                btnApplyBoth = v.findViewById(R.id.btn_apply_both);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveBootProp(String type, String value) {
        String prop = "wifi".equals(type)
                ? "persist.obsidian.dst.wifi_icon_preset"
                : "persist.obsidian.dst.signal_icon_preset";
        try {
            Runtime.getRuntime().exec(new String[]{
                "su", "-c",
                "resetprop " + prop + " " + (value.isEmpty() ? "\"\"" : value)
            });
        } catch (Throwable ignored) {}
    }

    /** Compila e attiva lo stile Wi-Fi scelto come overlay di SystemUI (WIFI1). La prima volta
     *  serve un riavvio perché il sistema conosca il nuovo overlay; poi i cambi sono immediati. */
    private void applyWifiOverlay(String presetKey) {
        String[] keys = it.tugaia56.obsidian.xposed.hooks.systemui.DstWifiIconStyle.getPresetKeys();
        String[] names = it.tugaia56.obsidian.xposed.hooks.systemui.DstWifiIconStyle.getPresetNames();
        String name = null;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(presetKey)) name = names[i];
        if (name == null) return;
        final String presetName = name;
        Toast.makeText(requireContext(), R.string.app_themes_working, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = false;
            try {
                it.tugaia56.obsidian.utils.overlay.compiler.GenericAppThemeCompiler.beginBatch();
                ok = !it.tugaia56.obsidian.utils.overlay.compiler.GenericAppThemeCompiler
                        .buildNamedInBatch("com.android.systemui", "WIFI_" + presetName, "WIFI1");
            } catch (Throwable ignored) {
            } finally {
                it.tugaia56.obsidian.utils.overlay.compiler.GenericAppThemeCompiler.finishNamed("WIFI1", ok);
            }
            final boolean fOk = ok;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        fOk ? getString(R.string.toast_applied) : getString(R.string.toast_error),
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void restartSystemUI() {
        Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
    }

    /** Atomically sets both props, then shows the manual-restart hint. */
    private void applyBothAndRestart(String wifiKey, String signalKey) {
        try {
            String cmd = "resetprop persist.obsidian.dst.wifi_icon_preset " + wifiKey
                    + " && resetprop persist.obsidian.dst.signal_icon_preset " + signalKey;
            Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        } catch (Throwable ignored) {}
        Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
    }
}
