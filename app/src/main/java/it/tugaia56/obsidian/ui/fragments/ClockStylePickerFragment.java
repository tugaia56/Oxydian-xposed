package it.tugaia56.obsidian.ui.fragments;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.utils.ObsidianPrefs;
import it.tugaia56.obsidian.utils.ObsidianTheme;
import it.tugaia56.obsidian.xposed.utils.ViewHelper;

/**
 * Carosello orizzontale con anteprima live di tutti i 132 stili orologio, come il picker
 * reale di OC: cornice a schermo intero con lo sfondo (il wallpaper del telefono se
 * disponibile, altrimenti un colore) e bordo bianco/accento per lo stile selezionato.
 * Al tocco applica subito lo stile e torna indietro. Condivisa da Orologio SdB e Orologio AOD.
 */
public class ClockStylePickerFragment extends Fragment {

    private static final int STYLE_COUNT = 132;

    /** Gli stili da 0 a ANALOG_FIRST-1 sono digitali, da ANALOG_FIRST in poi analogici. */
    public static final int ANALOG_FIRST = 61;

    /** Intervallo mostrato dal carosello: tutti gli stili, solo i digitali o solo gli analogici. */
    public static final int RANGE_ALL = 0, RANGE_DIGITAL = 1, RANGE_ANALOG = 2;

    /** Il carosello è "circolare": moltiplichiamo gli stili reali per tante copie virtuali,
     *  cosi si può scorrere avanti/indietro senza mai incontrare una fine — es. dallo stile
     *  132 avanti si torna subito all'1, senza dover tornare indietro fino in fondo. */
    private static final int LOOP_MULTIPLIER = 200;

    /** Larghezza "di riferimento" a cui sono pensati i layout reali (schermo intero):
     *  la scala di ogni anteprima è cornice/questo valore, la STESSA per tutti gli stili,
     *  cosi ingrandire la cornice mostra più sfondo invece di ingrandire il testo. */
    private static final int DESIGN_WIDTH_DP = 400;

    private static final String ARG_STYLE_KEY = "style_key";
    private static final String ARG_COLOR_SWITCH_KEY = "color_switch_key";
    private static final String ARG_COLOR_ACCENT1_KEY = "color_accent1_key";
    private static final String ARG_COLOR_TEXT1_KEY = "color_text1_key";
    private static final String ARG_DEFAULT_ACCENT_IS_THEME = "default_accent_is_theme";
    private static final String ARG_BG_COLOR = "bg_color";
    private static final String ARG_RANGE = "range";

    public static ClockStylePickerFragment newInstance(String styleKey, String colorSwitchKey,
            String accentKey, String textKey, boolean defaultAccentIsTheme, int bgColor) {
        return newInstance(styleKey, colorSwitchKey, accentKey, textKey, defaultAccentIsTheme, bgColor, RANGE_ALL);
    }

    public static ClockStylePickerFragment newInstance(String styleKey, String colorSwitchKey,
            String accentKey, String textKey, boolean defaultAccentIsTheme, int bgColor, int range) {
        Bundle b = new Bundle();
        b.putInt(ARG_RANGE, range);
        b.putString(ARG_STYLE_KEY, styleKey);
        b.putString(ARG_COLOR_SWITCH_KEY, colorSwitchKey);
        b.putString(ARG_COLOR_ACCENT1_KEY, accentKey);
        b.putString(ARG_COLOR_TEXT1_KEY, textKey);
        b.putBoolean(ARG_DEFAULT_ACCENT_IS_THEME, defaultAccentIsTheme);
        b.putInt(ARG_BG_COLOR, bgColor);
        ClockStylePickerFragment f = new ClockStylePickerFragment();
        f.setArguments(b);
        return f;
    }

