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
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Gradiente personalizzato: 2+ colori scelti dall'utente, ciascuno con una posizione 0-100%,
 * direzione Orizzontale/Verticale — alternativa ai Preset fissi di "Immagine sfondo"/"Progresso
 * a riga" in VolumePanelColorsFragment, richiesta esplicita: "posso mettere più di due colori?
 * ed assegnare una percentuale?". Un solo screen riusato per entrambi i target (bg/line) tramite
 * gli argument, stesso formato stringa "AARRGGBB,pos;AARRGGBB,pos;..." letto/scritto anche da
 * VolumePanelMod.parseGradientStops() — duplicato lì per indipendenza (processo diverso).
 */
public class GradientStopsFragment extends Fragment {

    public static final String ARG_TARGET = "target"; // "bg" | "line"

    private static final String PREF_CUSTOM_BG       = "volume_panel_seekbar_bg_color_enabled";
    private static final String PREF_BG_MODE         = "volume_panel_seekbar_bg_mode";
    private static final String PREF_BG_STOPS        = "volume_panel_seekbar_bg_gradient_custom_stops";
    private static final String PREF_BG_VERTICAL     = "volume_panel_seekbar_bg_gradient_custom_vertical";

    private static final String PREF_LINE_FILL_MODE  = "volume_panel_seekbar_progress_line_fill_mode";
    private static final String PREF_LINE_STOPS      = "volume_panel_seekbar_progress_line_gradient_custom_stops";
    private static final String PREF_LINE_VERTICAL   = "volume_panel_seekbar_progress_line_gradient_custom_vertical";

    private static final int MAX_STOPS = 6;
    private static final int DIALOG_STOP_COLOR = "volume_panel_custom_gradient_stop_color".hashCode();

    private boolean mIsBg; // true = "Immagine sfondo" target, false = "Progresso a riga"
    private RecyclerView mRv;
    private final List<Integer> mColors = new ArrayList<>();
    private final List<Integer> mPositions = new ArrayList<>(); // 0-100
    private int mEditingIndex = -1;

    public static GradientStopsFragment newInstance(String target) {
        GradientStopsFragment f = new GradientStopsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TARGET, target);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        mIsBg = getArguments() == null || !"line".equals(getArguments().getString(ARG_TARGET));
        loadStops();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

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

    // ── Persistence ──────────────────────────────────────────────────────────

    private String stopsPrefKey() { return mIsBg ? PREF_BG_STOPS : PREF_LINE_STOPS; }
    private String verticalPrefKey() { return mIsBg ? PREF_BG_VERTICAL : PREF_LINE_VERTICAL; }

    private void loadStops() {
        mColors.clear();
        mPositions.clear();
        String raw = ObsidianPrefs.getString(stopsPrefKey(), "");
        if (!raw.isEmpty()) {
            for (String part : raw.split(";")) {
                String[] kv = part.split(",");
                if (kv.length != 2) continue;
                try {
                    mColors.add((int) Long.parseLong(kv[0], 16));
                    mPositions.add(Math.max(0, Math.min(100, Integer.parseInt(kv[1]))));
                } catch (Throwable ignored) {}
            }
        }
        if (mColors.size() < 2) {
            mColors.clear(); mPositions.clear();
            mColors.add(0xFF00C9FF); mPositions.add(0);
            mColors.add(0xFF1B2029); mPositions.add(100);
        }
    }

    private void saveStops() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mColors.size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(Integer.toHexString(mColors.get(i))).append(',').append(mPositions.get(i));
        }
        ObsidianPrefs.putString(stopsPrefKey(), sb.toString());
        // Attiva la modalità gradiente personalizzato — stesso pattern dei preset fissi
        // (showBgGradientPresetDialog/showProgressLineGradientPresetDialog in VolumePanelColorsFragment).
        if (mIsBg) {
            ObsidianPrefs.putString(PREF_BG_MODE, "gradient_custom");
            ObsidianPrefs.putBoolean(PREF_CUSTOM_BG, true);
        } else {
            ObsidianPrefs.putString(PREF_LINE_FILL_MODE, "gradient_custom");
        }
    }

    // ── Color picker round-trip ──────────────────────────────────────────────

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() != DIALOG_STOP_COLOR || mEditingIndex < 0 || mEditingIndex >= mColors.size()) return;
        mColors.set(mEditingIndex, event.color());
        saveStops();
        rebuild();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void rebuild() {
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        boolean vertical = ObsidianPrefs.getBoolean(verticalPrefKey(), mIsBg);
        ListWidgetAdapter.ListItem directionItem = new ListWidgetAdapter.ListItem(
                getString(R.string.gradient_stops_direction_title),
                vertical ? getString(R.string.gradient_stops_direction_vertical)
                         : getString(R.string.gradient_stops_direction_horizontal),
                this::showDirectionDialog);
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gradient_stops_section))));
        GroupUtils.addGroup(chain, List.of(directionItem));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.gradient_stops_colors_section))));
        List<Object> stopRows = new ArrayList<>();
        for (int i = 0; i < mColors.size(); i++) {
            final int index = i;
            int color = mColors.get(i);
            ListWidgetAdapter.ListItem colorItem = new ListWidgetAdapter.ListItem(
                    getString(R.string.gradient_stops_color_label, index + 1),
                    String.format("#%06X", 0xFFFFFF & color),
                    () -> {
                        mEditingIndex = index;
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(
                                    DIALOG_STOP_COLOR, color, true, true, true);
                        }
                    });
            stopRows.add(colorItem);

            SliderWidgetAdapter.SliderItem posItem = new SliderWidgetAdapter.SliderItem(
                    getString(R.string.gradient_stops_position_label, index + 1),
                    mPositions.get(i), 0, 100, "%",
                    value -> {
                        mPositions.set(index, value);
                        saveStops();
                    });
            stopRows.add(posItem);

            if (mColors.size() > 2) {
                ListWidgetAdapter.ListItem removeItem = new ListWidgetAdapter.ListItem(
                        getString(R.string.gradient_stops_remove, index + 1), null,
                        () -> {
                            mColors.remove(index);
                            mPositions.remove(index);
                            saveStops();
                            rebuild();
                        });
                removeItem.useAccentColor = false;
                stopRows.add(removeItem);
            }
        }
        GroupUtils.addGroup(chain, stopRows);

        if (mColors.size() < MAX_STOPS) {
            ListWidgetAdapter.ListItem addItem = new ListWidgetAdapter.ListItem(
                    getString(R.string.gradient_stops_add), null,
                    () -> {
                        int lastPos = mPositions.get(mPositions.size() - 1);
                        mColors.add(0xFFFFFFFF);
                        mPositions.add(Math.min(100, lastPos));
                        saveStops();
                        mEditingIndex = mColors.size() - 1;
                        rebuild();
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(
                                    DIALOG_STOP_COLOR, 0xFFFFFFFF, true, true, true);
                        }
                    });
            GroupUtils.addGroup(chain, List.of(addItem));
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    private void showDirectionDialog() {
        String[] entries = {
                getString(R.string.gradient_stops_direction_horizontal),
                getString(R.string.gradient_stops_direction_vertical)
        };
        boolean currentVertical = ObsidianPrefs.getBoolean(verticalPrefKey(), mIsBg);
        final int[] selected = {currentVertical ? 1 : 0};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.gradient_stops_direction_title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putBoolean(verticalPrefKey(), selected[0] == 1);
                    saveStops();
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }
}
