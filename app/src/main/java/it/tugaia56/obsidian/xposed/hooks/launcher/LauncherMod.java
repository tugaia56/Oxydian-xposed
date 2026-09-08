package it.tugaia56.obsidian.xposed.hooks.launcher;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticIntField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static de.robv.android.xposed.XposedHelpers.setStaticBooleanField;
import static it.tugaia56.obsidian.utils.Constants.Packages.LAUNCHER;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;
import static it.tugaia56.obsidian.xposed.utils.ViewHelper.dp2px;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.util.Pair;
import android.widget.Toast;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Real OC Launcher.java mechanism, ported one section at a time. So far: hide app labels
 * (Home/Drawer), the full "Recenti" section (Apri Dettagli App, Disabilita Pagina Recenti
 * Precedente, Sostituisci Blocco), Rimuovi Impaginazione (Home + Cartelle), Nascondi
 * Scroller, Comportamento Personalizzato Swipe Destro (Discover/Shelf panel), Sfondo
 * Dock (ported from OC's DockBackground.java — solid/"Materiale" blur via OplusBlurProperties,
 * Android 15+ only), and Riordina Layout Drawer (colonne del cassetto app). Rest of OC's
 * Launcher.java (Home/Folder columns-rows, force-dock) is next, one at a time so each can
 * be tested in isolation.
 *
 * Icone a Tema (Forza/Alternativa monocroma) was attempted and reverted 2026-08-28 — see
 * [[project_launcher_mods_rollout]] memory: the OEM's own themed-icon pipeline barely fires
 * on the test device (getIconThemeDrawable never called; getMonochrome only fires on a fresh
 * icon-cache build, which needs a genuine device reboot and even then wasn't visually
 * changing icons), so the feature never worked reliably and the user asked to drop it rather
 * than keep chasing it. Ported utility class GoogleMonochromeIconFactory.java was removed too.
 *
 * Forza Dock a Colonne was also attempted and reverted the same day: isDockerMax5 lifted the
 * cap check but the dock stayed at 5 icons regardless, and a follow-up dedicated
 * getDockerNumShownHotseatIcons hook (tried across 4 candidate classes, found via dex-string
 * grep) had zero effect either — "hooked OK" logs proved nothing since hookAllMethods no-ops
 * silently on a non-matching method name. Never confirmed working; dropped per user request.
 */
public class LauncherMod extends XposedMods {

    private static final String KEY_HIDE_DESKTOP_LABELS = "desktop_hide_app_labels";
    private static final String KEY_HIDE_DRAWER_LABELS   = "drawer_hide_app_labels";
    private static final String KEY_OPEN_APP_DETAILS     = "launcher_open_app_details";
    private static final String KEY_DISABLE_PREV_RECENTS = "disable_previous_recents";
    private static final String KEY_REPLACE_LOCK         = "replace_lock";
    private static final String KEY_REMOVE_HOME_PAGE     = "remove_home_pagination";
    private static final String KEY_REMOVE_FOLDER_PAGE   = "remove_folder_pagination";
    private static final String KEY_HIDE_SCROLLER        = "hide_scroller";
    private static final String KEY_SWIPE_RIGHT_ENABLED  = "launcher_custom_shelf_switch";
    private static final String KEY_SWIPE_RIGHT_MODE     = "laucher_shelf_custom"; // matches OC/UI exactly
    private static final String KEY_REARRANGE_HOME = "rearrange_home";
    private static final String KEY_LAUNCHER_COLUMNS = "launcher_columns";
    private static final String KEY_LAUNCHER_ROWS    = "launcher_rows";
    private static final String KEY_REARRANGE_DRAWER = "rearrange_drawer";
    private static final String KEY_DRAWER_COLUMNS   = "drawer_columns";
    private static final String KEY_REARRANGE_FOLDER   = "rearrange_folder";
    private static final String KEY_FOLDER_MAX_COLUMNS = "folder_max_columns";
    private static final String KEY_FOLDER_MAX_ROWS    = "folder_max_rows";
    private static final String KEY_REARRANGE_PREVIEW  = "rearrange_preview";
    private static final String KEY_DOCK_BG          = "dockBackground";
    private static final String KEY_DOCK_BG_MATERIAL = "dockBackgroundMaterial";
    private static final String KEY_DOCK_BG_AMOUNT   = "dockBackgroundMaterialAmount";
    private static final String KEY_DOCK_BG_RADIUS   = "dockBackgroundRadius";
    private static final String KEY_DOCK_MAX_LIMIT   = "removeDockMaxLimit";
    private static final String KEY_AUTO_CLOSE_FOLDER = "autoCloseFolder";
    private static final int[] DOCK_BLUR_AMOUNTS = { 240, 300, 480, 660, 800 };