    private String styleKey, colorSwitchKey, accentKey, textKey;
    private boolean defaultAccentIsTheme;
    private int bgColor;
    private int first = 0, count = STYLE_COUNT;     // intervallo di stili mostrato
    /** Posizione nel carosello -> numero dello stile: per gli analogici e' in ordine alfabetico di nome. */
    private int[] order;
    private Drawable wallpaper;
    private int frameW, frameH;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) {
            styleKey = a.getString(ARG_STYLE_KEY);
            colorSwitchKey = a.getString(ARG_COLOR_SWITCH_KEY);
            accentKey = a.getString(ARG_COLOR_ACCENT1_KEY);
            textKey = a.getString(ARG_COLOR_TEXT1_KEY);
            defaultAccentIsTheme = a.getBoolean(ARG_DEFAULT_ACCENT_IS_THEME, true);
            bgColor = a.getInt(ARG_BG_COLOR, 0xFF000000);
            int range = a.getInt(ARG_RANGE, RANGE_ALL);
            if (range == RANGE_DIGITAL) { first = 0; count = ANALOG_FIRST; }
            else if (range == RANGE_ANALOG) { first = ANALOG_FIRST; count = STYLE_COUNT - ANALOG_FIRST; }
            if (range == RANGE_ANALOG) {
                final android.content.Context ctx = requireContext();
                java.util.List<Integer> styles = new java.util.ArrayList<>();
                for (int st = first; st < first + count; st++) styles.add(st);
                final java.text.Collator collator = java.text.Collator.getInstance();
                styles.sort((x, y) -> collator.compare(analogName(ctx, x), analogName(ctx, y)));
                order = new int[count];
                for (int i = 0; i < count; i++) order[i] = styles.get(i);
            }
        }
        try {
            wallpaper = loadLockscreenWallpaper();
        } catch (Throwable ignored) {
            wallpaper = null;
        }
    }

    /** Il wallpaper della Schermata di Blocco, non quello della Home — sono spesso diversi.
     *  Se l'utente non ne ha impostato uno separato FLAG_LOCK torna null (Android stesso lo
     *  considera "non impostato" in quel caso) e si ricade sullo sfondo Home. */
    private Drawable loadLockscreenWallpaper() {
        WallpaperManager wm = WallpaperManager.getInstance(requireContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try (ParcelFileDescriptor pfd = wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)) {
                if (pfd != null) {
                    Bitmap bmp = BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor());
                    if (bmp != null) return new BitmapDrawable(getResources(), bmp);
                }
            } catch (Throwable ignored) {}
        }
        try {
            return wm.getDrawable();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Cornice grande quanto possibile, fino quasi alla navbar: percentuale dell'altezza
        // schermo invece di un dp fisso, cosi si adatta al dispositivo.
        int screenH = getResources().getDisplayMetrics().heightPixels;
        frameH = Math.round(screenH * 0.66f);
        frameW = Math.round(frameH / 2.15f);

        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Velocità di fling limitata: cosi lo snap si ferma sempre su UNA cornice per
        // scorrimento, invece di "volare" per 3-4-5 stili con un colpo veloce.
        int maxFlingVelocity = frameW * 4;
        RecyclerView rv = new RecyclerView(requireContext()) {
            @Override public boolean fling(int velocityX, int velocityY) {
                int clamped = Math.max(-maxFlingVelocity, Math.min(maxFlingVelocity, velocityX));
                return super.fling(clamped, velocityY);
            }
        };
        rv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rv.setPadding(dp(16), dp(16), dp(16), dp(16));
        rv.setClipToPadding(false);
        rv.setAdapter(new CarouselAdapter());
        // Si ferma sempre allineato su una cornice, invece di scorrere libero e lasciare
        // lo scroll a metà tra due stili.
        new androidx.recyclerview.widget.LinearSnapHelper().attachToRecyclerView(rv);

        // Parte da un blocco "centrale" del loop, sullo stile attualmente selezionato, cosi
        // c'è ampio margine per scorrere in entrambe le direzioni senza mai finire il loop.
        int currentStyle = 0;
        if (styleKey != null) {
            try {
                currentStyle = Integer.parseInt(ObsidianPrefs.getString(styleKey, "0"));
            } catch (NumberFormatException ignored) {}
        }
        int middleBlock = (LOOP_MULTIPLIER / 2) * count;
        int offset = currentStyle - first;
        if (order != null) {
            offset = -1;
            for (int i = 0; i < order.length; i++) if (order[i] == currentStyle) { offset = i; break; }
        }
        rv.scrollToPosition(middleBlock + (offset >= 0 && offset < count ? offset : 0));

        root.addView(rv);

        return root;
    }

    private class CarouselAdapter extends RecyclerView.Adapter<CarouselAdapter.VH> {
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            return new VH(col);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            int style = order != null ? order[position % count] : first + position % count;
            LinearLayout col = (LinearLayout) h.itemView;
            col.removeAllViews();
            RecyclerView.LayoutParams colLp = new RecyclerView.LayoutParams(
                    frameW, ViewGroup.LayoutParams.WRAP_CONTENT);
            colLp.setMargins(dp(6), 0, dp(6), 0);
            col.setLayoutParams(colLp);

            boolean selected = styleKey != null
                    && String.valueOf(style).equals(ObsidianPrefs.getString(styleKey, "0"));

            // Cornice "telefono": angoli arrotondati, sfondo = wallpaper del telefono.
            FrameLayout frame = new FrameLayout(requireContext());
            frame.setLayoutParams(new LinearLayout.LayoutParams(frameW, frameH));
            GradientDrawable clipShape = new GradientDrawable();
            clipShape.setShape(GradientDrawable.RECTANGLE);
            clipShape.setCornerRadius(dp(22));
            clipShape.setColor(0xFF000000);
            frame.setBackground(clipShape);
            frame.setClipToOutline(true);

            ImageView wallpaperView = new ImageView(requireContext());
            wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (wallpaper != null) {
                wallpaperView.setImageDrawable(wallpaper);
            } else {
                wallpaperView.setBackgroundColor(bgColor);
            }
            frame.addView(wallpaperView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Anteprima orologio: rimpicciolita con una scala PROPORZIONALE fissa (cornice
            // larghezza / larghezza di riferimento), uguale per tutti gli stili. Cosi
            // ingrandire la cornice mostra più sfondo attorno, non ingrandisce il testo —
            // ed è misurata forzando la larghezza di riferimento, cosi gli elementi
            // "match_parent" dei layout reali si dispongono come sullo schermo vero.
            View clockView = inflateClockStyle(style, accentColor(), textColor());
            if (clockView != null) {
                int designWidthPx = dp(DESIGN_WIDTH_DP);
                int wSpec = View.MeasureSpec.makeMeasureSpec(designWidthPx, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                clockView.measure(wSpec, hSpec);
                float scale = frameW / (float) designWidthPx;
                clockView.setScaleX(scale);
                clockView.setScaleY(scale);
                clockView.setPivotX(0);
                clockView.setPivotY(0);

                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                        designWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = dp(16);
                frame.addView(clockView, clp);
            }

            // Bordo di selezione, sopra a tutto.
            View border = new View(requireContext());
            GradientDrawable borderShape = new GradientDrawable();
            borderShape.setShape(GradientDrawable.RECTANGLE);
            borderShape.setCornerRadius(dp(22));
            borderShape.setColor(Color.TRANSPARENT);
            borderShape.setStroke(dp(selected ? 3 : 1), selected ? ObsidianTheme.accentColor() : 0x33FFFFFF);
            border.setBackground(borderShape);
            frame.addView(border, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            col.addView(frame);

            TextView label = new TextView(requireContext());
            label.setText(style >= ANALOG_FIRST && first == ANALOG_FIRST
                    ? analogName(requireContext(), style)
                    : getString(R.string.lockscreen_clock_style_title) + " " + (style + 1));
            label.setTextColor(selected ? ObsidianTheme.accentColor() : 0xCCFFFFFF);
            label.setTextSize(12);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setPadding(0, dp(6), 0, 0);
            col.addView(label);

            col.setOnClickListener(v -> {
                if (styleKey != null) ObsidianPrefs.putString(styleKey, String.valueOf(style));
                if (getActivity() != null) getActivity().onBackPressed();
            });
        }

        @Override public int getItemCount() { return count * LOOP_MULTIPLIER; }
    }

    /** Nome del quadrante analogico (array non tradotto, stile 62 = indice 0 dopo ANALOG_FIRST). */
    public static String analogName(android.content.Context c, int style) {
        String[] names = c.getResources().getStringArray(R.array.analog_clock_style_names);
        int i = style - ANALOG_FIRST;
        return i >= 0 && i < names.length ? names[i] : c.getString(R.string.aod_clock_style_analog_title) + " " + (i + 1);
    }

    private int accentColor() {
        boolean customColor = colorSwitchKey != null && ObsidianPrefs.getBoolean(colorSwitchKey, false);
        return (customColor && accentKey != null)
                ? (ObsidianPrefs.getInt(accentKey, 0xFF006781) | 0xFF000000)
                : (defaultAccentIsTheme ? ObsidianTheme.accentColor() : 0xFFFFFFFF);
    }

    private int textColor() {
        boolean customColor = colorSwitchKey != null && ObsidianPrefs.getBoolean(colorSwitchKey, false);
        return (customColor && textKey != null)
                ? (ObsidianPrefs.getInt(textKey, 0xFFFFFFFF) | 0xFF000000)
                : 0xFFFFFFFF;
    }

    /** Stessa logica del hook reale (LockscreenClockMod): inflazione per nome + styling via
     *  tag, in-app quindi senza reflection cross-processo. */
    private View inflateClockStyle(int style, int accentColor, int textColor) {
        int layoutId = getResources().getIdentifier(
                "lockscreen_clock_" + style, "layout", requireContext().getPackageName());
        if (layoutId == 0) return null;
        View view;
        try {
            view = LayoutInflater.from(requireContext()).inflate(layoutId, null);
        } catch (Throwable t) {
            return null;
        }
        ViewHelper.findViewWithTagAndChangeColor(view, "accent1", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(view, "accent2", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(view, "accent3", accentColor);
        ViewHelper.findViewWithTagAndChangeColor(view, "text1", textColor);
        injectLottiePreview(view, style);
        return view;
    }

    private void injectLottiePreview(View clockView, int style) {
        View slot = ViewHelper.findViewWithTag(clockView, "lottie");
        if (!(slot instanceof ViewGroup lottieSlot) || lottieSlot.getChildCount() > 0) return;
        int animRes = getResources().getIdentifier(
                "lottie_clock_style_" + style, "raw", requireContext().getPackageName());
        if (animRes == 0) return;
        try {
            com.airbnb.lottie.LottieAnimationView lottie = new com.airbnb.lottie.LottieAnimationView(requireContext());
            lottie.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            lottie.setAnimation(animRes);
            lottie.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
            lottie.playAnimation();
            lottieSlot.addView(lottie);
        } catch (Throwable ignored) {}
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
