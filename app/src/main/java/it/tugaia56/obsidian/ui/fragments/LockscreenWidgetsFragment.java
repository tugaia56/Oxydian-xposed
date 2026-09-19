package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.weather.WeatherIconPacks;
import it.tugaia56.obsidian.weather.WeatherInfo;
import it.tugaia56.obsidian.xposed.views.CurrentWeatherView;

/**
 * Aggiungi Widget (Schermata di Blocco) — UI port of OC's lockscreen_widgets.xml.
 * UI/prefs only for now, no Xposed hook wired. Mirrors OC's dependency-gated visibility: most
 * of the screen only appears once "Abilita Widget" is on; the device-widget settings row needs
 * its own switch; the 8 colours need "Colori Personalizzati" on. The "Varie" card (link to
 * Meteo + scale/margin) matches OC's misc category grouping — the weather link is always
 * visible there, the sliders follow "Abilita Widget" like in OC's XML.
 */
public class LockscreenWidgetsFragment extends Fragment {

    /** Indice "App Personalizzata" in R.array.lockscreen_widget_entries — quando uno slot lo
     *  usa, il pacchetto scelto vive in "&lt;chiave_slot&gt;_app_package" (letto anche
     *  dall'hook reale, LockscreenWidgetsMod). */
    private static final int TYPE_CUSTOM_APP = 1;

    private static final String KEY_ENABLED       = "lockscreen_widgets_enabled";
    private static final String KEY_DEVICE_WIDGET  = "lockscreen_device_widget";
    private static final String KEY_MAIN_WIDGET1   = "main_custom_widgets1";
    private static final String KEY_MAIN_WIDGET2   = "main_custom_widgets2";
    private static final String KEY_MINI_WIDGET1   = "custom_widgets1";
    private static final String KEY_MINI_WIDGET2   = "custom_widgets2";
    private static final String KEY_MINI_WIDGET3   = "custom_widgets3";
    private static final String KEY_MINI_WIDGET4   = "custom_widgets4";
    private static final String KEY_COLOR_SWITCH   = "lockscreen_widgets_custom_color";
    private static final String KEY_BG_SWITCH      = "lockscreen_widgets_custom_bg";
    /** Stesse 9 varianti di "Sfondo Meteo" (R.array.lockscreen_weather_bg_entries),
     *  applicate qui ai widget invece che al widget Meteo — vedi WeatherBgFactory. */
    private static final String KEY_BG_SELECTION   = "lockscreen_widgets_bg_selection";
    private static final String KEY_SCALE          = "widget_scale";                  // 50-100 %
    private static final String KEY_TOP_MARGIN     = "lockscreen_widgets_top_margin"; // -50..200 dp

    private static final String[] BIG_COLOR_KEYS = {
            "lockscreen_widgets_big_active", "lockscreen_widgets_big_inactive",
            "lockscreen_widgets_big_icon_active", "lockscreen_widgets_big_icon_inactive",
    };
    private static final String[] MINI_COLOR_KEYS = {
            "lockscreen_widgets_small_active", "lockscreen_widgets_small_inactive",
            "lockscreen_widgets_small_icon_active", "lockscreen_widgets_small_icon_inactive",
    };