    private boolean mHideDesktopLabels = false;
    private boolean mHideDrawerLabels  = false;
    private boolean mOpenAppDetails       = false;
    private boolean mDisablePrevRecents   = false;
    private boolean mReplaceLock          = false;
    private boolean mRemoveHomePagination   = false;
    private boolean mRemoveFolderPagination = false;
    private boolean mHideScroller           = false;
    private boolean mCustomShelfBehavior    = false;
    private int mShelfBehavior              = 2; // SHELF_STOCK — matches default when disabled

    private boolean mDockBackground = false;
    private boolean mDockBackgroundMaterial = false;
    private int mDockBackgroundBlurAmount = DOCK_BLUR_AMOUNTS[0];
    private int mDockBackgroundRadius = 30;
    private boolean mRemoveDockMaxLimit = false;
    private boolean mAutoCloseFolder = false;
    private Object mBlurProp; // com.android.launcher3.uioverrides.states.blurdrawable.OplusBlurProperties instance

    private boolean mRearrangeHome = false;
    private int mMaxColumns = 5;
    private int mMaxRows = 6;
    private boolean mRearrangeDrawer = false;
    private int mDrawerColumns = 4;
    private boolean mRearrangeFolder = false;
    private int mFolderColumns = 3;
    private int mFolderRows = 3;
    private boolean mFolderPreview = false;

    private View mFastScrollView;

