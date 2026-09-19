package it.tugaia56.obsidian.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Riquadro di ritaglio FISSO (sempre della stessa dimensione comoda da vedere/trascinare) con la
 * foto che si sposta e si ingrandisce sotto, come un vero cropper foto (Google Photos ecc.) —
 * estratto da VolumePanelColorsFragment.CropOverlayView (2026-09-15) per riuso su altre immagini
 * (Immagine intestazione QS, Pillolone/Sfondo Menù/Pallino del Power Menu). Un riquadro che si
 * rimpicciolisce con lo zoom (il primo tentativo) diventava inutilizzabile con zoom alti — qui
 * il riquadro non cambia mai, è la foto che scala.
 *
 * Uso: setAspect() (larghezza/altezza del riquadro, es. 138f/540f per la barra volume, screenW/
 * headerPx per l'header QS), setBitmap(), setZoomPercent() (>=100, 100 = immagine intera senza
 * ritaglio, più alto = più zoom), setCropCenter() per impostare la posizione iniziale (da una
 * pref salvata), e
 * setOnCropChangedListener() per essere avvisati a fine trascinamento (salvare su pref). Questa
 * classe non tocca prefs/persistenza da sola — è compito del chiamante.
 */
public class ImageCropOverlayView extends View {

    /** Notificato una sola volta, a fine trascinamento (ACTION_UP), non ad ogni frame. */
    public interface OnCropChanged {
        void onCropChanged(float cx, float cy);
    }

    private float mAspect = 1f; // larghezza/altezza del riquadro
    private float mBoxHeightFraction = 0.85f; // quanta altezza della view occupa il riquadro
    private float mCornerRadiusPx = -1f; // -1 = auto (pillolone, min(w,h)/2) — vedi setCornerRadiusPx
    private Bitmap mBmp;
    private float mBmpW = 1f, mBmpH = 1f;
    private float mCx = 0.5f, mCy = 0.5f; // 0..1 — punto della foto al centro del riquadro
    private int mZoomPercent = 100; // >=100, 100 = immagine intera (nessun ritaglio), più alto = più zoom
    private OnCropChanged mListener;
    // Blocca il trascinamento su un asse — richiesta esplicita: Sfondo Menù Power (foto già larga
    // quanto lo schermo, muoverla a dx/sx non serve) e Pillolone (pillola bassa, muoverla su/giù
    // non serve) devono restare fissi sull'asse che non ha senso spostare, l'utente sposta solo
    // l'altro. Default: entrambi liberi (comportamento di prima, invariato per chi non li chiama).
    // SOLO a zoom<=100 però — richiesta esplicita: "non deve bloccarsi se voglio usare solo una
    // parte dell'img" — con zoom>100 (ritaglio volontario) l'asse "senza senso" può comunque avere
    // margine da sfruttare (es. foto più larga del riquadro), va lasciato libero. Vedi onTouchEvent.
    private boolean mLockDragX = false, mLockDragY = false;
    // 2026-09-17: modalità "mai ritaglia" per il Pallino — richiesta esplicita ("mai tagliare,
    // mostra tutta l'immagine"), diversa dal comportamento normale (100%=riempi/ritaglia) usato
    // da Pillolone/Sfondo Menù/Header QS/barra volume, che resta invariato per chi non la attiva.
    // Quando attiva, onDraw ignora del tutto il ramo cover/crop sopra 100% — lo zoom (20-100)
    // scala solo l'"adatta" naturale, mai oltre, quindi non si perde mai una parte della foto.
    private boolean mContainOnly = false;
    private Runnable mTapListener;
    private float mDownX, mDownY;
    private static final float TAP_SLOP_DP = 8f; // stesso ordine di grandezza del touch slop di sistema

    private final Paint mPhotoPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint mScrimPaint = new Paint();
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBoxRectF = new RectF();
    private float mDragStartX, mDragStartY, mDragStartCx, mDragStartCy;
    private boolean mDragging = false;

