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

import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * Power Menu hub — mirrors OC's "Menù Accensione" section:
 *   - power_menu_hide_sos: hide SOS entry
 *   - show_advanced_reboot: extra button on the shutdown menu → Recovery/Bootloader/Safe Mode/
 *     Fast Reboot/Restart SystemUI chooser
 *   - advanced_reboot_auth: require biometric auth before showing that chooser
 *   - advanced_reboot_y_offset: vertical offset (dp) of that same button, drawn in
 *     MiscMods.drawAdvancedReboot()
 *   - advanced_reboot_use_accent / advanced_reboot_custom_color: fill colour of that same
 *     button — was hardcoded to the stock "oplus_road_color" grey, now Accento/Personalizzato
 *     like every other colour picker in the app.
 */
public class PowerMenuFragment extends Fragment {

    private static final String PREF_USE_ACCENT   = "advanced_reboot_use_accent";
    private static final String PREF_CUSTOM_COLOR = "advanced_reboot_custom_color";
    private static final int DIALOG_CUSTOM_COLOR  = PREF_CUSTOM_COLOR.hashCode();

    // Riavvia/Spegni pill — independent from the button's own colour above.
    private static final String PREF_GRADIENT_MODE   = "power_menu_gradient_mode";
    private static final String PREF_GRADIENT_CUSTOM = "power_menu_gradient_custom_color";
    private static final int DIALOG_GRADIENT_CUSTOM_COLOR = PREF_GRADIENT_CUSTOM.hashCode();
    private static final String PREF_BG_MODE   = "power_menu_bg_mode";
    private static final String PREF_BG_CUSTOM = "power_menu_bg_custom_color";
    private static final int DIALOG_BG_CUSTOM_COLOR = PREF_BG_CUSTOM.hashCode();
    private static final String PREF_BORDER = "power_menu_border_enabled";
    private static final String PREF_BORDER_USE_ACCENT   = "power_menu_border_use_accent";
    private static final String PREF_BORDER_CUSTOM_COLOR = "power_menu_border_custom_color";
    private static final int DIALOG_BORDER_CUSTOM_COLOR = PREF_BORDER_CUSTOM_COLOR.hashCode();

