package it.tugaia56.obsidian.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

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
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.ui.models.DarkShadowItem;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.views.DeviceStatGaugeView;

/**
 * Widget Dispositivo (Schermata di Blocco) — anteprima live + tipo per i 2 gauge (batteria/
 * RAM/volume/temperatura), Stile (Circolare/Lineare, stesso algoritmo di OC's
 * ArcProgressWidget), Colori Personalizzati (colore Circolare + colore Lineare — le "2
 * barre"), Colore testo, Nome dispositivo personalizzato. Letto dal vero hook in
 * LockscreenWidgetsMod.java (stesse chiavi).
 */
public class DeviceWidgetSettingsFragment extends Fragment {

    private static final String KEY_STYLE        = "device_widget_style"; // "0"=circolare "1"=lineare
    private static final String KEY_COLOR_SWITCH = "device_widget_custom_color";
    private static final String KEY_COLOR_CIRCULAR = "device_widget_color_circular";
    private static final String KEY_COLOR_LINEAR   = "device_widget_color_linear";
    private static final String KEY_TEXT_COLOR   = "device_widget_text_color";
    private static final String KEY_NAME         = "device_widget_name";
    /** 6 slot indipendenti come i widget principali (main_custom_widgets1..custom_widgets4). */
    private static final String[] KEY_TYPES = {
            "device_widget_type1", "device_widget_type2", "device_widget_type3",
            "device_widget_type4", "device_widget_type5", "device_widget_type6",
    };
    private static final int[] DEFAULT_TYPE = {1, 2, 0, 0, 0, 0}; // Batteria, RAM, resto Nessuno

    private static final String[] TYPE_LABELS = {"Nessuno", "Batteria", "RAM", "Volume", "Temperatura", "Wi-Fi", "Bluetooth"};

    private RecyclerView mRv;
    private boolean mColorExpanded = false;
    private final List<DarkShadowItem> mColorItems = new ArrayList<>();
    private DarkShadowColorListener mColorAdapter;