    public ImageCropOverlayView(Context ctx) {
        super(ctx);
        mScrimPaint.setColor(0xAA000000);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(2 * ctx.getResources().getDisplayMetrics().density);
        mBorderPaint.setColor(0xFFFFFFFF);
    }

    /** Larghezza/altezza del riquadro — cambiarlo ricalcola subito mBoxRectF (es. l'header QS ha
     *  un'altezza regolabile dall'utente, l'aspect ratio non è fisso come per la barra volume). */
    public void setAspect(float aspect) {
        mAspect = aspect;
        if (getWidth() > 0 && getHeight() > 0) computeBoxRect();
        invalidate();
    }

    /** Frazione (0..1) dell'altezza della view occupata dal riquadro — default 0.85, un valore
     *  più basso (es. 0.5) serve per aspect ratio molto larghi/bassi (header QS) dove un riquadro
     *  all'85% dell'altezza risulterebbe troppo largo per la view. */
    public void setBoxHeightFraction(float fraction) {
        mBoxHeightFraction = fraction;
        if (getWidth() > 0 && getHeight() > 0) computeBoxRect();
        invalidate();
    }

    /** Raggio degli angoli del riquadro in px. Default (-1, non chiamato) = pillolone, cioè
     *  arrotondato al massimo (min(w,h)/2) — adatto a forme strette come la barra volume. Per
     *  un riquadro rettangolare (es. header QS, largo quanto il pannello) passare un raggio
     *  piccolo, tipo lo stesso raggio della card che lo contiene. */
    public void setCornerRadiusPx(float px) { mCornerRadiusPx = px; invalidate(); }

    public void setBitmap(Bitmap bmp) {
        mBmp = bmp;
        mBmpW = Math.max(1, bmp.getWidth());
        mBmpH = Math.max(1, bmp.getHeight());
        invalidate();
    }

    /** 100 = riempie il riquadro senza ritagliare più del minimo indispensabile ("riempi");
     *  sopra 100 ingrandisce/ritaglia di più; sotto 100 rimpicciolisce verso "adatta" (l'intera
     *  foto visibile, con margine vuoto se non ha lo stesso aspect ratio del riquadro) — utile
     *  quando la foto sorgente è molto più "lunga" del riquadro e il minimo "riempi" ne mostra
     *  solo una fetta centrale. Il chiamante decide il range effettivo dello slider (es. 100-300
     *  per restare solo in modalità riempi, o 20-300 per abilitare anche il rimpicciolimento). */
    public void setZoomPercent(int percent) { mZoomPercent = percent; invalidate(); }

    /** Imposta la posizione iniziale (0..1) senza notificare il listener — usarlo per caricare
     *  un valore già salvato in una pref, non durante il trascinamento. */
    public void setCropCenter(float cx, float cy) { mCx = cx; mCy = cy; invalidate(); }

    public void setOnCropChangedListener(OnCropChanged l) { mListener = l; }

    /** Blocca il trascinamento su un asse (l'altro resta libero) — vedi commento sui campi. */
    public void setLockDrag(boolean lockX, boolean lockY) { mLockDragX = lockX; mLockDragY = lockY; }

    /** Notificato su un tocco semplice (nessun trascinamento reale, sotto la soglia normale di
     *  touch-slop di Android) — distinto da OnCropChanged, che scatta ad OGNI ACTION_UP anche
     *  dopo un vero trascinamento. Usato per azioni "tocca per fare X" sulla stessa area
     *  pannabile senza interferire col gesto di trascinamento (2026-09-17, richiesta esplicita:
     *  tocco sul preview per tornare al preset scelto nella griglia sottostante). */
    public void setOnTapListener(Runnable r) { mTapListener = r; }

