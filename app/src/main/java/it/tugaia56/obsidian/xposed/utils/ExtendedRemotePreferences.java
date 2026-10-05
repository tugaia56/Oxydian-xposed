package it.tugaia56.obsidian.xposed.utils;
import android.content.Context;
import com.crossbowffs.remotepreferences.RemotePreferences;
public class ExtendedRemotePreferences extends RemotePreferences {
    public ExtendedRemotePreferences(Context context, String authority, String prefFileName) { super(context, authority, prefFileName); }
    public ExtendedRemotePreferences(Context context, String authority, String prefFileName, boolean strictMode) { super(context, authority, prefFileName, strictMode); }
    public int   getSliderInt(String key, int defVal)     { return getInt(key, defVal); }
    public float getSliderFloat(String key, float defVal) { return getFloat(key, defVal); }

    // I colori accento/sfondo scelti in Oxydian Theme sostituiscono il "Preset accento/sfondo" di Oxydian
    // per tutti i mod che leggono queste chiavi (vedi ThemeOverride).
    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if ("DST_ACCENT1_on".equals(key) && it.tugaia56.obsidian.utils.ThemeOverride.accent() != null) return true;
        if ("DST_BACKGROUND_on".equals(key) && it.tugaia56.obsidian.utils.ThemeOverride.bg() != null) return true;
        return super.getBoolean(key, defValue);
    }

    @Override
    public int getInt(String key, int defValue) {
        if ("DST_ACCENT1".equals(key)) {
            Integer o = it.tugaia56.obsidian.utils.ThemeOverride.accent();
            if (o != null) return o;
        } else if ("DST_BACKGROUND".equals(key)) {
            Integer o = it.tugaia56.obsidian.utils.ThemeOverride.bg();
            if (o != null) return o;
        }
        return super.getInt(key, defValue);
    }
}
