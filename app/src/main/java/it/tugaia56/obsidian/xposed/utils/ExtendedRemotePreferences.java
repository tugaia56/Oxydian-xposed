package it.tugaia56.obsidian.xposed.utils;
import android.content.Context;
import android.os.SystemClock;
import com.crossbowffs.remotepreferences.RemotePreferenceAccessException;
import com.crossbowffs.remotepreferences.RemotePreferences;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preferenze di Oxydian lette da dentro altri processi (SystemUI, Impostazioni, app OEM...).
 * Se il provider non e' raggiungibile (per esempio in un'app OEM che non vede Oxydian, o subito
 * dopo l'avvio) RemotePreferences lancia RemotePreferenceAccessException: qui si restituisce il
 * valore predefinito invece di mandare in crash il processo che ospita il mod.
 *
 * Ogni lettura di RemotePreferences e' una chiamata binder sincrona verso l'app Oxydian. Le mod di SystemUI leggono
 * le stesse chiavi a ogni layout / getView del blocco schermo: con tutto quel traffico il thread principale di SystemUI
 * e l'app Oxydian restavano al 40-90% di CPU anche a schermo spento (consumo di batteria, blocco del PIN). Qui i
 * valori letti si tengono in memoria per pochi secondi; al cambio di una preferenza la copia si azzera (XPrefs).
 */
public class ExtendedRemotePreferences extends RemotePreferences {
    private static final long CACHE_TTL_MS = 5000;
    private static final Object NULL = new Object();
    private final ConcurrentHashMap<String, Object> mCache = new ConcurrentHashMap<>();
    private volatile long mCacheAt = 0;

    public ExtendedRemotePreferences(Context context, String authority, String prefFileName) { super(context, authority, prefFileName); }
    public ExtendedRemotePreferences(Context context, String authority, String prefFileName, boolean strictMode) { super(context, authority, prefFileName, strictMode); }

    /** Azzera la copia in memoria (chiamato al cambio di una preferenza). */
    public void invalidateCache() { mCache.clear(); }

    // la chiave comprende il valore predefinito: la stessa preferenza puo' essere letta con predefiniti diversi
    private Object cached(String type, String key, Object def) {
        long now = SystemClock.elapsedRealtime();
        if (now - mCacheAt > CACHE_TTL_MS) { mCache.clear(); mCacheAt = now; }
        return mCache.get(type + ":" + key + ":" + def);
    }

    private void remember(String type, String key, Object def, Object v) {
        mCache.put(type + ":" + key + ":" + def, v == null ? NULL : v);
    }

    public int   getSliderInt(String key, int defVal)     { return getInt(key, defVal); }
    public float getSliderFloat(String key, float defVal) { return getFloat(key, defVal); }

    // I colori accento/sfondo scelti in Oxydian Theme sostituiscono il "Preset accento/sfondo" di Oxydian
    // per tutti i mod che leggono queste chiavi (vedi ThemeOverride).
    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if ("DST_ACCENT1_on".equals(key) && it.tugaia56.obsidian.utils.ThemeOverride.accent() != null) return true;
        if ("DST_BACKGROUND_on".equals(key) && it.tugaia56.obsidian.utils.ThemeOverride.bg() != null) return true;
        Object c = cached("b", key, defValue);
        if (c != null && c != NULL) return (Boolean) c;
        try {
            boolean v = super.getBoolean(key, defValue);
            remember("b", key, defValue, v);
            return v;
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
        Object c = cached("i", key, defValue);
        if (c != null && c != NULL) return (Integer) c;
        try {
            int v = super.getInt(key, defValue);
            remember("i", key, defValue, v);
            return v;
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        Object c = cached("s", key, defValue);
        if (c != null) return c == NULL ? defValue : (String) c;
        try {
            String v = super.getString(key, defValue);
            remember("s", key, defValue, v);
            return v;
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object c = cached("f", key, defValue);
        if (c != null && c != NULL) return (Float) c;
        try {
            float v = super.getFloat(key, defValue);
            remember("f", key, defValue, v);
            return v;
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    public long getLong(String key, long defValue) {
        Object c = cached("l", key, defValue);
        if (c != null && c != NULL) return (Long) c;
        try {
            long v = super.getLong(key, defValue);
            remember("l", key, defValue, v);
            return v;
        } catch (RemotePreferenceAccessException e) {
            return defValue;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValue) {
        Object c = cached("ss", key, defValue);
        if (c != null) return c == NULL ? defValue : (Set<String>) c;
        try {
            Set<String> v = super.getStringSet(key, defValue);
            remember("ss", key, defValue, v);
            return v;
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
