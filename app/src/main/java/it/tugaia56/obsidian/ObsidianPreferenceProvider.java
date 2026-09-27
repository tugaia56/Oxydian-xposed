package it.tugaia56.obsidian;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.util.Log;

import com.crossbowffs.remotepreferences.RemotePreferenceFile;
import com.crossbowffs.remotepreferences.RemotePreferenceProvider;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;

public class ObsidianPreferenceProvider extends RemotePreferenceProvider {

    private static final String TAG       = "ObsidianCP";
    private static final String PREFS_NAME = "it.tugaia56.oxydian_preferences";

    public ObsidianPreferenceProvider() {
        super("it.tugaia56.oxydian", new RemotePreferenceFile[]{
            new RemotePreferenceFile(PREFS_NAME, true)  // worldReadable=true: consente a SystemUI di leggere
        });
    }

    /**
     * Pass a device-protected context to super so RemotePreferenceProvider.onCreate()
     * reads from /data/user_de/0/ (available before user unlock).
     */
    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context.createDeviceProtectedStorageContext(), info);
    }

    /**
     * Verify that the context is actually device-protected. If the attachInfo override
     * didn't work (Android 16 may handle it differently), fix SharedPreferences via
     * reflection so the ContentProvider always reads from DE storage.
     */
    @Override
    public boolean onCreate() {
        boolean result = super.onCreate();

        Context ctx = getContext();
        boolean isDE = (ctx != null) && ctx.isDeviceProtectedStorage();
        Log.d(TAG, "onCreate: isDeviceProtectedStorage=" + isDE);

        if (!isDE) {
            // attachInfo override did not stick — fallback: replace cached SharedPreferences
            // instances in RemotePreferenceProvider with DE-backed ones via reflection.
            Log.w(TAG, "Context is NOT DE-protected — applying reflection fix");
            fixPrefsToDeStorage();
        } else {
            // Sanity-check: log the value so we can confirm it's correct in logcat
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Log.d(TAG, "DE prefs OK: DST_ACCENT1_on=" + sp.getBoolean("DST_ACCENT1_on", false)
                    + "  DST_ACCENT1=0x" + Integer.toHexString(sp.getInt("DST_ACCENT1", 0)));
        }

        return result;
    }

    /**
     * Uses reflection to replace RemotePreferenceProvider's internal SharedPreferences
     * map entries with instances backed by device-protected storage.
     */
    private void fixPrefsToDeStorage() {
        try {
            Context deCtx = getContext().createDeviceProtectedStorageContext();

            // Walk the class hierarchy to find mPreferences in RemotePreferenceProvider
            Field field = null;
            for (Class<?> cls = getClass().getSuperclass(); cls != null; cls = cls.getSuperclass()) {
                try {
                    field = cls.getDeclaredField("mPreferences");
                    field.setAccessible(true);
                    break;
                } catch (NoSuchFieldException ignored) { /* try parent */ }
            }

            if (field == null) {
                Log.e(TAG, "mPreferences field not found in hierarchy — reflection fix skipped");
                return;
            }

            @SuppressWarnings("unchecked")
            HashMap<String, SharedPreferences> map =
                    (HashMap<String, SharedPreferences>) field.get(this);

            if (map == null) {
                Log.e(TAG, "mPreferences map is null");
                return;
            }

            for (String name : new HashSet<>(map.keySet())) {
                SharedPreferences dePrefs = deCtx.getSharedPreferences(name, Context.MODE_PRIVATE);
                map.put(name, dePrefs);
                Log.d(TAG, "Fixed " + name + ": DST_ACCENT1_on="
                        + dePrefs.getBoolean("DST_ACCENT1_on", false)
                        + "  DST_ACCENT1=0x"
                        + Integer.toHexString(dePrefs.getInt("DST_ACCENT1", 0)));
            }

        } catch (Throwable t) {
            Log.e(TAG, "fixPrefsToDeStorage failed: " + t);
        }
    }
}
