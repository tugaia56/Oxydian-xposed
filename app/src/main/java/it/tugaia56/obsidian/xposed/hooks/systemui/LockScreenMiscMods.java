package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.ResourceManager;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Lock screen "Varie" (mirrors OC's lockscreen_misc_category, minus the random-PIN shuffle
 * and the capsule toggle — capsule removal dropped, it's needed for notifications now):
 *  - lockscreen_hide_sos: hides the bottom SOS/emergency button on the lock screen itself
 *  - lockscreen_hide_carrier: hides the carrier text
 *  - lockscreen_carrier_replacement: replaces the carrier text with a fixed custom string
 *  - lockscreen_hide_statusbar: hides the icon area + carrier together
 */
public class LockScreenMiscMods extends XposedMods {

    private boolean mHideSos          = false;
    private boolean mHideCarrier      = false;
    private boolean mHideStatusbar    = false;
    private String  mCarrierReplacement = "";

    private TextView mCarrierText = null;

    public LockScreenMiscMods(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mHideSos             = Xprefs.getBoolean("lockscreen_hide_sos", false);
        mHideCarrier         = Xprefs.getBoolean("lockscreen_hide_carrier", false);
        mHideStatusbar       = Xprefs.getBoolean("lockscreen_hide_statusbar", false);
        mCarrierReplacement  = Xprefs.getString("lockscreen_carrier_replacement", "");
        applyCarrierText();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        hookSosButton(lp);
        hookCarrier(lp);
        hookLockscreenLayout();
    }

    // ── SOS button ───────────────────────────────────────────────────────────

    private void hookSosButton(XC_LoadPackage.LoadPackageParam lp) {
        // Tastierino PIN a viste (RisingOS, crDroid): il controller mostra il pulsante in modo asincrono dopo l'aggiornamento, quindi
        // si intercetta View.setVisibility e, se la vista e' il pulsante Emergenza, si forza GONE
        try {
            final Class<?> btn = XposedHelpers.findClassIfExists("com.android.keyguard.EmergencyButton", lp.classLoader);
            if (btn != null) {
                XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (mHideSos && btn.isInstance(p.thisObject)) p.args[0] = View.GONE;
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: EmergencyButton visibility hook failed: " + t);
        }
        // Tastierino PIN in Compose (Android 16/17 puro): il pulsante Emergenza arriva da BouncerActionButtonInteractor.actionButton
        // (un Flow): con l'opzione accesa lo si sostituisce con un flusso che emette solo "nessun pulsante"
        try {
            Class<?> inter = XposedHelpers.findClassIfExists("com.android.systemui.bouncer.domain.interactor.BouncerActionButtonInteractor", lp.classLoader);
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: BouncerActionButtonInteractor class=" + inter + " hideSos=" + mHideSos);
            if (inter != null) {
                XposedBridge.hookAllConstructors(inter, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        XposedBridge.log("[ Obsidian ] LockScreenMiscMods: BouncerActionButtonInteractor created, hideSos=" + mHideSos);
                        if (!mHideSos) return;
                        try {
                            Class<?> flowKt = XposedHelpers.findClass("kotlinx.coroutines.flow.FlowKt", lp.classLoader);
                            Object empty = XposedHelpers.callStaticMethod(flowKt, "flowOf", new Class<?>[]{Object.class}, new Object[]{null});
                            XposedHelpers.setObjectField(p.thisObject, "actionButton", empty);
                            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: bouncer emergency button hidden");
                        } catch (Throwable t) {
                            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: hide bouncer emergency failed: " + t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: BouncerActionButtonInteractor hook failed: " + t);
        }
        // Android puro recente (RisingOS, crDroid): il pulsante e' governato da EmergencyButtonController, che lo mostra
        // o lo nasconde da solo: dopo il suo aggiornamento lo si rimette a GONE quando l'opzione e' accesa
        try {
            Class<?> ctrl = XposedHelpers.findClassIfExists("com.android.keyguard.EmergencyButtonController", lp.classLoader);
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: EmergencyButtonController class=" + ctrl);
            if (ctrl != null) {
                hookAllMethods(ctrl, "updateEmergencyCallButton", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!mHideSos) return;
                        try {
                            Object v = XposedHelpers.getObjectField(p.thisObject, "mView");
                            if (v instanceof View) ((View) v).setVisibility(View.GONE);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: EmergencyButtonController hook failed: " + t);
        }
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                Class<?> cls = XposedHelpers.findClass("com.android.keyguard.EmergencyButton", lp.classLoader);
                hookAllMethods(cls, "updateEmergencyCallButton", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mHideSos) return;
                        callMethod(p.thisObject, "setVisibility", View.GONE);
                        p.setResult(null);
                    }
                });
            } else if (Build.VERSION.SDK_INT >= 34) {
                Class<?> cls = XposedHelpers.findClass("com.oplus.keyguard.OplusEmergencyButtonExImpl", lp.classLoader);
                hookAllMethods(cls, "disableShowEmergencyButton", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (mHideSos) p.setResult(true);
                    }
                });
            } else {
                Class<?> cls = tryFindClass(lp,
                        "com.oplus.keyguard.EmergencyButton", "com.android.keyguard.EmergencyButton");
                if (cls == null) return;
                hookAllMethods(cls, "updateEmergencyCallButton", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (mHideSos) ((View) p.thisObject).setVisibility(View.GONE);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: SOS button hook failed: " + t);
        }
    }

    // ── Carrier text replacement ────────────────────────────────────────────

    private void hookCarrier(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.oplus.systemui.statusbar.widget.OplusStatCarrierTextController", lp.classLoader);
            hookAllMethods(cls, "updateCarrierInfo", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        TextView view = (TextView) getObjectField(p.thisObject, "mView");
                        int carrierId = mContext.getResources().getIdentifier(
                                "keyguard_carrier_text", "id", SYSTEM_UI);
                        if (view.getId() != carrierId) return;
                        mCarrierText = view;
                        applyCarrierText();
                        if (!TextUtils.isEmpty(mCarrierReplacement)) p.setResult(null);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: carrier controller not found: " + t);
        }
    }

    private void applyCarrierText() {
        if (mCarrierText != null && !TextUtils.isEmpty(mCarrierReplacement)) {
            mCarrierText.post(() -> mCarrierText.setText(mCarrierReplacement));
        }
    }

    // ── Carrier / status icon area visibility ────────────────────────────────

    private void hookLockscreenLayout() {
        XC_InitPackageResources.InitPackageResourcesParam resparam = ResourceManager.resparams.get(SYSTEM_UI);
        if (resparam == null) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: no resparam yet for " + SYSTEM_UI);
            return;
        }
        try {
            resparam.res.hookLayout(SYSTEM_UI, "layout", "keyguard_status_bar", new XC_LayoutInflated() {
                @SuppressLint("DiscouragedApi")
                @Override public void handleLayoutInflated(LayoutInflatedParam liparam) {
                    if (mHideCarrier || mHideStatusbar) hideById(liparam, "keyguard_carrier_text");
                    if (mHideStatusbar) hideById(liparam, "status_icon_area");
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenMiscMods: hookLayout failed: " + t);
        }
    }

    @SuppressLint("DiscouragedApi")
    private void hideById(XC_LayoutInflated.LayoutInflatedParam liparam, String idName) {
        try {
            View v = liparam.view.findViewById(liparam.res.getIdentifier(idName, "id", SYSTEM_UI));
            if (v == null) return;
            v.getLayoutParams().height = 0;
            v.setVisibility(View.INVISIBLE);
            v.requestLayout();
        } catch (Throwable ignored) {}
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return XposedHelpers.findClass(name, lp.classLoader); }
            catch (Throwable ignored) {}
        }
        XposedBridge.log("[ Obsidian ] LockScreenMiscMods: none of " + java.util.Arrays.toString(names) + " found");
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
