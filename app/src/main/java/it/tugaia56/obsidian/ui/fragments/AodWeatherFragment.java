package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import it.tugaia56.obsidian.weather.GpsLocationHelper;
import it.tugaia56.obsidian.weather.WeatherIconPacks;

/**
 * Meteo AOD — UI port of OC's aod_weather_prefs.xml. UI/prefs only for now, no Xposed hook
 * wired — visible so the controls exist, wiring is a separate future pass. The
 * update-interval/provider/keys/units rows share the same pref keys as Meteo SdB (OC does too).
 */
public class AodWeatherFragment extends Fragment {

    private static final String KEY_ENABLED        = "aod_weather_enabled";
    private static final String KEY_UPDATE_INTERVAL= "weather_update_interval";   // shared w/ lockscreen
    private static final String KEY_PROVIDER       = "weather_provider";          // shared
    private static final String KEY_OWM_KEY        = "owm_key";                   // shared
    private static final String KEY_YANDEX_KEY     = "yandex_key";                // shared
    private static final String KEY_UNITS          = "weather_units";             // shared
    private static final String KEY_SHOW_LOCATION  = "aod_weather_show_location";
    private static final String KEY_SHOW_CONDITION = "aod_weather_show_condition";
    private static final String KEY_SHOW_HUMIDITY  = "aod_weather_show_humidity";
    private static final String KEY_SHOW_WIND      = "aod_weather_show_wind";
    private static final String KEY_TEXT_SIZE      = "aod_weather_text_size";     // 13-24 dp
    private static final String KEY_IMAGE_SIZE     = "aod_weather_image_size";    // 13-24 dp
    private static final String KEY_COLOR          = "aod_weather_custom_color";
    private static final String KEY_LOC_SWITCH     = "weather_custom_location_switch"; // shared
    private static final String KEY_LOC_VALUE      = "weather_custom_location_value";  // shared
    private static final String KEY_CENTERED       = "aod_weather_centered";
    private static final String KEY_MARGINS_SWITCH = "aod_weather_custom_margins";
    private static final String KEY_MARGIN_TOP     = "aod_weather_margin_top";    // 0-100 dp
    private static final String KEY_MARGIN_LEFT    = "aod_weather_margin_left";   // 0-100 dp
    private static final String KEY_FONT_SWITCH    = "aod_weather_custom_font_enabled";
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

        SwitchWidgetAdapter.SwitchItem weatherSwitch = gatingSwitch(getString(R.string.lockscreen_weather_enabled), null, KEY_ENABLED);
        weatherSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_ENABLED, weatherSwitch.checked);
            mWeatherExpanded = weatherSwitch.checked;
            rebuild();
        };
        weatherSwitch.onRowClick = () -> { mWeatherExpanded = !mWeatherExpanded; rebuild(); };
        List<Object> topRows = new ArrayList<>();
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
        GroupUtils.addGroup(chain, topRows);

        if (mWeatherExpanded) {
            chain.add(new SectionTitleAdapter(List.of(getString(R.string.aod_clock_prefs))));
            GroupUtils.addGroup(chain, List.of(
                    prefSwitch(getString(R.string.weather_show_location), null, KEY_SHOW_LOCATION),
                    prefSwitch(getString(R.string.weather_show_condition), null, KEY_SHOW_CONDITION),
                    prefSwitch(getString(R.string.weather_show_humidity), null, KEY_SHOW_HUMIDITY),
                    prefSwitch(getString(R.string.weather_show_wind), null, KEY_SHOW_WIND),
                    sliderItem(getString(R.string.weather_text_size), KEY_TEXT_SIZE, 13, 24, 16, "dp"),
                    sliderItem(getString(R.string.weather_image_size), KEY_IMAGE_SIZE, 13, 24, 18, "dp")));
        }

        chain.add(colorRow());

        if (mWeatherExpanded) {
            boolean manual = ObsidianPrefs.getBoolean(KEY_LOC_SWITCH, false);
            SwitchWidgetAdapter.SwitchItem locSwitch = new SwitchWidgetAdapter.SwitchItem(
                    getString(R.string.weather_location_mode_title), null, !manual, null);
            locSwitch.onChanged = () -> {
                ObsidianPrefs.putBoolean(KEY_LOC_SWITCH, !locSwitch.checked);
                mLocExpanded = true;
                rebuild();
            };
            locSwitch.onRowClick = () -> { mLocExpanded = !mLocExpanded; rebuild(); };
            List<Object> locRows = new ArrayList<>();
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
            locRows.add(prefSwitch(getString(R.string.weather_centered), getString(R.string.weather_centered_summary), KEY_CENTERED));
            GroupUtils.addGroup(chain, locRows);
        }

        SwitchWidgetAdapter.SwitchItem marginsSwitch = gatingSwitch(getString(R.string.weather_custom_margins), null, KEY_MARGINS_SWITCH);
        marginsSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_MARGINS_SWITCH, marginsSwitch.checked);
            mMarginsExpanded = marginsSwitch.checked;
            rebuild();
        };
        marginsSwitch.onRowClick = () -> { mMarginsExpanded = !mMarginsExpanded; rebuild(); };
        List<Object> marginRows = new ArrayList<>();
        marginRows.add(marginsSwitch);
        if (mMarginsExpanded) {
            marginRows.add(sliderItem(getString(R.string.weather_margin_top), KEY_MARGIN_TOP, -400, 400, 0, "dp", 10));
            marginRows.add(sliderItem(getString(R.string.weather_margin_left), KEY_MARGIN_LEFT, 0, 100, 0, "dp"));
        }
        GroupUtils.addGroup(chain, marginRows);

        SwitchWidgetAdapter.SwitchItem fontSwitch = gatingSwitch(getString(R.string.pick_font_title), null, KEY_FONT_SWITCH);
        fontSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_FONT_SWITCH, fontSwitch.checked);
            mFontExpanded = fontSwitch.checked;
            rebuild();
        };
        fontSwitch.onRowClick = () -> { mFontExpanded = !mFontExpanded; rebuild(); };
        List<Object> fontRows = new ArrayList<>();
        fontRows.add(fontSwitch);
        if (mFontExpanded) fontRows.add(stubItem(getString(R.string.pick_font_title), getString(R.string.pick_font_summary)));
        GroupUtils.addGroup(chain, fontRows);

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
     *  direttamente dal Mod (AodWeather/LockscreenWeather) al posto della città digitata. */
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

    /** Come singleChoiceRow, ma ricostruisce la lista dopo la scelta — serve a mostrare/
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

    private ListWidgetAdapter.ListItem singleChoiceItem(String title, String key, int entriesArrayRes) {
        return new ListWidgetAdapter.ListItem(
                title, choiceLabel(key, entriesArrayRes),
                () -> showSingleChoiceDialog(title, key, entriesArrayRes));
    }

    private String choiceLabel(String key, int entriesArrayRes) {
        String[] entries = getResources().getStringArray(entriesArrayRes);
        int idx = 0;
        try { idx = Integer.parseInt(ObsidianPrefs.getString(key, "0")); } catch (NumberFormatException ignored) {}
        return (idx >= 0 && idx < entries.length) ? entries[idx] : entries[0];
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