    public LauncherMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... key) {
        if (Xprefs == null) return;
        mHideDesktopLabels   = Xprefs.getBoolean(KEY_HIDE_DESKTOP_LABELS, false);
        mHideDrawerLabels    = Xprefs.getBoolean(KEY_HIDE_DRAWER_LABELS, false);
        mOpenAppDetails      = Xprefs.getBoolean(KEY_OPEN_APP_DETAILS, false);
        mDisablePrevRecents  = Xprefs.getBoolean(KEY_DISABLE_PREV_RECENTS, false);
        mReplaceLock         = Xprefs.getBoolean(KEY_REPLACE_LOCK, false);
        mRemoveHomePagination   = Xprefs.getBoolean(KEY_REMOVE_HOME_PAGE, false);
        mRemoveFolderPagination = Xprefs.getBoolean(KEY_REMOVE_FOLDER_PAGE, false);
        mHideScroller           = Xprefs.getBoolean(KEY_HIDE_SCROLLER, false);
        mCustomShelfBehavior    = Xprefs.getBoolean(KEY_SWIPE_RIGHT_ENABLED, false);
        mShelfBehavior          = Xprefs.getInt(KEY_SWIPE_RIGHT_MODE, 2);

        mDockBackground         = Xprefs.getBoolean(KEY_DOCK_BG, false);
        mDockBackgroundMaterial = Xprefs.getBoolean(KEY_DOCK_BG_MATERIAL, false);
        int amountIndex = Xprefs.getInt(KEY_DOCK_BG_AMOUNT, 0);
        mDockBackgroundBlurAmount = DOCK_BLUR_AMOUNTS[Math.max(0, Math.min(amountIndex, DOCK_BLUR_AMOUNTS.length - 1))];
        mDockBackgroundRadius = Xprefs.getInt(KEY_DOCK_BG_RADIUS, 30);
        mRemoveDockMaxLimit = Xprefs.getBoolean(KEY_DOCK_MAX_LIMIT, false);
        mAutoCloseFolder = Xprefs.getBoolean(KEY_AUTO_CLOSE_FOLDER, false);

        mRearrangeHome = Xprefs.getBoolean(KEY_REARRANGE_HOME, false);
        mMaxColumns    = Xprefs.getInt(KEY_LAUNCHER_COLUMNS, 4);
        mMaxRows       = Xprefs.getInt(KEY_LAUNCHER_ROWS, 4);

        mRearrangeDrawer = Xprefs.getBoolean(KEY_REARRANGE_DRAWER, false);
        mDrawerColumns   = Xprefs.getInt(KEY_DRAWER_COLUMNS, 4);

        mRearrangeFolder = Xprefs.getBoolean(KEY_REARRANGE_FOLDER, false);
        mFolderColumns   = Xprefs.getInt(KEY_FOLDER_MAX_COLUMNS, 3);
        mFolderRows      = Xprefs.getInt(KEY_FOLDER_MAX_ROWS, 3);
        mFolderPreview   = Xprefs.getBoolean(KEY_REARRANGE_PREVIEW, false);

        if (key.length > 0 && KEY_HIDE_SCROLLER.equals(key[0])) {
            updateFastScroll();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        hookHideLabels(lpparam);
        hookOpenAppDetails(lpparam);
        hookDisablePreviousRecents(lpparam);
        hookReplaceLock(lpparam);
        hookRemovePagination(lpparam);
        hookHideScroller(lpparam);
        hookSwipeRightBehavior(lpparam);
        hookDockBackground(lpparam);
        hookRemoveDockMaxLimit(lpparam);
        hookAutoCloseFolder(lpparam);
        hookDrawerColumns(lpparam);
        hookHomeLayout(lpparam);
    }

    // ── Rimuovi limite icone Dock — porting da LuckyTool (github.com/luckyzyx/LuckyTool,
    // RemoveDockerMaxNumberLimit.kt), verificato presente su questo device reale via grep sul
    // dex di OplusLauncher.apk (2026-09-02) prima del porting. Un tentativo precedente
    // (isDockerMax5 + getDockerNumShownHotseatIcons, vedi commento più sopra) aveva fallito
    // perché puntava a classi legacy che su questa build non controllano più il vero limite.
    //
    // 2026-09-03: primo porting inefficace confermato dal vivo dall'utente (non riusciva ad
    // aggiungere icone) — decompilato di nuovo con jadx per trovare il VERO gate: è
    // HotseatDragController.isHotseatShouldAcceptOnDrop() → isOutOfNormalAreaSpace(), che
    // confronta il conteggio attuale con ExpandConfig.getHotseatNormalItemMaxCount() — quel
    // metodo a sua volta chiama proprio getHotseatNormalItemsMaxCountBy(boolean,boolean) (il
    // metodo già agganciato, firma corretta), quindi l'hook di per sé era quello giusto. Il bug
    // vero era nella condizione "solo se colonne home > limite attuale": su telefono (non
    // tablet) il valore stock è già hardcoded a 5 (vedi ExpandConfig sorgente reale), stesso
    // ordine di grandezza delle colonne home — la condizione non scattava mai. Fix: forzare
    // sempre un valore più alto (colonne+3, minimo 8) quando il toggle è ON, senza confronti.
    private void hookRemoveDockMaxLimit(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> expandConfig = lpparam.classLoader.loadClass(
                    "com.android.launcher3.hotseat.expand.ExpandConfig");
            Class<?> launcherAppState = lpparam.classLoader.loadClass(
                    "com.android.launcher3.LauncherAppState");
            findAndHookMethod(expandConfig, "getHotseatNormalItemsMaxCountBy",
                    boolean.class, boolean.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!mRemoveDockMaxLimit) return;
                    try {
                        int col = 5;
                        Object state = callStaticMethod(launcherAppState, "getInstanceNoCreate");
                        if (state != null) {
                            Object idp = callMethod(state, "getInvariantDeviceProfile");
                            if (idp != null) col = (Integer) callMethod(idp, "getNumColumns");
                        }
                        param.setResult(Math.max(col + 3, 8));
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            log("hookRemoveDockMaxLimit failed: " + t);
        }
    }

    // ── Chiudi automaticamente la cartella dopo aver aperto un'app — porting da LuckyTool
    // (EnableAutoCloseFolder.kt), verificato presente su questo device reale via grep sul dex
    // (2026-09-02). AbstractFloatingView.closeOpenViews() è il metodo generico del launcher che
    // chiude popup/overlay aperti; quando il bitmask "type" del chiamante include già
    // TYPE_FOLDER (il caso che scatta aprendo un'app da dentro una cartella) rinforziamo la
    // chiusura cercando a mano la cartella aperta nel DragLayer e chiamando close() su di essa —
    // hookAllMethods invece di un overload esatto perché il 4° parametro non è verificabile a
    // priori (stesso approccio difensivo già usato in hookHideLabels).
    private void hookAutoCloseFolder(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> afv = lpparam.classLoader.loadClass("com.android.launcher3.AbstractFloatingView");
            int typeFolder = getStaticIntField(afv, "TYPE_FOLDER");
            hookAllMethods(afv, "closeOpenViews", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!mAutoCloseFolder) return;
                    if (param.args.length != 4) return;
                    try {
                        if (!(param.args[1] instanceof Boolean) || !(param.args[2] instanceof Integer)) return;
                        boolean animate = (Boolean) param.args[1];
                        int type = (Integer) param.args[2];
                        if ((type & typeFolder) == 0) return;

                        Object activityContext = param.args[0];
                        Object dragLayerObj = callMethod(activityContext, "getDragLayer");
                        if (!(dragLayerObj instanceof ViewGroup)) return;
                        ViewGroup dragLayer = (ViewGroup) dragLayerObj;
                        for (int i = 0; i < dragLayer.getChildCount(); i++) {
                            View child = dragLayer.getChildAt(i);
                            if (!afv.isInstance(child)) continue;
                            Object isFolderObj = callMethod(child, "isOfType", typeFolder);
                            if (Boolean.TRUE.equals(isFolderObj)) {
                                callMethod(child, "close", animate);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            log("hookAutoCloseFolder failed: " + t);
        }
    }

    // ── Nascondi Etichette (Home/Drawer) ────────────────────────────────────
    private void hookHideLabels(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> bubbleTextView = lpparam.classLoader.loadClass("com.android.launcher3.BubbleTextView");
            // hookAllMethods (not findAndHookMethod with a guessed signature) — applyLabel has
            // multiple overloads across OOS versions, same approach OC's ReflectedClass.before() uses.
            hookAllMethods(bubbleTextView, "applyLabel", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int display = getIntField(param.thisObject, "mDisplay");
                    int drawerDisplay = 1;
                    try {
                        drawerDisplay = getStaticIntField(bubbleTextView, "DISPLAY_ALL_APPS");
                    } catch (Throwable ignored) {}

                    boolean hide = (display == drawerDisplay) ? mHideDrawerLabels : mHideDesktopLabels;
                    if (hide) param.setResult(null);
                }
            });
        } catch (Throwable t) {
            log("hookHideLabels failed: " + t);
        }
    }

    // ── Apri Dettagli App (tieni premuto un'icona in Recenti/Dock) ──────────
    private void hookOpenAppDetails(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> oplusTaskViewImpl = findClass("com.android.quickstep.views.OplusTaskViewImpl", lpparam.classLoader);
            hookAllMethods(oplusTaskViewImpl, "setIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View headerView = (View) callMethod(param.thisObject, "getHeaderView");
                        View iconView = (View) callMethod(headerView, "getTaskIcon");
                        View titleView = (View) callMethod(headerView, "getTitleTv");
                        Object task = callMethod(param.thisObject, "getTask");
                        if (task == null) return;
                        Object key = getObjectField(task, "key");
                        if (key == null) return;
                        String pkgName = (String) callMethod(key, "getPackageName");
                        int userId = getIntField(key, "userId");
                        AppDetailsClickListener listener = new AppDetailsClickListener(pkgName, userId);
                        iconView.setOnLongClickListener(listener);
                        titleView.setOnLongClickListener(listener);
                    } catch (Throwable t) {
                        log("hookOpenAppDetails (recents card) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("OplusTaskViewImpl not found: " + t);
        }

        try {
            Class<?> dockIconView = findClass("com.oplus.quickstep.dock.DockIconView", lpparam.classLoader);
            hookAllMethods(dockIconView, "setIcon", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object task = callMethod(param.thisObject, "getTask");
                        if (task == null) return;
                        Object key = getObjectField(task, "key");
                        if (key == null) return;
                        String pkgName = (String) callMethod(key, "getPackageName");
                        int userId = getIntField(key, "userId");
                        View iconView = (View) param.thisObject;
                        iconView.setOnLongClickListener(new AppDetailsClickListener(pkgName, userId));
                    } catch (Throwable t) {
                        log("hookOpenAppDetails (dock icon) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("DockIconView not found: " + t);
        }
    }

    private class AppDetailsClickListener implements View.OnLongClickListener {
        final String pkgName;
        final int userId;

        AppDetailsClickListener(String pkgName, int userId) {
            this.pkgName = pkgName;
            this.userId = userId;
        }

        @Override
        public boolean onLongClick(View v) {
            if (!mOpenAppDetails) return false;
            Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", pkgName, null));
            appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appDetails.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            appDetails.putExtra("userId", userId);
            mContext.startActivity(appDetails);
            return true;
        }
    }

    // ── Disabilita Pagina Recenti Precedente ────────────────────────────────
    private void hookDisablePreviousRecents(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> appFeatureUtils = findClass("com.android.common.util.AppFeatureUtils", lpparam.classLoader);
            hookAllMethods(appFeatureUtils, "isSupportAutoFocusToNextPageInOverviewState", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mDisablePrevRecents) param.setResult(false);
                }
            });
        } catch (Throwable t) {
            log("hookDisablePreviousRecents failed: " + t);
        }
    }

    // ── Sostituisci Blocco: lo swipe-up-e-tieni in Recenti chiude l'app invece di bloccarla ──
    private void hookReplaceLock(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        try {
            Class<?> stackTouchCtrl = findClass(
                    "com.android.quickstep.uioverrides.touchcontrollers.OplusStackTaskViewTouchCtrl", cl);
            hookAllMethods(stackTouchCtrl, "onDragEnd", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!mReplaceLock) return;
                    try {
                        Object recentsView = getObjectField(param.thisObject, "mRecentsView");
                        Object orientationHandler = callMethod(recentsView, "getPagedOrientationHandler");
                        int rotation = (int) callMethod(orientationHandler, "getRotation");
                        float displacement = (float) getObjectField(param.thisObject, "mDisplacement");
                        boolean isRtl = (boolean) getObjectField(param.thisObject, "mIsRtl");

                        boolean shouldKill;
                        if (rotation == 1) {
                            shouldKill = isRtl ? (displacement > 100.0f) : (displacement < -100.0f);
                        } else if (rotation == 3) {
                            shouldKill = isRtl ? (displacement < -100.0f) : (displacement > 100.0f);
                        } else {
                            shouldKill = (displacement > 200.0f);
                        }
                        if (!shouldKill) return;

                        param.setResult(null);
                        Object taskView = getObjectField(param.thisObject, "mTaskBeingDragged");
                        if (taskView == null) return;
                        Object task = callMethod(taskView, "getTask");
                        if (task == null) return;
                        killTaskPackage(taskView, task);
                    } catch (Throwable t) {
                        log("hookReplaceLock (stack onDragEnd) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("OplusStackTaskViewTouchCtrl not found: " + t);
        }

        try {
            Class<?> taskViewTouchCtrl = findClass(
                    "com.android.quickstep.uioverrides.touchcontrollers.OplusTaskViewTouchControllerImpl", cl);
            hookAllMethods(taskViewTouchCtrl, "onDragEnd", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!mReplaceLock) return;
                    try {
                        Object taskBeingDragged = getObjectField(param.thisObject, "mTaskBeingDragged");
                        setAdditionalInstanceField(taskBeingDragged, "mShouldKill", true);
                    } catch (Throwable t) {
                        log("hookReplaceLock (task onDragEnd) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("OplusTaskViewTouchControllerImpl not found: " + t);
        }

        XC_MethodHook killHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!mReplaceLock) return;
                try {
                    Object taskView = param.args[0];
                    boolean shouldKill;
                    try {
                        shouldKill = (boolean) getAdditionalInstanceField(taskView, "mShouldKill");
                    } catch (Throwable ignored) {
                        shouldKill = false;
                    }
                    if (!shouldKill) return;
                    param.setResult(null);
                    Object task = callMethod(taskView, "getTask");
                    if (task == null) return;
                    killTaskPackage(taskView, task);
                } catch (Throwable t) {
                    log("hookReplaceLock (lock manager) failed: " + t);
                }
            }
        };
        try {
            Class<?> lockManager = findClass("com.oplus.quickstep.applock.OplusLockManager", cl);
            hookAllMethods(lockManager, "unLockForPullDown", killHook);
            hookAllMethods(lockManager, "lockForPullDown", killHook);
        } catch (Throwable t) {
            log("OplusLockManager not found: " + t);
        }
    }

    private void killTaskPackage(Object taskView, Object task) {
        String packageName;
        try {
            packageName = (String) callMethod(task, "getPackageName");
        } catch (Throwable t) {
            log("killTaskPackage: getPackageName failed: " + t);
            return;
        }

        try {
            int accessibilityCloseId = mContext.getResources()
                    .getIdentifier("accessibility_close", "string", LAUNCHER);
            callMethod(taskView, "performAccessibilityAction", accessibilityCloseId, null);
        } catch (Throwable t) {
            log("killTaskPackage: dismiss card failed: " + t);
        }
        Toast.makeText(mContext, "App Killed", Toast.LENGTH_SHORT).show();

        // Actual kill happens off the touch-handling thread — forceStopPackageAsUser needs
        // FORCE_STOP_PACKAGES, which this launcher's priv-app whitelist doesn't grant on every
        // ROM (confirmed via SecurityException on this device); su fallback covers that case.
        final String pkg = packageName;
        new Thread(() -> forceStopPackage(pkg)).start();
    }

    private void forceStopPackage(String packageName) {
        try {
            callMethod(mContext.getSystemService(Context.ACTIVITY_SERVICE),
                    "forceStopPackageAsUser",
                    packageName,
                    callMethod(Process.myUserHandle(), "getIdentifier"));
            return;
        } catch (Throwable ignored) {
            // Expected when the launcher doesn't hold FORCE_STOP_PACKAGES — fall through to su.
        }
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop " + packageName});
            p.waitFor();
        } catch (Throwable t) {
            log("forceStopPackage: su fallback failed: " + t);
        }
    }

    // ── Rimuovi Impaginazione (Home + Cartelle, stesso hook per entrambe come in OC) ──────
    private void hookRemovePagination(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook pageIndicatorHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!mRemoveHomePagination && !mRemoveFolderPagination) return;
                View v = (View) param.thisObject;
                if (v.getParent() == null) return;
                String parentClass = v.getParent().getClass().getCanonicalName();
                if (parentClass == null) return;
                if (parentClass.equals("com.android.launcher3.OplusDragLayer")) {
                    if (mRemoveHomePagination) {
                        v.setVisibility(View.GONE);
                        param.setResult(null);
                    }
                } else if (parentClass.equals("android.widget.FrameLayout")) {
                    if (mRemoveFolderPagination) {
                        v.setVisibility(View.GONE);
                        param.setResult(null);
                    }
                }
            }
        };
        try {
            Class<?> pageIndicator = findClass("com.android.launcher.pageindicators.OplusPageIndicator", lpparam.classLoader);
            hookAllMethods(pageIndicator, "dispatchDraw", pageIndicatorHook);
            hookAllMethods(pageIndicator, "onDraw", pageIndicatorHook);
        } catch (Throwable t) {
            log("OplusPageIndicator not found: " + t);
        }

        try {
            Class<?> touchHelper = findClass("com.android.launcher.pageindicators.PageIndicatorTouchHelper", lpparam.classLoader);
            hookAllMethods(touchHelper, "dispatchTouchEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mRemoveHomePagination) param.setResult(false);
                }
            });
        } catch (Throwable t) {
            log("PageIndicatorTouchHelper not found: " + t);
        }
    }

    // ── Nascondi Scroller (barra a lettere del Drawer) ──────────────────────
    private void hookHideScroller(XC_LoadPackage.LoadPackageParam lpparam) {
        // Class name changed across OOS versions — try both, whichever exists on this ROM.
        String[] candidates = {
                "com.android.launcher3.allapps.OplusFastScrollLayout", // OOS 14-15
                "com.android.launcher3.allapps.LetterIndexFastScrollHelper" // OOS 13
        };
        for (String className : candidates) {
            try {
                Class<?> fastScrollClass = findClass(className, lpparam.classLoader);
                hookAllConstructors(fastScrollClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (className.endsWith("LetterIndexFastScrollHelper")) {
                                mFastScrollView = (View) getObjectField(param.thisObject, "mFastScroll");
                            } else {
                                mFastScrollView = (View) param.thisObject;
                            }
                            updateFastScroll();
                        } catch (Throwable t) {
                            log("hookHideScroller construction failed: " + t);
                        }
                    }
                });
                return; // found one candidate, no need to try the other
            } catch (Throwable ignored) {
                // try next candidate
            }
        }
        log("No fast-scroll class found on this ROM — Nascondi Scroller has no effect here");
    }

    private void updateFastScroll() {
        if (mFastScrollView == null) return;
        mFastScrollView.setVisibility(mHideScroller ? View.GONE : View.VISIBLE);
    }

    // ── Comportamento Personalizzato Swipe Destro (pannello Discover/Shelf) ─────────────────
    private void hookSwipeRightBehavior(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> overlayProxy = findClass("com.android.overlay.OverlayProxy", lpparam.classLoader);
            hookAllMethods(overlayProxy, "getAssistScreenType", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mCustomShelfBehavior) param.setResult(mShelfBehavior);
                }
            });
            hookAllMethods(overlayProxy, "setAssistScreenType", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mCustomShelfBehavior) param.args[0] = mShelfBehavior;
                }
            });
        } catch (Throwable t) {
            log("OverlayProxy not found: " + t);
        }

        try {
            Class<?> featureOption = findClass("com.android.common.config.FeatureOption", lpparam.classLoader);
            hookAllMethods(featureOption, "getSShelfAssistantEnable", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mCustomShelfBehavior) param.setResult(mShelfBehavior == 0);
                }
            });
            hookAllMethods(featureOption, "updateSupportShelfAssistant", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!mCustomShelfBehavior) return;
                    try {
                        if (param.thisObject != null) {
                            setBooleanField(param.thisObject, "sShelfAssistantEnable", mShelfBehavior == 0);
                        } else {
                            // Some OOS versions expose a static overload of this method.
                            setStaticBooleanField(featureOption, "sShelfAssistantEnable", mShelfBehavior == 0);
                        }
                    } catch (Throwable t) {
                        log("hookSwipeRightBehavior (updateSupportShelfAssistant) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("FeatureOption not found: " + t);
        }
    }

    // ── Sfondo Dock (solo/pieno o "Materiale"/sfocato, richiede OOS con blur di sistema) ────
    private void hookDockBackground(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT < 35) return; // stesso limite di OC — il blur di sistema serve Android 15+
        ClassLoader cl = lpparam.classLoader;

        try {
            Class<?> oplusHotseat = findClass("com.android.launcher3.OplusHotseat", cl);
            XposedBridge.log("[ Obsidian - LauncherMod DIAG ] OplusHotseat hook attached OK");

            hookAllMethods(oplusHotseat, "init", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.log("[ Obsidian - LauncherMod DIAG ] OplusHotseat.init called, bg=" + mDockBackground + " material=" + mDockBackgroundMaterial);
                        if (mDockBackground) {
                            callMethod(param.thisObject, "setDockerBackground");
                        } else if (mDockBackgroundMaterial) {
                            initDockBlurBackground(param.thisObject);
                        }
                    } catch (Throwable t) {
                        log("hookDockBackground (init) failed: " + t);
                    }
                }
            });

            hookAllMethods(oplusHotseat, "onAttachedToWindow", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        ViewGroup oplusHotseatView = (ViewGroup) param.thisObject;
                        oplusHotseatView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                oplusHotseatView.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (mDockBackgroundMaterial) {
                                    try {
                                        initDockBlurBackground(oplusHotseatView);
                                    } catch (Throwable t) {
                                        log("initDockBlurBackground (onAttachedToWindow) failed: " + t);
                                    }
                                }
                                return true;
                            }
                        });
                    } catch (Throwable t) {
                        log("hookDockBackground (onAttachedToWindow) failed: " + t);
                    }
                }
            });

            hookAllMethods(oplusHotseat, "onDraw", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.log("[ Obsidian - LauncherMod DIAG ] OplusHotseat.onDraw called, bg=" + mDockBackground + " material=" + mDockBackgroundMaterial);
                        if (mDockBackground) {
                            callMethod(param.thisObject, "setDockerBackground");
                        } else if (!mDockBackgroundMaterial) {
                            View shortcutsAndWidgets = (View) getObjectField(param.thisObject, "mShortcutsAndWidgets");
                            if (shortcutsAndWidgets.getBackground() != null) {
                                shortcutsAndWidgets.setBackground(null);
                            }
                        }
                    } catch (Throwable t) {
                        log("hookDockBackground (onDraw) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian - LauncherMod DIAG ] OplusHotseat not found: " + t);
        }
    }

    private void initDockBlurBackground(Object oplusHotseat) {
        try {
            View shortcutsAndWidgets = (View) getObjectField(oplusHotseat, "mShortcutsAndWidgets");
            Context context = ((View) oplusHotseat).getContext();
            ClassLoader cl = oplusHotseat.getClass().getClassLoader();
            Class<?> blurPropsClass = findClass(
                    "com.android.launcher3.uioverrides.states.blurdrawable.OplusBlurProperties", cl);
            Object blurColorParams = callStaticMethod(blurPropsClass, "getBlurColorParams", context);
            Object blendMode = callMethod(blurColorParams, "getBlendMode");
            Object blendColor = callMethod(blurColorParams, "getBlendColor");
            Object mixColor = callMethod(blurColorParams, "getMixColor");

            if (mBlurProp == null) {
                Object prepared = callStaticMethod(blurPropsClass, "prepareBlur",
                        shortcutsAndWidgets, context, true, true, 3);
                if (prepared == null) return;
                callMethod(prepared, "setBlurCornerRadius", dp2px(mContext, mDockBackgroundRadius), false);
                callMethod(prepared, "setBlurParams", mDockBackgroundBlurAmount, blendMode, blendColor, mixColor);
                mBlurProp = prepared;
                return;
            }

            Class<?> layerBlurDrawableClass = findClass(
                    "com.android.launcher3.uioverrides.states.blurdrawable.LayerBlurDrawable", cl);
            if (!layerBlurDrawableClass.isInstance(shortcutsAndWidgets.getBackground())) {
                callMethod(mBlurProp, "setBlurBgToView", shortcutsAndWidgets);
            }
            callMethod(mBlurProp, "setBlurCornerRadius", dp2px(mContext, mDockBackgroundRadius), false);
            callMethod(mBlurProp, "setBlurParams", mDockBackgroundBlurAmount, blendMode, blendColor, mixColor);
        } catch (Throwable t) {
            log("initDockBlurBackground failed: " + t);
        }
    }

    // ── Riordina Layout Drawer (colonne del cassetto app) + Cartelle (colonne/righe/anteprima) ──
    private void hookDrawerColumns(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        try {
            Class<?> gridOption = findClass("com.android.launcher3.InvariantDeviceProfile$GridOption", cl);
            hookAllConstructors(gridOption, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (mRearrangeDrawer) setIntField(param.thisObject, "numAllAppsColumns", mDrawerColumns);
                    } catch (Throwable t) {
                        log("hookDrawerColumns (GridOption) failed: " + t);
                    }
                    try {
                        if (mRearrangeFolder) {
                            setIntField(param.thisObject, "numFolderColumns", mFolderColumns);
                            setIntField(param.thisObject, "numFolderRows", mFolderRows);
                        }
                        if (mFolderPreview && mFolderColumns > 3) {
                            setIntField(param.thisObject, "numFolderPreview", mFolderColumns);
                        }
                    } catch (Throwable t) {
                        log("hookFolderLayout (GridOption) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("InvariantDeviceProfile$GridOption not found: " + t);
        }

        try {
            Class<?> allAppsParam = findClass("com.android.launcher.layoutparam.AllAppsParam", cl);
            hookAllMethods(allAppsParam, "getNumAllAppsColumns", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (mRearrangeDrawer) param.setResult(mDrawerColumns);
                }
            });
        } catch (Throwable t) {
            log("AllAppsParam not found: " + t);
        }

        // Anteprima cartella (icona chiusa) — mostra righe/colonne piene invece della miniatura
        // 2x2 di default, solo per le cartelle con un singolo item 1x1.
        try {
            Class<?> folderInfo = findClass("com.android.launcher3.model.data.FolderInfo", cl);
            hookAllMethods(folderInfo, "getPreviewRow", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!mFolderPreview) return;
                        int spanX = getIntField(param.thisObject, "spanX");
                        int spanY = getIntField(param.thisObject, "spanY");
                        if (spanX == 1 && spanY == 1) param.setResult(mFolderRows);
                    } catch (Throwable t) {
                        log("hookFolderLayout (getPreviewRow) failed: " + t);
                    }
                }
            });
            hookAllMethods(folderInfo, "getPreviewColumn", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!mFolderPreview) return;
                        int spanX = getIntField(param.thisObject, "spanX");
                        int spanY = getIntField(param.thisObject, "spanY");
                        if (spanX == 1 && spanY == 1) param.setResult(mFolderColumns);
                    } catch (Throwable t) {
                        log("hookFolderLayout (getPreviewColumn) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("FolderInfo not found: " + t);
        }
    }

    // ── Disposizione Home (colonne/righe griglia) — sblocca il selettore nativo OOS ──────────
    // Diverso dagli altri: non forza direttamente una griglia, allarga la lista di opzioni che
    // il menu nativo "Disposizione griglia" della Home offre — l'utente sceglie lì il valore
    // finale, dentro il nuovo intervallo Min..mMaxColumns/mMaxRows.
    private void hookHomeLayout(XC_LoadPackage.LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        final int MIN_COLS = 3;
        final int MIN_ROWS = 4;

        try {
            Class<?> uiConfig = findClass("com.android.launcher.UiConfig", cl);

            hookAllMethods(uiConfig, "isSupportLayout", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (mRearrangeHome) param.setResult(true);
                }
            });

            if (Build.VERSION.SDK_INT >= 36) { // OOS16
                hookAllMethods(uiConfig, "getSupportLayout", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (!mRearrangeHome) return;
                            ArrayList<Pair<Integer, Pair<Integer, Integer>>> list = new ArrayList<>();
                            for (int col = MIN_COLS; col <= mMaxColumns; col++) {
                                for (int row = MIN_ROWS; row <= mMaxRows; row++) {
                                    list.add(Pair.create(col, Pair.create(row, row)));
                                }
                            }
                            param.setResult(list);
                        } catch (Throwable t) {
                            log("hookHomeLayout (getSupportLayout) failed: " + t);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            log("UiConfig not found: " + t);
        }

        try {
            Class<?> toggleBarLayoutAdapter = findClass(
                    "com.android.launcher.togglebar.adapter.ToggleBarLayoutAdapter", cl);
            hookAllMethods(toggleBarLayoutAdapter, "initToggleBarLayoutConfigs", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!mRearrangeHome) return;
                        int[] minMaxRows = (int[]) getObjectField(param.thisObject, "MIN_MAX_ROW");
                        int[] minMaxColumns = (int[]) getObjectField(param.thisObject, "MIN_MAX_COLUMN");
                        minMaxRows[1] = mMaxRows;
                        minMaxColumns[1] = mMaxColumns;
                        setObjectField(param.thisObject, "MIN_MAX_ROW", minMaxRows);
                        setObjectField(param.thisObject, "MIN_MAX_COLUMN", minMaxColumns);
                    } catch (Throwable t) {
                        log("hookHomeLayout (initToggleBarLayoutConfigs) failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("ToggleBarLayoutAdapter not found: " + t);
        }
    }

    @Override
    public boolean listensTo(String packageName) {
        return LAUNCHER.equals(packageName);
    }
}