    private RecyclerView mRv;
    /** Independent of the switch itself — tap the row NAME to expand/collapse, switch only enables. */
    private boolean mAdvancedRebootExpanded = false;
    private boolean mBorderExpanded = false;
    private boolean mBgExpanded = false;

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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_CUSTOM_COLOR, event.color());
        } else if (event.dialogId() == DIALOG_GRADIENT_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_GRADIENT_CUSTOM, event.color());
        } else if (event.dialogId() == DIALOG_BG_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_BG_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_BG_MODE, "custom"); // picking a colour implies "on"
        } else if (event.dialogId() == DIALOG_BORDER_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_BORDER_CUSTOM_COLOR, event.color());
        } else {
            return;
        }
        rebuild();
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
        // Top-level again — now gates BOTH the advanced-reboot chooser AND the stock Riavvia/
        // Spegni slider (GlobalActionsComponent.reboot()/shutdown() hook in MiscMods), so it's
        // no longer specific to "Riavvio Avanzato" alone.
        SwitchWidgetAdapter.SwitchItem authItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.use_auth_for_advanced_reboot_title),
                getString(R.string.use_auth_for_advanced_reboot_summary),
                ObsidianPrefs.getBoolean("advanced_reboot_auth", false),
                null);
        authItem.onChanged = () ->
                ObsidianPrefs.putBoolean("advanced_reboot_auth", authItem.checked);

        SwitchWidgetAdapter.SwitchItem hideSosItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.misc_power_menu_hide_sos), null,
                ObsidianPrefs.getBoolean("power_menu_hide_sos", false),
                null);
        hideSosItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("power_menu_hide_sos", hideSosItem.checked);
            AppUtils.showRestartReminder(requireContext());
        };

        // Switch enables only — tap the row NAME to expand/collapse "Colore Pulsante" below it.
        SwitchWidgetAdapter.SwitchItem advancedRebootItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.show_advanced_reboot_title),
                getString(R.string.show_advanced_reboot_summary),
                ObsidianPrefs.getBoolean("show_advanced_reboot", false),
                null);
        advancedRebootItem.onChanged = () -> {
            ObsidianPrefs.putBoolean("show_advanced_reboot", advancedRebootItem.checked);
            AppUtils.showRestartReminder(requireContext());
        };
        advancedRebootItem.onRowClick = () -> {
            mAdvancedRebootExpanded = !mAdvancedRebootExpanded;
            rebuild();
        };

        SliderWidgetAdapter.SliderItem yOffsetItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.advanced_reboot_y_offset_title),
                ObsidianPrefs.getInt("advanced_reboot_y_offset", 0),
                0, 100, "dp", 0,
                value -> ObsidianPrefs.putInt("advanced_reboot_y_offset", value));
        SliderWidgetAdapter yOffsetAdapter = new SliderWidgetAdapter(List.of(yOffsetItem));

        // Switch enables only — tap the row NAME to expand/collapse the colour-picker row below it.
        SwitchWidgetAdapter.SwitchItem bgItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_bg_color_title), null,
                "custom".equals(ObsidianPrefs.getString(PREF_BG_MODE, "stock")),
                null);
        bgItem.onChanged = () ->
                ObsidianPrefs.putString(PREF_BG_MODE, bgItem.checked ? "custom" : "stock");
        bgItem.onRowClick = () -> {
            mBgExpanded = !mBgExpanded;
            rebuild();
        };

        // Switch enables only — tap the row NAME to expand/collapse "Colore Bordo" below it.
        SwitchWidgetAdapter.SwitchItem borderItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_border_title),
                getString(R.string.power_menu_border_summary),
                ObsidianPrefs.getBoolean(PREF_BORDER, false),
                null);
        borderItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BORDER, borderItem.checked);
            AppUtils.showRestartReminder(requireContext());
        };
        borderItem.onRowClick = () -> {
            mBorderExpanded = !mBorderExpanded;
            rebuild();
        };

        List<RecyclerView.Adapter<?>> sections = new java.util.ArrayList<>();
        GroupUtils.addGroup(sections, List.of(authItem, hideSosItem, advancedRebootItem));
        if (mAdvancedRebootExpanded) {
            GroupUtils.addGroup(sections, List.of(colorModeItem()), true);
        }
        sections.add(yOffsetAdapter);
        sections.add(new ListWidgetAdapter(List.of(gradientColorItem())));
        GroupUtils.addGroup(sections, List.of(bgItem));
        if (mBgExpanded) {
            ListWidgetAdapter.ListItem bgPickItem = new ListWidgetAdapter.ListItem(
                    getString(R.string.power_menu_bg_color_title),
                    triColorLabel(PREF_BG_MODE, PREF_BG_CUSTOM, "stock"),
                    this::openBgColorPicker);
            GroupUtils.addGroup(sections, List.of(bgPickItem), true);
        }
        GroupUtils.addGroup(sections, List.of(borderItem));
        if (mBorderExpanded) {
            ListWidgetAdapter.ListItem borderColorItem = accentCustomColorItem(
                    PREF_BORDER_USE_ACCENT, PREF_BORDER_CUSTOM_COLOR,
                    DIALOG_BORDER_CUSTOM_COLOR, R.string.power_menu_border_color_title);
            GroupUtils.addGroup(sections, List.of(borderColorItem), true);
        }

        mRv.setAdapter(new ConcatAdapter(sections));
    }

    private ListWidgetAdapter.ListItem colorModeItem() {
        return accentCustomColorItem(PREF_USE_ACCENT, PREF_CUSTOM_COLOR,
                DIALOG_CUSTOM_COLOR, R.string.advanced_reboot_color_title);
    }

    /** 2-way Accento/Personalizzato picker — shared by the button colour and the border colour
     *  (unlike the gradient/background pickers, these always have SOME colour, no "Stock" option). */
    private ListWidgetAdapter.ListItem accentCustomColorItem(String useAccentKey, String customColorKey,
                                                               int dialogId, int titleResId) {
        return new ListWidgetAdapter.ListItem(
                getString(titleResId),
                accentCustomColorLabel(useAccentKey, customColorKey),
                () -> showAccentCustomDialog(useAccentKey, customColorKey, dialogId, titleResId));
    }

    private String accentCustomColorLabel(String useAccentKey, String customColorKey) {
        boolean useAccent = ObsidianPrefs.getBoolean(useAccentKey, true);
        if (useAccent) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(customColorKey, ObsidianTheme.DEFAULT_ACCENT));
    }

    private void showAccentCustomDialog(String useAccentKey, String customColorKey, int dialogId, int titleResId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        int current = ObsidianPrefs.getBoolean(useAccentKey, true) ? 0 : 1;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putBoolean(useAccentKey, useAccent);
                    rebuild();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(customColorKey, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private ListWidgetAdapter.ListItem gradientColorItem() {
        return new ListWidgetAdapter.ListItem(
                getString(R.string.power_menu_gradient_color_title),
                triColorLabel(PREF_GRADIENT_MODE, PREF_GRADIENT_CUSTOM, "accent"),
                () -> showTriColorDialog(PREF_GRADIENT_MODE, PREF_GRADIENT_CUSTOM,
                        DIALOG_GRADIENT_CUSTOM_COLOR, R.string.power_menu_gradient_color_title, "accent"));
    }

    // AOSP framework dark-surface greys (android:color/background_dark and friends) — kept dark
    // on purpose so the border (which shares the gradient colour) stays readable against it;
    // anyone who wants a bright/colourful background can still pick it by hand via the picker's
    // own custom-colour controls.
    private static final int[] BG_PRESET_COLORS = {
            0xFF1B2029, 0xFF22262F, 0xFF242832, 0xFF282C36, 0xFF2C313A,
            0xFF30353F, 0xFF353944, 0xFF393E48, 0xFF3E424D, 0xFF9CA1AD, 0x00000000
    };

    /** Same picker used by "Inattivo" in Personalizza Riquadri — the standard ColorPickerDialog
     *  with a presets grid, instead of a hand-rolled chooser dialog. Reached only once the
     *  "Sfondo Pillolone" switch above is on (that's what "Stock" vs "Personalizzato" now is). */
    private void openBgColorPicker() {
        int currentColor = ObsidianPrefs.getInt(PREF_BG_CUSTOM, BG_PRESET_COLORS[0]);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showColorPickerDialog(
                    DIALOG_BG_CUSTOM_COLOR, currentColor, true, true, true, BG_PRESET_COLORS);
        }
    }

    private String triColorLabel(String modePrefKey, String customPrefKey, String defaultMode) {
        String mode = ObsidianPrefs.getString(modePrefKey, defaultMode);
        if ("custom".equals(mode)) {
            return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(customPrefKey, ObsidianTheme.DEFAULT_ACCENT));
        }
        return "accent".equals(mode) ? getString(R.string.color_mode_accent) : getString(R.string.color_mode_stock);
    }

    /** 3-way Stock/Accento/Personalizzato picker, shared by the Riavvia/Spegni gradient and the
     *  pill background — "Stock" (unlike the button's own 2-way picker above) leaves OOS's
     *  original colour alone instead of forcing accent or custom. */
    private void showTriColorDialog(String modePrefKey, String customPrefKey, int dialogId,
                                     int titleResId, String defaultMode) {
        String[] entries = {
                getString(R.string.color_mode_stock),
                getString(R.string.color_mode_accent),
                getString(R.string.color_mode_custom)
        };
        String currentMode = ObsidianPrefs.getString(modePrefKey, defaultMode);
        int current = "accent".equals(currentMode) ? 1 : "custom".equals(currentMode) ? 2 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String newMode = selected[0] == 1 ? "accent" : selected[0] == 2 ? "custom" : "stock";
                    ObsidianPrefs.putString(modePrefKey, newMode);
                    rebuild();
                    if (selected[0] == 2 && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(customPrefKey, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }
}
