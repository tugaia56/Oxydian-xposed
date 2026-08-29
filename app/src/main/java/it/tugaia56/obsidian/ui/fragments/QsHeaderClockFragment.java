package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.utils.ViewHelper;

import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_ENABLED;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STYLE;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_COLOR_ALL_ON;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_COLOR;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_TEXT2;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_ACCENT;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_ACCENT2;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_ACCENT3;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_SCALE;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_FORMAT;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_CUSTOM_FONT;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_TOP_MARGIN;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_LEFT_MARGIN;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_COLOR_ON;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_COLOR;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_HIDE_DATE;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_DATE_COLOR_ON;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_DATE_COLOR;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_HIDE_CARRIER;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_CLOCK_CHIP_ON;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_DATE_CHIP_ON;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_RED_MODE;
import static it.tugaia56.obsidian.xposed.hooks.systemui.QsHeaderClock.PREF_STOCK_RED_COLOR;

/**
 * Orologio Intestazione QS — UI port reale di OC's qs_header_clock_prefs.xml.
 *
 * "Stile Orologio" ha ora tutti e 8 gli stili reali OC (+ "Nessuno") — vedi
 * obs_qs_clock_style_0..8.xml, porting reale di preview_header_clock_0..8.xml.
 *
 * "Modalità Orologio RED" ha le 4 modalità reali OC (Predefinito/Disabilita/Colore
 * Accento/Colore Personalizzato), applicate dal vero hook in QsHeaderClock.
 *
 * Background Chip (orologio/data) — stesso editor completo del chip Barra di stato
 * (ClockChipStyleFragment, riusato via prefisso chiave prefs) e stessa logica di disegno
 * reale (ChipStyleHelper), applicata in QsHeaderClock.applyStockPrefs().
 */
public class QsHeaderClockFragment extends Fragment {

    // Stesso schema chiavi/editor del chip Barra di stato (ClockChipStyleFragment +
    // ChipStyleHelper) — qui due istanze indipendenti, una per l'orologio e una per la data.
    private static final String CLOCK_CHIP_PREFIX = "qs_header_clock_background_chip";
    private static final String DATE_CHIP_PREFIX  = "qs_header_date_background_chip";

    private static final int RED_ONE_MODE_CUSTOM = 3; // "Colore Personalizzato"

    private static final int RED_ONE_COLOR_DIALOG_ID   = PREF_STOCK_RED_COLOR.hashCode();
    private static final int STOCK_COLOR_DIALOG_ID      = PREF_STOCK_COLOR.hashCode();
    private static final int STOCK_DATE_COLOR_DIALOG_ID = PREF_STOCK_DATE_COLOR.hashCode();

    private RecyclerView mRv;
    private final List<DarkShadowItem> mColorItems = new ArrayList<>();
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude l'opzione sottostante — stesso pattern di QsTilesCustomizeFragment. Il
    // toggle principale "Attiva" NON è incluso: seleziona la modalità (Stock/Personalizzato),
    // non nasconde/mostra righe di dettaglio proprie — sempre mostra un set di opzioni.
    private boolean mStockColorExpanded = ObsidianPrefs.getBoolean(PREF_STOCK_COLOR_ON, false);
    private boolean mDateColorExpanded  = ObsidianPrefs.getBoolean(PREF_STOCK_DATE_COLOR_ON, false);
    private boolean mClockChipExpanded  = ObsidianPrefs.getBoolean(PREF_STOCK_CLOCK_CHIP_ON, false);
    private boolean mDateChipExpanded   = ObsidianPrefs.getBoolean(PREF_STOCK_DATE_CHIP_ON, false);
    private boolean mFontExpanded       = ObsidianPrefs.getBoolean(PREF_CUSTOM_FONT, false);
    private boolean mColorAllExpanded   = ObsidianPrefs.getBoolean(PREF_COLOR_ALL_ON, false);

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
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

    @Override
    public void onResume() {
        super.onResume();
        // Aggiorna l'etichetta "Stile Chip" al ritorno dall'editor (ClockChipStyleFragment).
        if (mRv != null) rebuild();
    }

