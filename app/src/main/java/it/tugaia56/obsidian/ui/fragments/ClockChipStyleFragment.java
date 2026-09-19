package it.tugaia56.obsidian.ui.fragments;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Editor completo dello "Stile Chip di sfondo" — porting reale di OC's BackgroundChipPreference
 * (bottom sheet): anteprima live + stile (pieno/contorno/misto) + colore riempimento +
 * colore/spessore bordo + angoli arrotondati + margine + padding, tutto in un'unica schermata,
 * come in OC. Parametrizzato per prefisso chiave prefs cosi la STESSA schermata serve sia il
 * chip dell'orologio Barra di stato (StatusbarClock hook) sia i chip orologio/data
 * dell'Intestazione QS (QsHeaderClock hook), senza duplicare l'editor 3 volte — vedi
 * {@code it.tugaia56.obsidian.xposed.utils.ChipStyleHelper}, che legge/disegna con lo stesso
 * schema di chiavi "&lt;prefix&gt;_switch/_style/...".
 */
public class ClockChipStyleFragment extends Fragment {

    private static final String ARG_PREFIX = "chip_prefix";

    public static ClockChipStyleFragment newInstance(String prefix) {
        Bundle b = new Bundle();
        b.putString(ARG_PREFIX, prefix);
        ClockChipStyleFragment f = new ClockChipStyleFragment();
        f.setArguments(b);
        return f;
    }

    private String mPrefix = "status_bar_clock_background_chip";

    private String prefStyle()        { return mPrefix + "_style"; }
    private String prefFillAccent()   { return mPrefix + "_fill_accent"; }
    private String prefFillColor()    { return mPrefix + "_color"; }
    private String prefStrokeAccent() { return mPrefix + "_stroke_accent"; }
    private String prefStrokeColor()  { return mPrefix + "_stroke_color"; }
    private String prefStrokeWidth()  { return mPrefix + "_stroke_width"; }
    private String prefRound()        { return mPrefix + "_round_corners"; }
    private String prefCorner()       { return mPrefix + "_corner"; }
    private String prefMarginTop()    { return mPrefix + "_margin_top"; }
    private String prefMarginLeft()   { return mPrefix + "_margin_left"; }
    private String prefMarginRight()  { return mPrefix + "_margin_right"; }
    private String prefMarginBottom() { return mPrefix + "_margin_bottom"; }
    private String prefPadTop()       { return mPrefix + "_padding_top"; }
    private String prefPadLeft()      { return mPrefix + "_padding_left"; }
    private String prefPadRight()     { return mPrefix + "_padding_right"; }
    private String prefPadBottom()    { return mPrefix + "_padding_bottom"; }

    private static final int DIALOG_FILL_COLOR   = 0x0C318;
    private static final int DIALOG_STROKE_COLOR = 0x0C319;