    private RecyclerView mRv;
    private final List<DarkShadowItem> mBigColorItems = new ArrayList<>();
    private final List<DarkShadowItem> mMiniColorItems = new ArrayList<>();
    private DarkShadowColorListener mBigColorAdapter;
    private DarkShadowColorListener mMiniColorAdapter;
    private boolean mBigColorsExpanded;
    private boolean mMiniColorsExpanded;
    private boolean mWidgetsEnabledExpanded = false;
    private boolean mDeviceWidgetExpanded   = false;
    private boolean mColorSwitchExpanded    = false;
    private boolean mBgSwitchExpanded       = false;

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
        mBigColorItems.clear();
        mMiniColorItems.clear();
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        SwitchWidgetAdapter.SwitchItem widgetsEnabledSwitch = gatingSwitch(
                getString(R.string.lockscreen_widgets_enabled_title),
                getString(R.string.lockscreen_widgets_enabled_summary), KEY_ENABLED);
        widgetsEnabledSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ENABLED, widgetsEnabledSwitch.checked);
            mWidgetsEnabledExpanded = widgetsEnabledSwitch.checked;
            rebuild();
        };
        widgetsEnabledSwitch.onRowClick = () -> { mWidgetsEnabledExpanded = !mWidgetsEnabledExpanded; rebuild(); };
        chain.add(new SwitchWidgetAdapter(List.of(widgetsEnabledSwitch)));

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.lockscreen_display_widgets_title))));
        SwitchWidgetAdapter.SwitchItem deviceWidgetSwitch = gatingSwitch(
                getString(R.string.lockscreen_display_widgets_title),
                getString(R.string.lockscreen_display_widgets_summary), KEY_DEVICE_WIDGET);
        deviceWidgetSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_DEVICE_WIDGET, deviceWidgetSwitch.checked);
            mDeviceWidgetExpanded = deviceWidgetSwitch.checked;
            rebuild();
        };
        deviceWidgetSwitch.onRowClick = () -> { mDeviceWidgetExpanded = !mDeviceWidgetExpanded; rebuild(); };
        List<Object> deviceWidgetRows = new ArrayList<>();
        deviceWidgetRows.add(deviceWidgetSwitch);
        if (mDeviceWidgetExpanded) {
            ListWidgetAdapter.ListItem deviceSettingsItem = new ListWidgetAdapter.ListItem(
                    getString(R.string.lockscreen_device_widget_settings), null,
                    () -> navigate(new DeviceWidgetSettingsFragment(), getString(R.string.lockscreen_device_widget_settings)));
            deviceWidgetRows.add(deviceSettingsItem);
        }
        GroupUtils.addGroup(chain, deviceWidgetRows);

        if (mWidgetsEnabledExpanded) {
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.large_widgets_category_title))));
            GroupUtils.addGroup(chain, List.of(
                    singleChoiceItem(getString(R.string.main_custom_widgets1), KEY_MAIN_WIDGET1, R.array.lockscreen_widget_entries),
                    singleChoiceItem(getString(R.string.main_custom_widgets2), KEY_MAIN_WIDGET2, R.array.lockscreen_widget_entries)));

            chain.add(new SectionTitleAdapter(List.of(getString(R.string.mini_widgets_category_title))));
            GroupUtils.addGroup(chain, List.of(
                    singleChoiceItem(getString(R.string.custom_widgets1), KEY_MINI_WIDGET1, R.array.lockscreen_widget_entries),
                    singleChoiceItem(getString(R.string.custom_widgets2), KEY_MINI_WIDGET2, R.array.lockscreen_widget_entries),
                    singleChoiceItem(getString(R.string.custom_widgets3), KEY_MINI_WIDGET3, R.array.lockscreen_widget_entries),
                    singleChoiceItem(getString(R.string.custom_widgets4), KEY_MINI_WIDGET4, R.array.lockscreen_widget_entries)));
        }

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.widgets_custom_color))));
        SwitchWidgetAdapter.SwitchItem colorSwitch = exclusiveGatingSwitch(
                getString(R.string.widgets_custom_color_title), null, KEY_COLOR_SWITCH, KEY_BG_SWITCH);
        colorSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_COLOR_SWITCH, colorSwitch.checked);
            mColorSwitchExpanded = colorSwitch.checked;
            if (colorSwitch.checked) {
                ObsidianPrefs.putBoolean(KEY_BG_SWITCH, false);
                mBgSwitchExpanded = false;
            }
            rebuild();
        };
        colorSwitch.onRowClick = () -> { mColorSwitchExpanded = !mColorSwitchExpanded; rebuild(); };
        GroupUtils.addGroup(chain, List.of(colorSwitch));
        if (mColorSwitchExpanded) {
            chain.add(collapsibleHeader(getString(R.string.widgets_big_color_section),
                    () -> { mBigColorsExpanded = !mBigColorsExpanded; rebuild(); }));
            if (mBigColorsExpanded) chain.add(colorsRow(mBigColorItems, BIG_COLOR_KEYS, true));

            chain.add(collapsibleHeader(getString(R.string.widgets_mini_color_section),
                    () -> { mMiniColorsExpanded = !mMiniColorsExpanded; rebuild(); }));
            if (mMiniColorsExpanded) chain.add(colorsRow(mMiniColorItems, MINI_COLOR_KEYS, false));
        }

        // Stessa sezione "IMPOSTAZIONI WIDGET" di sopra (Colori Personalizzati) — switch
        // gemello per lo Sfondo, invece di una nuova SectionTitleAdapter.
        SwitchWidgetAdapter.SwitchItem bgSwitch = exclusiveGatingSwitch(
                getString(R.string.widgets_custom_bg_title), null, KEY_BG_SWITCH, KEY_COLOR_SWITCH);
        bgSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_BG_SWITCH, bgSwitch.checked);
            mBgSwitchExpanded = bgSwitch.checked;
            if (bgSwitch.checked) {
                ObsidianPrefs.putBoolean(KEY_COLOR_SWITCH, false);
                mColorSwitchExpanded = false;
            }
            rebuild();
        };
        bgSwitch.onRowClick = () -> { mBgSwitchExpanded = !mBgSwitchExpanded; rebuild(); };
        List<Object> bgRows = new ArrayList<>();
        bgRows.add(bgSwitch);
        if (mBgSwitchExpanded) bgRows.add(bgItem());
        GroupUtils.addGroup(chain, bgRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.misc_category))));
        if (mWidgetsEnabledExpanded) {
            GroupUtils.addGroup(chain, List.of(
                    sliderItem(getString(R.string.widgets_scale), KEY_SCALE, 50, 100, 100, "%"),
                    sliderItem(getString(R.string.lockscreen_clock_top_margin_title), KEY_TOP_MARGIN, -50, 200, 0, "dp")));
        }

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Collapsible section header (chiuso di default, click per aprire/chiudere) ──

    private ListWidgetAdapter collapsibleHeader(String title, Runnable onToggle) {
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(title, null, onToggle::run);
        item.useAccentColor = false;
        return new ListWidgetAdapter(List.of(item));
    }

    // ── Colors (4 swatches x 2 sections: Widget Grande / Widget Mini) ────────────

    private DarkShadowColorListener colorsRow(List<DarkShadowItem> items, String[] keys, boolean big) {
        String[] labels = big
                ? new String[]{
                    getString(R.string.big_active), getString(R.string.big_inactive),
                    getString(R.string.big_icon_active), getString(R.string.big_icon_inactive),
                  }
                : new String[]{
                    getString(R.string.mini_active), getString(R.string.mini_inactive),
                    getString(R.string.mini_icon_active), getString(R.string.mini_icon_inactive),
                  };
        items.clear();
        for (int i = 0; i < keys.length; i++) {
            items.add(new DarkShadowItem(labels[i], keys[i],
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(), null,
                    ObsidianPrefs.getInt(keys[i], 0xFFFFFFFF),
                    ObsidianPrefs.getBoolean(keys[i] + "_on", false)));
        }
        DarkShadowColorListener adapter = new DarkShadowColorListener(items, this::onColorEnabled, this::onColorDisabled, this::onColorSwatch);
        if (big) mBigColorAdapter = adapter; else mMiniColorAdapter = adapter;
        return adapter;
    }

    private void onColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        ObsidianPrefs.putInt(item.getOverlayName(), item.getColor());
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", true);
    }

    private void onColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", false);
    }

    private void onColorSwatch(DarkShadowItem item, int dialogId) {
        // Gli sfondi "Inattivo" (non le icone) vogliono in genere un colore scuro tipo
        // sfondo — niente Accento lì, solo le sfumature derivate dal colore sfondo attuale.
        String name = item.getName();
        boolean bgLike = getString(R.string.big_inactive).equals(name) || getString(R.string.mini_inactive).equals(name);
        if (bgLike) {
            if (getActivity() instanceof MainActivity) {
                int[] presets = ObsidianTheme.bgDerivedPresets();
                ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true, presets);
            }
            return;
        }
        showColorAccentChoice(item, dialogId);
    }

    /** Accento/Personalizzato inserted before the swatch opens the raw picker — Accento resolves
     *  immediately (reuses onColorEnabled's existing save path), Personalizzato opens the picker
     *  as before. Baked at selection time (no live re-resolve), same as every other picker. */
    private void showColorAccentChoice(DarkShadowItem item, int dialogId) {
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
                        onColorEnabled(item);
                        if (mBigColorAdapter != null) mBigColorAdapter.notifyDataSetChanged();
                        if (mMiniColorAdapter != null) mMiniColorAdapter.notifyDataSetChanged();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        for (DarkShadowItem item : mBigColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            ObsidianPrefs.putBoolean(item.getOverlayName() + "_use_accent", false); // picking a colour implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mBigColorAdapter != null) mBigColorAdapter.notifyDataSetChanged();
            return;
        }
        for (DarkShadowItem item : mMiniColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            ObsidianPrefs.putBoolean(item.getOverlayName() + "_use_accent", false); // picking a colour implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mMiniColorAdapter != null) mMiniColorAdapter.notifyDataSetChanged();
            return;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    // ── Sfondo Personalizzato — stesse 9 varianti e stessa dialog a griglia di "Sfondo
    // Meteo" (LockscreenWeatherFragment), solo puntate su KEY_BG_SELECTION invece di
    // "weather_background". L'anteprima resta un CurrentWeatherView (identica a quella del
    // Meteo, come richiesto) anche se il risultato reale si applica ai widget grandi/mini. ──

    private ListWidgetAdapter.ListItem bgItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.lockscreen_weather_selection_title),
                choiceLabel(KEY_BG_SELECTION, R.array.lockscreen_weather_bg_entries),
                this::showBgDialog);
    }

    private void showBgDialog() {
        int saved = 0;
        try { saved = Integer.parseInt(ObsidianPrefs.getString(KEY_BG_SELECTION, "0")); } catch (NumberFormatException ignored) {}

        RecyclerView grid = new RecyclerView(requireContext());
        grid.setLayoutManager(new LinearLayoutManager(requireContext()));
        int pad = dp(8);
        grid.setPadding(pad, pad, pad, pad);
        grid.setClipToPadding(false);

        androidx.appcompat.app.AlertDialog[] dlgRef = new androidx.appcompat.app.AlertDialog[1];
        IntConsumer onPick = selection -> {
            ObsidianPrefs.putString(KEY_BG_SELECTION, String.valueOf(selection));
            rebuild();
            if (dlgRef[0] != null) dlgRef[0].dismiss();
        };
        grid.setAdapter(new WidgetBgPreviewAdapter(saved, onPick));

        dlgRef[0] = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.lockscreen_weather_selection_title))
                .setView(grid)
                .setNegativeButton(R.string.close, null)
                .show();
        ObsidianTheme.themeDialog(dlgRef[0]);
    }

    /** Copia di WeatherBgPreviewAdapter (LockscreenWeatherFragment) — stessa anteprima
     *  identica al Meteo, come richiesto, anche se qui la scelta si applica ai widget. */
    private class WidgetBgPreviewAdapter extends RecyclerView.Adapter<WidgetBgPreviewAdapter.VH> {
        private final int mSelected;
        private final IntConsumer mOnPick;
        private final WeatherInfo mMock;

        WidgetBgPreviewAdapter(int selected, IntConsumer onPick) {
            mSelected = selected;
            mOnPick = onPick;
            mMock = new WeatherInfo();
            mMock.tempC = 22;
            mMock.humidityPct = 55;
            mMock.windKmh = 12;
            mMock.weatherCode = 1;
            mMock.isDay = true;
            mMock.cityName = getString(R.string.lockscreen_weather_selection_title);
        }

        class VH extends RecyclerView.ViewHolder {
            final CurrentWeatherView weatherView;
            final TextView label;
            VH(View v, CurrentWeatherView weatherView, TextView label) {
                super(v);
                this.weatherView = weatherView;
                this.label = label;
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_HORIZONTAL);
            int padH = dp(16), padV = dp(14);
            row.setPadding(padH, padV, padH, padV);

            CurrentWeatherView weatherView = new CurrentWeatherView(requireContext());
            LinearLayout.LayoutParams wvLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            row.addView(weatherView, wvLp);

            TextView label = new TextView(requireContext());
            label.setTextColor(0xCCFFFFFF);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(8);
            row.addView(label, labelLp);

            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            rootLp.setMargins(m, m, m, m);
            row.setLayoutParams(rootLp);

            return new VH(row, weatherView, label);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            boolean selected = pos == mSelected;
            int accent = ObsidianTheme.accentColor();
            int accent2 = ObsidianTheme.accentColor2();

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(dp(10));
            if (selected) bg.setStroke(dp(2), accent);
            h.itemView.setBackground(bg);

            h.weatherView.updateIconPack(WeatherIconPacks.DEFAULT);
            h.weatherView.updateData(mMock, true, false, true, false, false);
            h.weatherView.updateSizes(16, 18);
            h.weatherView.updateColor(Color.WHITE);
            h.weatherView.updateWeatherBg(pos, accent, accent2);

            String[] entries = getResources().getStringArray(R.array.lockscreen_weather_bg_entries);
            h.label.setText(pos < entries.length ? entries[pos] : String.valueOf(pos));

            h.itemView.setOnClickListener(v -> mOnPick.accept(pos));
        }

        @Override public int getItemCount() { return 9; }
    }

    // ── Generic row helpers (UI/prefs only, no restart reminder — not wired yet) ──

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

    /** Come gatingSwitch, ma mutuamente esclusivo con otherKey — Colori Personalizzati e
     *  Sfondo Personalizzato non possono stare accesi insieme (il secondo sovrascrive
     *  comunque lo sfondo del primo nell'hook, quindi tenerli entrambi aperti confonde
     *  soltanto: si editano colori che poi non si vedono). Accendere l'uno spegne l'altro. */
    private SwitchWidgetAdapter.SwitchItem exclusiveGatingSwitch(String title, String summary, String key, String otherKey) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            if (item.checked) ObsidianPrefs.putBoolean(otherKey, false);
            rebuild();
        };
        return item;
    }

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes));
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        if (entriesArrayRes == R.array.lockscreen_widget_entries && idx == TYPE_CUSTOM_APP) {
            String pkg = ObsidianPrefs.getString(key + "_app_package", "");
            if (!pkg.isEmpty()) {
                try {
                    return requireContext().getPackageManager()
                            .getApplicationLabel(requireContext().getPackageManager().getApplicationInfo(pkg, 0))
                            .toString();
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
        }
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    if (entriesArrayRes == R.array.lockscreen_widget_entries && selected[0] == TYPE_CUSTOM_APP) {
                        showAppPickerDialog(key);
                    } else {
                        rebuild();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    // ── Selettore "App Personalizzata" ───────────────────────────────────────────

    private void showAppPickerDialog(String slotKey) {
        PackageManager pm = requireContext().getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
        apps.sort(Comparator.comparing(r -> r.loadLabel(pm).toString().toLowerCase()));

        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(8);
        list.setPadding(pad, pad, pad, pad);

        androidx.appcompat.app.AlertDialog[] dlgRef = new androidx.appcompat.app.AlertDialog[1];
        for (ResolveInfo info : apps) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String packageName = appInfo.packageName;
            String label = info.loadLabel(pm).toString();
            Drawable icon = info.loadIcon(pm);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));

            ImageView iv = new ImageView(requireContext());
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(36), dp(36));
            ivLp.setMarginEnd(dp(16));
            iv.setLayoutParams(ivLp);
            iv.setImageDrawable(icon);
            row.addView(iv);

            TextView tv = new TextView(requireContext());
            tv.setText(label);
            tv.setTextColor(ObsidianTheme.textColor());
            tv.setTextSize(15);
            row.addView(tv);

            row.setOnClickListener(v -> {
                ObsidianPrefs.putString(slotKey + "_app_package", packageName);
                rebuild();
                if (dlgRef[0] != null) dlgRef[0].dismiss();
            });
            list.addView(row);
        }

        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(requireContext());
        scroll.addView(list);

        dlgRef[0] = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.lockscreen_widgets_pick_app_title))
                .setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dlgRef[0]);
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        int current = ObsidianPrefs.getInt(key, def);
        return new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
    }

    private void navigate(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(fragment, title);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
