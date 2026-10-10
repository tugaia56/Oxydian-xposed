package it.tugaia56.obsidian.xposed.utils;
import android.content.Context;
import com.crossbowffs.remotepreferences.RemotePreferenceAccessException;
import com.crossbowffs.remotepreferences.RemotePreferences;

import java.util.Map;
import java.util.Set;

/**
 * Preferenze di Oxydian lette da dentro altri processi (SystemUI, Impostazioni, app OEM...).
 * Se il provider non e' raggiungibile (per esempio in un'app OEM che non vede Oxydian, o subito
 * dopo l'avvio) RemotePreferences lancia RemotePreferenceAccessException: qui si restituisce il
 * valore predefinito invece di mandare in crash il processo che ospita il mod.
 */
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
        try {
            return super.getBoolean(key, defValue);
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
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
        try {
            return super.getInt(key, defValue);
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        try {
            return super.getString(key, defValue);
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        try {
            return super.getFloat(key, defValue);
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        try {
            return super.getLong(key, defValue);
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValue) {
        try {
            return super.getStringSet(key, defValue);
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public boolean contains(String key) {
        try {
            return super.contains(key);
        } catch (RemotePreferenceAccessException e) {
            return false;
        }
    }

    @Override
    public Map<String, ?> getAll() {
        try {
            return super.getAll();
        } catch (RemotePreferenceAccessException e) {
            return java.util.Collections.emptyMap();
        }
    }
}