    private RecyclerView mRv;
    private TextView mPreview;
    private Button mBtnSolid, mBtnOutline, mBtnMixed;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude l'opzione sottostante — stesso pattern di QsTilesCustomizeFragment.
    private boolean mCornerExpanded;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null && a.getString(ARG_PREFIX) != null) {
            mPrefix = a.getString(ARG_PREFIX);
        }
        mCornerExpanded = false;
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
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ── Anteprima ────────────────────────────────────────────────────────
        FrameLayout previewBox = new FrameLayout(requireContext());
        previewBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        previewBox.setForegroundGravity(Gravity.CENTER);

        mPreview = new TextView(requireContext());
        mPreview.setText("19:02");
        mPreview.setTextColor(ObsidianTheme.textColor());
        mPreview.setTextSize(20);
        FrameLayout.LayoutParams previewLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        mPreview.setLayoutParams(previewLp);
        previewBox.addView(mPreview);
        root.addView(previewBox);

        // ── Stile (Pieno / Con bordo / Misto) ───────────────────────────────
        LinearLayout styleRow = new LinearLayout(requireContext());
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(16);
        styleRow.setPadding(pad, dp(4), pad, dp(12));
        LinearLayout.LayoutParams styleRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        styleRow.setLayoutParams(styleRowLp);

        mBtnSolid   = styleButton(getString(R.string.clock_chip_style_solid), 0);
        mBtnOutline = styleButton(getString(R.string.clock_chip_style_outline), 1);
        mBtnMixed   = styleButton(getString(R.string.clock_chip_style_mixed), 2);
        for (Button b : new Button[]{mBtnSolid, mBtnOutline, mBtnMixed}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.setMarginEnd(dp(6));
            b.setLayoutParams(lp);
            styleRow.addView(b);
        }
        root.addView(styleRow);

        // ── Lista sezioni ─────────────────────────────────────────────────────
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 0, 0, 24);
        rv.setClipToPadding(false);
        root.addView(rv);
        mRv = rv;

        return root;
    }

    private Button styleButton(String label, int styleValue) {
        Button b = new Button(requireContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(v -> {
            ObsidianPrefs.putString(prefStyle(), String.valueOf(styleValue));
            rebuild();
        });
        return b;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rebuild();
    }

    private void rebuild() {
        int style = parseIntSafe(ObsidianPrefs.getString(prefStyle(), "0"), 0);
        boolean showFill   = style == 0 || style == 2;
        boolean showStroke = style == 1 || style == 2;

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        if (showFill) {
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_chip_fill_section))));
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(
                    colorModeItem(getString(R.string.clock_chip_color_title), prefFillAccent(), prefFillColor(), DIALOG_FILL_COLOR)));
        }

        if (showStroke) {
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_chip_stroke_section))));
            List<Object> strokeRows = new ArrayList<>();
            strokeRows.add(colorModeItem(getString(R.string.clock_chip_color_title), prefStrokeAccent(), prefStrokeColor(), DIALOG_STROKE_COLOR));
            strokeRows.add(sliderItem(getString(R.string.clock_chip_stroke_width_title), prefStrokeWidth(), 0, 8, 2, "dp"));
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, strokeRows);
        }

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_chip_corner_section))));
        List<Object> cornerRows = new ArrayList<>();
        SwitchWidgetAdapter.SwitchItem cornerSwitch = gatingSwitch(getString(R.string.clock_chip_round_corners_title), prefRound(), false);
        cornerSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(prefRound(), cornerSwitch.checked);
            mCornerExpanded = cornerSwitch.checked;
            rebuild();
        };
        cornerSwitch.onRowClick = () -> { mCornerExpanded = !mCornerExpanded; rebuild(); };
        cornerRows.add(cornerSwitch);
        if (mCornerExpanded) {
            cornerRows.add(sliderItem(getString(R.string.clock_chip_corner_title), prefCorner(), 0, 28, 14, "dp"));
        }
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, cornerRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_chip_margin_section))));
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(
                sliderItem(getString(R.string.direction_top),    prefMarginTop(),    0, 32, 0, "dp"),
                sliderItem(getString(R.string.direction_left),   prefMarginLeft(),   0, 32, 0, "dp"),
                sliderItem(getString(R.string.direction_right),  prefMarginRight(),  0, 32, 0, "dp"),
                sliderItem(getString(R.string.direction_bottom), prefMarginBottom(), 0, 32, 0, "dp")));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.clock_chip_padding_section))));
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, List.of(
                sliderItem(getString(R.string.direction_top),    prefPadTop(),    0, 24, 0, "dp"),
                sliderItem(getString(R.string.direction_left),   prefPadLeft(),   0, 24, 0, "dp"),
                sliderItem(getString(R.string.direction_right),  prefPadRight(),  0, 24, 0, "dp"),
                sliderItem(getString(R.string.direction_bottom), prefPadBottom(), 0, 24, 0, "dp")));

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }

        updatePreview();
    }

    // ── Anteprima live ───────────────────────────────────────────────────────

    private void updatePreview() {
        if (mPreview == null) return;
        int style = parseIntSafe(ObsidianPrefs.getString(prefStyle(), "0"), 0);

        int accent = systemAccentColorSafe();
        boolean fillAccent = ObsidianPrefs.getBoolean(prefFillAccent(), true);
        int fillColor = fillAccent ? accent : ObsidianPrefs.getInt(prefFillColor(), Color.WHITE);
        boolean strokeAccent = ObsidianPrefs.getBoolean(prefStrokeAccent(), true);
        int strokeColor = strokeAccent ? accent : ObsidianPrefs.getInt(prefStrokeColor(), Color.WHITE);
        int strokeWidthDp = ObsidianPrefs.getInt(prefStrokeWidth(), 2);
        boolean round = ObsidianPrefs.getBoolean(prefRound(), false);
        int cornerDp = ObsidianPrefs.getInt(prefCorner(), 14);

        int fill = Color.TRANSPARENT;
        int strokeW = 0;
        switch (style) {
            case 1: strokeW = dp(strokeWidthDp); break;
            case 2: fill = fillColor; strokeW = dp(strokeWidthDp); break;
            default: fill = fillColor; break;
        }
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(round ? dp(cornerDp) : 0);
        if (strokeW > 0) d.setStroke(strokeW, strokeColor);
        mPreview.setBackground(d);

        mPreview.setPadding(
                dp(ObsidianPrefs.getInt(prefPadLeft(), 0)), dp(ObsidianPrefs.getInt(prefPadTop(), 0)),
                dp(ObsidianPrefs.getInt(prefPadRight(), 0)), dp(ObsidianPrefs.getInt(prefPadBottom(), 0)));

        ViewGroup.LayoutParams lp = mPreview.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
            mlp.setMargins(
                    dp(ObsidianPrefs.getInt(prefMarginLeft(), 0)), dp(ObsidianPrefs.getInt(prefMarginTop(), 0)),
                    dp(ObsidianPrefs.getInt(prefMarginRight(), 0)), dp(ObsidianPrefs.getInt(prefMarginBottom(), 0)));
            mPreview.setLayoutParams(mlp);
        }

        updateStyleButtons(style);
    }

    private void updateStyleButtons(int style) {
        setStyleButtonSelected(mBtnSolid, style == 0);
        setStyleButtonSelected(mBtnOutline, style == 1);
        setStyleButtonSelected(mBtnMixed, style == 2);
    }

    private void setStyleButtonSelected(Button b, boolean selected) {
        if (b == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(20));
        bg.setColor(selected ? systemAccentColorSafe() : ObsidianTheme.cardColor());
        b.setBackground(bg);
        b.setTextColor(selected ? Color.WHITE : ObsidianTheme.textColor(0xB0));
    }

    private int systemAccentColorSafe() {
        try { return requireContext().getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { return 0xFF908DFF; }
    }

    // ── Righe generiche ──────────────────────────────────────────────────────

    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String key, boolean def) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, null, ObsidianPrefs.getBoolean(key, def), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> {
                    ObsidianPrefs.putInt(key, value);
                    updatePreview();
                });
    }

    /** Riga unica "Accento / Personalizzato": tocco apre un dialog a scelta singola invece
     *  di uno switch + riga separata — stesso pattern di BatteryChargingIconFragment. */
    private ListWidgetAdapter.ListItem colorModeItem(String title, String accentKey, String colorKey, int dialogId) {
        return new ListWidgetAdapter.ListItem(title, colorModeLabel(accentKey, colorKey),
                () -> showColorModeDialog(title, accentKey, colorKey, dialogId));
    }

    private String colorModeLabel(String accentKey, String colorKey) {
        if (ObsidianPrefs.getBoolean(accentKey, true)) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(colorKey, 0xFFFFFFFF));
    }

    private void showColorModeDialog(String title, String accentKey, String colorKey, int dialogId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        int current = ObsidianPrefs.getBoolean(accentKey, true) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean accent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(accentKey, accent);
                    rebuild();
                    if (!accent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(colorKey, 0xFFFFFFFF);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_FILL_COLOR) {
            ObsidianPrefs.putInt(prefFillColor(), event.color());
            rebuild();
        } else if (event.dialogId() == DIALOG_STROKE_COLOR) {
            ObsidianPrefs.putInt(prefStrokeColor(), event.color());
            rebuild();
        }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
