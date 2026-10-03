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
 * Fragment: Icone Segnale Mobile
 *
 * 42 presets (SGIC1-SGIC42). Same layout as WifiIconsFragment.
 * Presets with a matching WiFi name show "Applica entrambi".
 * Pressing Apply/Both also restarts SystemUI immediately.
 */
public class SignalIconsFragment extends Fragment {

    private static final String PREF_KEY_SIGNAL = "DST_PRESET_SIGNAL_ICON";
    private static final String PREF_KEY_WIFI   = "DST_PRESET_WIFI_ICON";

    // ── Preset definition ─────────────────────────────────────────────────────

    private static class Preset {
        final String key;           // e.g. "DSTSIG_AURORA"
        final String wifiKey;       // matching WiFi key, or null
        final String displayName;
        final String drawableName;  // e.g. "aurora" for obs_signal_aurora_*

        Preset(String key, String wifiKey, String displayName, String drawableName) {
            this.key          = key;
            this.wifiKey      = wifiKey;
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
        mCurrentPreset = ObsidianPrefs.getString(PREF_KEY_SIGNAL, null);
        mPresets = buildPresets();
        ((RecyclerView) view).setAdapter(new PresetsAdapter());
    }

    // ── Presets (SGIC1-SGIC42 order) ─────────────────────────────────────────

