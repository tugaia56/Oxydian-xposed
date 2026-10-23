package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.os.SystemClock;
import android.util.SparseIntArray;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Nasconde l'icona di segnale di una SIM nella barra di stato lasciandola attiva. Si aggancia a
 * setVisibleState() delle icone mobili (ModernStatusBarMobileView e la variante Oplus): se la
 * SIM della vista e' quella da nascondere, lo stato diventa STATE_HIDDEN. Lo slot si ricava dal
 * subId con SubscriptionManager.getSlotIndex().
 */
public class SimIconHideMod extends XposedMods {

    private static final String PREF_HIDE_SIM1 = "OBS_STATUSBAR_HIDE_SIM1";
    private static final String PREF_HIDE_SIM2 = "OBS_STATUSBAR_HIDE_SIM2";
    private static final int STATE_HIDDEN = 2;
    private static final int TAG_KEY = 0x7f0a0fff;   // chiave tag locale (id inventato, solo per questa mod)

    private boolean mHideSim1 = false;
    private boolean mHideSim2 = false;

    /** Viste mobili conosciute e ultimo stato richiesto dal sistema (per riapplicare al cambio opzione). */
    private final Map<View, Integer> mViews = new WeakHashMap<>();

    public SimIconHideMod(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mHideSim1 = Xprefs.getBoolean(PREF_HIDE_SIM1, false);
        mHideSim2 = Xprefs.getBoolean(PREF_HIDE_SIM2, false);
        if (Key.length > 0 && (PREF_HIDE_SIM1.equals(Key[0]) || PREF_HIDE_SIM2.equals(Key[0]))) {
            synchronized (mViews) {
                for (Map.Entry<View, Integer> e : mViews.entrySet()) {
                    final View v = e.getKey();
                    final int state = e.getValue();
                    v.post(() -> {
                        try {
                            callMethod(v, "setVisibleState", state);
                            // opzione spenta: la vista non e' piu' da tenere nascosta
                            if (!shouldHide(v) && state != STATE_HIDDEN && v.getVisibility() == View.GONE) v.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {}
                    });
                }
            }
        }
    }

    /** Segna la vista come gestita da questa mod: da quel momento setVisibility() la tiene GONE finche' la SIM va nascosta
     *  (vedi l'aggancio a View.setVisibility). Niente listener di layout: rimettere GONE a ogni layout mentre il sistema
     *  la rimostrava produceva un ciclo di misura/layout a ogni frame (SystemUI al 65% di CPU, blocco del PIN). */
    private void ensureCollapsed(View v) {
        if (v.getTag(TAG_KEY) == null) v.setTag(TAG_KEY, Boolean.TRUE);
    }

    /** subId di ogni icona (ricordato per 2 secondi) e slot per subId (5 secondi): shouldHide gira a ogni misura/layout
     *  delle icone e la ricerca per riflessione (XposedHelpers.callMethod) a ogni chiamata saturava il thread principale di SystemUI. */
    private final Map<View, long[]> mSubIds = new WeakHashMap<>();
    private final SparseIntArray mSlots = new SparseIntArray();
    private long mSlotsAt;
    private boolean mLoggedFail;

    private int subIdOf(View v) {
        long now = SystemClock.elapsedRealtime();
        synchronized (mSubIds) {
            long[] c = mSubIds.get(v);
            if (c != null && now - c[1] < 2000) return (int) c[0];
        }
        int subId = (Integer) callMethod(v, "getSubId");
        synchronized (mSubIds) { mSubIds.put(v, new long[]{subId, now}); }
        return subId;
    }

    private boolean shouldHide(View v) {
        if (!mHideSim1 && !mHideSim2) return false;
        try {
            int subId = subIdOf(v);
            int slot;
            synchronized (mSlots) {
                long now = SystemClock.elapsedRealtime();
                if (now - mSlotsAt > 5000) { mSlots.clear(); mSlotsAt = now; }
                slot = mSlots.get(subId, -2);
                if (slot == -2) {
                    slot = (Integer) callStaticMethod(android.telephony.SubscriptionManager.class, "getSlotIndex", subId);
                    mSlots.put(subId, slot);
                }
            }
            return (slot == 0 && mHideSim1) || (slot == 1 && mHideSim2);
        } catch (Throwable t) {
            if (!mLoggedFail) { mLoggedFail = true; XposedBridge.log("[ Obsidian ] SimIconHideMod.shouldHide failed: " + t); }
            return false;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    View v = (View) param.thisObject;
                    // la classe base serve anche Wi-Fi e altre icone: solo quelle mobili
                    if (!v.getClass().getName().contains("MobileView")) return;
                    int state = (Integer) param.args[0];
                    synchronized (mViews) { mViews.put(v, state); }
                    if (state != STATE_HIDDEN && shouldHide(v)) param.args[0] = STATE_HIDDEN;
                    ensureCollapsed(v);
                } catch (Throwable ignored) {}
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    View v = (View) param.thisObject;
                    if (v.getClass().getName().contains("MobileView") && shouldHide(v)
                            && v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                } catch (Throwable ignored) {}
            }
        };
        // Le viste gestite restano GONE: ogni setVisibility() del sistema su di loro viene riportato a GONE prima di
        // essere applicato (GONE su GONE non cambia nulla, quindi nessun nuovo layout).
        try {
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        View v = (View) param.thisObject;
                        if (v.getTag(TAG_KEY) == null) return;
                        if ((Integer) param.args[0] != View.GONE && shouldHide(v)) param.args[0] = View.GONE;
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] SimIconHideMod: hook su View.setVisibility fallito: " + t);
        }
        // Si aggancia la classe base: le sottoclassi (Oplus) non vengono intercettate da sole.
        try {
            hookAllMethods(findClass("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
                    lp.classLoader), "setVisibleState", hook);
            // il contenitore delle icone riserva lo spazio di ogni icona che si dichiara visibile
            hookAllMethods(findClass("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
                    lp.classLoader), "isIconVisible", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View v = (View) param.thisObject;
                        if (v.getClass().getName().contains("MobileView") && shouldHide(v)) param.setResult(false);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[Obsidian] SimIconHideMod: classe delle icone non trovata: " + t);
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
