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
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.DarkShadowColorListener;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.weather.GpsLocationHelper;
import it.tugaia56.obsidian.weather.WeatherIconPacks;
import it.tugaia56.obsidian.weather.WeatherInfo;
import it.tugaia56.obsidian.xposed.views.CurrentWeatherView;

/**
 * Meteo Schermata di Blocco — UI port of OC's LockscreenWeather / lockscreen_weather_prefs.xml.
 * UI/prefs only for now, no Xposed hook wired. Mirrors OC's dependency-gated visibility exactly
 * (per-row android:dependency in the source XML): most rows only appear once "Abilita" is on,
 * the custom-colour swatch and "Centrato"/"Margini Personalizzati" switches are independent of
 * it, and the margin sliders / location picker / font picker only appear once their own switch
 * is on.
 */
public class LockscreenWeatherFragment extends Fragment {

    private static final String KEY_ENABLED        = "lockscreen_weather_enabled";
    private static final String KEY_UPDATE_INTERVAL= "weather_update_interval";   // "0".."3"
    private static final String KEY_PROVIDER       = "weather_provider";          // "0".."2"
    private static final String KEY_OWM_KEY        = "owm_key";
    private static final String KEY_YANDEX_KEY     = "yandex_key";
    private static final String KEY_UNITS          = "weather_units";             // "0".."1"
    private static final String KEY_SHOW_LOCATION  = "weather_show_location";
    private static final String KEY_SHOW_CONDITION = "weather_show_condition";
    private static final String KEY_SHOW_HUMIDITY  = "weather_show_humidity";
    private static final String KEY_SHOW_WIND      = "weather_show_wind";
    private static final String KEY_TEXT_SIZE      = "weather_text_size";         // 13-24 sp
    private static final String KEY_IMAGE_SIZE     = "weather_image_size";        // 13-24 dp
    private static final String KEY_COLOR          = "weather_custom_color";
    private static final String KEY_LOC_SWITCH     = "weather_custom_location_switch";
    private static final String KEY_LOC_VALUE      = "weather_custom_location_value";
    private static final String KEY_CENTERED       = "weather_centered";
    private static final String KEY_MARGINS_SWITCH = "weather_custom_margins";
    private static final String KEY_MARGIN_TOP     = "weather_margin_top";        // 0-200 dp
    private static final String KEY_MARGIN_LEFT    = "weather_margin_left";       // 0-100 dp
    private static final String KEY_BACKGROUND     = "weather_background";        // "0".."2"
    private static final String KEY_FONT_SWITCH    = "ls_weather_custom_font_enabled";
    private static final String KEY_ICON_PACK      = "weather_icon_pack";         // shared

