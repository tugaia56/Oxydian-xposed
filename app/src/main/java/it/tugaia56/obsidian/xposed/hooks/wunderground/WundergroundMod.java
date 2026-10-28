package it.tugaia56.obsidian.xposed.hooks.wunderground;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.util.TypedValue;
import android.view.ViewGroup;
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

    /** Titolo dell'intestazione (nome della localita'): con nomi lunghi va a capo su 4 righe e l'ultima viene tagliata.
     *  Il testo si rimpicciolisce da solo fino a starci tutto (mai sotto il 60% della misura originale). */
    private void fixTitle(View root) {
        if (mTitleId <= 0) return;
        View v = root.findViewById(mTitleId);
        if (!(v instanceof TextView) || v.getTag(mTitleId) != null) return;
        TextView tv = (TextView) v;
        float max = tv.getTextSize();
        if (max <= 0f) return;
        tv.setAutoSizeTextTypeUniformWithConfiguration(Math.round(max * 0.6f), Math.round(max), 1, TypedValue.COMPLEX_UNIT_PX);
        tv.setTag(mTitleId, Boolean.TRUE);
    }
}
