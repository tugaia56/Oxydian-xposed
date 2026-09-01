package it.tugaia56.obsidian.ui.fragments;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SwitchWidgetAdapter;
import it.tugaia56.obsidian.ui.events.ColorSelectedEvent;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.DstFabricatedUtil;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.hooks.framework.DstDialogStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstNotifStyle;
import it.tugaia56.obsidian.xposed.hooks.systemui.DstToastStyle;

/**
 * Temi e Stile — raggruppa: Stili di notifica, Raggio Angolo, Toast, Stile Dialogo.
 */
public class ThemeStyleFragment extends Fragment {

    private static final String PREF_NOTIF_PRESET = "DST_PRESET_NOTIF";
    private static final String PREF_NOTIF_CORNER = "DST_NOTIF_CORNER";
    private static final String PREF_TOAST_PRESET = "DST_PRESET_TOAST";
    private static final String PREF_TOAST_CORNER = "DST_TOAST_CORNER";
    private static final String PREF_DLG_PRESET   = "DST_DLG_PRESET_NAME";
    private static final String PREF_DLG_CORNER   = "DST_DLG_CORNER";

    private static final String[] DLG_STYLE_KEYS = {
        "DSTDHT", "DSTDHTO", "DSTDLT", "DSTDLYO",
        "DSTDMT", "DSTDMTO", "DSTDS",  "DSTDSO"
    };

    // Dialog-style item index in mNavItems (for refresh after picker) — ordine attuale:
    // 0 Stile Notifica, 1 Stile Toast, 2 Stile Dialogo, 3 Regolazioni Varie (ultima, contiene
    // Raggio Notifiche/Toast/Dialogo + Regolazioni Notifiche a texture come card annidate).
    private static final int IDX_DLG = 2;
    private static final String PREF_TEX_SIZE  = "DST_NOTIF_TEXTURE_SIZE";
    private static final String PREF_TEX_ALPHA = "DST_NOTIF_TEXTURE_ALPHA";
    private static final String PREF_TEX_COLOR_MODE    = "DST_NOTIF_TEXTURE_COLOR_MODE";
    private static final String PREF_TEX_COLOR_CUSTOM  = "DST_NOTIF_TEXTURE_COLOR_CUSTOM";
    private static final String PREF_TEX_BORDER_ON     = "DST_NOTIF_TEXTURE_BORDER_ENABLED";
    private static final String PREF_TEX_BORDER_MODE   = "DST_NOTIF_TEXTURE_BORDER_MODE";
    private static final String PREF_TEX_BORDER_CUSTOM = "DST_NOTIF_TEXTURE_BORDER_CUSTOM";
    private static final int DIALOG_TEX_COLOR_CUSTOM  = PREF_TEX_COLOR_CUSTOM.hashCode();
    private static final int DIALOG_TEX_BORDER_CUSTOM = PREF_TEX_BORDER_CUSTOM.hashCode();

    private final List<NavAdapter.NavItem> mNavItems = new ArrayList<>();
    private NavAdapter mNavAdapter;
    /** Se il dialogo "Regolazioni varie" è aperto, questo lo ridisegna dopo che un colore
     *  personalizzato torna dal ColorPickerDialog (altrimenti l'etichetta resterebbe vecchia
     *  finché il dialogo non viene chiuso e riaperto). Null quando il dialogo non è aperto. */
    private Runnable mTextureDialogRefresh;

    private static final String[] NOTIF_OVERLAYS = {
        "DSTNFNTOT", "DSTNFNO25", "DSTNFNO50", "DSTNFNO75",
        "DSTNFNOAC", "DSTNFNST",  "DSTNFNTR",  "DSTNFNPB",
        "DSTNFNMN",  "DSTNFNAS",
        "DSTNFNLYR", "DSTNFNTO2", "DSTNFNBTM", "DSTNFNNM1",
        "DSTNFNSTK", "DSTNFNSS",  "DSTNFNOL4",
        "DSTNFNLT1", "DSTNFNLT2", "DSTNFNLT3", "DSTNFNNM2", "DSTNFNCP1",
        "DSTNFNCP2", "DSTNFNTL",  "DSTNFNFD",  "DSTNFNDB",
        "DSTNFNDL",  "DSTNFNIOS", "DSTNFNDOT", "DSTNFNLNS", "DSTNFNGRN",
        "DSTNFNHRT", "DSTNFNDIA", "DSTNFNCLB", "DSTNFNSPD",
        "DSTNFNCHK", "DSTNFNWAV", "DSTNFNXH",  "DSTNFNIMG"
    };