    private RecyclerView mRv;
    private final List<DarkShadowItem> mColorItems = new ArrayList<>();
    private DarkShadowColorListener mColorAdapter;
    // Stato SOLO visivo (non persistito): lo switch attiva soltanto, il tocco sul nome
    // apre/chiude le opzioni sottostanti — stesso pattern di QsTilesCustomizeFragment.
    private boolean mWeatherExpanded = false;
    private boolean mLocExpanded;
    private boolean mMarginsExpanded = false;
    private boolean mFontExpanded    = false;
    private ActivityResultLauncher<String> mRequestLocationPermission;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        mRequestLocationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) fetchGpsLocation();
                    else Toast.makeText(requireContext(), R.string.weather_gps_permission_denied, Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchGpsLocation() {
        Toast.makeText(requireContext(), R.string.weather_gps_updating, Toast.LENGTH_SHORT).show();
        GpsLocationHelper.requestFix(requireContext(), success -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(),
                    success ? R.string.weather_gps_updated : R.string.weather_gps_failed, Toast.LENGTH_SHORT).show();
            if (success) rebuild();
        });
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
        mColorItems.clear();
        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();

        List<Object> topRows = new ArrayList<>();
        SwitchWidgetAdapter.SwitchItem weatherSwitch = gatingSwitch(getString(R.string.lockscreen_weather_enabled), null, KEY_ENABLED);
        weatherSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ENABLED, weatherSwitch.checked);
            mWeatherExpanded = weatherSwitch.checked;
            rebuild();
        };
        weatherSwitch.onRowClick = () -> { mWeatherExpanded = !mWeatherExpanded; rebuild(); };
        topRows.add(weatherSwitch);

        if (mWeatherExpanded) {
            topRows.add(singleChoiceItem(getString(R.string.weather_update_interval_title),
                    KEY_UPDATE_INTERVAL, R.array.weather_update_interval_entries));
            topRows.add(lastUpdateItem());
            topRows.add(providerChoiceItem());
            String provider = ObsidianPrefs.getString(KEY_PROVIDER, "2");
            if ("0".equals(provider)) {
                topRows.add(editTextItem(getString(R.string.weather_owm_key), getString(R.string.weather_owm_key), KEY_OWM_KEY));
            } else if ("3".equals(provider)) {
                topRows.add(editTextItem(getString(R.string.weather_yandex_key), getString(R.string.weather_yandex_key), KEY_YANDEX_KEY));
            }
            topRows.add(singleChoiceItem(getString(R.string.weather_units_title),
                    KEY_UNITS, R.array.weather_units_entries));
        }
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, topRows);

        if (mWeatherExpanded) {
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.lockscreen_weather_selection_title))));
            List<Object> displayRows = new ArrayList<>(List.of(
                    prefSwitch(getString(R.string.weather_show_location), null, KEY_SHOW_LOCATION),
                    prefSwitch(getString(R.string.weather_show_condition), null, KEY_SHOW_CONDITION),
                    prefSwitch(getString(R.string.weather_show_humidity), null, KEY_SHOW_HUMIDITY),
                    prefSwitch(getString(R.string.weather_show_wind), null, KEY_SHOW_WIND)));
            displayRows.add(sliderItem(getString(R.string.weather_text_size), KEY_TEXT_SIZE, 13, 24, 16, "sp"));
            displayRows.add(sliderItem(getString(R.string.weather_image_size), KEY_IMAGE_SIZE, 13, 24, 18, "dp"));
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, displayRows);
        }

        chain.add(colorRow());

        if (mWeatherExpanded) {
            List<Object> locRows = new ArrayList<>();
            boolean manual = ObsidianPrefs.getBoolean(KEY_LOC_SWITCH, false);
            SwitchWidgetAdapter.SwitchItem locSwitch = new SwitchWidgetAdapter.SwitchItem(
                    getString(R.string.weather_location_mode_title), null, !manual, null);
            locSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(KEY_LOC_SWITCH, !locSwitch.checked);
                mLocExpanded = true;
                rebuild();
            };
            locSwitch.onRowClick = () -> { mLocExpanded = !mLocExpanded; rebuild(); };
            locRows.add(locSwitch);
            if (mLocExpanded) {
                if (manual) {
                    locRows.add(editTextItem(getString(R.string.weather_custom_location_picker_title),
                            getString(R.string.weather_location_hint), KEY_LOC_VALUE));
                } else {
                    locRows.add(gpsRefreshItem());
                }
            }
            locRows.add(iconPackChoiceItem());
            it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, locRows);
        }

        List<Object> bottomRows = new ArrayList<>();
        bottomRows.add(prefSwitch(getString(R.string.weather_centered), getString(R.string.weather_centered_summary), KEY_CENTERED));
        SwitchWidgetAdapter.SwitchItem marginsSwitch = gatingSwitch(getString(R.string.weather_custom_margins), null, KEY_MARGINS_SWITCH);
        marginsSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_MARGINS_SWITCH, marginsSwitch.checked);
            mMarginsExpanded = marginsSwitch.checked;
            rebuild();
        };
        marginsSwitch.onRowClick = () -> { mMarginsExpanded = !mMarginsExpanded; rebuild(); };
        bottomRows.add(marginsSwitch);
        if (mMarginsExpanded) {
            bottomRows.add(sliderItem(getString(R.string.weather_margin_top), KEY_MARGIN_TOP, -400, 400, 0, "dp", 10));
            bottomRows.add(sliderItem(getString(R.string.weather_margin_left), KEY_MARGIN_LEFT, 0, 100, 0, "dp"));
        }
        if (mWeatherExpanded) {
            bottomRows.add(backgroundItem());
        }
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, bottomRows);

        List<Object> fontRows = new ArrayList<>();
        SwitchWidgetAdapter.SwitchItem fontSwitch = gatingSwitch(getString(R.string.pick_font_title), null, KEY_FONT_SWITCH);
        fontSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_FONT_SWITCH, fontSwitch.checked);
            mFontExpanded = fontSwitch.checked;
            rebuild();
        };
        fontSwitch.onRowClick = () -> { mFontExpanded = !mFontExpanded; rebuild(); };
        fontRows.add(fontSwitch);
        if (mFontExpanded) fontRows.add(stubItem(getString(R.string.pick_font_title), getString(R.string.pick_font_summary)));
        it.tugaia56.obsidian.ui.adapters.GroupUtils.addGroup(chain, fontRows);

        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Color ────────────────────────────────────────────────────────────────

    private DarkShadowColorListener colorRow() {
        DarkShadowItem item = new DarkShadowItem(getString(R.string.weather_custom_color), KEY_COLOR,
                java.util.Collections.emptyList(), java.util.Collections.emptyList(), null,
                ObsidianPrefs.getInt(KEY_COLOR, 0xFFFFFFFF), ObsidianPrefs.getBoolean(KEY_COLOR + "_on", false));
        mColorItems.add(item);
        mColorAdapter = new DarkShadowColorListener(List.of(item), this::onColorEnabled, this::onColorDisabled, this::onColorSwatch);
        return mColorAdapter;
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
        showColorAccentChoice(item, dialogId, KEY_COLOR);
    }

    /** Accento/Personalizzato inserted before the swatch opens the raw picker — Accento resolves
     *  immediately (reuses onColorEnabled's existing save path), Personalizzato opens the picker
     *  as before. Baked at selection time (no live re-resolve), same as every other picker. */
    private void showColorAccentChoice(DarkShadowItem item, int dialogId, String colorKey) {
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
                        if (mColorAdapter != null) mColorAdapter.notifyDataSetChanged();
                    } else if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, item.getColor(), true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        for (DarkShadowItem item : mColorItems) {
            if (event.dialogId() != System.identityHashCode(item)) continue;
            ObsidianPrefs.putBoolean(KEY_COLOR + "_use_accent", false); // picking a colour implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mColorAdapter != null) mColorAdapter.notifyDataSetChanged();
            return;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    // ── Generic row helpers (UI/prefs only, no restart reminder — not wired yet) ──

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

    /** Riga di sola lettura — scritta dall'hook (AodWeather/LockscreenWeather) a ogni fetch riuscito. */
    private ListWidgetAdapter.ListItem lastUpdateItem() {
        String value = ObsidianPrefs.getString("weather_last_update", "—");
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                getString(R.string.weather_last_update), value, null);
        item.useAccentColor = false;
        return item;
    }

    /** Riga "Posizione automatica" (switch spento) — tocco chiede il permesso se manca e poi
     *  un fix GPS/rete una tantum (GpsLocationHelper), il cui risultato viene letto
     *  direttamente dal Mod (LockscreenWeather/AodWeather) al posto della città digitata. */
    private ListWidgetAdapter.ListItem gpsRefreshItem() {
        String name = GpsLocationHelper.lastKnownName();
        String summary = name != null ? name : getString(R.string.weather_gps_never_updated);
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                getString(R.string.weather_gps_refresh_title), summary, () -> {
            if (GpsLocationHelper.hasPermission(requireContext())) {
                fetchGpsLocation();
            } else {
                mRequestLocationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
        item.useAccentColor = false;
        return item;
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

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(summary)
                .setView(layout)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String text = et.getText().toString().trim();
                    ObsidianPrefs.putString(key, text);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        ObsidianTheme.themeDialog(dialog);
    }

    private String textOrDefault(String text, String fallback) {
        return text.isEmpty() ? fallback : text;
    }

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes));
    }

    /** Come singleChoiceItem, ma ricostruisce la lista dopo la scelta — serve a mostrare/
     *  nascondere il campo chiave giusto in base al provider selezionato. */
    private ListWidgetAdapter.ListItem providerChoiceItem() {
        String title = getString(R.string.weather_provider_title);
        String[] entries = getResources().getStringArray(R.array.weather_provider_entries);
        int currentValue = 2;
        try { currentValue = Integer.parseInt(ObsidianPrefs.getString(KEY_PROVIDER, "2")); } catch (NumberFormatException ignored) {}
        final int current = currentValue;
        final int[] selected = {current};
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(KEY_PROVIDER, R.array.weather_provider_entries),
                () -> ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                        .setPositiveButton(R.string.apply, (d, w) -> {
                            ObsidianPrefs.putString(KEY_PROVIDER, String.valueOf(selected[0]));
                            rebuild();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show()));
    }

    // ── Pacchetto icone condizioni (18 pacchetti, come OC) ──────────────────────

    private ListWidgetAdapter.ListItem iconPackChoiceItem() {
        String title = getString(R.string.weather_icon_pack_title);
        return new ListWidgetAdapter.ListItem(
                title, iconPackLabel(),
                () -> showIconPackDialog(title));
    }

    private String iconPackLabel() {
        String prefix = ObsidianPrefs.getString(KEY_ICON_PACK, WeatherIconPacks.DEFAULT);
        int idx = WeatherIconPacks.indexForPrefix(prefix);
        return WeatherIconPacks.labels(requireContext())[idx];
    }

    private void showIconPackDialog(String title) {
        String[] entries = WeatherIconPacks.labels(requireContext());
        int current = WeatherIconPacks.indexForPrefix(ObsidianPrefs.getString(KEY_ICON_PACK, WeatherIconPacks.DEFAULT));
        final int[] selected = {current};

        ArrayAdapter<String> listAdapter = new ArrayAdapter<>(requireContext(), 0, entries) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int padH = dp(16), padV = dp(12);
                row.setPadding(padH, padV, padH, padV);

                RadioButton radio = new RadioButton(requireContext());
                radio.setChecked(position == selected[0]);
                radio.setClickable(false);
                radio.setFocusable(false);

                ImageView icon = new ImageView(requireContext());
                icon.setImageResource(WeatherIconPacks.previewIcon(position));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(28), dp(28));
                iconLp.setMarginStart(dp(16));
                iconLp.setMarginEnd(dp(20));
                icon.setLayoutParams(iconLp);

                TextView text = new TextView(requireContext());
                text.setText(entries[position]);
                text.setTextColor(ObsidianTheme.systemDialogTextColor(requireContext()));
                text.setTextSize(16);

                row.addView(radio);
                row.addView(icon);
                row.addView(text);
                return row;
            }
        };

        ListView listView = new ListView(requireContext());
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((parent, v, position, id) -> {
            selected[0] = position;
            listAdapter.notifyDataSetChanged();
        });

        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(listView)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(KEY_ICON_PACK, WeatherIconPacks.prefixForIndex(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
    }

    // ── Sfondo meteo — griglia con anteprima reale delle 9 scelte, cosi si vedono tutte
    // insieme invece di doverle provare una alla volta. ─────────────────────────────────

    private ListWidgetAdapter.ListItem backgroundItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.lockscreen_weather_selection_title),
                choiceLabel(KEY_BACKGROUND, R.array.lockscreen_weather_bg_entries),
                this::showBackgroundDialog);
    }

    private void showBackgroundDialog() {
        int saved = 0;
        try { saved = Integer.parseInt(ObsidianPrefs.getString(KEY_BACKGROUND, "0")); } catch (NumberFormatException ignored) {}

        RecyclerView grid = new RecyclerView(requireContext());
        grid.setLayoutManager(new LinearLayoutManager(requireContext()));
        int pad = dp(8);
        grid.setPadding(pad, pad, pad, pad);
        grid.setClipToPadding(false);

        androidx.appcompat.app.AlertDialog[] dlgRef = new androidx.appcompat.app.AlertDialog[1];
        IntConsumer onPick = selection -> {
            ObsidianPrefs.putString(KEY_BACKGROUND, String.valueOf(selection));
            rebuild();
            if (dlgRef[0] != null) dlgRef[0].dismiss();
        };
        grid.setAdapter(new WeatherBgPreviewAdapter(saved, onPick));

        dlgRef[0] = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.lockscreen_weather_selection_title))
                .setView(grid)
                .setNegativeButton(R.string.close, null)
                .show();
        ObsidianTheme.themeDialog(dlgRef[0]);
    }

    /** Una riga per scelta, con un CurrentWeatherView reale (dati finti) e lo sfondo N
     *  applicato — stessa view/logica usata dall'hook reale (LockscreenWeather ->
     *  CurrentWeatherView.updateWeatherBg), quindi l'anteprima è fedele al risultato vero. */
    private class WeatherBgPreviewAdapter extends RecyclerView.Adapter<WeatherBgPreviewAdapter.VH> {
        private final int mSelected;
        private final IntConsumer mOnPick;
        private final WeatherInfo mMock;

        WeatherBgPreviewAdapter(int selected, IntConsumer onPick) {
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
            // Etichetta SOTTO il preview (non a fianco) — a fianco andava a capo troppe
            // volte per via dello spazio orizzontale stretto.
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

            GradientDrawable bg = new GradientDrawable();
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

    private void showSingleChoiceDialog(String title, String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int current = 0;
        try { current = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit) {
        return sliderItem(title, key, min, max, def, unit, 1);
    }

    private SliderWidgetAdapter.SliderItem sliderItem(String title, String key, int min, int max, int def, String unit, int step) {
        int current = ObsidianPrefs.getInt(key, def);
        SliderWidgetAdapter.SliderItem item = new SliderWidgetAdapter.SliderItem(
                title, current, min, max, unit, def,
                value -> ObsidianPrefs.putInt(key, value));
        item.step = step;
        return item;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
