package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import java.util.Collections;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.Constants;
import it.tugaia56.obsidian.utils.DarkShadowUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.utils.overlay.FabricatedUtil;

import static it.tugaia56.obsidian.utils.DarkShadowUtils.PREF_PREFIX;

/**
 * Clock & Date customisation sub-screen:
 *  – Card: Stile Orologio → ClockStyleFragment (position / size / padding)
 *  – Card: Stile Data → ClockOraDataFragment (date display, AM/PM, advanced format)
 *  – Colore Icone Barra di Stato (SBI overlay) — moved here from the DST tab
 *  – Colore Orologio (custom clock color)
 */
public class ClockDateFragment extends Fragment {

    // ── Status bar icon color (SBI overlay) ────────────────────────────────────
    private static final String SBI_OVERLAY = "SBI";
    private static final String[] SBI_NAMES = {
        "SBI_8",  "SBI_9",  "SBI_10",
        "SBI_11", "SBI_12", "SBI_13",
        "SBI_14", "SBI_15", "SBI_16", "SBI_17", "SBI_18", "SBI_19", "SBI_20",
        "SBI_21", "SBI_22"
    };

    // ── Clock custom color (Xposed hook approach, separate from SBI overlay) ──
    private static final String PREF_CLOCK_COLOR_ON = "status_bar_custom_clock_color";
    private static final String PREF_CLOCK_COLOR    = "status_bar_clock_color";

    // ── Adapters ──────────────────────────────────────────────────────────────
    private final List<DarkShadowItem> sbiItems        = new ArrayList<>();
    private final List<DarkShadowItem> clockColorItems = new ArrayList<>();
    private DarkShadowColorListener sbiAdapter;
    private DarkShadowColorListener clockColorAdapter;
    private int     mPendingDialogId = -1;
    private boolean mPendingIsClock  = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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
        rv.setPadding(0, 8, 0, 24);
        rv.setClipToPadding(false);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = (RecyclerView) view;

        // ── Status bar icon color ────────────────────────────────────────────
        sbiItems.clear();
        int     sbiColor = ObsidianPrefs.getInt(PREF_PREFIX + SBI_OVERLAY, 0xFFFFFFFF);
        boolean sbiOn    = ObsidianPrefs.getBoolean(PREF_PREFIX + SBI_OVERLAY + "_on", false);
        sbiItems.add(new DarkShadowItem(
                getString(R.string.section_statusbar_icon_color),
                SBI_OVERLAY,
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                sbiColor, sbiOn
        ));
        sbiAdapter = new DarkShadowColorListener(
                sbiItems, this::onSbiEnabled, this::onSbiDisabled, this::onSbiSwatch);

        // ── Custom clock color ────────────────────────────────────────────────
        clockColorItems.clear();
        int     clockColor = ObsidianPrefs.getInt(PREF_CLOCK_COLOR, Color.WHITE);
        boolean clockOn    = ObsidianPrefs.getBoolean(PREF_CLOCK_COLOR_ON, false);
        clockColorItems.add(new DarkShadowItem(
                getString(R.string.clock_color_title),
                "CLOCK_COLOR",
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                clockColor, clockOn
        ));
        clockColorAdapter = new DarkShadowColorListener(
                clockColorItems, this::onClockColorEnabled, this::onClockColorDisabled, this::onClockColorSwatch);

