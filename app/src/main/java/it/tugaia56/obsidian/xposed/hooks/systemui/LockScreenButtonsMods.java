package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Lock screen bottom buttons (mirrors OC's "Pulsanti inferiori Schermata di blocco"):
 *  - lockscreen_hide_lock_icon: hides the lock icon (all known class variants, OOS13-16)
 *  - lockscreen_affordance_remove_left / _right: hides the two bottom shortcut buttons
 */
public class LockScreenButtonsMods extends XposedMods {

    private boolean mHideLockIcon        = false;
    private boolean mRemoveLeftAffordance  = false;
    private boolean mRemoveRightAffordance = false;

    public LockScreenButtonsMods(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mHideLockIcon          = Xprefs.getBoolean("lockscreen_hide_lock_icon", false);
        // "Rimuovi pulsante sinistro/destro": tolti dal modulo (le ROM lo permettono da sole nelle impostazioni); un vecchio valore salvato non ha piu' effetto
        mRemoveLeftAffordance  = false;
        mRemoveRightAffordance = false;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;
        hookLockIcon(lp);
        hookAffordance(lp);
    }

    // ── Lock icon ────────────────────────────────────────────────────────────

    private void hookLockIcon(XC_LoadPackage.LoadPackageParam lp) {
        // AOAP pre-16 keyguard package
        try {
            Class<?> cls = XposedHelpers.findClass("com.android.keyguard.LockIconView", lp.classLoader);
            XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: found com.android.keyguard.LockIconView");
            hookAllMethods(cls, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (mHideLockIcon) ((View) p.thisObject).setVisibility(View.GONE);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: com.android.keyguard.LockIconView not found: " + t);
        }

        // OOS16: LockIconView moved to systemui package (several candidate paths)
        if (Build.VERSION.SDK_INT >= 36) {
            for (String className : new String[]{
                    "com.android.systemui.keyguard.ui.view.LockIconView",
                    "com.oplus.systemui.keyguard.lock.LockIconView",
                    "com.oplus.systemui.keyguard.ui.view.LockIconView"}) {
                try {
                    Class<?> cls = XposedHelpers.findClass(className, lp.classLoader);
                    XC_MethodHook hook = new XC_MethodHook() {
                        private boolean mDumped = false;
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (!mDumped) {
                                mDumped = true;
                                XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: " + className
                                        + " view fired, class=" + p.thisObject.getClass().getName());
                            }
                            if (mHideLockIcon) ((View) p.thisObject).setVisibility(View.GONE);
                        }
                    };
                    hookAllMethods(cls, "onFinishInflate", hook);
                    hookAllMethods(cls, "onAttachedToWindow", hook);
                    XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: hooked " + className);
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: " + className + " not found: " + t);
                }
            }
        }

        // OOS13-15 controller
        try {
            Class<?> cls = XposedHelpers.findClass("com.oplus.keyguard.OplusLockIconViewExImpl", lp.classLoader);
            XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: found OplusLockIconViewExImpl");
            hookAllMethods(cls, "addOplusIconView", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mHideLockIcon) return;
                    // Field names vary by build: older OOS uses "mLockIcon"/"mLockIconContainer",
                    // this OOS16 build uses Kotlin-style "lockIcon"/"lockIconContainer" (+ tips/space views).
                    for (String field : new String[]{
                            "mLockIconContainer", "mLockIcon",
                            "lockIconContainer", "lockIcon", "lockIconTipsView", "spaceView"}) {
                        try {
                            ((View) getObjectField(p.thisObject, field)).setVisibility(View.GONE);
                        } catch (Throwable ignored) {}
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: OplusLockIconViewExImpl not found: " + t);
        }
    }

    // ── Bottom shortcut buttons (affordance) ────────────────────────────────

    private void hookAffordance(XC_LoadPackage.LoadPackageParam lp) {
        if (Build.VERSION.SDK_INT >= 34) {
            Class<?> cls = tryFindClass(lp,
                    "com.oplus.systemui.keyguard.ui.binder.OplusKeyguardBottomAreaViewBinder",
                    "com.android.systemui.keyguard.ui.binder.KeyguardBottomAreaViewBinder");
            if (cls == null) return;

            hookAllMethods(cls, "updateButton", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(mRemoveLeftAffordance || mRemoveRightAffordance)) return;
                    try {
                        ImageView view = (ImageView) p.args[0];
                        if (view == null) return;
                        int startId = mContext.getResources().getIdentifier("start_button", "id", SYSTEM_UI);
                        int endId   = mContext.getResources().getIdentifier("end_button", "id", SYSTEM_UI);
                        if (view.getId() == startId && mRemoveLeftAffordance) view.setVisibility(View.GONE);
                        else if (view.getId() == endId && mRemoveRightAffordance) view.setVisibility(View.GONE);
                    } catch (Throwable ignored) {}
                }
            });

            hookAllMethods(cls, "access$updateButton", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(mRemoveLeftAffordance || mRemoveRightAffordance)) return;
                    try {
                        if (Build.VERSION.SDK_INT >= 36) {
                            ImageView view = (ImageView) p.args[2];
                            Object viewModel = p.args[3];
                            if (view == null) return;
                            String slotId = (String) getObjectField(viewModel, "slotId");
                            if ("bottom_start".equals(slotId) && mRemoveLeftAffordance) view.setVisibility(View.GONE);
                            else if ("bottom_end".equals(slotId) && mRemoveRightAffordance) view.setVisibility(View.GONE);
                        } else {
                            ImageView view = (ImageView) p.args[1];
                            if (view == null) return;
                            int startId = mContext.getResources().getIdentifier("start_button", "id", SYSTEM_UI);
                            int endId   = mContext.getResources().getIdentifier("end_button", "id", SYSTEM_UI);
                            if (view.getId() == startId && mRemoveLeftAffordance) view.setVisibility(View.GONE);
                            else if (view.getId() == endId && mRemoveRightAffordance) view.setVisibility(View.GONE);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } else {
            Class<?> cls = tryFindClass(lp, "com.android.systemui.statusbar.phone.KeyguardBottomAreaView");
            if (cls == null) return;

            hookAllMethods(cls, "updateCameraVisibility", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mRemoveRightAffordance) return;
                    try { ((View) getObjectField(p.thisObject, "mRightAffordanceView")).setVisibility(View.GONE); }
                    catch (Throwable ignored) {}
                }
            });
            hookAllMethods(cls, "updateLeftAffordanceVisibility", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mRemoveLeftAffordance) return;
                    try { ((View) getObjectField(p.thisObject, "mLeftAffordanceView")).setVisibility(View.GONE); }
                    catch (Throwable ignored) {}
                }
            });
        }
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return XposedHelpers.findClass(name, lp.classLoader); }
            catch (Throwable ignored) {}
        }
        XposedBridge.log("[ Obsidian ] LockScreenButtonsMods: none of " + java.util.Arrays.toString(names) + " found");
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
