package it.tugaia56.obsidian.xposed.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Tag-based view manipulation helpers for custom clock layouts (e.g. lockscreen_clock_0.xml) —
 * trimmed port of OC's ViewHelper: only the subset actually used to apply colour/font/scale/
 * margin choices onto a tagged layout at hook time.
 */
public class ViewHelper {

    private ViewHelper() {}

    public static int dp2px(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static void setMargins(View view, Context context, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams lp) {
            lp.setMargins(dp2px(context, left), dp2px(context, top), dp2px(context, right), dp2px(context, bottom));
        } else if (params instanceof FrameLayout.LayoutParams lp) {
            lp.setMargins(dp2px(context, left), dp2px(context, top), dp2px(context, right), dp2px(context, bottom));
        } else if (params instanceof ViewGroup.MarginLayoutParams lp) {
            lp.setMargins(dp2px(context, left), dp2px(context, top), dp2px(context, right), dp2px(context, bottom));
        }
        view.setLayoutParams(params);
    }

    public static View findViewWithTag(View view, String tag) {
        if (view == null) return null;
        if (view instanceof ViewGroup viewGroup) {
            if (viewGroup.getTag() != null && viewGroup.getTag().toString().contains(tag)) return viewGroup;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View result = findViewWithTag(viewGroup.getChildAt(i), tag);
                if (result != null) return result;
            }
        } else if (view.getTag() != null && view.getTag().toString().contains(tag)) {
            return view;
        }
        return null;
    }

    /** Recursively re-colours every descendant whose tag contains {@code tagContains}. */
    public static void findViewWithTagAndChangeColor(View view, String tagContains, int color) {
        if (view == null) return;
        checkTagAndChangeColor(view, tagContains, color);
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                findViewWithTagAndChangeColor(viewGroup.getChildAt(i), tagContains, color);
            }
        }
    }

    /** Orologio analogico (AnalogClock): colore del quadrante e delle lancette (se attivi) e
     *  dimensione (lato = baseDp * size). Colori spenti = disegno originale (quadrante: tag accent1). */
    public static void applyAnalogOptions(View root, boolean dialOn, int dialColor, boolean imgOn, int imgColor,
                                          boolean handsOn, int handsColor, float size, int baseDp, boolean seconds) {
        android.widget.AnalogClock ac = findAnalogClock(root);
        if (ac == null) return;
        try {
            applyDialColors(ac, dialOn, dialColor, imgOn, imgColor);
            ColorStateList hands = handsOn ? ColorStateList.valueOf(handsColor) : null;
            ac.setHourHandTintList(hands);
            ac.setMinuteHandTintList(hands);
            ac.setSecondHandTintList(hands);
            // lancetta dei secondi: solo dove scelto (non sull'AOD, per non consumare batteria)
            if (!seconds) ac.setSecondHand(null);
            ViewGroup.LayoutParams lp = ac.getLayoutParams();
            if (lp != null) {
                int px = Math.round(baseDp * size * ac.getResources().getDisplayMetrics().density);
                if (lp.width != px || lp.height != px) { lp.width = px; lp.height = px; ac.setLayoutParams(lp); }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Quadrante a due livelli (0 = cerchio, numeri e tacche; 1 = immagine interna): "Colore quadrante" tinge il primo,
     * "Colore immagine quadrante" il secondo. I quadranti senza immagine (un solo livello) si tingono per intero
     * con "Colore quadrante".
     */
    public static void applyDialColors(android.widget.AnalogClock ac, boolean dialOn, int dialColor, boolean imgOn, int imgColor) {
        try {
            Object dial = de.robv.android.xposed.XposedHelpers.getObjectField(ac, "mDial");
            if (dial instanceof android.graphics.drawable.LayerDrawable ld && ld.getNumberOfLayers() == 2) {
                Drawable ring = ld.getDrawable(0).mutate();
                Drawable img = ld.getDrawable(1).mutate();
                ring.setTintList(dialOn ? ColorStateList.valueOf(dialColor) : null);
                img.setTintList(imgOn ? ColorStateList.valueOf(imgColor) : null);
                ac.invalidate();
            } else if (dialOn) {
                ac.setDialTintList(ColorStateList.valueOf(dialColor));
            }
        } catch (Throwable t) {
            if (dialOn) ac.setDialTintList(ColorStateList.valueOf(dialColor));
        }
    }

    private static android.widget.AnalogClock findAnalogClock(View v) {
        if (v instanceof android.widget.AnalogClock a) return a;
        if (v instanceof ViewGroup g) for (int i = 0; i < g.getChildCount(); i++) {
            android.widget.AnalogClock a = findAnalogClock(g.getChildAt(i));
            if (a != null) return a;
        }
        return null;
    }

    private static void checkTagAndChangeColor(View view, String tagContains, int color) {
        Object tag = view.getTag();
        if (tag == null || !tag.toString().toLowerCase().contains(tagContains)) return;
        changeViewColor(view, color);
    }

    private static void changeViewColor(View view, int color) {
        if (view instanceof TextView textView) {
            textView.setTextColor(color);
            for (Drawable d : textView.getCompoundDrawablesRelative()) {
                if (d == null) continue;
                d.mutate();
                d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
        } else if (view instanceof ImageView imageView) {
            imageView.setColorFilter(color);
        } else if (view instanceof android.widget.AnalogClock) {
            // il quadrante resta com'e' (bianco): lo si colora solo con le scelte "Colore quadrante / immagine quadrante"
        } else if (view instanceof ProgressBar progressBar) {
            progressBar.setProgressTintList(ColorStateList.valueOf(color));
            progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(color));
        } else if (view instanceof ViewGroup viewGroup) {
            viewGroup.setBackgroundTintList(ColorStateList.valueOf(color));
        } else if (view.getBackground() != null) {
            view.getBackground().mutate().setTint(color);
        }
    }

    public static void applyFontRecursively(ViewGroup viewGroup, Typeface typeface) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup childGroup) applyFontRecursively(childGroup, typeface);
            else if (child instanceof TextView textView) textView.setTypeface(typeface);
        }
    }

    /** Adds {@code topMarginDp} (converted to px) on top of every descendant TextView's existing top margin. */
    public static void applyTextMarginRecursively(Context context, ViewGroup viewGroup, int topMarginDp) {
        int topMarginPx = dp2px(context, topMarginDp);
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup childGroup) {
                applyTextMarginRecursively(context, childGroup, topMarginDp);
            } else if (child instanceof TextView) {
                ViewGroup.LayoutParams params = child.getLayoutParams();
                if (params instanceof LinearLayout.LayoutParams lp) lp.topMargin += topMarginPx;
                else if (params instanceof FrameLayout.LayoutParams lp) lp.topMargin += topMarginPx;
                else if (params instanceof RelativeLayout.LayoutParams lp) lp.topMargin += topMarginPx;
                child.setLayoutParams(params);
            }
        }
    }

    public static void applyTextScalingRecursively(ViewGroup viewGroup, float scaleFactor) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup childGroup) {
                applyTextScalingRecursively(childGroup, scaleFactor);
            } else if (child instanceof TextView textView) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textView.getTextSize() * scaleFactor);
            }
        }
    }
}