        buildRecyclerView(rv);
    }

    /** Assemble the ConcatAdapter. */
    private void buildRecyclerView(RecyclerView rv) {
        NavAdapter styleNav = new NavAdapter(List.of(
                new NavAdapter.NavItem(
                        R.drawable.ic_clock,
                        getString(R.string.nav_clock_style),
                        getString(R.string.nav_clock_style_summary),
                        () -> navigate(new ClockStyleFragment(), getString(R.string.nav_clock_style))),
                new NavAdapter.NavItem(
                        R.drawable.ic_clock,
                        getString(R.string.nav_clock_date),
                        getString(R.string.nav_clock_date_summary),
                        () -> navigate(new ClockOraDataFragment(), getString(R.string.nav_clock_date)))
        ), 0xFF7C4DFF); // purple, colore categoria "Barra di Stato"

        rv.setAdapter(new ConcatAdapter(
                new SectionTitleAdapter(List.of(getString(R.string.section_clock_date))),
                styleNav,
                new SectionTitleAdapter(List.of(getString(R.string.section_statusbar_icon_color))),
                new NoteAdapter(getString(R.string.sbi_note)),
                sbiAdapter,
                new SectionTitleAdapter(List.of(getString(R.string.clock_color_title))),
                clockColorAdapter
        ));
    }

    /** Riga di testo esplicativo (una breve descrizione sotto il titolo di una sezione). */
    private static class NoteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final String text;

        NoteAdapter(String text) { this.text = text; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView t = new TextView(parent.getContext());
            t.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            float d = parent.getResources().getDisplayMetrics().density;
            t.setPadding((int) (16 * d), (int) (2 * d), (int) (16 * d), (int) (8 * d));
            t.setTextSize(13);
            return new RecyclerView.ViewHolder(t) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            TextView t = (TextView) h.itemView;
            t.setText(text);
            t.setTextColor(ObsidianTheme.textColor(0x99));
        }

        @Override public int getItemCount() { return 1; }
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }

    // ── Status bar icon color callbacks ───────────────────────────────────────

    private void onSbiEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        DarkShadowUtils.saveColor(item);
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> applySbiColors(item.getColor(), ctx)).start();
    }

    private void onSbiDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(PREF_PREFIX + SBI_OVERLAY + "_on", false);
        Context ctx = requireContext().getApplicationContext();
        new Thread(() -> disableSbiColors(ctx)).start();
    }

    private void onSbiSwatch(DarkShadowItem item, int dialogId) {
        showColorAccentChoice(item, dialogId, PREF_PREFIX + SBI_OVERLAY, false, this::onSbiEnabled, sbiAdapter);
    }

    // ── Clock color callbacks ─────────────────────────────────────────────────

    private void onClockColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        ObsidianPrefs.putBoolean(PREF_CLOCK_COLOR_ON, true);
        ObsidianPrefs.putInt(PREF_CLOCK_COLOR, item.getColor());
    }

    private void onClockColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(PREF_CLOCK_COLOR_ON, false);
    }

    private void onClockColorSwatch(DarkShadowItem item, int dialogId) {
        showColorAccentChoice(item, dialogId, PREF_CLOCK_COLOR, true, this::onClockColorEnabled, clockColorAdapter);
    }

    /** Accento/Personalizzato inserted before the swatch opens the raw picker — Accento resolves
     *  immediately (reuses the file's existing onXxxEnabled save path), Personalizzato falls
     *  through to mPendingDialogId/mPendingIsClock + the real picker, same as before. */
    private void showColorAccentChoice(DarkShadowItem item, int dialogId, String colorKey,
                                        boolean isClock, java.util.function.Consumer<DarkShadowItem> onEnabled,
                                        DarkShadowColorListener adapter) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(colorKey + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(colorKey + "_use_accent", useAccent);
                    if (useAccent) {
                        item.setColor(ObsidianTheme.accentColor());
                        onEnabled.accept(item);
                        if (adapter != null) adapter.notifyDataSetChanged();
                    } else {
                        mPendingDialogId = dialogId;
                        mPendingIsClock  = isClock;
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Color picker result ───────────────────────────────────────────────────

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() != mPendingDialogId) return;
        mPendingDialogId = -1;

        if (mPendingIsClock) {
            mPendingIsClock = false;
            DarkShadowItem item = clockColorItems.get(0);
            ObsidianPrefs.putBoolean(PREF_CLOCK_COLOR + "_use_accent", false); // picking a colour implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(PREF_CLOCK_COLOR, event.color());
            if (clockColorAdapter != null) clockColorAdapter.notifyDataSetChanged();
        } else {
            DarkShadowItem item = sbiItems.get(0);
            ObsidianPrefs.putBoolean(DarkShadowUtils.PREF_PREFIX + item.getOverlayName() + "_use_accent", false); // picking a colour implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(DarkShadowUtils.PREF_PREFIX + item.getOverlayName(), event.color());
            if (sbiAdapter != null) sbiAdapter.notifyDataSetChanged();
            Context ctx = requireContext().getApplicationContext();
            new Thread(() -> applySbiColors(event.color(), ctx)).start();
        }
    }

    private void applySbiColors(int color, Context ctx) {
        String hex    = fmt(color);
        String transp = "0x00000000";
        String ripple = fmt(0x80000000 | (color & 0x00FFFFFF));
        String sui    = Constants.SYSTEM_UI;
        FabricatedUtil.buildAndEnableOverlays(
            new Object[]{sui, "SBI_8",  "color", "batterymeter_charge_color",                     hex},
            new Object[]{sui, "SBI_9",  "color", "batterymeter_bolt_color",                       hex},
            new Object[]{sui, "SBI_10", "color", "light_mode_icon_color_single_tone",             hex},
            new Object[]{sui, "SBI_11", "color", "light_mode_icon_color_dual_tone_background",    transp},
            new Object[]{sui, "SBI_12", "color", "light_mode_icon_color_dual_tone_fill",          hex},
            new Object[]{sui, "SBI_13", "color", "light_mode_qs_icon_color_single_tone",          hex},
            new Object[]{sui, "SBI_14", "color", "dark_mode_icon_color_single_tone",              hex},
            new Object[]{sui, "SBI_15", "color", "dark_mode_icon_color_dual_tone_background",     transp},
            new Object[]{sui, "SBI_16", "color", "dark_mode_icon_color_dual_tone_fill",           hex},
            new Object[]{sui, "SBI_17", "color", "dark_mode_qs_icon_color_dual_tone_background",  transp},
            new Object[]{sui, "SBI_18", "color", "dark_mode_qs_icon_color_single_tone",           hex},
            new Object[]{sui, "SBI_19", "color", "dark_mode_qs_icon_color_dual_tone_fill",        hex},
            new Object[]{sui, "SBI_20", "color", "status_bar_clock_color",                        hex},
            new Object[]{sui, "SBI_21", "color", "bright_foreground_disabled_material_dark",      ripple},
            new Object[]{sui, "SBI_22", "color", "stat_sys_airplane_icon_color",                  hex}
        );
        AppUtils.showRestartReminder(ctx);
    }

    private void disableSbiColors(Context ctx) {
        FabricatedUtil.disableOverlays(SBI_NAMES);
        AppUtils.showRestartReminder(ctx);
    }

    private static String fmt(int color) {
        return String.format("0x%08X", 0xFFFFFFFFL & color);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