    private static final String[] TOAST_OVERLAYS = {
        "DSTTST1", "DSTTST2", "DSTTST3",  "DSTTST4",
        "DSTTST5", "DSTTST6", "DSTTST7",  "DSTTST8",
        "DSTTST9", "DSTTST10", "DSTTST11", "DSTTST12"
    };

    private static final int[] CORNER_VALUES = { 2, 4, 8, 12, 16, 20, 24, 28, 32 };
    private static final String NOTIF_BG_IMAGE_FILENAME = "notif_bg_image";

    private ActivityResultLauncher<String> mPickNotifBgImage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        mPickNotifBgImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onNotifBgImagePicked);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onColorSelected(ColorSelectedEvent event) {
        if (event.dialogId() == DIALOG_TEX_COLOR_CUSTOM) {
            ObsidianPrefs.putInt(PREF_TEX_COLOR_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_TEX_COLOR_MODE, "custom");
        } else if (event.dialogId() == DIALOG_TEX_BORDER_CUSTOM) {
            ObsidianPrefs.putInt(PREF_TEX_BORDER_CUSTOM, event.color());
            ObsidianPrefs.putString(PREF_TEX_BORDER_MODE, "custom");
        } else {
            return;
        }
        DstFabricatedUtil.saveBootProps();
        AppUtils.showRestartReminder(requireContext());
        if (mTextureDialogRefresh != null) mTextureDialogRefresh.run();
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
        LayoutAnimationController anim = AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_slide_up);
        rv.setLayoutAnimation(anim);
        return rv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView rv = (RecyclerView) view;

        RecyclerView.Adapter<RecyclerView.ViewHolder> headerAdapter =
                new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @NonNull @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                        View v = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_home_header, parent, false);
                        return new RecyclerView.ViewHolder(v) {};
                    }
                    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {}
                    @Override public int getItemCount() { return 1; }
                };

        String[] notifNames  = getResources().getStringArray(R.array.dst_notif_preset_names);
        String[] cornerNames = getResources().getStringArray(R.array.dst_notif_corner_names);
        String[] toastNames  = getResources().getStringArray(R.array.dst_toast_preset_names);

        mNavItems.clear();
        mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_notifications,
                getString(R.string.nav_notif_style),
                getString(R.string.nav_notif_style_summary),
                () -> showNotifPreviewDialog(getString(R.string.nav_notif_style), notifNames)));

        mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_drawing,
                getString(R.string.nav_toast_style),
                getString(R.string.nav_toast_style_summary),
                () -> showToastPreviewDialog(getString(R.string.nav_toast_style), toastNames)));

        mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_ui_styles,
                getString(R.string.nav_dialog_style),
                getDlgPresetLabel(),
                this::showDialogStylePresetDialog));

        mNavItems.add(new NavAdapter.NavItem(
                R.drawable.ic_ui_styles,
                getString(R.string.nav_misc_settings),
                getString(R.string.nav_misc_settings_summary),
                () -> showMiscSettingsDialog(cornerNames)));

        mNavAdapter = new NavAdapter(mNavItems, 0xFF00BCD4); // cyan, colore categoria "Stili Notifica e Toast"
        rv.setAdapter(new ConcatAdapter(headerAdapter, mNavAdapter));
    }

    // ── "Regolazioni varie" — Dimensione/Opacità/Colore/Bordo per Puntini/Righe/Rumore ──────
    // Riga a sé, stesso stile delle altre card di questa schermata (icona+titolo+summary),
    // apre un proprio dialogo — non più annidato dentro il dialogo dei preset notifica.

    private int texColor() {
        boolean useAccent = !"custom".equals(ObsidianPrefs.getString(PREF_TEX_COLOR_MODE, "accent"));
        return useAccent ? currentAccent()
                : ObsidianPrefs.getInt(PREF_TEX_COLOR_CUSTOM, ObsidianTheme.DEFAULT_ACCENT);
    }

    private boolean texBorderOn() {
        return ObsidianPrefs.getBoolean(PREF_TEX_BORDER_ON, false);
    }

    private int texBorderColor() {
        boolean useAccent = !"custom".equals(ObsidianPrefs.getString(PREF_TEX_BORDER_MODE, "accent"));
        return useAccent ? currentAccent()
                : ObsidianPrefs.getInt(PREF_TEX_BORDER_CUSTOM, ObsidianTheme.DEFAULT_ACCENT);
    }

    private String modeAccentCustomLabel(String modeKey, String customKey) {
        boolean useAccent = !"custom".equals(ObsidianPrefs.getString(modeKey, "accent"));
        if (useAccent) return getString(R.string.color_mode_accent);
        return String.format("#%06X", 0xFFFFFF & ObsidianPrefs.getInt(customKey, ObsidianTheme.DEFAULT_ACCENT));
    }

    /** "Regolazioni Varie" — riunisce in un solo dialogo le 4 impostazioni "secondarie" che
     *  prima erano righe separate nella lista principale: i 3 raggi d'angolo (Notifiche/Toast/
     *  Finestre Dialogo) + "Regolazioni Notifiche a texture" (che apre a sua volta il proprio
     *  dialogo Dimensione/Opacità/Colore/Bordo). Le card restano le stesse di prima, solo
     *  spostate qui dentro invece di essere righe a sé nella lista principale. */
    private void showMiscSettingsDialog(String[] cornerNames) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        int pad = dp(8);
        rv.setPadding(pad, pad, pad, pad);
        rv.setClipToPadding(false);
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListWidgetAdapter.ListItem notifCornerItem = new ListWidgetAdapter.ListItem(
                getString(R.string.nav_notif_corner),
                getString(R.string.nav_notif_corner_summary),
                () -> showCornerPickerDialog(cornerNames));
        notifCornerItem.useAccentColor = false;

        ListWidgetAdapter.ListItem toastCornerItem = new ListWidgetAdapter.ListItem(
                getString(R.string.nav_toast_corner),
                getString(R.string.nav_toast_corner_summary),
                () -> showCornerPickerDialog(cornerNames, PREF_TOAST_CORNER, 24, R.string.nav_toast_corner));
        toastCornerItem.useAccentColor = false;

        ListWidgetAdapter.ListItem dlgCornerItem = new ListWidgetAdapter.ListItem(
                getString(R.string.nav_dlg_corner),
                getString(R.string.nav_dlg_corner_summary),
                () -> showCornerPickerDialog(cornerNames, PREF_DLG_CORNER, 14, R.string.nav_dlg_corner));
        dlgCornerItem.useAccentColor = false;

        ListWidgetAdapter.ListItem textureItem = new ListWidgetAdapter.ListItem(
                getString(R.string.nav_notif_texture_settings),
                getString(R.string.nav_notif_texture_settings_summary),
                this::showTextureSettingsDialog);
        textureItem.useAccentColor = false;

        RecyclerView.Adapter<?> adapter = new ListWidgetAdapter(
                List.of(notifCornerItem, toastCornerItem, dlgCornerItem, textureItem));
        rv.setAdapter(adapter);

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.nav_misc_settings)
                .setView(rv)
                .setPositiveButton(R.string.close, null)
                .show();
        applyDialogBg(dlg);
        fixButtonCaps(dlg);
    }

    private void showTextureSettingsDialog() {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        int pad = dp(8);
        rv.setPadding(pad, pad, pad, pad);
        rv.setClipToPadding(false);
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Runnable[] refreshRef = new Runnable[1];
        refreshRef[0] = () -> rv.setAdapter(buildTextureSettingsAdapter(refreshRef[0]));
        rv.setAdapter(buildTextureSettingsAdapter(refreshRef[0]));

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.nav_notif_texture_settings)
                .setView(rv)
                .setPositiveButton(R.string.close, null)
                .show();
        applyDialogBg(dlg);
        fixButtonCaps(dlg);

        mTextureDialogRefresh = refreshRef[0];
        dlg.setOnDismissListener(d -> mTextureDialogRefresh = null);
    }

    private RecyclerView.Adapter<?> buildTextureSettingsAdapter(Runnable onLiveChange) {
        int sizePct  = ObsidianPrefs.getInt(PREF_TEX_SIZE, 100);
        int alphaPct = ObsidianPrefs.getInt(PREF_TEX_ALPHA, 25);

        SliderWidgetAdapter.SliderItem sizeItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.nav_notif_texture_size), sizePct, 50, 200, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_TEX_SIZE, value);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                });
        sizeItem.nested = true;
        sizeItem.groupPos = ObsidianTheme.GroupPos.TOP;

        SliderWidgetAdapter.SliderItem alphaItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.nav_notif_texture_alpha), alphaPct, 5, 70, "%", 25,
                value -> {
                    ObsidianPrefs.putInt(PREF_TEX_ALPHA, value);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                });
        alphaItem.nested = true;
        alphaItem.groupPos = ObsidianTheme.GroupPos.BOTTOM;

        SliderWidgetAdapter sliders = new SliderWidgetAdapter(List.of(sizeItem, alphaItem));

        // Colore — sempre accento o personalizzato, nessuno stato "stock" (stesso pattern di
        // "Colore Pulsante" nel Menù Accensione).
        ListWidgetAdapter.ListItem colorItem = new ListWidgetAdapter.ListItem(
                getString(R.string.notif_texture_color_title),
                modeAccentCustomLabel(PREF_TEX_COLOR_MODE, PREF_TEX_COLOR_CUSTOM),
                () -> showTexModeDialog(PREF_TEX_COLOR_MODE, PREF_TEX_COLOR_CUSTOM,
                        DIALOG_TEX_COLOR_CUSTOM, R.string.notif_texture_color_title, onLiveChange));
        ListWidgetAdapter colorAdapter = new ListWidgetAdapter(List.of(colorItem));

        // Bordo — switch di attivazione + accento/personalizzato, stesso pattern di "Bordo
        // Pillolone" nel Menù Accensione (switch ON apre subito il dialogo colore).
        SwitchWidgetAdapter.SwitchItem borderItem = new SwitchWidgetAdapter.SwitchItem(
                getString(R.string.notif_texture_border_title),
                texBorderOn()
                        ? modeAccentCustomLabel(PREF_TEX_BORDER_MODE, PREF_TEX_BORDER_CUSTOM)
                        : getString(R.string.notif_texture_border_summary),
                texBorderOn(),
                null);
        borderItem.onChanged = () -> {
            ObsidianPrefs.putBoolean(PREF_TEX_BORDER_ON, borderItem.checked);
            DstFabricatedUtil.saveBootProps();
            AppUtils.showRestartReminder(requireContext());
            if (onLiveChange != null) onLiveChange.run();
            if (borderItem.checked) showTexModeDialog(PREF_TEX_BORDER_MODE, PREF_TEX_BORDER_CUSTOM,
                    DIALOG_TEX_BORDER_CUSTOM, R.string.notif_texture_border_title, onLiveChange);
        };
        borderItem.onRowClick = () -> {
            if (texBorderOn()) showTexModeDialog(PREF_TEX_BORDER_MODE, PREF_TEX_BORDER_CUSTOM,
                    DIALOG_TEX_BORDER_CUSTOM, R.string.notif_texture_border_title, onLiveChange);
        };
        SwitchWidgetAdapter borderAdapter = new SwitchWidgetAdapter(List.of(borderItem));

        return new ConcatAdapter(sliders, colorAdapter, borderAdapter);
    }

    /** 2-way Accento/Personalizzato — stesso dialogo usato per i colori del Menù Accensione. */
    private void showTexModeDialog(String modeKey, String customKey, int dialogId, int titleResId,
                                    Runnable onLiveChange) {
        String[] entries = { getString(R.string.color_mode_accent), getString(R.string.color_mode_custom) };
        String currentMode = ObsidianPrefs.getString(modeKey, "accent");
        int current = "custom".equals(currentMode) ? 1 : 0;
        final int[] selected = {current};
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setSingleChoiceItems(entries, current, (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    boolean useAccent = selected[0] == 0;
                    ObsidianPrefs.putString(modeKey, useAccent ? "accent" : "custom");
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                    if (onLiveChange != null) onLiveChange.run();
                    if (!useAccent && getActivity() instanceof MainActivity) {
                        int currentColor = ObsidianPrefs.getInt(customKey, ObsidianTheme.DEFAULT_ACCENT);
                        ((MainActivity) getActivity()).showColorPickerDialog(dialogId, currentColor, true, true, true);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    // ── Notification / Toast preset picker — griglia con anteprima reale ───────
    // Usa lo stesso drawable costruito a runtime dall'hook (DstNotifStyle.buildNotifBg /
    // DstToastStyle.buildToastBg), non un'approssimazione.

    private int currentAccent() {
        boolean a1On = ObsidianPrefs.getBoolean("DST_ACCENT1_on", false);
        return a1On ? ObsidianPrefs.getInt("DST_ACCENT1", 0xFFFFFFFF) : 0xFFFFFFFF;
    }

    /** Il colore "Sfondo" REALE del device (DST_BACKGROUND, di default il navy scuro storico),
     *  usato per costruire le anteprime dei preset reali (Notifiche/Toast/Stile Dialogo) — NON
     *  deve seguire il Tema dell'app: preset come "Scuro"/"Scuro con bordo" derivano il loro
     *  stesso nome da questo default, e mostrarli chiari in Tema Chiaro li contraddirebbe. Resta
     *  un asse indipendente dal Tema, alla pari di DST_ACCENT1/il resto delle impostazioni DST. */
    private int currentBg() {
        boolean bgOn = ObsidianPrefs.getBoolean("DST_BACKGROUND_on", false);
        return bgOn ? ObsidianPrefs.getInt("DST_BACKGROUND", 0xFF1B2029) : 0xFF1B2029;
    }

    private void showNotifPreviewDialog(String title, String[] names) {
        int accent = currentAccent();
        int bg = currentBg();
        int cornerDp = ObsidianPrefs.getInt(PREF_NOTIF_CORNER, 24);
        float density = getResources().getDisplayMetrics().density;

        showPresetPreviewDialog(title, names, PREF_NOTIF_PRESET, NOTIF_OVERLAYS,
                preset -> DstNotifStyle.buildNotifBg(preset, accent, bg, density, cornerDp,
                        ObsidianPrefs.getInt(PREF_TEX_SIZE, 100), ObsidianPrefs.getInt(PREF_TEX_ALPHA, 25),
                        texColor(), texBorderOn(), texBorderColor()),
                1, 64, idx -> {
            // "Immagine" non si applica subito come gli altri preset: prima serve scegliere
            // davvero una foto. Se l'utente annulla il picker, il preset resta quello di prima
            // (stesso pattern "safe cancel" di PowerMenuHandlerPresetFragment).
            if (idx >= 0 && "DSTNFNIMG".equals(NOTIF_OVERLAYS[idx])) {
                mPickNotifBgImage.launch("image/*");
                return;
            }
            if (idx < 0) ObsidianPrefs.remove(PREF_NOTIF_PRESET);
            else ObsidianPrefs.putString(PREF_NOTIF_PRESET, NOTIF_OVERLAYS[idx]);
            DstFabricatedUtil.saveBootProps();
            AppUtils.showRestartReminder(requireContext());
        });
    }

    /** Copia la foto scelta in .obsidian/notif_bg_image e attiva il preset "Immagine" solo se
     *  la copia va a buon fine — annullare il picker di sistema lascia il preset precedente
     *  invariato, stesso pattern di PowerMenuHandlerPresetFragment.onImagePicked(). */
    private void onNotifBgImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            File dest = getNotifBgImageFile();
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            ObsidianPrefs.putString(PREF_NOTIF_PRESET, "DSTNFNIMG");
            DstFabricatedUtil.saveBootProps();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    private File getNotifBgImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + NOTIF_BG_IMAGE_FILENAME);
    }

    private void showToastPreviewDialog(String title, String[] names) {
        int accent = currentAccent();
        int bg = currentBg();
        int cornerDp = ObsidianPrefs.getInt(PREF_TOAST_CORNER, 24);
        float density = getResources().getDisplayMetrics().density;

        showPresetPreviewDialog(title, names, PREF_TOAST_PRESET, TOAST_OVERLAYS,
                preset -> DstToastStyle.buildToastBg(preset, accent, bg, density, cornerDp),
                1, 56, idx -> {
            if (idx < 0) ObsidianPrefs.remove(PREF_TOAST_PRESET);
            else ObsidianPrefs.putString(PREF_TOAST_PRESET, TOAST_OVERLAYS[idx]);
            DstFabricatedUtil.saveBootProps();
            AppUtils.showRestartReminder(requireContext());
        });
    }

    private interface DrawableForPreset {
        Drawable build(String presetKey);
    }

    /** Griglia 2 colonne, celle quadrate: "Nessuno" + una cella per preset. Usata da Dialogo. */
    private void showPresetPreviewDialog(String title, String[] names, String prefKey,
                                          String[] overlayKeys, DrawableForPreset drawableFactory) {
        showPresetPreviewDialog(title, names, prefKey, overlayKeys, drawableFactory, 2, 120, idx -> {
            if (idx < 0) ObsidianPrefs.remove(prefKey);
            else ObsidianPrefs.putString(prefKey, overlayKeys[idx]);
            DstFabricatedUtil.saveBootProps();
            AppUtils.showRestartReminder(requireContext());
        });
    }

    /** Variante con callback di applicazione personalizzato (es. refresh riga + thread). */
    private void showPresetPreviewDialog(String title, String[] names, String prefKey,
                                          String[] overlayKeys, DrawableForPreset drawableFactory,
                                          IntConsumer onApply) {
        showPresetPreviewDialog(title, names, prefKey, overlayKeys, drawableFactory, 2, 120, onApply);
    }

    /** Variante completa: {@code columns}/{@code swatchHeightDp} — notifiche e toast usano
     *  1 colonna e celle basse/larghe (rettangolari) per somigliare alla forma reale, invece
     *  della griglia 2 colonne quadrata usata dal Dialogo. */
    private void showPresetPreviewDialog(String title, String[] names, String prefKey,
                                          String[] overlayKeys, DrawableForPreset drawableFactory,
                                          int columns, int swatchHeightDp, IntConsumer onApply) {
        String current = ObsidianPrefs.getString(prefKey, null);

        RecyclerView grid = new RecyclerView(requireContext());
        grid.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        int pad = dp(8);
        grid.setPadding(pad, pad, pad, pad);
        grid.setClipToPadding(false);
        grid.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog[] dlgRef = new AlertDialog[1];
        PresetPreviewAdapter[] adapterRef = new PresetPreviewAdapter[1];
        // Resta aperto dopo la scelta: l'utente confronta più preset di seguito senza dover
        // riaprire il dialogo ogni volta — si chiude solo con "Esci".
        IntConsumer onPick = idx -> {
            onApply.accept(idx);
            if (adapterRef[0] != null) adapterRef[0].setSelected(idx);
        };
        PresetPreviewAdapter adapter = new PresetPreviewAdapter(names, overlayKeys, current, drawableFactory, onPick, swatchHeightDp);
        adapterRef[0] = adapter;
        grid.setAdapter(adapter);

        AlertDialog dlg = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(grid)
                .setNeutralButton(R.string.restart_systemui, null)
                .setNegativeButton(R.string.close, null)
                .show();
        dlgRef[0] = dlg;
        applyDialogBg(dlg);
        fixButtonCaps(dlg);

        // Override del listener di default: i bottoni di AlertDialog chiudono il dialogo dopo
        // il click, ma qui serve il contrario — l'anteprima deve restare visibile DURANTE e
        // DOPO il riavvio, per vedere subito l'effetto reale senza riaprire il dialogo.
        Button restartBtn = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (restartBtn != null) {
            restartBtn.setOnClickListener(v -> AppUtils.restartSystemUI(requireContext()));
        }

        // Finestra quasi a schermo intero: anteprime più grandi e leggibili invece di
        // rimpicciolirsi al contenuto minimo.
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            w.setLayout(Math.round(dm.widthPixels * 0.94f), Math.round(dm.heightPixels * 0.88f));
        }
    }

    /** Cella: "Nessuno" a indice -1, poi una per ogni preset — ognuna con l'anteprima reale. */
    private class PresetPreviewAdapter extends RecyclerView.Adapter<PresetPreviewAdapter.VH> {
        private final String[] mNames;
        private final String[] mOverlayKeys;
        private int mSelectedIdx;
        private final DrawableForPreset mDrawableFactory;
        private final IntConsumer mOnPick;
        private final int mSwatchHeightDp;

        PresetPreviewAdapter(String[] names, String[] overlayKeys, String current,
                              DrawableForPreset drawableFactory, IntConsumer onPick,
                              int swatchHeightDp) {
            mNames = names;
            mOverlayKeys = overlayKeys;
            mDrawableFactory = drawableFactory;
            mOnPick = onPick;
            mSwatchHeightDp = swatchHeightDp;
            int idx = -1;
            for (int i = 0; i < overlayKeys.length; i++) {
                if (overlayKeys[i].equals(current)) { idx = i; break; }
            }
            mSelectedIdx = idx;
        }

        /** Aggiorna l'evidenziazione dopo una scelta, senza chiudere il dialogo. */
        void setSelected(int idx) {
            mSelectedIdx = idx;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout swatch = new FrameLayout(parent.getContext());
            swatch.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(mSwatchHeightDp)));

            TextView label = new TextView(parent.getContext());
            label.setTextSize(14);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            label.setLayoutParams(labelLp);
            swatch.addView(label);

            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            int padH = dp(6), padV = dp(6);
            root.setPadding(padH, padV, padH, padV);
            root.addView(swatch);

            RecyclerView.LayoutParams rootLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = dp(4);
            rootLp.setMargins(m, m, m, m);
            root.setLayoutParams(rootLp);

            return new VH(root, swatch, label);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            int idx = pos - 1; // pos 0 = "Nessuno"
            boolean selected = idx == mSelectedIdx;
            int accent = ObsidianTheme.accentColor();

            GradientDrawable outerBg = new GradientDrawable();
            outerBg.setColor(ObsidianTheme.cardColor());
            outerBg.setCornerRadius(dp(10));
            if (selected) outerBg.setStroke(dp(2), accent);
            h.itemView.setBackground(outerBg);

            if (idx < 0) {
                h.swatch.setBackground(null);
                h.label.setText(R.string.dst_none);
                h.label.setTextColor(selected ? accent : ObsidianTheme.textColor(0xCC));
            } else {
                try {
                    Drawable d = mDrawableFactory.build(mOverlayKeys[idx]);
                    h.swatch.setBackground(d);
                } catch (Throwable ignored) {
                    h.swatch.setBackground(null);
                }
                h.label.setText(idx < mNames.length ? mNames[idx] : mOverlayKeys[idx]);
                h.label.setTextColor(0xFFFFFFFF);
                h.label.setShadowLayer(3f, 0f, 0f, 0xFF000000);
            }

            h.itemView.setOnClickListener(v -> mOnPick.accept(idx));
        }

        @Override public int getItemCount() { return mOverlayKeys.length + 1; }

        class VH extends RecyclerView.ViewHolder {
            final FrameLayout swatch;
            final TextView label;
            VH(View v, FrameLayout swatch, TextView label) {
                super(v);
                this.swatch = swatch;
                this.label = label;
            }
        }
    }

    // ── Corner radius picker ──────────────────────────────────────────────────

    private void showCornerPickerDialog(String[] names) {
        showCornerPickerDialog(names, PREF_NOTIF_CORNER, 24, R.string.nav_notif_corner);
    }

    /** Generico — riusato anche per "Raggio Angolo Toast" e "Raggio Finestre Dialogo", stesso
     *  elenco di valori (2..32dp), solo pref key/default/titolo cambiano. */
    private void showCornerPickerDialog(String[] names, String prefKey, int defaultDp, int titleResId) {
        int currentDp = ObsidianPrefs.getInt(prefKey, defaultDp);
        int currentIdx = 0;
        for (int i = 0; i < CORNER_VALUES.length; i++) {
            if (CORNER_VALUES[i] == currentDp) { currentIdx = i; break; }
            if (CORNER_VALUES[i] == defaultDp) currentIdx = i; // fallback se currentDp non combacia
        }

        final int[] selected = {currentIdx};
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleResId)
                .setSingleChoiceItems(names, currentIdx,
                        (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(prefKey, CORNER_VALUES[selected[0]]);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                })
                .setNeutralButton(R.string.reset, (d, w) -> {
                    ObsidianPrefs.remove(prefKey);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                })
                .setNegativeButton(R.string.close, null)
                .show();
        applyDialogBg(dialog);
        fixButtonCaps(dialog);
    }

    // ── Dialog Window Style preset ────────────────────────────────────────────

    private void showDialogStylePresetDialog() {
        String[] names = {
            getString(R.string.dst_dlg_ht),
            getString(R.string.dst_dlg_hto),
            getString(R.string.dst_dlg_lt),
            getString(R.string.dst_dlg_lyo),
            getString(R.string.dst_dlg_mt),
            getString(R.string.dst_dlg_mto),
            getString(R.string.dst_dlg_s),
            getString(R.string.dst_dlg_so),
        };

        int accent = currentAccent();
        int bg = currentBg();
        int cornerDp = ObsidianPrefs.getInt(PREF_DLG_CORNER, 14);
        float density = getResources().getDisplayMetrics().density;

        showPresetPreviewDialog(getString(R.string.nav_dialog_style), names, PREF_DLG_PRESET,
                DLG_STYLE_KEYS,
                preset -> DstDialogStyle.buildPreviewDrawable(preset, accent, bg, density, cornerDp),
                idx -> {
                    if (idx < 0) ObsidianPrefs.remove(PREF_DLG_PRESET);
                    else ObsidianPrefs.putString(PREF_DLG_PRESET, DLG_STYLE_KEYS[idx]);
                    refreshDlgRow();
                    new Thread(() -> {
                        DstFabricatedUtil.saveBootProps();
                        AppUtils.showRestartReminder(requireContext());
                    }).start();
                });
    }

    private String getDlgPresetLabel() {
        String saved = ObsidianPrefs.getString(PREF_DLG_PRESET, null);
        if (saved == null) return getString(R.string.dst_none);
        int[] nameRes = {
            R.string.dst_dlg_ht,  R.string.dst_dlg_hto,
            R.string.dst_dlg_lt,  R.string.dst_dlg_lyo,
            R.string.dst_dlg_mt,  R.string.dst_dlg_mto,
            R.string.dst_dlg_s,   R.string.dst_dlg_so,
        };
        for (int i = 0; i < DLG_STYLE_KEYS.length; i++) {
            if (DLG_STYLE_KEYS[i].equals(saved)) return getString(nameRes[i]);
        }
        return saved;
    }

    private void refreshDlgRow() {
        if (mNavAdapter == null || mNavItems.size() <= IDX_DLG || !isAdded()) return;
        mNavItems.set(IDX_DLG, new NavAdapter.NavItem(
                R.drawable.ic_ui_styles,
                getString(R.string.nav_dialog_style),
                getDlgPresetLabel(),
                this::showDialogStylePresetDialog));
        mNavAdapter.notifyItemChanged(IDX_DLG);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static void fixButtonCaps(AlertDialog d) {
        Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neu = d.getButton(AlertDialog.BUTTON_NEUTRAL);
        // Forcing minWidth to 0 (previous approach) let all 3 buttons always fit on one row,
        // but squeezed "Disabilita" so hard it broke mid-word ("Disabilt" / "a") instead of
        // wrapping at a word boundary. Leaving the natural min-width in place lets Android's
        // own button bar fall back to stacking the buttons vertically when they don't fit —
        // each one then gets full width and reads normally.
        for (Button b : new Button[]{pos, neg, neu}) {
            if (b == null) continue;
            b.setAllCaps(false);
            b.setSingleLine(false);
            b.setMaxLines(2);
            b.setEllipsize(null);
        }
    }

    private void applyDialogBg(AlertDialog d) {
        android.view.Window w = d.getWindow();
        if (w == null) return;
        String preset = ObsidianPrefs.getString(PREF_DLG_PRESET, null);
        android.graphics.drawable.Drawable bg;
        if (preset != null) {
            float density = getResources().getDisplayMetrics().density;
            int cornerDp = ObsidianPrefs.getInt(PREF_DLG_CORNER, 14);
            bg = DstDialogStyle.buildDrawable(preset, currentAccent(), currentBg(), density, cornerDp);
        } else {
            bg = ObsidianTheme.dialogBackground(requireContext());
        }
        w.setBackgroundDrawable(bg);
    }
}
