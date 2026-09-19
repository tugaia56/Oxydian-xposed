package it.tugaia56.obsidian.ui.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import it.tugaia56.obsidian.ui.widgets.ImageCropOverlayView;
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
    /** "Due colori" — Riavvia e Spegni con colore proprio, indipendente da Accento/Personalizzato
     *  (richiesta esplicita dell'utente). PREF_GRADIENT_MODE=="split" quando attivo. */
    private static final String PREF_GRADIENT_RESTART  = "power_menu_gradient_restart_color";
    private static final String PREF_GRADIENT_SHUTDOWN = "power_menu_gradient_shutdown_color";
    private static final int DIALOG_GRADIENT_RESTART_COLOR  = PREF_GRADIENT_RESTART.hashCode();
    private static final int DIALOG_GRADIENT_SHUTDOWN_COLOR = PREF_GRADIENT_SHUTDOWN.hashCode();
    private static final String PREF_BG_MODE   = "power_menu_bg_mode";
    private static final String PREF_BG_CUSTOM = "power_menu_bg_custom_color";
    private static final int DIALOG_BG_CUSTOM_COLOR = PREF_BG_CUSTOM.hashCode();
    private static final String BG_MODE_IMAGE = "image";
    /** Same relative path MiscMods.java reads from — keep both sides in sync if this changes. */
    private static final String BG_IMAGE_FILENAME = "power_menu_bg_image";
    /** Ritaglio (cx/cy/zoom) — stessa convenzione di ImageCropOverlayView/QsHeaderImage, vedi
     *  MiscMods.customCropSrcRect(). Indipendente per Pillolone/Sfondo Menù/Pallino. */
    private static final String PREF_BG_CROP_CX   = "power_menu_bg_crop_cx";
    private static final String PREF_BG_CROP_CY   = "power_menu_bg_crop_cy";
    private static final String PREF_BG_CROP_ZOOM = "power_menu_bg_crop_zoom";
    private static final String PREF_BORDER = "power_menu_border_enabled";
    private static final String PREF_BORDER_USE_ACCENT   = "power_menu_border_use_accent";
    private static final String PREF_BORDER_CUSTOM_COLOR = "power_menu_border_custom_color";
    private static final int DIALOG_BORDER_CUSTOM_COLOR = PREF_BORDER_CUSTOM_COLOR.hashCode();

    // Colore Pallino — the draggable thumb (mHandlerColor in OplusShutdownView).
    private static final String PREF_HANDLER_MODE   = "power_menu_handler_mode";
    private static final String PREF_HANDLER_CUSTOM = "power_menu_handler_custom_color";
    private static final int DIALOG_HANDLER_CUSTOM_COLOR = PREF_HANDLER_CUSTOM.hashCode();
    private static final String PREF_HANDLER_BORDER = "power_menu_handler_border_enabled";
    private static final String PREF_HANDLER_BORDER_USE_ACCENT   = "power_menu_handler_border_use_accent";
    private static final String PREF_HANDLER_BORDER_CUSTOM_COLOR = "power_menu_handler_border_custom_color";
    private static final int DIALOG_HANDLER_BORDER_CUSTOM_COLOR = PREF_HANDLER_BORDER_CUSTOM_COLOR.hashCode();

    // Sfondo Menù Power — background of the WHOLE popup window, independent from the
    // pillolone's own background above. Same 3-way shape, own pref keys/file.
    private static final String PREF_MENU_BG_MODE   = "power_menu_menu_bg_mode";
    private static final String PREF_MENU_BG_CUSTOM = "power_menu_menu_bg_custom_color";
    private static final int DIALOG_MENU_BG_CUSTOM_COLOR = PREF_MENU_BG_CUSTOM.hashCode();
    /** Same relative path MiscMods.java reads from — keep both sides in sync if this changes. */
    private static final String MENU_BG_IMAGE_FILENAME = "power_menu_menu_bg_image";
    private static final String PREF_MENU_BG_CROP_CX   = "power_menu_menu_bg_crop_cx";
    private static final String PREF_MENU_BG_CROP_CY   = "power_menu_menu_bg_crop_cy";
    private static final String PREF_MENU_BG_CROP_ZOOM = "power_menu_menu_bg_crop_zoom";

    private RecyclerView mRv;
    /** Sempre chiuso quando si apre lo schermo (richiesta esplicita: "non voglio trovarle aperte
     *  appena apro Menù Accensione") — si espande SOLO toccando la riga, non segue più lo stato
     *  ON/OFF dello switch. Riparte da false ad ogni nuova istanza del Fragment (ogni volta che si
     *  rientra nello schermo). */
    private boolean mAdvancedRebootExpanded = false;
    /** Editor di ritaglio (zoom+preview) di "Sfondo Pillolone"/"Sfondo Menù Power" — sempre
     *  chiuso all'apertura dello schermo (non deve "trovarsi già aperto"). Il tap sulla riga apre
     *  SEMPRE il dialog Accento/Colore/Immagine (richiesta esplicita, non fa più da toggle) — per
     *  riaprire l'editor senza dover scegliere una nuova foto ogni volta, ri-confermare "Immagine"
     *  nel dialog quando è già la modalità attiva lo riespande direttamente (vedi
     *  showBgModeDialog/showMenuBgModeDialog), oltre all'espansione automatica subito dopo aver
     *  scelto una foto nuova. */
    private boolean mBgExpanded = false;
    private boolean mMenuBgExpanded = false;
    /** Shared by both image pickers (pillolone bg + whole-menu bg) — only one can be open at a
     *  time, so a single launcher + a "which target" flag avoids registering two. Colore Pallino
     *  has its own picker screen (PowerMenuHandlerPresetFragment, with its own launcher) instead
     *  of using this one directly — see navigateToHandlerImagePicker(). */
    private ActivityResultLauncher<String> mPickImage;
    private String mPendingImageTarget; // "pill" or "menu"

    // ── Anteprima ritaglio (Pillolone/Sfondo Menù, solo quando mode=="image") ──────────────────
    private Bitmap mBgPreviewBmp;
    private ImageCropOverlayView mBgCropOverlay;
    private Bitmap mMenuBgPreviewBmp;
    private ImageCropOverlayView mMenuBgCropOverlay;

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
                    String modeKey = pill ? PREF_BG_MODE : PREF_MENU_BG_MODE;
                    if (copyImageToExternal(uri, dest)) {
                        ObsidianPrefs.putString(modeKey, BG_MODE_IMAGE);
                        // Foto nuova: il riquadro di una foto precedente non ha senso, riparte
                        // centrato senza zoom (stesso pattern di VolumePanelColorsFragment).
                        String cxKey = pill ? PREF_BG_CROP_CX : PREF_MENU_BG_CROP_CX;
                        String cyKey = pill ? PREF_BG_CROP_CY : PREF_MENU_BG_CROP_CY;
                        String zoomKey = pill ? PREF_BG_CROP_ZOOM : PREF_MENU_BG_CROP_ZOOM;
                        ObsidianPrefs.putInt(cxKey, 50);
                        ObsidianPrefs.putInt(cyKey, 50);
                        ObsidianPrefs.putInt(zoomKey, 100);
                        if (pill) mBgExpanded = true; else mMenuBgExpanded = true;
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

    @Override
    public void onPause() {
        super.onPause();
        // 2026-09-07: "il pillolone e lo sfondo spesso tornano stock" — MiscMods non aveva un
        // preloadFallback come SettingsCardBackgroundMod/DstCpbStyle/DstNotifStyle, quindi le
        // property di boot per il menù accensione restavano sempre a zero. Salviamole quando si
        // esce da questa schermata (invece che ad ogni singola opzione) — copre tutte le
        // modifiche di questa sessione in un colpo solo, non serve toccare ogni singolo
        // ObsidianPrefs.put... sparso nel file.
        new Thread(it.tugaia56.obsidian.utils.DstFabricatedUtil::saveBootProps).start();
        // 2026-09-17: "non si chiudono se tocco titolo... dopo aver riavviato UI rimangono
        // aperti" — Fragment.replace()+addToBackStack riusa la STESSA istanza quando si torna
        // indietro (es. da un'altra schermata dopo aver toccato Riavvia SystemUI altrove), quindi
        // mBgExpanded/mMenuBgExpanded restavano quello che erano PRIMA di uscire. Azzerarli QUI
        // (in uscita, non al rientro) evita di rompere l'espansione automatica dopo aver scelto
        // una foto nuova dalla galleria (che avviene DOPO il rientro, nel callback di
        // mPickImage — onPause() scatta anche quando si apre il picker di sistema, ma qui non fa
        // danno: il callback la reimposta comunque a true subito dopo il ritorno).
        mBgExpanded = false;
        mMenuBgExpanded = false;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_CUSTOM_COLOR, event.color());
        } else if (event.dialogId() == DIALOG_GRADIENT_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_GRADIENT_CUSTOM, event.color());
        } else if (event.dialogId() == DIALOG_GRADIENT_RESTART_COLOR) {
            ObsidianPrefs.putInt(PREF_GRADIENT_RESTART, event.color());
            // Incatena subito il picker per Spegni — "Due colori" richiede entrambi, non ha senso
            // fermarsi a metà. Il mode passa a "split" solo quando anche questo è stato scelto.
            if (getActivity() instanceof MainActivity) {
                int current = ObsidianPrefs.getInt(PREF_GRADIENT_SHUTDOWN, 0xFFEB3B2F);
                ((MainActivity) getActivity()).showColorPickerDialog(
                        DIALOG_GRADIENT_SHUTDOWN_COLOR, current, true, true, true);
            }
            return; // niente rebuild ancora, manca il secondo colore
        } else if (event.dialogId() == DIALOG_GRADIENT_SHUTDOWN_COLOR) {
            ObsidianPrefs.putInt(PREF_GRADIENT_SHUTDOWN, event.color());
            ObsidianPrefs.putString(PREF_GRADIENT_MODE, "split");
        } else if (event.dialogId() == DIALOG_BG_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_BG_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_BG_MODE, "custom"); // picking a colour implies "on"
        } else if (event.dialogId() == DIALOG_BORDER_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_BORDER_CUSTOM_COLOR, event.color());
        } else if (event.dialogId() == DIALOG_MENU_BG_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_MENU_BG_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_MENU_BG_MODE, "custom");
        } else if (event.dialogId() == DIALOG_HANDLER_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_HANDLER_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_HANDLER_MODE, "custom");
        } else if (event.dialogId() == DIALOG_HANDLER_BORDER_CUSTOM_COLOR) {
            ObsidianPrefs.putInt(PREF_HANDLER_BORDER_CUSTOM_COLOR, event.color());
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

    @Override
    public void onResume() {
        super.onResume();
        // Rinfresca al ritorno da PowerMenuHandlerPresetFragment (es. preset/foto cambiati là).
        if (mRv != null) rebuild();
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
                        ? gradientColorLabel() : null,
                !"stock".equals(ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent")),
                null);
        gradientSwitch.onChanged = () -> {
            ObsidianPrefs.putString(PREF_GRADIENT_MODE, gradientSwitch.checked ? "accent" : "stock");
            rebuild();
            if (gradientSwitch.checked) showGradientModeDialog();
        };
        gradientSwitch.onRowClick = () -> {
            if (!"stock".equals(ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent"))) {
                showGradientModeDialog();
            }
        };

        SwitchWidgetAdapter.SwitchItem handlerSwitch = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_handler_color_title),
                !"stock".equals(ObsidianPrefs.getString(PREF_HANDLER_MODE, "stock"))
                        ? triColorLabel(PREF_HANDLER_MODE, PREF_HANDLER_CUSTOM, "accent") : null,
                !"stock".equals(ObsidianPrefs.getString(PREF_HANDLER_MODE, "stock")),
                null);
        handlerSwitch.onChanged = () -> {
            ObsidianPrefs.putString(PREF_HANDLER_MODE, handlerSwitch.checked ? "accent" : "stock");
            rebuild();
            if (handlerSwitch.checked) showHandlerModeDialog();
        };
        handlerSwitch.onRowClick = () -> {
            if (!"stock".equals(ObsidianPrefs.getString(PREF_HANDLER_MODE, "stock"))) showHandlerModeDialog();
        };

        SwitchWidgetAdapter.SwitchItem handlerBorderItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.power_menu_handler_border_title),
                ObsidianPrefs.getBoolean(PREF_HANDLER_BORDER, false)
                        ? accentCustomColorLabel(PREF_HANDLER_BORDER_USE_ACCENT, PREF_HANDLER_BORDER_CUSTOM_COLOR)
                        : getString(R.string.power_menu_handler_border_summary),
                ObsidianPrefs.getBoolean(PREF_HANDLER_BORDER, false),
                null);
        handlerBorderItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_HANDLER_BORDER, handlerBorderItem.checked);
            AppUtils.showRestartReminder(requireContext());
            rebuild();
            if (handlerBorderItem.checked) showAccentCustomDialog(PREF_HANDLER_BORDER_USE_ACCENT, PREF_HANDLER_BORDER_CUSTOM_COLOR,
                    DIALOG_HANDLER_BORDER_CUSTOM_COLOR, R.string.power_menu_handler_border_color_title);
        };
        handlerBorderItem.onRowClick = () -> {
            if (ObsidianPrefs.getBoolean(PREF_HANDLER_BORDER, false)) {
                showAccentCustomDialog(PREF_HANDLER_BORDER_USE_ACCENT, PREF_HANDLER_BORDER_CUSTOM_COLOR,
                        DIALOG_HANDLER_BORDER_CUSTOM_COLOR, R.string.power_menu_handler_border_color_title);
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
        boolean bgIsPhoto = BG_MODE_IMAGE.equals(ObsidianPrefs.getString(PREF_BG_MODE, "stock"));
        // 2026-09-17 (di nuovo): il tap deve poter CHIUDERE l'editor direttamente, senza passare
        // dal dialog — se è già aperto, chiudilo e basta; solo se è chiuso il tap apre il dialog
        // (per scegliere/confermare la modalità, che poi riapre l'editor da lì).
        bgItem.onRowClick = () -> {
            if (bgIsPhoto && mBgExpanded) { mBgExpanded = false; rebuild(); return; }
            if (!"stock".equals(ObsidianPrefs.getString(PREF_BG_MODE, "stock"))) showBgModeDialog();
        };
        SliderWidgetAdapter.SliderItem bgZoomItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_zoom),
                ObsidianPrefs.getInt(PREF_BG_CROP_ZOOM, 100),
                20, 300, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_BG_CROP_ZOOM, value);
                    if (mBgCropOverlay != null) mBgCropOverlay.setZoomPercent(value);
                });
        bgZoomItem.onLivePreview = value -> { if (mBgCropOverlay != null) mBgCropOverlay.setZoomPercent(value); };

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
        boolean menuBgIsPhoto = BG_MODE_IMAGE.equals(ObsidianPrefs.getString(PREF_MENU_BG_MODE, "stock"));
        menuBgItem.onRowClick = () -> {
            if (menuBgIsPhoto && mMenuBgExpanded) { mMenuBgExpanded = false; rebuild(); return; }
            if (!"stock".equals(ObsidianPrefs.getString(PREF_MENU_BG_MODE, "stock"))) showMenuBgModeDialog();
        };
        SliderWidgetAdapter.SliderItem menuBgZoomItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_zoom),
                ObsidianPrefs.getInt(PREF_MENU_BG_CROP_ZOOM, 100),
                20, 300, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_MENU_BG_CROP_ZOOM, value);
                    if (mMenuBgCropOverlay != null) mMenuBgCropOverlay.setZoomPercent(value);
                });
        menuBgZoomItem.onLivePreview = value -> { if (mMenuBgCropOverlay != null) mMenuBgCropOverlay.setZoomPercent(value); };

        List<RecyclerView.Adapter<?>> sections = new java.util.ArrayList<>();

        // ── Menù Power: autenticazione, SOS — tutto quello che NON riguarda il pillolone
        // Riavvia/Spegni né il Riavvio Avanzato (sua card a sé, sotto). ──────────────────
        GroupUtils.addGroup(sections, List.of(authItem, hideSosItem));

        // ── Riavvio Avanzato: card a sé con titolo — separata dal resto (richiesta esplicita).
        // Colore Pulsante/Offset compaiono solo quando mAdvancedRebootExpanded, che riparte
        // sempre chiuso ad ogni apertura dello schermo (vedi commento sul campo). ──────────
        sections.add(new SectionTitleAdapter(List.of(getString(R.string.show_advanced_reboot_title))));
        List<Object> advRebootRows = new java.util.ArrayList<>(List.of(advancedRebootItem));
        if (mAdvancedRebootExpanded) {
            advRebootRows.add(colorModeItem());
            advRebootRows.add(yOffsetItem);
        }
        GroupUtils.addGroup(sections, advRebootRows);

        // ── Sfondo Menù Power — sopra Pillolone, sua card a sé con titolo "Power Menù" (stesso
        // trattamento di Riavvio Avanzato). Ritaglio (anteprima+zoom) solo quando il mode è
        // "image" — la preview card (custom adapter) interrompe il "run" di GroupUtils, stessa
        // nota già presente in QsHeaderImageFragment/VolumePanelColorsFragment. ─────────────────
        sections.add(new SectionTitleAdapter(List.of(getString(R.string.power_menu_menu_section))));
        if (menuBgIsPhoto) {
            GroupUtils.addGroup(sections, List.of(menuBgItem));
            if (mMenuBgExpanded) {
                GroupUtils.addGroup(sections, List.of(menuBgZoomItem));
                sections.add(new MenuBgPreviewAdapter());
            }
        } else {
            GroupUtils.addGroup(sections, List.of(menuBgItem));
        }

        // ── Pillolone: Colore Riavvia/Spegni → Sfondo Pillolone (+ ritaglio se "image") → Bordo
        // Pillolone, un'unica card continua, una riga sola ciascuno. ─────────────────────────
        sections.add(new SectionTitleAdapter(List.of(getString(R.string.power_menu_pill_section))));
        if (bgIsPhoto) {
            GroupUtils.addGroup(sections, List.of(gradientSwitch, handlerSwitch, handlerBorderItem, bgItem));
            if (mBgExpanded) {
                GroupUtils.addGroup(sections, List.of(bgZoomItem));
                sections.add(new PillBgPreviewAdapter());
            }
            GroupUtils.addGroup(sections, List.of(borderItem));
        } else {
            GroupUtils.addGroup(sections, List.of(gradientSwitch, handlerSwitch, handlerBorderItem, bgItem, borderItem));
        }

        // Preserva la posizione di scroll — senza questo, tornare dal picker foto/preset (che ora
        // rilancia rebuild() anche da onResume()) faceva risalire la lista in cima ad ogni volta,
        // come segnalato: "tornano al menù accensione appena aperto".
        android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                ? mRv.getLayoutManager().onSaveInstanceState() : null;
        mRv.setAdapter(new ConcatAdapter(sections));
        if (scrollState != null && mRv.getLayoutManager() != null) {
            mRv.getLayoutManager().onRestoreInstanceState(scrollState);
        }
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

    /** "Colore Riavvia/Spegni" label — Accento/Personalizzato come prima, oppure il nome della
     *  modalità "Due colori" quando PREF_GRADIENT_MODE=="split" (Riavvia e Spegni indipendenti). */
    private String gradientColorLabel() {
        String mode = ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent");
        if ("split".equals(mode)) return getString(R.string.power_menu_gradient_split_title);
        boolean useAccent = !"custom".equals(mode);
        if (useAccent) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(PREF_GRADIENT_CUSTOM, ObsidianTheme.DEFAULT_ACCENT));
    }

    /** "Colore Riavvia/Spegni" row tap — 3-way Accento/Personalizzato/Due colori chooser
     *  (richiesta esplicita: "aggiungi anche una scelta che comprenda due picker, uno per
     *  Riavvia e uno per Spegni... se attivi questi accento e personalizzato si spengono" — già
     *  garantito by design, dato che è un unico pref stringa con un solo valore alla volta). */
    private void showGradientModeDialog() {
        String[] entries = {
                getString(R.string.color_mode_accent),
                getString(R.string.color_mode_custom),
                getString(R.string.power_menu_gradient_split_title)
        };
        String currentMode = ObsidianPrefs.getString(PREF_GRADIENT_MODE, "accent");
        int current = "split".equals(currentMode) ? 2 : "custom".equals(currentMode) ? 1 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.power_menu_gradient_color_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] == 2) {
                        // Incatena Riavvia→Spegni via onColorSelected — vedi lì per il perché.
                        if (getActivity() instanceof MainActivity) {
                            int currentRestart = ObsidianPrefs.getInt(PREF_GRADIENT_RESTART, 0xFF00BD13);
                            ((MainActivity) getActivity()).showColorPickerDialog(
                                    DIALOG_GRADIENT_RESTART_COLOR, currentRestart, true, true, true);
                        }
                        return;
                    }
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putString(PREF_GRADIENT_MODE, useAccent ? "accent" : "custom");
                    rebuild();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(PREF_GRADIENT_CUSTOM, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(
                                DIALOG_GRADIENT_CUSTOM_COLOR, currentColor, true, true, true);
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
                    if (selected[0] == 2) {
                        // Già in modalità Immagine e la riconfermi: non forzare una nuova scelta
                        // dalla galleria, riapri l'editor — il dialog qui è raggiungibile SOLO
                        // quando l'editor è già chiuso (vedi onRowClick, che ora chiude
                        // direttamente al tap se è aperto), quindi qui basta aprire, mai un toggle.
                        if (BG_MODE_IMAGE.equals(currentMode)) { mBgExpanded = true; rebuild(); return; }
                        pickBgImage();
                        return;
                    }
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
                    if (selected[0] == 2) {
                        // Stessa logica di showBgModeDialog — vedi lì.
                        if (BG_MODE_IMAGE.equals(currentMode)) { mMenuBgExpanded = true; rebuild(); return; }
                        pickMenuBgImage();
                        return;
                    }
                    ObsidianPrefs.putString(PREF_MENU_BG_MODE, selected[0] == 1 ? "custom" : "accent");
                    rebuild();
                    if (selected[0] == 1) openMenuBgColorPicker();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** Same picker/presets as openBgColorPicker(), just for "Colore Pallino"'s own colour pref. */
    private void openHandlerColorPicker() {
        int currentColor = ObsidianPrefs.getInt(PREF_HANDLER_CUSTOM, BG_PRESET_COLORS[0]);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showColorPickerDialog(
                    DIALOG_HANDLER_CUSTOM_COLOR, currentColor, true, true, true, BG_PRESET_COLORS);
        }
    }

    /** "Colore Pallino" row tap — same 3-way Accento/Colore/Immagine chooser as "Sfondo
     *  Pillolone", targeting the draggable thumb's own independent mode/colour/image. */
    private void showHandlerModeDialog() {
        String[] entries = {
                getString(R.string.color_mode_accent),
                getString(R.string.power_menu_bg_mode_custom),
                getString(R.string.color_mode_image)
        };
        String currentMode = ObsidianPrefs.getString(PREF_HANDLER_MODE, "accent");
        int current = "custom".equals(currentMode) ? 1 : BG_MODE_IMAGE.equals(currentMode) ? 2 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.power_menu_handler_color_title)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    if (selected[0] == 2) { navigateToHandlerImagePicker(); return; }
                    ObsidianPrefs.putString(PREF_HANDLER_MODE, selected[0] == 1 ? "custom" : "accent");
                    rebuild();
                    if (selected[0] == 1) openHandlerColorPicker();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    /** "Immagine" per il Pallino — a differenza di Sfondo Pillolone/Menù Power (foto diretta
     *  da galleria), qui apre uno screen dedicato con anche i preset fingerprint_N già inclusi
     *  nell'app (stessa richiesta esplicita dell'utente: "apre le anteprime delle impronte"),
     *  oltre alla scelta di una foto propria. */
    private void navigateToHandlerImagePicker() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(new PowerMenuHandlerPresetFragment(),
                    getString(R.string.power_menu_handler_color_title));
        }
    }

    // ── Anteprima ritaglio — Pillolone/Sfondo Menù Power ────────────────────────────────────
    // Stesso pattern (fixed-box + pannable/zoomable-photo, ImageCropOverlayView) già usato per
    // Immagine sfondo barra volume e Immagine intestazione QS. Aspect ratio APPROSSIMATIVO (non
    // misurato via diagnostica live come per la barra volume/header QS, per cui è stato necessario
    // qualche giro di correzione) — tarato a vista, potrebbe servire un aggiustamento dopo un
    // primo test sul dispositivo reale.
    // 2026-09-16: la misura 234x270 (quasi un cerchio) del 2026-09-15 si è rivelata SBAGLIATA —
    // confronto diretto utente tra preview e pillolone reale (screenshot con righe rosse sopra la
    // foto reale) mostra un ovale molto più stretto e alto. Causa probabile: il log diagnostico
    // "una tantum" ha catturato mBarRectF al PRIMO frame disegnato, cioè a metà dell'animazione di
    // apertura del menù (che parte piccola e si espande) — non la dimensione finale a riposo.
    // Corretto qui a vista sul confronto reale utente (righe rosse); se dovesse servire un'altra
    // misura via log, loggare il rect PIÙ GRANDE visto su più frame, non il primo.
    private static final float PILL_ASPECT = 0.36f;

    private void reloadBgPreviewBitmap() {
        File f = getBgImageFile();
        if (!f.exists()) { mBgPreviewBmp = null; return; }
        mBgPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mBgCropOverlay != null && mBgPreviewBmp != null) mBgCropOverlay.setBitmap(mBgPreviewBmp);
    }

    private void reloadMenuBgPreviewBitmap() {
        File f = getMenuBgImageFile();
        if (!f.exists()) { mMenuBgPreviewBmp = null; return; }
        mMenuBgPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mMenuBgCropOverlay != null && mMenuBgPreviewBmp != null) mMenuBgCropOverlay.setBitmap(mMenuBgPreviewBmp);
    }

    private FrameLayout buildPreviewCard(int heightDp, int cornerRadiusPx, ImageCropOverlayView overlay) {
        FrameLayout card = new FrameLayout(requireContext());
        int hPx = ObsidianTheme.dp(requireContext(), heightDp);
        int mH  = ObsidianTheme.dp(requireContext(), 12);
        int mV  = ObsidianTheme.dp(requireContext(), 6);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, hPx);
        lp.setMargins(mH, mV, mH, mV);
        card.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ObsidianTheme.cardColor());
        bg.setCornerRadius(ObsidianTheme.dp(requireContext(), 16));
        card.setBackground(bg);
        card.setClipToOutline(true);

        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setCornerRadiusPx(cornerRadiusPx);
        card.addView(overlay);
        return card;
    }

    private class PillBgPreviewAdapter extends RecyclerView.Adapter<PillBgPreviewAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageCropOverlayView overlay = new ImageCropOverlayView(parent.getContext());
            // 2026-09-15: card ingrandita a 300dp come Sfondo Menù Power (utente: "fai uguale a
            // Sfondo Menù Power") — MA la forma resta l'ovale/pillola (raggio auto, -1), non un
            // rettangolo: "con 'solo arrotondato sopra e sotto' intendevo ovale". Il rendering
            // REALE sul pillolone non dipende da questo (MiscMods legge il vero raggio/forma
            // live), qui è solo l'editor, più grande ma con la forma giusta.
            FrameLayout card = buildPreviewCard(300, -1, overlay);
            return new VH(card, overlay);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            mBgCropOverlay = h.overlay;
            mBgCropOverlay.setAspect(PILL_ASPECT);
            // A zoom<=100% (riempi base, nessun ritaglio volontario) l'immagine resta ferma su
            // ENTRAMBI gli assi — richiesta esplicita dopo un primo tentativo solo-Y insufficiente
            // ("si muove ancora in entrambe le direzioni"). Oltre il 100% (zoom volontario per
            // scegliere una parte precisa) torna libera su entrambi, vedi ImageCropOverlayView.
            mBgCropOverlay.setLockDrag(true, true);
            mBgCropOverlay.setZoomPercent(ObsidianPrefs.getInt(PREF_BG_CROP_ZOOM, 100));
            mBgCropOverlay.setCropCenter(
                    ObsidianPrefs.getInt(PREF_BG_CROP_CX, 50) / 100f,
                    ObsidianPrefs.getInt(PREF_BG_CROP_CY, 50) / 100f);
            mBgCropOverlay.setOnCropChangedListener((cx, cy) -> {
                ObsidianPrefs.putInt(PREF_BG_CROP_CX, Math.round(cx * 100));
                ObsidianPrefs.putInt(PREF_BG_CROP_CY, Math.round(cy * 100));
            });
            reloadBgPreviewBitmap();
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final ImageCropOverlayView overlay;
            VH(FrameLayout card, ImageCropOverlayView overlay) { super(card); this.overlay = overlay; }
        }
    }

    private class MenuBgPreviewAdapter extends RecyclerView.Adapter<MenuBgPreviewAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageCropOverlayView overlay = new ImageCropOverlayView(parent.getContext());
            // Sfondo Menù Power è lo sfondo dell'intera finestra — rettangolo, non pillola, raggio
            // piccolo come la card che lo contiene (stesso trattamento dell'header QS).
            FrameLayout card = buildPreviewCard(300, ObsidianTheme.dp(requireContext(), 16), overlay);
            return new VH(card, overlay);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            mMenuBgCropOverlay = h.overlay;
            android.util.DisplayMetrics dm = requireContext().getResources().getDisplayMetrics();
            mMenuBgCropOverlay.setAspect(dm.heightPixels > 0 ? (float) dm.widthPixels / dm.heightPixels : 1f);
            // Stesso trattamento del Pillolone: ferma su entrambi gli assi a zoom<=100%, libera
            // solo oltre (zoom volontario) — vedi commento in PillBgPreviewAdapter.
            mMenuBgCropOverlay.setLockDrag(true, true);
            mMenuBgCropOverlay.setZoomPercent(ObsidianPrefs.getInt(PREF_MENU_BG_CROP_ZOOM, 100));
            mMenuBgCropOverlay.setCropCenter(
                    ObsidianPrefs.getInt(PREF_MENU_BG_CROP_CX, 50) / 100f,
                    ObsidianPrefs.getInt(PREF_MENU_BG_CROP_CY, 50) / 100f);
            mMenuBgCropOverlay.setOnCropChangedListener((cx, cy) -> {
                ObsidianPrefs.putInt(PREF_MENU_BG_CROP_CX, Math.round(cx * 100));
                ObsidianPrefs.putInt(PREF_MENU_BG_CROP_CY, Math.round(cy * 100));
            });
            reloadMenuBgPreviewBitmap();
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final ImageCropOverlayView overlay;
            VH(FrameLayout card, ImageCropOverlayView overlay) { super(card); this.overlay = overlay; }
        }
    }
}