    private List<Preset> buildPresets() {
        List<Preset> list = new ArrayList<>();
        list.add(new Preset("DSTSIG_AQUARIUM",       null,                  "Aquarium",                    "aquarium"));
        list.add(new Preset("DSTSIG_AURORA",         "DSTWIFI_AURORA",      "Aurora",                      "aurora"));
        list.add(new Preset("DSTSIG_BARS",           "DSTWIFI_BARS",        "Bars",                        "bars"));
        list.add(new Preset("DSTSIG_BUTTERFLY",      null,                  "Butterfly",                   "butterfly"));
        list.add(new Preset("DSTSIG_CIRCLE",         null,                  "Circle",                      "circle"));
        list.add(new Preset("DSTSIG_DAUN",           null,                  "Daun",                        "daun"));
        list.add(new Preset("DSTSIG_DEC",            null,                  "Dec",                         "dec"));
        list.add(new Preset("DSTSIG_DEEP",           null,                  "Deep",                        "deep"));
        list.add(new Preset("DSTSIG_DORA",           "DSTWIFI_DORA",        "Dora",                        "dora"));
        list.add(new Preset("DSTSIG_EQUAL",          null,                  "Equal",                       "equal"));
        list.add(new Preset("DSTSIG_FAINT_UI",       "DSTWIFI_FAINT_UI",    "Faint UI",                    "faint_ui"));
        list.add(new Preset("DSTSIG_FAN",            null,                  "Fan",                         "fan"));
        list.add(new Preset("DSTSIG_FORLORN",        "DSTWIFI_FORLORN",     "Forlorn",                     "forlorn"));
        list.add(new Preset("DSTSIG_GRADICON",       "DSTWIFI_GRADICON",    "Gradicon",                    "gradicon"));
        list.add(new Preset("DSTSIG_HUAWEI",         null,                  "Huawei",                      "huawei"));
        list.add(new Preset("DSTSIG_INSIDE",         "DSTWIFI_INSIDE",      "Inside",                      "inside"));
        list.add(new Preset("DSTSIG_IOS",            null,                  "iOS",                         "ios"));
        list.add(new Preset("DSTSIG_MINI",           null,                  "Mini",                        "mini"));
        list.add(new Preset("DSTSIG_NOTHING_DOT",    "DSTWIFI_NOTHING_DOT", "Nothing Dot",                 "nothing_dot"));
        list.add(new Preset("DSTSIG_ODIN",           null,                  "Odin",                        "odin"));
        list.add(new Preset("DSTSIG_PILLS",          null,                  "Pills",                       "pills"));
        list.add(new Preset("DSTSIG_PLUMPY",         "DSTWIFI_PLUMPY",      "Plumpy",                      "plumpy"));
        list.add(new Preset("DSTSIG_PUI",            "DSTWIFI_PUI",         "PUI",                         "pui"));
        list.add(new Preset("DSTSIG_REL",            null,                  "Rel",                         "rel"));
        list.add(new Preset("DSTSIG_ROMAN",          null,                  "Roman",                       "roman"));
        list.add(new Preset("DSTSIG_ROUND",          "DSTWIFI_ROUND",       "Round",                       "round"));
        list.add(new Preset("DSTSIG_SCROLL",         null,                  "Scroll",                      "scroll"));
        list.add(new Preset("DSTSIG_SEA",            null,                  "Sea",                         "sea"));
        list.add(new Preset("DSTSIG_SNEAKY",         "DSTWIFI_SNEAKY",      "Sneaky",                      "sneaky"));
        list.add(new Preset("DSTSIG_STACK",          null,                  "Stack",                       "stack"));
        list.add(new Preset("DSTSIG_STROKE",         "DSTWIFI_STROKE",      "Stroke",                      "stroke"));
        list.add(new Preset("DSTSIG_WANNUI",         null,                  "Wannui",                      "wannui"));
        list.add(new Preset("DSTSIG_WAVY",           "DSTWIFI_WAVY",        "Wavy",                        "wavy"));
        list.add(new Preset("DSTSIG_WINDOWS",        null,                  "Windows",                     "windows"));
        list.add(new Preset("DSTSIG_WING",           null,                  "Wing",                        "wing"));
        list.add(new Preset("DSTSIG_XPERIA",         "DSTWIFI_XPERIA",      "Xperia",                      "xperia"));
        list.add(new Preset("DSTSIG_ZIGZAG",         "DSTWIFI_ZIGZAG",      "ZigZag",                      "zigzag"));
        list.add(new Preset("DSTSIG_HOS",            "DSTWIFI_HOS",         "HOS",                         "hos"));
        list.add(new Preset("DSTSIG_NOTHING_DOT_V2", null,                  "Nothing Dot V2",              "nothing_dot_v2"));
        list.add(new Preset("DSTSIG_IOS_10",         null,                  "iOS 10",                      "ios_10"));
        list.add(new Preset("DSTSIG_IOS_BY",         null,                  "iOS by 买啤酒也用券",         "ios_by"));
        list.add(new Preset("DSTSIG_IOS_DOUBLE_BY",  null,                  "iOS double by 买啤酒也用券",  "ios_double_by"));
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

            h.name.setText(preset.displayName);
            h.name.setTextColor(isActive
                    ? ContextCompat.getColor(requireContext(), R.color.obs_primary)
                    : android.graphics.Color.WHITE);

            // Preview icons obs_signal_{name}_{1..4}
            int tint = ContextCompat.getColor(requireContext(), R.color.obs_primary);
            Resources res = requireContext().getResources();
            String pkg    = requireContext().getPackageName();
            ImageView[] previews = { h.preview1, h.preview2, h.preview3, h.preview4 };
            for (int i = 0; i < 4; i++) {
                int resId = res.getIdentifier(
                        "obs_signal_" + preset.drawableName + "_" + (i + 1),
                        "drawable", pkg);
                Drawable d = resId != 0
                        ? ContextCompat.getDrawable(requireContext(), resId) : null;
                if (d != null) { d = d.mutate(); d.setTint(tint); }
                previews[i].setImageDrawable(d);
            }

            h.row.setOnClickListener(v -> {
                int prev = mExpandedPos;
                mExpandedPos = isExpanded ? -1 : pos;
                if (prev >= 0) notifyItemChanged(prev);
                notifyItemChanged(pos);
            });

            h.actions.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            if (isActive) {
                // Active: DISABILITA
                h.btnApply.setText(getString(R.string.icon_preset_disable));
                h.btnApply.setOnClickListener(v -> {
                    mCurrentPreset = null;
                    ObsidianPrefs.remove(PREF_KEY_SIGNAL);
                    saveBootProp("signal", "");
                    it.tugaia56.obsidian.utils.overlay.SystemUiIconOverlays.removeSignal();
                    restartSystemUI();
                    mExpandedPos = -1;
                    notifyDataSetChanged();
                });
                h.btnApplyBoth.setVisibility(View.GONE);
            } else {
                // Not active: APPLICA (+ APPLICA ENTRAMBI if WiFi match exists)
                h.btnApply.setText(getString(R.string.icon_preset_apply));
                h.btnApply.setOnClickListener(v -> {
                    mCurrentPreset = preset.key;
                    ObsidianPrefs.putString(PREF_KEY_SIGNAL, preset.key);
                    ObsidianPrefs.putBoolean("SIGNAL_STYLE_OVERLAY", true);
                    saveBootProp("signal", "");
                    it.tugaia56.obsidian.utils.overlay.SystemUiIconOverlays.apply(requireContext(), null, preset.key);
                    mExpandedPos = -1;
                    notifyDataSetChanged();
                });

                if (preset.wifiKey != null) {
                    h.btnApplyBoth.setVisibility(View.VISIBLE);
                    h.btnApplyBoth.setOnClickListener(v -> {
                        mCurrentPreset = preset.key;
                        ObsidianPrefs.putString(PREF_KEY_SIGNAL, preset.key);
                        ObsidianPrefs.putString(PREF_KEY_WIFI,   preset.wifiKey);
                        ObsidianPrefs.putBoolean("SIGNAL_STYLE_OVERLAY", true);
                        ObsidianPrefs.putBoolean("WIFI_STYLE_OVERLAY", true);
                        saveBootProp("signal", "");
                        saveBootProp("wifi", "");
                        it.tugaia56.obsidian.utils.overlay.SystemUiIconOverlays.apply(requireContext(), preset.wifiKey, preset.key);
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
