package it.tugaia56.obsidian.ui.fragments;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.ui.adapters.ListWidgetAdapter;
import it.tugaia56.obsidian.ui.adapters.SectionTitleAdapter;
import it.tugaia56.obsidian.ui.adapters.SliderWidgetAdapter;
import it.tugaia56.obsidian.ui.widgets.ImageCropOverlayView;
import it.tugaia56.obsidian.utils.AppUtils;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;

/**
 * "Immagine" per il Pallino del Menù Accensione — stessa idea di FingerprintPresetFragment
 * (griglia dei preset fingerprint_N già inclusi nell'app, riusati qui come forme decorative
 * circolari pronte) PLUS una riga in cima per scegliere una foto propria dalla galleria —
 * unico screen invece di due, dato che qui non serve nemmeno il toggle remove/custom di
 * FingerprintIconFragment (l'attivazione è già lo switch "Colore Pallino" sulla schermata
 * precedente). Il preset scelto viene renderizzato in bitmap e salvato nello stesso file PNG
 * che una foto da galleria userebbe — MiscMods.java non distingue le due origini.
 */
public class PowerMenuHandlerPresetFragment extends Fragment {

    /** fingerprint_74..89: preset da un pacchetto animazioni impronta PUI, 2026-09-18. */
    private static final java.util.Set<Integer> FINGERPRINT_CREDIT_RANGE =
            new java.util.HashSet<>(java.util.Arrays.asList(74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89));

    private static final String PREF_HANDLER_MODE = "power_menu_handler_mode";
    private static final String HANDLER_IMAGE_FILENAME = "power_menu_handler_image";
    private static final String BG_MODE_IMAGE = "image";
    /** Ritaglio (cx/cy/zoom) — stessa convenzione di ImageCropOverlayView/QsHeaderImage, vedi
     *  MiscMods.customCropSrcRect(). Mostrato per qualunque immagine attiva (foto o preset), a
     *  50/50/100 (default) riproduce il vecchio comportamento sempre-centrato senza differenze. */
    private static final String PREF_HANDLER_CROP_CX   = "power_menu_handler_crop_cx";
    private static final String PREF_HANDLER_CROP_CY   = "power_menu_handler_crop_cy";
    private static final String PREF_HANDLER_CROP_ZOOM = "power_menu_handler_crop_zoom";