    private void rebuild() {
        mColorItems.clear();
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        // ── Attiva ────────────────────────────────────────────────────────────
        boolean enabled = ObsidianPrefs.getBoolean(PREF_ENABLED, false);
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_clock_prefs))));
        chain.add(new SwitchWidgetAdapter(List.of(
                gatingSwitch(getString(R.string.qs_header_clock_enabled),
                        getString(R.string.qs_header_clock_enabled_summary), PREF_ENABLED))));

        if (!enabled) {
            // ── Preferenze Orologio Stock — reale OC: visibile SOLO quando l'orologio
            // personalizzato è disattivato (stesso comportamento di OC). Tutte le righe
            // (incluse quelle condizionali) restano nella STESSA sezione senza titoli
            // intermedi — un unico gruppo/card. ──────────────────────────────────────
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_stock_section))));
            List<Object> stockRows = new ArrayList<>();
            stockRows.add(redOneModeItem());

            SwitchWidgetAdapter.SwitchItem stockColorSwitch = gatingSwitch(getString(R.string.qs_header_stock_time_color_title), null, PREF_STOCK_COLOR_ON);
            stockColorSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(PREF_STOCK_COLOR_ON, stockColorSwitch.checked);
                mStockColorExpanded = stockColorSwitch.checked;
                rebuild();
            };
            stockColorSwitch.onRowClick = () -> { mStockColorExpanded = !mStockColorExpanded; rebuild(); };
            stockRows.add(stockColorSwitch);
            if (mStockColorExpanded) {
                stockRows.add(colorTriggerItem(getString(R.string.qs_header_stock_color),
                        PREF_STOCK_COLOR, STOCK_COLOR_DIALOG_ID, Color.WHITE));
            }

            boolean hideDate = ObsidianPrefs.getBoolean(PREF_STOCK_HIDE_DATE, false);
            stockRows.add(gatingSwitch(getString(R.string.qs_header_stock_hide_date), null, PREF_STOCK_HIDE_DATE));

            if (!hideDate) {
                SwitchWidgetAdapter.SwitchItem dateColorSwitch = gatingSwitch(getString(R.string.qs_header_stock_clock_date_custom_color_title), null, PREF_STOCK_DATE_COLOR_ON);
                dateColorSwitch.onChanged = () -> {
                    ObsidianPrefs.putBoolean(PREF_STOCK_DATE_COLOR_ON, dateColorSwitch.checked);
                    mDateColorExpanded = dateColorSwitch.checked;
                    rebuild();
                };
                dateColorSwitch.onRowClick = () -> { mDateColorExpanded = !mDateColorExpanded; rebuild(); };
                stockRows.add(dateColorSwitch);
                if (mDateColorExpanded) {
                    stockRows.add(colorTriggerItem(getString(R.string.qs_header_stock_clock_date_custom_color),
                            PREF_STOCK_DATE_COLOR, STOCK_DATE_COLOR_DIALOG_ID, Color.WHITE));
                }
            }

            SwitchWidgetAdapter.SwitchItem clockChipSwitch = gatingSwitch(getString(R.string.qs_header_stock_clock_background_chip), null, PREF_STOCK_CLOCK_CHIP_ON);
            clockChipSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(PREF_STOCK_CLOCK_CHIP_ON, clockChipSwitch.checked);
                mClockChipExpanded = clockChipSwitch.checked;
                rebuild();
            };
            clockChipSwitch.onRowClick = () -> { mClockChipExpanded = !mClockChipExpanded; rebuild(); };
            stockRows.add(clockChipSwitch);
            if (mClockChipExpanded) stockRows.add(chipStyleItem(CLOCK_CHIP_PREFIX,
                    getString(R.string.qs_header_stock_clock_background_chip_style)));

            if (!hideDate) {
                SwitchWidgetAdapter.SwitchItem dateChipSwitch = gatingSwitch(getString(R.string.qs_header_stock_date_background_chip), null, PREF_STOCK_DATE_CHIP_ON);
                dateChipSwitch.onChanged = () -> {
                    ObsidianPrefs.putBoolean(PREF_STOCK_DATE_CHIP_ON, dateChipSwitch.checked);
                    mDateChipExpanded = dateChipSwitch.checked;
                    rebuild();
                };
                dateChipSwitch.onRowClick = () -> { mDateChipExpanded = !mDateChipExpanded; rebuild(); };
                stockRows.add(dateChipSwitch);
                if (mDateChipExpanded) stockRows.add(chipStyleItem(DATE_CHIP_PREFIX,
                        getString(R.string.qs_header_stock_date_background_chip_style)));
            }

            stockRows.add(prefSwitch(getString(R.string.qs_header_stock_clock_hide_carrier_label), null, PREF_STOCK_HIDE_CARRIER));
            GroupUtils.addGroup(chain, stockRows);
        } else {
            // ── Stile Orologio — griglia con anteprima reale, visibile SOLO quando
            // l'orologio personalizzato è attivo (stesso comportamento di OC). ─────
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_clock_style))));
            chain.add(clockStylePreviewRow());

            // ── Font ─────────────────────────────────────────────────────────────
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_font_section))));
            SwitchWidgetAdapter.SwitchItem fontSwitch = gatingSwitch(getString(R.string.qs_header_clock_font_title), null, PREF_CUSTOM_FONT);
            fontSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(PREF_CUSTOM_FONT, fontSwitch.checked);
                mFontExpanded = fontSwitch.checked;
                rebuild();
            };
            fontSwitch.onRowClick = () -> { mFontExpanded = !mFontExpanded; rebuild(); };
            List<Object> fontRows = new ArrayList<>();
            fontRows.add(fontSwitch);
            if (mFontExpanded) fontRows.add(stubItem(getString(R.string.pick_font_title), getString(R.string.pick_font_summary)));
            GroupUtils.addGroup(chain, fontRows);

            // ── Preferenze Orologio Personalizzato: colori/scala/formato/immagine ──
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_clock_custom_prefs_section))));
            SwitchWidgetAdapter.SwitchItem colorAllSwitch = gatingSwitch(getString(R.string.qs_header_clock_colors_title), null, PREF_COLOR_ALL_ON);
            colorAllSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(PREF_COLOR_ALL_ON, colorAllSwitch.checked);
                mColorAllExpanded = colorAllSwitch.checked;
                rebuild();
            };
            colorAllSwitch.onRowClick = () -> { mColorAllExpanded = !mColorAllExpanded; rebuild(); };
            GroupUtils.addGroup(chain, List.of(colorAllSwitch));
            if (mColorAllExpanded) chain.add(clockColorsRow());
            GroupUtils.addGroup(chain, List.of(
                    sliderItem(getString(R.string.qs_header_clock_scale), PREF_SCALE, 50, 200, 100, "%"),
                    editTextItem(getString(R.string.lockscreen_clock_custom_format_title),
                            getString(R.string.lockscreen_clock_custom_format_summary), PREF_FORMAT)));

            // ── Margini Orologio ─────────────────────────────────────────────────
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.qs_header_adjust))));
            GroupUtils.addGroup(chain, List.of(
                    sliderItem(getString(R.string.qs_header_clock_top_margin), PREF_TOP_MARGIN, 0, 100, 0, "dp"),
                    sliderItem(getString(R.string.qs_header_clock_left_margin), PREF_LEFT_MARGIN, 0, 100, 8, "dp")));
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Stile Orologio — carosello orizzontale con anteprima live ───────────────

    private RecyclerView.Adapter<?> clockStylePreviewRow() {
        return new ClockStylePreviewAdapter();
    }

    private int currentClockStyle() {
        try { return Integer.parseInt(ObsidianPrefs.getString(PREF_STYLE, "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Una sola riga scrollabile orizzontalmente: ogni cella inflate il vero layout dello stile. */
    private class ClockStylePreviewAdapter extends RecyclerView.Adapter<ClockStylePreviewAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView inner = new RecyclerView(parent.getContext());
            inner.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
            int pad = dp(12);
            inner.setPadding(pad, pad, pad, pad);
            inner.setClipToPadding(false);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            inner.setLayoutParams(lp);
            inner.setAdapter(new StyleCardAdapter());
            return new VH(inner);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {}
        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }

    private static final int[] STYLE_LAYOUTS = {
            R.layout.obs_qs_clock_style_0, R.layout.obs_qs_clock_style_1,
            R.layout.obs_qs_clock_style_2, R.layout.obs_qs_clock_style_3,
            R.layout.obs_qs_clock_style_4, R.layout.obs_qs_clock_style_5,
            R.layout.obs_qs_clock_style_6, R.layout.obs_qs_clock_style_7,
            R.layout.obs_qs_clock_style_8,
    };

    private class StyleCardAdapter extends RecyclerView.Adapter<StyleCardAdapter.VH> {
        private final int mSelected = currentClockStyle();
        private final String[] mNames = getResources().getStringArray(R.array.qs_header_clock_style_entries);

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            int padH = dp(12), padV = dp(14);
            root.setPadding(padH, padV, padH, padV);
            root.setMinimumWidth(dp(140));

            FrameLayout previewFrame = new FrameLayout(parent.getContext());
            previewFrame.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));

            TextView label = new TextView(parent.getContext());
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(8);

            root.addView(previewFrame);
            root.addView(label, labelLp);

            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            rootLp.setMargins(m, m, m, m);
            root.setLayoutParams(rootLp);

            return new VH(root, previewFrame, label);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            boolean selected = pos == mSelected;
            int accent = ObsidianTheme.accentColor();

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(dp(12));
            if (selected) bg.setStroke(dp(2), accent);
            h.itemView.setBackground(bg);

            h.previewFrame.removeAllViews();
            try {
                View clockView = LayoutInflater.from(h.itemView.getContext())
                        .inflate(STYLE_LAYOUTS[pos], h.previewFrame, false);
                ViewHelper.findViewWithTagAndChangeColor(clockView, "text1", Color.WHITE);
                ViewHelper.findViewWithTagAndChangeColor(clockView, "text2", Color.WHITE);
                ViewHelper.findViewWithTagAndChangeColor(clockView, "accent1", accent);
                ViewHelper.findViewWithTagAndChangeColor(clockView, "accent2", accent);
                ViewHelper.findViewWithTagAndChangeColor(clockView, "accent3", accent);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
                h.previewFrame.addView(clockView, clp);
            } catch (Throwable ignored) {}

            h.label.setText(pos < mNames.length ? mNames[pos] : String.valueOf(pos));
            h.label.setTextColor(selected ? accent : 0x99FFFFFF);

            h.itemView.setOnClickListener(v -> {
                ObsidianPrefs.putString(PREF_STYLE, String.valueOf(pos));
                rebuild();
            });
        }

        @Override public int getItemCount() { return STYLE_LAYOUTS.length; }

        class VH extends RecyclerView.ViewHolder {
            final FrameLayout previewFrame;
            final TextView label;
            VH(View v, FrameLayout previewFrame, TextView label) {
                super(v);
                this.previewFrame = previewFrame;
                this.label = label;
            }
        }
    }

    // ── 5-swatch clock colours (accent1/2/3, text1/2) ───────────────────────────

    private DarkShadowColorListener clockColorsRow() {
        List<DarkShadowItem> colors = List.of(
                colorItem(getString(R.string.general_color_accent1), PREF_ACCENT, 0xFF006781),
                colorItem(getString(R.string.general_color_accent2), PREF_ACCENT2, 0xFF006781),
                colorItem(getString(R.string.general_color_accent3), PREF_ACCENT3, 0xFF006781),
                colorItem(getString(R.string.general_color_text1), PREF_COLOR, 0xFFFFFFFF),
                colorItem(getString(R.string.general_color_text2), PREF_TEXT2, 0xFF000000));
        mColorItems.addAll(colors);
        return new DarkShadowColorListener(colors, this::onColorEnabled, this::onColorDisabled, this::onColorSwatch);
    }

    private DarkShadowItem colorItem(String label, String key, int def) {
        return new DarkShadowItem(label, key, Collections.emptyList(), Collections.emptyList(), null,
                ObsidianPrefs.getInt(key, def), ObsidianPrefs.getBoolean(key + "_on", false));
    }

    private void onColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        ObsidianPrefs.putInt(item.getOverlayName(), item.getColor());
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", true);
        rebuild();
    }

    private void onColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", false);
        rebuild();
    }

    private void onColorSwatch(DarkShadowItem item, int dialogId) {
        showClockColorAccentChoice(item, dialogId);
    }

    /** Accento/Personalizzato inserted before the swatch opens the raw picker — Accento resolves
     *  immediately (reuses onColorEnabled's existing save path), Personalizzato opens the picker
     *  as before. Baked at selection time (no live re-resolve), same as every other picker. */
    private void showClockColorAccentChoice(DarkShadowItem item, int dialogId) {
        String colorKey = item.getOverlayName();
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
                        onColorEnabled(item); // already calls rebuild()
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Comportamento Uno rosso — scelta a 4 vie, l'ultima apre il picker colore ────

    private String redOneModeLabel() {
        String[] entries = getResources().getStringArray(R.array.qs_header_red_one_mode_entries);
        int mode = 0;
        try { mode = Integer.parseInt(ObsidianPrefs.getString(PREF_STOCK_RED_MODE, "0")); }
        catch (NumberFormatException ignored) {}
        return (mode >= 0 && mode < entries.length) ? entries[mode] : entries[0];
    }

    private ListWidgetAdapter.ListItem redOneModeItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.qs_header_stock_uno_rosso), redOneModeLabel(),
                this::showRedOneModeDialog);
    }

    private void showRedOneModeDialog() {
        String[] entries = getResources().getStringArray(R.array.qs_header_red_one_mode_entries);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(PREF_STOCK_RED_MODE, "0")); }
        catch (NumberFormatException ignored) {}
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.qs_header_stock_uno_rosso))
                .setSingleChoiceItems(entries, current, (d, which) -> {
                    d.dismiss();
                    if (which == RED_ONE_MODE_CUSTOM) {
                        // L'ultima scelta apre direttamente il picker colore — onColorSelected
                        // imposta già la modalità a "custom" quando arriva un colore.
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showColorPickerDialog(
                                    RED_ONE_COLOR_DIALOG_ID,
                                    ObsidianPrefs.getInt(PREF_STOCK_RED_COLOR, Color.RED),
                                    true, true, true);
                        }
                        return;
                    }
                    ObsidianPrefs.putString(PREF_STOCK_RED_MODE, String.valueOf(which));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Single-colour "trigger" row — no persistent swatch, tap opens the picker ────

    private ListWidgetAdapter.ListItem colorTriggerItem(String title, String key, int dialogId, int def) {
        String label = ObsidianPrefs.getBoolean(key + "_use_accent", false)
                ? getString(R.string.color_mode_accent) : getString(R.string.pick_color);
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                title, label, () -> showTriggerColorAccentChoice(title, key, dialogId, def));
        item.useAccentColor = false;
        return item;
    }

    /** Accento/Personalizzato inserted before the row opens the raw picker — Accento resolves
     *  immediately, Personalizzato opens the picker as before. Baked at selection time (no live
     *  re-resolve), same as every other picker. */
    private void showTriggerColorAccentChoice(String title, String key, int dialogId, int def) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        boolean currentAccent = ObsidianPrefs.getBoolean(key + "_use_accent", false);
        final int[] selected = {currentAccent ? 0 : 1};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, selected[0], (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(key + "_use_accent", useAccent);
                    if (useAccent) {
                        ObsidianPrefs.putInt(key, ObsidianTheme.accentColor());
                        rebuild();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, ObsidianPrefs.getInt(key, def), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        for (DarkShadowItem item : mColorItems) {
            if (event.dialogId() == System.identityHashCode(item)) {
                ObsidianPrefs.putBoolean(item.getOverlayName() + "_use_accent", false); // picking implies custom
                item.setColor(event.color());
                ObsidianPrefs.putInt(item.getOverlayName(), event.color());
                rebuild();
                return;
            }
        }
        if (event.dialogId() == RED_ONE_COLOR_DIALOG_ID) {
            ObsidianPrefs.putInt(PREF_STOCK_RED_COLOR, event.color());
            ObsidianPrefs.putString(PREF_STOCK_RED_MODE, String.valueOf(RED_ONE_MODE_CUSTOM));
            rebuild();
        } else if (event.dialogId() == STOCK_COLOR_DIALOG_ID) {
            ObsidianPrefs.putBoolean(PREF_STOCK_COLOR + "_use_accent", false); // picking implies custom
            ObsidianPrefs.putInt(PREF_STOCK_COLOR, event.color());
            rebuild();
        } else if (event.dialogId() == STOCK_DATE_COLOR_DIALOG_ID) {
            ObsidianPrefs.putBoolean(PREF_STOCK_DATE_COLOR + "_use_accent", false); // picking implies custom
            ObsidianPrefs.putInt(PREF_STOCK_DATE_COLOR, event.color());
            rebuild();
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

    private ListWidgetAdapter.ListItem stubItem(String title, String summary) {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(title, summary,
                () -> Toast.makeText(requireContext(), R.string.section_wip_summary, Toast.LENGTH_SHORT).show());
        item.useAccentColor = false;
        return item;
    }

    /** Naviga all'editor completo "Stile Chip di sfondo" (stessa schermata del chip Barra di
     *  stato, riusata via prefisso — vedi ClockChipStyleFragment/ChipStyleHelper). */
    private ListWidgetAdapter.ListItem chipStyleItem(String prefix, String title) {
        return new ListWidgetAdapter.ListItem(title, chipStyleLabel(prefix),
                () -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).navigateTo(
                                ClockChipStyleFragment.newInstance(prefix), title);
                    }
                });
    }

    private String chipStyleLabel(String prefix) {
        String[] entries = requireContext().getResources().getStringArray(R.array.clock_chip_style_entries);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(prefix + "_style", "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private ListWidgetAdapter.ListItem editTextItem(String title, String summary, String key) {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                title, textOrDefault(ObsidianPrefs.getString(key, ""), summary),
                () -> showEditTextDialog(title, summary, key));
        item.useAccentColor = false;
        return item;
    }

    private void showEditTextDialog(String title, String summary, String key) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(ObsidianPrefs.getString(key, ""));
        et.setSingleLine(true);
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.addView(et);

        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(summary)
                .setView(layout)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String text = et.getText().toString().trim();
                    ObsidianPrefs.putString(key, text);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String textOrDefault(String text, String fallback) {
        return text.isEmpty() ? fallback : text;
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
