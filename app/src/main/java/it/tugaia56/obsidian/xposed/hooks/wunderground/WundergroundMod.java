package it.tugaia56.obsidian.xposed.hooks.wunderground;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Weather Underground (com.wunderground.android.weather), schermata "Condizioni attuali": nel layout dell'app la
 * velocita' del vento (cc_wind_speed, sotto la scritta VENTO) ha larghezza e altezza fisse di 12 dp, mentre il
 * separatore e la raffica accanto sono "wrap_content": le cifre della velocita' vengono tagliate (si vede solo meta'
 * dello zero) e la riga non e' allineata. Dopo che il layout e' stato gonfiato si rimette la misura "wrap_content".
 * Mod nascosta, sempre attiva. Serve la spunta su Weather Underground nell'ambito di Oxydian in LSPosed.
 */
public class WundergroundMod extends XposedMods {

    public static final String PKG = "com.wunderground.android.weather";
    private int mWindSpeedId = -1;
    private int mTitleId = -1;

    public WundergroundMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public boolean listensTo(String packageName) {
        return PKG.equals(packageName);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!PKG.equals(lp.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(LayoutInflater.class, "inflate",
                    XmlPullParser.class, ViewGroup.class, boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object r = p.getResult();
                                if (!(r instanceof View)) return;
                                if (mWindSpeedId == -1) {
                                    Resources res = ((LayoutInflater) p.thisObject).getContext().getResources();
                                    mWindSpeedId = res.getIdentifier("cc_wind_speed", "id", PKG);
                                }
                                if (mTitleId == -1) {
                                    Resources res = ((LayoutInflater) p.thisObject).getContext().getResources();
                                    mTitleId = res.getIdentifier("toolbar_title", "id", PKG);
                                }
                                fixWind((View) r);
                                fixTitle((View) r);
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log("[ Obsidian ] WundergroundMod: layout fixes (wind speed, header title) installed in " + lp.packageName);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] WundergroundMod: hook failed: " + t);
        }
    }

    private void fixWind(View root) {
        if (mWindSpeedId <= 0) return;
        View v = root.findViewById(mWindSpeedId);
        if (v == null) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        if (lp.width > 0 || lp.height > 0) {   // misura fissa (12 dp) nel layout dell'app
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            v.setLayoutParams(lp);
        }
    }

    /** Titolo dell'intestazione (nome della localita'): con nomi lunghi va a capo su 4 righe e l'ultima viene tagliata,
     *  perche' lo spazio e' alto 88 dp. La misura si abbassa a piccoli passi solo quanto serve a far stare tutte le
     *  righe in quello spazio (mai sotto il 60% dell'originale); con nomi corti resta quella dell'app. */
    private void fixTitle(View root) {
        if (mTitleId <= 0) return;
        View v = root.findViewById(mTitleId);
        if (!(v instanceof TextView) || v.getTag(mTitleId) != null) return;
        final TextView tv = (TextView) v;
        final float orig = tv.getTextSize();
        if (orig <= 0f) return;
        tv.setTag(mTitleId, Boolean.TRUE);
        tv.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) { fitTitle(tv, orig); }
        });
        tv.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) fitTitle(tv, orig);   // cambio di larghezza
        });
        tv.post(() -> fitTitle(tv, orig));
    }

    private void fitTitle(TextView tv, float orig) {
        try {
            CharSequence text = tv.getText();
            int w = tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight();
            if (text == null || text.length() == 0 || w <= 0) return;
            float maxH = 88f * tv.getResources().getDisplayMetrics().density;
            float best = orig * 0.6f;
            for (float size = orig; size >= orig * 0.6f; size -= orig * 0.04f) {
                TextPaint paint = new TextPaint(tv.getPaint());
                paint.setTextSize(size);
                StaticLayout l = StaticLayout.Builder.obtain(text, 0, text.length(), paint, w)
                        .setLineSpacing(tv.getLineSpacingExtra(), tv.getLineSpacingMultiplier())
                        .setIncludePad(tv.getIncludeFontPadding()).build();
                if (l.getHeight() <= maxH) {
                    best = size;
                    break;
                }
            }
            if (Math.abs(tv.getTextSize() - best) > 0.5f) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, best);
        } catch (Throwable ignored) {}
    }
}