    private ActivityResultLauncher<String> mPickImage;
    private Adapter mAdapter;
    private int mCurrentStyle = -2; // -2 = nessuno evidenziato (foto propria attiva)
    private Bitmap mCropPreviewBmp;
    private ImageCropOverlayView mCropOverlay;
    /** 2026-09-17: scegliere un preset lontano nella griglia (es. "Impronta digitale 68")
     *  lasciava lo scroll dov'era — l'anteprima (vicino in cima) restava fuori vista, serviva
     *  scorrere a mano ("bisogna scorrere troppo"). Quando true, il prossimo rebuild() scorre in
     *  cima invece di preservare la posizione — impostato subito prima di scegliere una nuova
     *  immagine (preset o galleria), NON durante un rebuild qualsiasi (es. zoom in diretta). */
    private boolean mScrollToTopOnNextRebuild = false;
    /** Indici reali dei preset nell'ultima rebuild() — serve a scrollToActiveStyle() per sapere a
     *  quale posizione "piatta" nella ConcatAdapter corrisponde il preset attivo. */
    private List<Integer> mStyles = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onImagePicked);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setPadding(0, 8, 0, 24);
        rv.setClipToPadding(false);
        return rv;
    }

    private RecyclerView mRv;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRv = (RecyclerView) view;
        rebuild();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkExternalImageChange();
        if (mRv != null) rebuild();
    }

    private void rebuild() {
        ListWidgetAdapter.ListItem galleryItem = new ListWidgetAdapter.ListItem(
                getString(R.string.power_menu_handler_gallery_title),
                getString(R.string.power_menu_handler_gallery_summary),
                () -> mPickImage.launch("image/*"));
        ListWidgetAdapter galleryAdapter = new ListWidgetAdapter(List.of(galleryItem));

        // "Scala Immagine" rimossa 2026-09-15 — sovrapposta a "Zoom" (che sotto 100% rimpicciolisce
        // già l'immagine visibile), richiesta esplicita: "se fanno la stessa cosa, togli Scala
        // immagine". "Zoom" resta l'unico controllo di dimensione, subito qui come prima.
        boolean isImage = BG_MODE_IMAGE.equals(ObsidianPrefs.getString(PREF_HANDLER_MODE, "accent"))
                && getImageFile().exists();
        // 2026-09-17: range ridotto a 20-100 (era 20-300) — il Pallino ora non ritaglia MAI
        // (richiesta esplicita: "mai tagliare, mostra tutta l'immagine"), quindi oltre 100% non
        // avrebbe più senso/effetto — vedi ImageCropOverlayView.setContainOnly.
        SliderWidgetAdapter.SliderItem cropZoomItem = new SliderWidgetAdapter.SliderItem(
                getString(R.string.qs_header_zoom),
                Math.min(100, ObsidianPrefs.getInt(PREF_HANDLER_CROP_ZOOM, 100)),
                20, 100, "%", 100,
                value -> {
                    ObsidianPrefs.putInt(PREF_HANDLER_CROP_ZOOM, value);
                    if (mCropOverlay != null) mCropOverlay.setZoomPercent(value);
                });
        cropZoomItem.onLivePreview = value -> { if (mCropOverlay != null) mCropOverlay.setZoomPercent(value); };

        List<SliderWidgetAdapter.SliderItem> sliderRows = new ArrayList<>();
        if (isImage) sliderRows.add(cropZoomItem);
        SliderWidgetAdapter scaleAdapter = new SliderWidgetAdapter(sliderRows);

        List<Integer> styles = new ArrayList<>();
        Resources res = requireContext().getResources();
        String pkg = requireContext().getPackageName();
        for (int i = 0; ; i++) {
            if (res.getIdentifier("fingerprint_" + i, "drawable", pkg) == 0) break;
            styles.add(i);
        }
        mStyles = styles;
        mAdapter = new Adapter(styles);

        List<RecyclerView.Adapter<?>> chain = new ArrayList<>();
        chain.add(galleryAdapter);
        chain.add(scaleAdapter);
        if (isImage) chain.add(new CropPreviewAdapter());
        chain.add(new SectionTitleAdapter(List.of(getString(R.string.power_menu_handler_style_section))));
        chain.add(mAdapter);
        if (mScrollToTopOnNextRebuild) {
            mScrollToTopOnNextRebuild = false;
            mRv.setAdapter(new ConcatAdapter(chain));
            mRv.scrollToPosition(0);
        } else {
            // Preserva la posizione di scroll — stesso motivo di PowerMenuFragment.rebuild().
            android.os.Parcelable scrollState = mRv.getLayoutManager() != null
                    ? mRv.getLayoutManager().onSaveInstanceState() : null;
            mRv.setAdapter(new ConcatAdapter(chain));
            if (scrollState != null && mRv.getLayoutManager() != null) {
                mRv.getLayoutManager().onRestoreInstanceState(scrollState);
            }
        }
    }

    // ── Foto propria da galleria ─────────────────────────────────────────────

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        try {
            File dest = getImageFile();
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return;
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            logImageWrite("gallery pick");
            ObsidianPrefs.putString(PREF_HANDLER_MODE, BG_MODE_IMAGE);
            mCurrentStyle = -1;
            // Foto nuova: il riquadro di una foto precedente non ha senso, riparte centrato
            // senza zoom (stesso pattern degli altri crop di questa sessione).
            ObsidianPrefs.putInt(PREF_HANDLER_CROP_CX, 50);
            ObsidianPrefs.putInt(PREF_HANDLER_CROP_CY, 50);
            ObsidianPrefs.putInt(PREF_HANDLER_CROP_ZOOM, 100);
            mScrollToTopOnNextRebuild = true;
            rebuild();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Preset (stessi fingerprint_N già inclusi nell'app) ──────────────────

    /** Un tocco sbagliato su una riga della lista (o durante lo scorrimento) sovrascriveva in
     *  silenzio l'immagine scelta - ora chiede conferma quando c'e' gia' un'immagine attiva. */
    private void confirmApplyStyle(int index) {
        boolean hasImage = BG_MODE_IMAGE.equals(ObsidianPrefs.getString(PREF_HANDLER_MODE, "accent"))
                && getImageFile().exists();
        if (!hasImage) { applyStyle(index); return; }
        if (index == mCurrentStyle) return;
        ObsidianTheme.themeDialog(new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.power_menu_handler_replace_title)
                .setMessage(R.string.power_menu_handler_replace_message)
                .setPositiveButton(R.string.apply, (d, w) -> applyStyle(index))
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void applyStyle(int index) {
        try {
            int resId = requireContext().getResources().getIdentifier(
                    "fingerprint_" + index, "drawable", requireContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(requireContext(), resId);
            if (d == null) return;

            // 2026-09-17: forzare SEMPRE un canvas quadrato (setBounds(0,0,size,size)) schiacciava
            // i preset non quadrati (es. fingerprint_68.webp è 216x298, non 216x216) — lo zoom a
            // valle non c'entrava, il file salvato era già distorto in partenza. Preserva l'aspect
            // ratio reale del drawable, scalato per stare entro maxDim su entrambi i lati (il
            // sistema di ritaglio/zoom condiviso gestisce già correttamente sorgenti non quadrate).
            int maxDim = Math.max(1, ObsidianTheme.dp(requireContext(), 216));
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = maxDim; ih = maxDim; }
            float scale = (float) maxDim / Math.max(iw, ih);
            int w = Math.max(1, Math.round(iw * scale));
            int h = Math.max(1, Math.round(ih * scale));
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, w, h);
            d.draw(canvas);

            File dest = getImageFile();
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(dest)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            logImageWrite("preset fingerprint_" + index);
            ObsidianPrefs.putString(PREF_HANDLER_MODE, BG_MODE_IMAGE);
            mCurrentStyle = index;
            ObsidianPrefs.putInt(PREF_HANDLER_CROP_CX, 50);
            ObsidianPrefs.putInt(PREF_HANDLER_CROP_CY, 50);
            ObsidianPrefs.putInt(PREF_HANDLER_CROP_ZOOM, 100);
            mScrollToTopOnNextRebuild = true;
            rebuild();
            AppUtils.showRestartReminder(requireContext());
        } catch (Throwable t) {
            Toast.makeText(requireContext(), R.string.qs_header_pick_error, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Diagnostica temporanea: chi riscrive l'immagine del Pallino (2026-09-19) ──────────────
    // Ogni scrittura fatta da questa schermata finisce in .obsidian/handler_image_log.txt; se
    // all'apertura la data del file non corrisponde all'ultima scrittura registrata, e' cambiato
    // per un'altra strada ("EXTERNAL CHANGE"). Da rimuovere quando la causa e' chiara.
    private static final String PREF_LAST_WRITE_MTIME = "power_menu_handler_last_write_mtime";

    private void appendImageLog(String line) {
        try {
            File log = new File(Environment.getExternalStorageDirectory(), ".obsidian/handler_image_log.txt");
            try (FileOutputStream out = new FileOutputStream(log, true)) {
                String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(new java.util.Date());
                out.write((ts + "  " + line + System.lineSeparator()).getBytes());
            }
        } catch (Throwable ignored) {}
    }

    private void logImageWrite(String source) {
        appendImageLog("WRITE by " + source);
        // mtime reale del file dopo la scrittura si conosce solo a compress/close finiti: si
        // registra all'apertura successiva, ma qui segno "attesa" cosi' il confronto usa il valore vero
        ObsidianPrefs.putString(PREF_LAST_WRITE_MTIME, "pending");
    }

    private void checkExternalImageChange() {
        File f = getImageFile();
        if (!f.exists()) return;
        String mt = String.valueOf(f.lastModified());
        String last = ObsidianPrefs.getString(PREF_LAST_WRITE_MTIME, null);
        if ("pending".equals(last)) {
            ObsidianPrefs.putString(PREF_LAST_WRITE_MTIME, mt);
        } else if (last != null && !last.equals(mt)) {
            appendImageLog("EXTERNAL CHANGE: file mtime " + mt + " != last own write " + last);
            ObsidianPrefs.putString(PREF_LAST_WRITE_MTIME, mt);
        } else if (last == null) {
            ObsidianPrefs.putString(PREF_LAST_WRITE_MTIME, mt);
        }
    }

    private File getImageFile() {
        return new File(Environment.getExternalStorageDirectory(), ".obsidian/" + HANDLER_IMAGE_FILENAME);
    }

    /** Scorre fino alla riga del preset attualmente attivo nella griglia "STILE" — chiamato dal
     *  tocco semplice sul preview (vedi ImageCropOverlayView.setOnTapListener). Posizione
     *  "piatta" = 1 (Scegli da Galleria) + 1 (Zoom, presente solo se isImage, sempre vero qui
     *  visto che il preview esiste solo in quel caso) + 1 (questo stesso preview) + 1 (titolo
     *  sezione "STILE") + indice del preset dentro mStyles. Se nessun preset è attivo (foto
     *  propria, mCurrentStyle==-1) non fa nulla — non c'è una riga a cui tornare. */
    private void scrollToActiveStyle() {
        if (mCurrentStyle < 0 || mRv == null) return;
        int idx = mStyles.indexOf(mCurrentStyle);
        if (idx < 0) return;
        int flatPos = 1 + 1 + 1 + 1 + idx;
        mRv.scrollToPosition(flatPos); // salto istantaneo, non smooth — l'animazione dava fastidio
    }

    // ── Anteprima ritaglio — Pallino ─────────────────────────────────────────
    // Il pallino è circolare/quasi quadrato (mHandlerRectF in MiscMods) — aspect 1:1, raggio
    // auto (-1, stadium) che su un riquadro quadrato coincide con un cerchio.
    private void reloadCropPreviewBitmap() {
        File f = getImageFile();
        if (!f.exists()) { mCropPreviewBmp = null; return; }
        mCropPreviewBmp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (mCropOverlay != null && mCropPreviewBmp != null) mCropOverlay.setBitmap(mCropPreviewBmp);
    }

    private class CropPreviewAdapter extends RecyclerView.Adapter<CropPreviewAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(parent.getContext());
            int hPx = ObsidianTheme.dp(parent.getContext(), 260);
            int mH  = ObsidianTheme.dp(parent.getContext(), 12);
            int mV  = ObsidianTheme.dp(parent.getContext(), 6);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, hPx);
            lp.setMargins(mH, mV, mH, mV);
            card.setLayoutParams(lp);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(ObsidianTheme.dp(parent.getContext(), 16));
            card.setBackground(bg);
            card.setClipToOutline(true);

            ImageCropOverlayView overlay = new ImageCropOverlayView(parent.getContext());
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            card.addView(overlay);
            return new VH(card, overlay);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            mCropOverlay = h.overlay;
            mCropOverlay.setAspect(1f);
            mCropOverlay.setContainOnly(true); // mai ritaglio — vedi commento sul campo
            mCropOverlay.setZoomPercent(ObsidianPrefs.getInt(PREF_HANDLER_CROP_ZOOM, 100));
            mCropOverlay.setCropCenter(
                    ObsidianPrefs.getInt(PREF_HANDLER_CROP_CX, 50) / 100f,
                    ObsidianPrefs.getInt(PREF_HANDLER_CROP_CY, 50) / 100f);
            mCropOverlay.setOnCropChangedListener((cx, cy) -> {
                ObsidianPrefs.putInt(PREF_HANDLER_CROP_CX, Math.round(cx * 100));
                ObsidianPrefs.putInt(PREF_HANDLER_CROP_CY, Math.round(cy * 100));
            });
            // Tocco semplice (non trascinamento) sul preview → torna alla riga del preset attivo
            // nella griglia sotto, richiesta esplicita per non dover scorrere avanti e indietro.
            mCropOverlay.setOnTapListener(PowerMenuHandlerPresetFragment.this::scrollToActiveStyle);
            reloadCropPreviewBitmap();
        }

        @Override public int getItemCount() { return 1; }

        class VH extends RecyclerView.ViewHolder {
            final ImageCropOverlayView overlay;
            VH(FrameLayout card, ImageCropOverlayView overlay) { super(card); this.overlay = overlay; }
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<Integer> items;
        Adapter(List<Integer> items) { this.items = items; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            int pad = ObsidianTheme.dp(ctx, 12);
            int radius = ObsidianTheme.dp(ctx, 12);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(pad, ObsidianTheme.dp(ctx, 4), pad, ObsidianTheme.dp(ctx, 4));
            row.setLayoutParams(rowLp);

            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(ObsidianTheme.cardColor());
            bg.setCornerRadius(radius);
            row.setBackground(bg);

            ImageView preview = new ImageView(ctx);
            preview.setTag("preview");
            int size = ObsidianTheme.dp(ctx, 48);
            LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(size, size);
            preview.setLayoutParams(previewLp);
            row.addView(preview);

            TextView label = new TextView(ctx);
            label.setTag("label");
            label.setTextColor(ObsidianTheme.textColor());
            label.setTextSize(15);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart(ObsidianTheme.dp(ctx, 16));
            label.setLayoutParams(labelLp);
            row.addView(label);

            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            int index = items.get(pos);
            boolean active = index == mCurrentStyle;

            int resId = h.itemView.getResources().getIdentifier(
                    "fingerprint_" + index, "drawable", h.itemView.getContext().getPackageName());
            Drawable d = ContextCompat.getDrawable(h.itemView.getContext(), resId);
            h.preview.setImageDrawable(d);

            String label = getString(R.string.lockscreen_fp_style, index);
            // Preset 74-89: estratti da un pacchetto animazioni impronta PUI (2026-09-18, su
            // richiesta esplicita dell'utente) — credito visibile accanto al nome, non solo nei
            // Crediti generali dell'app.
            if (FINGERPRINT_CREDIT_RANGE.contains(index)) {
                label += "\nby PUI (天伞桜&PanL)";
            }
            h.label.setText(label);
            h.label.setTextColor(active
                    ? ContextCompat.getColor(requireContext(), R.color.obs_primary)
                    : ObsidianTheme.textColor());

            h.itemView.setOnClickListener(v -> confirmApplyStyle(index));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView preview;
            TextView  label;
            VH(View v) {
                super(v);
                preview = v.findViewWithTag("preview");
                label   = v.findViewWithTag("label");
            }
        }
    }
}