    private LinearLayout mPreviewRow;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.device_widget_preview_title))));
        chain.add(previewRow());
        List<Object> typeRows = new ArrayList<>();
        for (int i = 0; i < KEY_TYPES.length; i++) {
            typeRows.add(pickTypeItem("Widget " + (i + 1), KEY_TYPES[i], DEFAULT_TYPE[i]));
        }
        GroupUtils.addGroup(chain, typeRows);

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.lockscreen_widgets_style_section))));
        chain.add(styleRow());

        SwitchWidgetAdapter.SwitchItem colorSwitch = gatingSwitch(
                getString(R.string.widgets_custom_color_title), null, KEY_COLOR_SWITCH);
        colorSwitch.onChanged = () -> {
            ObsidianPrefs.putBoolean(KEY_COLOR_SWITCH, colorSwitch.checked);
            mColorExpanded = colorSwitch.checked;
            rebuild();
        };
        colorSwitch.onRowClick = () -> { mColorExpanded = !mColorExpanded; rebuild(); };
        chain.add(new SwitchWidgetAdapter(List.of(colorSwitch)));
        if (mColorExpanded) chain.add(colorsRow());

        chain.add(new SectionTitleAdapter(List.of(getString(R.string.misc_category))));
        chain.add(editTextRow(getString(R.string.device_widget_name_title),
                android.os.Build.MODEL, KEY_NAME));

        // "Colori Personalizzati" (e ogni altro switch+expand qui) chiama rebuild(), che
        // altrimenti azzererebbe lo scroll ricreando l'adapter — stesso salva/ripristina di
        // AodWeatherFragment.
        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(chain.toArray(new RecyclerView.Adapter<?>[0])));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
    }

    // ── Anteprima live + scelta tipo per i (fino a 6) gauge ────────────────────────────────

    private RecyclerView.Adapter<?> previewRow() {
        return new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                int pad = dp(16);
                row.setPadding(pad, pad, pad, pad);
                mPreviewRow = row;
                applyPreviewStyle();
                return new RecyclerView.ViewHolder(row) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {}
            @Override public int getItemCount() { return 1; }
        };
    }

    private void applyPreviewStyle() {
        if (mPreviewRow == null) return;
        boolean circular = "0".equals(ObsidianPrefs.getString(KEY_STYLE, "0"));
        DeviceStatGaugeView.Style style = circular ? DeviceStatGaugeView.Style.CIRCULAR : DeviceStatGaugeView.Style.LINEAR;
        boolean customColor = ObsidianPrefs.getBoolean(KEY_COLOR_SWITCH, false);
        int progressColor = customColor
                ? ObsidianPrefs.getInt(circular ? KEY_COLOR_CIRCULAR : KEY_COLOR_LINEAR, 0xFF908DFF)
                : 0xFF908DFF;
        int textColor = customColor ? ObsidianPrefs.getInt(KEY_TEXT_COLOR, 0xFFFFFFFF) : 0xFFFFFFFF;

        int shown = 0;
        int[] indices = new int[KEY_TYPES.length];
        for (int i = 0; i < KEY_TYPES.length; i++) {
            indices[i] = typeIndex(KEY_TYPES[i], DEFAULT_TYPE[i]);
            if (indices[i] != 0) shown++;
        }
        int scale = shown <= 2 ? 100 : shown <= 4 ? 78 : 60;
        int w = (circular ? dp(80) : dp(160)) * scale / 100;
        int h = (circular ? dp(80) : dp(52)) * scale / 100;
        int gap = dp(8);

        mPreviewRow.removeAllViews();
        for (int idx : indices) {
            if (idx == 0) continue;
            DeviceStatGaugeView gauge = new DeviceStatGaugeView(requireContext());
            gauge.setStyle(style);
            gauge.setColors(progressColor, textColor);
            gauge.setType(typeFromIndex(idx));
            gauge.setIcon(requireContext().getDrawable(iconFor(typeFromIndex(idx))));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.leftMargin = gap; lp.rightMargin = gap;
            mPreviewRow.addView(gauge, lp);
        }
    }

    private DeviceStatGaugeView.StatType typeFromIndex(int idx) {
        return switch (idx) {
            case 2 -> DeviceStatGaugeView.StatType.RAM;
            case 3 -> DeviceStatGaugeView.StatType.VOLUME;
            case 4 -> DeviceStatGaugeView.StatType.TEMPERATURE;
            case 5 -> DeviceStatGaugeView.StatType.WIFI;
            case 6 -> DeviceStatGaugeView.StatType.BLUETOOTH;
            default -> DeviceStatGaugeView.StatType.BATTERY;
        };
    }

    private int iconFor(DeviceStatGaugeView.StatType type) {
        return switch (type) {
            case RAM -> R.drawable.ic_widget_ram;
            case VOLUME -> R.drawable.ic_sysui_volume;
            case TEMPERATURE -> R.drawable.ic_widget_temperature;
            case WIFI -> R.drawable.ic_widget_wifi;
            case BLUETOOTH -> R.drawable.ic_widget_bluetooth;
            default -> R.drawable.ic_battery;
        };
    }

    private ListWidgetAdapter.ListItem pickTypeItem(String title, String key, int defaultType) {
        return new ListWidgetAdapter.ListItem(
                title, typeLabel(key, defaultType), () -> showTypeDialog(title, key, defaultType));
    }

    private int typeIndex(String key, int defaultType) {
        return parseInt(ObsidianPrefs.getString(key, String.valueOf(defaultType)), defaultType);
    }

    private String typeLabel(String key, int defaultType) {
        int idx = typeIndex(key, defaultType);
        return (idx >= 0 && idx < TYPE_LABELS.length) ? TYPE_LABELS[idx] : TYPE_LABELS[0];
    }

    private void showTypeDialog(String title, String key, int defaultType) {
        int current = typeIndex(key, defaultType);
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(TYPE_LABELS, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(key, String.valueOf(selected[0]));
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Stile (Circolare / Lineare) ─────────────────────────────────────────────────────

    private ListWidgetAdapter styleRow() {
        final ListWidgetAdapter[] adapterRef = new ListWidgetAdapter[1];
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                getString(R.string.lockscreen_widgets_style_section), styleLabel(),
                () -> showStyleDialog(adapterRef[0]));
        ListWidgetAdapter adapter = new ListWidgetAdapter(List.of(item));
        adapterRef[0] = adapter;
        return adapter;
    }

    private String styleLabel() {
        return "0".equals(ObsidianPrefs.getString(KEY_STYLE, "0")) ? "Circolare" : "Lineare";
    }

    private void showStyleDialog(ListWidgetAdapter adapter) {
        String[] entries = {"Circolare", "Lineare"};
        int current = "0".equals(ObsidianPrefs.getString(KEY_STYLE, "0")) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.lockscreen_widgets_style_section))
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putString(KEY_STYLE, String.valueOf(selected[0]));
                    adapter.getItems().get(0).valueSummary = styleLabel();
                    adapter.notifyItemChanged(0);
                    rebuild();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Colori (Circolare + Lineare — "le 2 barre") ────────────────────────────────────────

    private DarkShadowColorListener colorsRow() {
        String[] keys = {KEY_COLOR_CIRCULAR, KEY_COLOR_LINEAR, KEY_TEXT_COLOR};
        String[] labels = {"Colore Circolare", "Colore Lineare", getString(R.string.device_widget_text_color)};
        for (int i = 0; i < keys.length; i++) {
            mColorItems.add(new DarkShadowItem(labels[i], keys[i],
                    Collections.emptyList(), Collections.emptyList(), null,
                    ObsidianPrefs.getInt(keys[i], 0xFFFFFFFF),
                    ObsidianPrefs.getBoolean(keys[i] + "_on", false)));
        }
        mColorAdapter = new DarkShadowColorListener(mColorItems, this::onColorEnabled, this::onColorDisabled, this::onColorSwatch);
        return mColorAdapter;
    }

    private void onColorEnabled(DarkShadowItem item) {
        item.setEnabled(true);
        ObsidianPrefs.putInt(item.getOverlayName(), item.getColor());
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", true);
        applyPreviewStyle();
    }

    private void onColorDisabled(DarkShadowItem item) {
        item.setEnabled(false);
        ObsidianPrefs.putBoolean(item.getOverlayName() + "_on", false);
        applyPreviewStyle();
    }

    private void onColorSwatch(DarkShadowItem item, int dialogId) {
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
            ObsidianPrefs.putBoolean(item.getOverlayName() + "_use_accent", false); // picking a colour implies custom
            item.setColor(event.color());
            ObsidianPrefs.putInt(item.getOverlayName(), event.color());
            if (mColorAdapter != null) mColorAdapter.notifyDataSetChanged();
            applyPreviewStyle();
            return;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    // ── Nome dispositivo ─────────────────────────────────────────────────────────────────

    private ListWidgetAdapter editTextRow(String title, String defaultValue, String key) {
        final ListWidgetAdapter[] adapterRef = new ListWidgetAdapter[1];
        String current = ObsidianPrefs.getString(key, "");
        ListWidgetAdapter.ListItem item = new ListWidgetAdapter.ListItem(
                title, current.isEmpty() ? defaultValue : current,
                () -> showEditTextDialog(title, defaultValue, key, adapterRef[0]));
        item.useAccentColor = false;
        ListWidgetAdapter adapter = new ListWidgetAdapter(List.of(item));
        adapterRef[0] = adapter;
        return adapter;
    }

    private void showEditTextDialog(String title, String defaultValue, String key, ListWidgetAdapter adapter) {
        EditText et = new EditText(requireContext());
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(ObsidianPrefs.getString(key, ""));
        et.setHint(defaultValue);
        et.setSingleLine(true);
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad / 2, pad, 0);
        layout.addView(et);

        ObsidianTheme.themeDialog(new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(layout)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String text = et.getText().toString().trim();
                    ObsidianPrefs.putString(key, text);
                    adapter.getItems().get(0).valueSummary = text.isEmpty() ? defaultValue : text;
                    adapter.notifyItemChanged(0);
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Generico ─────────────────────────────────────────────────────────────────────────

    private SwitchWidgetAdapter.SwitchItem gatingSwitch(String title, String summary, String key) {
        SwitchWidgetAdapter.SwitchItem item = new SwitchWidgetAdapter.SwitchItem(
                title, summary, ObsidianPrefs.getBoolean(key, false), null);
        item.onChanged = () -> {
            ObsidianPrefs.putBoolean(key, item.checked);
            rebuild();
        };
        return item;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