    /** Vedi commento sul campo mContainOnly. */
    public void setContainOnly(boolean containOnly) { mContainOnly = containOnly; invalidate(); }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        computeBoxRect();
    }

    private void computeBoxRect() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float boxH = h * mBoxHeightFraction, boxW = boxH * mAspect;
        if (boxW > w * 0.92f) { boxW = w * 0.92f; boxH = boxW / mAspect; }
        float left = (w - boxW) / 2f, top = (h - boxH) / 2f;
        mBoxRectF.set(left, top, left + boxW, top + boxH);
    }

    /** Finestra massima che rientra nella sorgente all'aspect ratio del riquadro, poi ridotta in
     *  base a mZoomPercent (100 = finestra massima, cioè immagine intera senza ritaglio). */
    private float[] computeCropSizeSrcPx() {
        float srcAspect = mBmpW / mBmpH;
        float maxCropW, maxCropH;
        if (srcAspect > mAspect) { maxCropH = mBmpH; maxCropW = mBmpH * mAspect; }
        else { maxCropW = mBmpW; maxCropH = mBmpW / mAspect; }
        float frac = Math.max(0.05f, Math.min(1f, 100f / Math.max(1, mZoomPercent)));
        return new float[]{maxCropW * frac, maxCropH * frac};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mBmp == null || mBmpW <= 1f || mBmpH <= 1f || mBoxRectF.width() <= 0) return;

        float scale, tx, ty;
        if (mContainOnly) {
            // Sempre "adatta" — mai ritaglio, indipendentemente da mZoomPercent. 20-100: 20 =
            // immagine più piccola con margine maggiore, 100 = adatta naturale (immagine intera,
            // margine minimo). MARGIN_FACTOR lascia sempre un minimo di spazio tra immagine e
            // bordo anche a 100%, anche quando l'aspect combacia esattamente col riquadro
            // (altrimenti l'immagine toccherebbe il bordo esattamente, richiesta esplicita:
            // "far si che rimanga un minimo di spazio tra img e bordo").
            final float MARGIN_FACTOR = 0.92f;
            float fitScale = Math.min(mBoxRectF.width() / mBmpW, mBoxRectF.height() / mBmpH) * MARGIN_FACTOR;
            float t = Math.max(0f, Math.min(1f, (mZoomPercent - 20) / 80f)); // 0=più piccola, 1=adatta piena
            scale = fitScale * (0.5f + 0.5f * t);
            tx = mBoxRectF.centerX() - (mBmpW * scale) / 2f;
            ty = mBoxRectF.centerY() - (mBmpH * scale) / 2f;
        } else if (mZoomPercent >= 100) {
            float[] size = computeCropSizeSrcPx();
            float cropWSrc = size[0], cropHSrc = size[1];
            // I cropWSrc pixel sorgente devono riempire esattamente il riquadro fisso — più
            // cropWSrc è piccolo (zoom alto), più la foto appare grande sullo schermo.
            scale = mBoxRectF.width() / cropWSrc;
            float leftSrc = clamp(mBmpW * mCx - cropWSrc / 2f, 0f, mBmpW - cropWSrc);
            float topSrc  = clamp(mBmpH * mCy - cropHSrc / 2f, 0f, mBmpH - cropHSrc);
            tx = mBoxRectF.left - leftSrc * scale;
            ty = mBoxRectF.top - topSrc * scale;
        } else {
            // Sotto 100%: invece di restare bloccati al minimo "riempi" (che per una foto molto
            // più "lunga" del riquadro mostra solo una fetta centrale), rimpicciolisce verso
            // "adatta" (immagine intera visibile, margine vuoto ai lati) — richiesta esplicita:
            // "se un'img è già grande di suo per farcela stare andrebbe diminuita". Centrato,
            // cx/cy ignorati qui: spostare non ha senso quando si vede quasi tutta la foto.
            float coverScale = Math.max(mBoxRectF.width() / mBmpW, mBoxRectF.height() / mBmpH);
            // 2026-09-17: quando la foto ha (quasi) lo stesso aspect ratio del riquadro (es.
            // Pallino: riquadro quadrato + preset "impronta" quadrato), "adatta" e "riempi"
            // combaciano matematicamente (min==max) — lo zoom sotto 100% non aveva ALCUN effetto
            // visibile, segnalato: "anche se uso 20% rimane a 100%". Forza un rimpicciolimento
            // minimo reale (60% del riempimento) anche in quel caso, così lo zoom fa sempre
            // qualcosa di visibile — se l'aspect è già molto diverso, il "adatta" naturale
            // (spesso già più piccolo) resta quello che conta (min tra i due).
            float containScale = Math.min(
                    Math.min(mBoxRectF.width() / mBmpW, mBoxRectF.height() / mBmpH),
                    coverScale * 0.6f);
            // 20 = minimo reale usato da tutti i chiamanti di questo range — vedi la stessa nota
            // in MiscMods.drawCustomCropped() (duplicata lì per indipendenza, processo diverso).
            float t = Math.max(0f, Math.min(1f, (mZoomPercent - 20) / 80f));
            scale = containScale + (coverScale - containScale) * t;
            tx = mBoxRectF.centerX() - (mBmpW * scale) / 2f;
            ty = mBoxRectF.centerY() - (mBmpH * scale) / 2f;
        }

        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(tx, ty);
        canvas.drawBitmap(mBmp, m, mPhotoPaint);

        // Scrim scuro fuori dal riquadro (angoli arrotondati) — ritaglio arrotondato via
        // clipOutPath, non 4 strisce a spigolo vivo (sporgerebbero negli angoli del riquadro).
        float radius = mCornerRadiusPx >= 0f
                ? mCornerRadiusPx
                : Math.min(mBoxRectF.width(), mBoxRectF.height()) / 2f;
        Path clip = new Path();
        clip.addRoundRect(mBoxRectF, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipOutPath(clip);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mScrimPaint);
        canvas.restore();
        canvas.drawRoundRect(mBoxRectF, radius, radius, mBorderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDragStartX = event.getX(); mDragStartY = event.getY();
                mDownX = event.getX(); mDownY = event.getY();
                mDragStartCx = mCx; mDragStartCy = mCy;
                mDragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!mDragging) return false;
                float[] size = computeCropSizeSrcPx();
                float cropWSrc = size[0], cropHSrc = size[1];
                float scale = mBoxRectF.width() / cropWSrc;
                // Trascinare la foto verso destra deve rivelare la parte SINISTRA della foto (si
                // trascina il contenuto sotto il riquadro fermo, non il riquadro stesso).
                // Il blocco vale solo entro il "riempi" base (zoom<=100) — oltre, l'utente ha
                // volutamente ingrandito per scegliere una parte precisa della foto, anche
                // sull'asse normalmente bloccato (vedi commento sui campi mLockDragX/Y).
                boolean lockX = mLockDragX && mZoomPercent <= 100;
                boolean lockY = mLockDragY && mZoomPercent <= 100;
                float dxSrc = lockX ? 0f : -(event.getX() - mDragStartX) / scale;
                float dySrc = lockY ? 0f : -(event.getY() - mDragStartY) / scale;
                float leftSrcStart = mDragStartCx * mBmpW - cropWSrc / 2f;
                float topSrcStart  = mDragStartCy * mBmpH - cropHSrc / 2f;
                float newLeftSrc = clamp(leftSrcStart + dxSrc, 0f, mBmpW - cropWSrc);
                float newTopSrc  = clamp(topSrcStart + dySrc, 0f, mBmpH - cropHSrc);
                mCx = (newLeftSrc + cropWSrc / 2f) / mBmpW;
                mCy = (newTopSrc + cropHSrc / 2f) / mBmpH;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                if (mListener != null) mListener.onCropChanged(mCx, mCy);
                if (event.getActionMasked() == MotionEvent.ACTION_UP && mTapListener != null) {
                    float slopPx = TAP_SLOP_DP * getResources().getDisplayMetrics().density;
                    if (Math.hypot(event.getX() - mDownX, event.getY() - mDownY) <= slopPx) {
                        mTapListener.run();
                    }
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
