package it.tugaia56.obsidian.ui.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.GroupUtils;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
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
    private static final String BG_MODE_IMAGE = "image";
    /** Same relative path MiscMods.java reads from — keep both sides in sync if this changes. */
    private static final String BG_IMAGE_FILENAME = "power_menu_bg_image";
    private static final String PREF_BORDER = "power_menu_border_enabled";
    private static final String PREF_BORDER_USE_ACCENT   = "power_menu_border_use_accent";
    private static final String PREF_BORDER_CUSTOM_COLOR = "power_menu_border_custom_color";
    private static final int DIALOG_BORDER_CUSTOM_COLOR = PREF_BORDER_CUSTOM_COLOR.hashCode();

    // Sfondo Menù Power — background of the WHOLE popup window, independent from the
    // pillolone's own background above. Same 3-way shape, own pref keys/file.
    private static final String PREF_MENU_BG_MODE   = "power_menu_menu_bg_mode";
    private static final String PREF_MENU_BG_CUSTOM = "power_menu_menu_bg_custom_color";
    private static final int DIALOG_MENU_BG_CUSTOM_COLOR = PREF_MENU_BG_CUSTOM.hashCode();
    /** Same relative path MiscMods.java reads from — keep both sides in sync if this changes. */
    private static final String MENU_BG_IMAGE_FILENAME = "power_menu_menu_bg_image";

    private RecyclerView mRv;
    /** Switch ON/OFF keeps this in sync (auto expand/collapse on activation); tapping the row
     *  NAME independently toggles it on top of that — same pattern as everywhere else in the app. */
    private boolean mAdvancedRebootExpanded = ObsidianPrefs.getBoolean("show_advanced_reboot", false);
    /** Shared by both image pickers (pillolone bg + whole-menu bg) — only one can be open at a
     *  time, so a single launcher + a "which target" flag avoids registering two. */
    private ActivityResultLauncher<String> mPickImage;
    private String mPendingImageTarget; // "pill" or "menu"

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        mPickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    boolean pill = "pill".equals(mPendingImageTarget);
                    File dest = pill ? getBgImageFile() : getMenuBgImageFile();
                    if (copyImageToExternal(uri, dest)) {
                        ObsidianPrefs.putString(pill ? PREF_BG_MODE : PREF_MENU_BG_MODE, BG_MODE_IMAGE);
                        rebuild();
                        Toast.makeText(requireContext(), R.string.obs_restart_ui_hint, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private File getBgImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + BG_IMAGE_FILENAME);
    }

    private File getMenuBgImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + MENU_BG_IMAGE_FILENAME);
    }

    private boolean copyImageToExternal(Uri uri, File dest) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), ".obsidian");
            if (!dir.exists() && !dir.mkdirs()) return false;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return false;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void pickBgImage() {
        mPendingImageTarget = "pill";
        launchImagePicker();
    }

    private void pickMenuBgImage() {
        mPendingImageTarget = "menu";
        launchImagePicker();
    }

    private void launchImagePicker() {
        if (!AppUtils.hasStoragePermission()) {
            AppUtils.requestStoragePermission(requireActivity());
        } else {
            mPickImage.launch("image/*");
        }
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
        } else if (event.dialogId() == DIALOG_MENU_BG_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_MENU_BG_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_MENU_BG_MODE, "custom");
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
            mAdvancedRebootExpanded = advancedRebootItem.checked;
            AppUtils.showRestartReminder(requireContext());
            rebuild();
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

        // ── Pillolone (Riavvia/Spegni): Colore / Sfondo / Bordo, own section. One row each —
        // switch ON immediately pops the mode dialog (no separate "tap to open" row underneath,
        // that read as a confusing duplicate of the switch's own label); switch OFF = stock.
        // Tapping the row NAME while already on reopens the same dialog to change the choice. ──

        SwitchWidgetAdapter.SwitchItem gradientSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_gradient_color_title),
                !"stock".equals(ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent"))
                        ? modeAccentCustomLabel(PREF_GRADIENT_MODE, PREF_GRADIENT_CUSTOM) : null,
                !"stock".equals(ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent")),
                null);
        gradientSwitch.onChanged = () -> {
            ObsidianPrefs.putString(PREF_GRADIENT_MODE, gradientSwitch.checked ? "accent" : "stock");
            rebuild();
            if (gradientSwitch.checked) showModeAccentCustomDialog(PREF_GRADIENT_MODE, PREF_GRADIENT_CUSTOM,
                    DIALOG_GRADIENT_CUSTOM_COLOR, R.string.power_menu_gradient_color_title);
        };
        gradientSwitch.onRowClick = () -> {
            if (!"stock".equals(ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent"))) {
                showModeAccentCustomDialog(PREF_GRADIENT_MODE, PREF_GRADIENT_CUSTOM,
                        DIALOG_GRADIENT_CUSTOM_COLOR, R.string.power_menu_gradient_color_title);
            }
        };

        SwitchWidgetAdapter.SwitchItem bgItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_bg_color_title),
                !"stock".equals(ObsidianPrefs.getString(PREF_BG_MODE, "stock"))
                        ? triColorLabel(PREF_BG_MODE, PREF_BG_CUSTOM, "accent") : null,
                !"stock".equals(ObsidianPrefs.getString(PREF_BG_MODE, "stock")),
                null);
        bgItem.onChanged = () -> {
            ObsidianPrefs.putString(PREF_BG_MODE, bgItem.checked ? "accent" : "stock");
            rebuild();
            if (bgItem.checked) showBgModeDialog();
        };
        bgItem.onRowClick = () -> {
            if (!"stock".equals(ObsidianPrefs.getString(PREF_BG_MODE, "stock"))) showBgModeDialog();
        };

        SwitchWidgetAdapter.SwitchItem borderItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_border_title),
                ObsidianPrefs.getBoolean(PREF_BORDER, false)
                        ? accentCustomColorLabel(PREF_BORDER_USE_ACCENT, PREF_BORDER_CUSTOM_COLOR)
                        : getString(R.string.power_menu_border_summary),
                ObsidianPrefs.getBoolean(PREF_BORDER, false),
                null);
        borderItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_BORDER, borderItem.checked);
            AppUtils.showRestartReminder(requireContext());
            rebuild();
            if (borderItem.checked) showAccentCustomDialog(PREF_BORDER_USE_ACCENT, PREF_BORDER_CUSTOM_COLOR,
                    DIALOG_BORDER_CUSTOM_COLOR, R.string.power_menu_border_color_title);
        };
        borderItem.onRowClick = () -> {
            if (ObsidianPrefs.getBoolean(PREF_BORDER, false)) {
                showAccentCustomDialog(PREF_BORDER_USE_ACCENT, PREF_BORDER_CUSTOM_COLOR,
                        DIALOG_BORDER_CUSTOM_COLOR, R.string.power_menu_border_color_title);
            }
        };

        // Sfondo Menù Power — background of the WHOLE popup window, same one-row/auto-open shape.
        SwitchWidgetAdapter.SwitchItem menuBgItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_bg_section),
                !"stock".equals(ObsidianPrefs.getString(PREF_MENU_BG_MODE, "stock"))
                        ? triColorLabel(PREF_MENU_BG_MODE, PREF_MENU_BG_CUSTOM, "accent") : null,
                !"stock".equals(ObsidianPrefs.getString(PREF_MENU_BG_MODE, "stock")),
                null);
        menuBgItem.onChanged = () -> {
            ObsidianPrefs.putString(PREF_MENU_BG_MODE, menuBgItem.checked ? "accent" : "stock");
            rebuild();
            if (menuBgItem.checked) showMenuBgModeDialog();
        };
        menuBgItem.onRowClick = () -> {
            if (!"stock".equals(ObsidianPrefs.getString(PREF_MENU_BG_MODE, "stock"))) showMenuBgModeDialog();
        };

        List<RecyclerView.Adapter<?>> sections = new java.util.ArrayList<>();

        // ── Menù Power: autenticazione, SOS, Riavvio Avanzato (+ Colore Pulsante/Offset, solo
        // se attivo) — tutto quello che NON riguarda il pillolone Riavvia/Spegni. ──────────
        List<Object> menuRows = new java.util.ArrayList<>(List.of(authItem, hideSosItem, advancedRebootItem));
        if (mAdvancedRebootExpanded) {
            menuRows.add(colorModeItem());
            menuRows.add(yOffsetItem);
        }
        GroupUtils.addGroup(sections, menuRows);

        // ── Sfondo Menù Power — sopra Pillolone, sua card a sé. ───────────────────────────
        GroupUtils.addGroup(sections, List.of(menuBgItem));

        // ── Pillolone: Colore Riavvia/Spegni → Sfondo Pillolone → Bordo Pillolone, un'unica
        // card continua, una riga sola ciascuno. ─────────────────────────────────────────
        sections.add(new SectionTitleAdapter(List.of(getString(R.string.power_menu_pill_section))));
        GroupUtils.addGroup(sections, List.of(gradientSwitch, bgItem, borderItem));

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

    /** Same label logic as accentCustomColorLabel(), just reading a string-mode pref
     *  ("accent"/"custom", "stock" only reachable via the row's own switch being off) instead
     *  of a boolean — shared by "Colore Riavvia/Spegni" and "Sfondo Pillolone"'s colour sub-case. */
    private String modeAccentCustomLabel(String modePrefKey, String customPrefKey) {
        boolean useAccent = !"custom".equals(ObsidianPrefs.getString(modePrefKey, "accent"));
        if (useAccent) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(customPrefKey, ObsidianTheme.DEFAULT_ACCENT));
    }

    /** 2-way Accento/Personalizzato sub-dialog for a string-mode pref — same choices as
     *  showAccentCustomDialog() but for "Colore Riavvia/Spegni", which stores "accent"/"custom"
     *  (stock is the row's own switch being off, not a third dialog choice anymore). */
    private void showModeAccentCustomDialog(String modePrefKey, String customPrefKey, int dialogId, int titleResId) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        String currentMode = ObsidianPrefs.getString(modePrefKey, "accent");
        int current = "custom".equals(currentMode) ? 1 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putString(modePrefKey, useAccent ? "accent" : "custom");
                    rebuild();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(customPrefKey, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
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

    /** "Sfondo Pillolone" row tap — 3-way Accento/Colore/Immagine chooser ("Stock" is the row's
     *  own switch being off, not a dialog choice). Picking "Colore" opens the existing preset
     *  colour picker unchanged; picking "Immagine" launches the system image picker (permission-
     *  gated, same flow as QsHeaderImageFragment) — the mode pref only flips to "image" once a
     *  file is actually copied successfully, so cancelling the picker leaves the mode untouched. */
    private void showBgModeDialog() {
        String[] entries = {
                getString(R.string.color_mode_accent),
                getString(R.string.power_menu_bg_mode_custom),
                getString(R.string.color_mode_image)
        };
        String currentMode = ObsidianPrefs.getString(PREF_BG_MODE, "accent");
        int current = "custom".equals(currentMode) ? 1 : BG_MODE_IMAGE.equals(currentMode) ? 2 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.power_menu_bg_color_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] == 2) { pickBgImage(); return; }
                    ObsidianPrefs.putString(PREF_BG_MODE, selected[0] == 1 ? "custom" : "accent");
                    rebuild();
                    if (selected[0] == 1) openBgColorPicker();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private String triColorLabel(String modePrefKey, String customPrefKey, String defaultMode) {
        String mode = ObsidianPrefs.getString(modePrefKey, defaultMode);
        if (BG_MODE_IMAGE.equals(mode)) return getString(R.string.color_mode_image);
        if ("custom".equals(mode)) {
            return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(customPrefKey, ObsidianTheme.DEFAULT_ACCENT));
        }
        return "accent".equals(mode) ? getString(R.string.color_mode_accent) : getString(R.string.color_mode_stock);
    }

    /** Same picker/presets as openBgColorPicker(), just for "Sfondo Menù Power"'s own colour pref. */
    private void openMenuBgColorPicker() {
        int currentColor = ObsidianPrefs.getInt(PREF_MENU_BG_CUSTOM, BG_PRESET_COLORS[0]);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showColorPickerDialog(
                    DIALOG_MENU_BG_CUSTOM_COLOR, currentColor, true, true, true, BG_PRESET_COLORS);
        }
    }

    /** "Sfondo Menù Power" row tap — same 3-way Accento/Colore/Immagine chooser as "Sfondo
     *  Pillolone", just targeting the whole popup window's own independent mode/colour/image. */
    private void showMenuBgModeDialog() {
        String[] entries = {
                getString(R.string.color_mode_accent),
                getString(R.string.power_menu_bg_mode_custom),
                getString(R.string.color_mode_image)
        };
        String currentMode = ObsidianPrefs.getString(PREF_MENU_BG_MODE, "accent");
        int current = "custom".equals(currentMode) ? 1 : BG_MODE_IMAGE.equals(currentMode) ? 2 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.power_menu_bg_section)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] == 2) { pickMenuBgImage(); return; }
                    ObsidianPrefs.putString(PREF_MENU_BG_MODE, selected[0] == 1 ? "custom" : "accent");
                    rebuild();
                    if (selected[0] == 1) openMenuBgColorPicker();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }
}
