package it.tugaia56.obsidian.ui.fragments;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.activity.MainActivity;
import it.tugaia56.obsidian.ui.adapters.NavAdapter;
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
    private static final String PREF_DLG_PRESET   = "DST_DLG_PRESET_NAME";

    private static final String[] DLG_STYLE_KEYS = {
        "DSTDHT", "DSTDHTO", "DSTDLT", "DSTDLYO",
        "DSTDMT", "DSTDMTO", "DSTDS",  "DSTDSO"
    };

    // Dialog-style item index in mNavItems (for refresh after picker)
    private static final int IDX_DLG = 3;

    private final List<NavAdapter.NavItem> mNavItems = new ArrayList<>();
    private NavAdapter mNavAdapter;

    private static final String[] NOTIF_OVERLAYS = {
        "DSTNFNTOT", "DSTNFNO25", "DSTNFNO50", "DSTNFNO75",
        "DSTNFNOAC", "DSTNFNST",  "DSTNFNTR",  "DSTNFNPB",
        "DSTNFNMN",  "DSTNFNAS",
        "DSTNFNLYR", "DSTNFNTO2", "DSTNFNBTM", "DSTNFNNM1",
        "DSTNFNSTK", "DSTNFNSS",  "DSTNFNOL4",
        "DSTNFNLT1", "DSTNFNLT2", "DSTNFNLT3", "DSTNFNNM2", "DSTNFNCP1",
        "DSTNFNCP2", "DSTNFNTL",  "DSTNFNFD",  "DSTNFNDB",
        "DSTNFNDL",  "DSTNFNIOS", "DSTNFNDOT"
    };

    private static final String[] TOAST_OVERLAYS = {
        "DSTTST1", "DSTTST2", "DSTTST3",  "DSTTST4",
        "DSTTST5", "DSTTST6", "DSTTST7",  "DSTTST8",
        "DSTTST9", "DSTTST10", "DSTTST11", "DSTTST12"
    };

    private static final int[] CORNER_VALUES = { 8, 12, 16, 20, 24, 28, 32 };

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
                R.drawable.ic_ui_styles,
                getString(R.string.nav_notif_corner),
                getString(R.string.nav_notif_corner_summary),
                () -> showCornerPickerDialog(cornerNames)));

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

        mNavAdapter = new NavAdapter(mNavItems, 0xFF00BCD4); // cyan, colore categoria "Stili Notifica e Toast"
        rv.setAdapter(new ConcatAdapter(headerAdapter, mNavAdapter));
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
                preset -> DstNotifStyle.buildNotifBg(preset, accent, bg, density, cornerDp),
                1, 64, idx -> {
            if (idx < 0) ObsidianPrefs.remove(PREF_NOTIF_PRESET);
            else ObsidianPrefs.putString(PREF_NOTIF_PRESET, NOTIF_OVERLAYS[idx]);
            DstFabricatedUtil.saveBootProps();
            AppUtils.showRestartReminder(requireContext());
        });
    }

    private void showToastPreviewDialog(String title, String[] names) {
        int accent = currentAccent();
        int bg = currentBg();
        float density = getResources().getDisplayMetrics().density;

        showPresetPreviewDialog(title, names, PREF_TOAST_PRESET, TOAST_OVERLAYS,
                preset -> DstToastStyle.buildToastBg(preset, accent, bg, density),
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
        int currentDp = ObsidianPrefs.getInt(PREF_NOTIF_CORNER, 24);
        int currentIdx = 4; // default index for 24dp
        for (int i = 0; i < CORNER_VALUES.length; i++) {
            if (CORNER_VALUES[i] == currentDp) { currentIdx = i; break; }
        }

        final int[] selected = {currentIdx};
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.nav_notif_corner)
                .setSingleChoiceItems(names, currentIdx,
                        (d, which) -> selected[0] = which)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ObsidianPrefs.putInt(PREF_NOTIF_CORNER, CORNER_VALUES[selected[0]]);
                    DstFabricatedUtil.saveBootProps();
                    AppUtils.showRestartReminder(requireContext());
                })
                .setNeutralButton(R.string.reset, (d, w) -> {
                    ObsidianPrefs.remove(PREF_NOTIF_CORNER);
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
        float density = getResources().getDisplayMetrics().density;

        showPresetPreviewDialog(getString(R.string.nav_dialog_style), names, PREF_DLG_PRESET,
                DLG_STYLE_KEYS,
                preset -> DstDialogStyle.buildPreviewDrawable(preset, accent, bg, density),
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
            bg = DstDialogStyle.buildDrawable(preset, currentAccent(), currentBg(), density);
        } else {
            bg = ObsidianTheme.dialogBackground(requireContext());
        }
        w.setBackgroundDrawable(bg);
    }
}
