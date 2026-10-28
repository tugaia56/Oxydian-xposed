package it.tugaia56.obsidian.xposed.hooks.wunderground;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.text.StaticLayout;
import android.text.TextPaint;
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
            hookSetText();
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

    /** Titolo dell'intestazione (nome della localita'): con nomi lunghi va a capo su 4 righe, l'ultima viene tagliata e sopra
     *  resta molto spazio vuoto. Si va a capo dopo la prima virgola ("Montecchio Maggiore," / "provincia di Vicenza"), la
     *  misura del testo si abbassa a piccoli passi solo finche' le righe ci stanno (mai sotto il 55% dell'originale) e
     *  l'intestazione si accorcia in modo da stare attorno al testo. */
    private void fixTitle(View root) {
        if (mTitleId <= 0) return;
        View v = root.findViewById(mTitleId);
        if (!(v instanceof TextView) || v.getTag(mTitleId) != null) return;
        final TextView tv = (TextView) v;
        final float orig = tv.getTextSize();
        if (orig <= 0f) return;
        tv.setTag(mTitleId, orig);
        tv.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) tv.post(() -> fitTitle(tv));   // cambio di larghezza
        });
        tv.post(() -> fitTitle(tv));
    }

    private void hookSetText() {
        XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, TextView.BufferType.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    TextView tv = (TextView) p.thisObject;
                    if (mTitleId <= 0 || tv.getId() != mTitleId || !(p.args[0] instanceof CharSequence)) return;
                    String t = p.args[0].toString();
                    int c = t.indexOf(", ");
                    if (c > 0 && t.indexOf('\n') < 0) p.args[0] = t.substring(0, c + 1) + "\n" + t.substring(c + 2);
                } catch (Throwable ignored) {}
            }

            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    final TextView tv = (TextView) p.thisObject;
                    if (mTitleId > 0 && tv.getId() == mTitleId && tv.getTag(mTitleId) != null) tv.post(() -> fitTitle(tv));
                } catch (Throwable ignored) {}
            }
        });
    }

    private void fitTitle(TextView tv) {
        try {
            Object o = tv.getTag(mTitleId);
            if (!(o instanceof Float)) return;
            float orig = (Float) o;
            CharSequence text = tv.getText();
            int w = tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight();
            if (text == null || text.length() == 0 || w <= 0) return;
            float density = tv.getResources().getDisplayMetrics().density;
            int explicit = 1;
            for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') explicit++;
            float maxH = 88f * density;
            float best = -1f;
            int bestH = 0;
            // prima scelta: la misura piu' grande per cui non serve nessun altro a capo oltre a quelli voluti
            for (float size = orig; size >= orig * 0.55f; size -= orig * 0.02f) {
                StaticLayout l = layout(tv, text, size, w);
                if (l.getLineCount() <= explicit && l.getHeight() <= maxH) { best = size; bestH = l.getHeight(); break; }
            }
            if (best < 0f) {   // altrimenti la piu' grande che sta nell'altezza
                best = orig * 0.55f;
                for (float size = orig; size >= orig * 0.55f; size -= orig * 0.02f) {
                    StaticLayout l = layout(tv, text, size, w);
                    if (l.getHeight() <= maxH) { best = size; break; }
                }
                bestH = layout(tv, text, best, w).getHeight();
            }
            if (Math.abs(tv.getTextSize() - best) > 0.5f) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, best);
            shrinkHeader(tv, bestH + tv.getPaddingTop() + tv.getPaddingBottom(), density);
        } catch (Throwable ignored) {}
    }

    private static StaticLayout layout(TextView tv, CharSequence text, float size, int w) {
        TextPaint paint = new TextPaint(tv.getPaint());
        paint.setTextSize(size);
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, w)
                .setLineSpacing(tv.getLineSpacingExtra(), tv.getLineSpacingMultiplier())
                .setIncludePad(tv.getIncludeFontPadding()).build();
    }

    /** Accorcia la barra in alto per eliminare lo spazio vuoto sopra il titolo (mai sotto 100 dp, mai oltre l'originale). */
    private void shrinkHeader(TextView tv, int textH, float density) {
        View frame = (View) tv.getParent();
        if (frame == null || !(frame.getParent() instanceof View)) return;
        View bar = (View) frame.getParent();
        ViewGroup.LayoutParams lp = bar.getLayoutParams();
        if (lp == null) return;
        Object saved = bar.getTag(mTitleId);
        int origH;
        if (saved instanceof Integer) origH = (Integer) saved;
        else {
            origH = bar.getHeight() > 0 ? bar.getHeight() : lp.height;
            if (origH <= 0) return;
            bar.setTag(mTitleId, origH);
        }
        int bottomGap = 36;   // distanza del testo dal fondo, come nel layout dell'app (12 dp a 3x)
        try {
            if (frame.getHeight() > 0 && tv.getBottom() > 0 && tv.getHeight() > 0) {
                bottomGap = Math.max(0, frame.getHeight() - (tv.getBottom() + (tv.getHeight() == 0 ? 0 : 0)));
            }
        } catch (Throwable ignored) {}
        int topGap = Math.round(8f * density);
        int need = frame.getTop() + textH + bottomGap + topGap;
        int min = Math.round(100f * density);
        int newH = Math.min(origH, Math.max(min, need));
        if (lp.height != newH) {
            lp.height = newH;
            bar.setLayoutParams(lp);
        }
    }
}
