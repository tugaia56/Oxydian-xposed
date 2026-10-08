package it.tugaia56.obsidian.xposed.hooks.systemui;

import static android.view.MotionEvent.ACTION_DOWN;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Zona gesto Indietro + stile pillola di navigazione — porting reale di OC's
 * GestureNavbarManager, limitato a: (a) restrizione dell'altezza della zona
 * attiva del gesto Indietro per lato (dual-slider min/max), rispettando
 * rotazione schermo e presenza del launcher in foreground; (b) colore
 * accento e larghezza della pillola (OplusNavigationHandle/OplusNavigationBarInflaterView).
 *
 * L'override "tieni premuto Indietro" e' stato rimosso (già
 * funzionante, non duplicato qui). Lo scambio icona durante il gesto fisico
 * (SideGestureNavView.setAppIcon in OC) NON è portato — richiede accesso alle
 * icone delle app dal processo SystemUI e non è collegato a nessuna UI
 * esistente: non funziona.
 */
public class GestureNavZones extends XposedMods {

    private static final String PREF_MASTER         = "OBS_NAV_GESTURE_MASTER";
    private static final String PREF_LEFT_ON        = "OBS_NAV_GESTURE_LEFT";
    private static final String PREF_LEFT_MIN        = "OBS_NAV_GESTURE_LEFT_HEIGHT_MIN";
    private static final String PREF_LEFT_MAX        = "OBS_NAV_GESTURE_LEFT_HEIGHT_MAX";
    private static final String PREF_RIGHT_ON       = "OBS_NAV_GESTURE_RIGHT";
    private static final String PREF_RIGHT_MIN       = "OBS_NAV_GESTURE_RIGHT_HEIGHT_MIN";
    private static final String PREF_RIGHT_MAX       = "OBS_NAV_GESTURE_RIGHT_HEIGHT_MAX";
    private static final String PREF_ON_ROTATE      = "OBS_NAV_GESTURE_ON_ROTATE";
    private static final String PREF_PILL_ACCENT    = "OBS_NAV_PILL_ACCENT";
    private static final String PREF_PILL_WIDTH     = "OBS_NAV_PILL_WIDTH";

    private static final int STOCK_LIGHT = 0xEBFFFFFF, STOCK_DARK = 0x99000000;

    private boolean leftEnabled = true, rightEnabled = true, onRotateToo = true;
    private int leftMin = 0, leftMax = 100, rightMin = 0, rightMax = 100;
    private boolean pillColorAccent = false;
    private float widthFactor = 1f;

    private Object mSideGestureConfiguration = null;
    private Object mNavigationBarInflaterView = null;
    private boolean colorReplaced = false;

    public GestureNavZones(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;

        leftEnabled  = Xprefs.getBoolean(PREF_LEFT_ON, true);
        rightEnabled = Xprefs.getBoolean(PREF_RIGHT_ON, true);
        leftMin  = Xprefs.getInt(PREF_LEFT_MIN, 0);
        leftMax  = Xprefs.getInt(PREF_LEFT_MAX, 100);
        rightMin = Xprefs.getInt(PREF_RIGHT_MIN, 0);
        rightMax = Xprefs.getInt(PREF_RIGHT_MAX, 100);
        onRotateToo = Xprefs.getBoolean(PREF_ON_ROTATE, true);

        // Interruttore generale spento: il gesto indietro resta quello del sistema (entrambi i lati, tutta l'altezza)
        if (!Xprefs.getBoolean(PREF_MASTER, false)) {
            leftEnabled = true; rightEnabled = true; onRotateToo = true;
            leftMin = 0; leftMax = 100; rightMin = 0; rightMax = 100;
        }

        pillColorAccent = Xprefs.getBoolean(PREF_PILL_ACCENT, false);
        widthFactor = Xprefs.getInt(PREF_PILL_WIDTH, 50) * .02f;

        if (Key.length > 0 && (PREF_PILL_ACCENT.equals(Key[0]) || PREF_PILL_WIDTH.equals(Key[0]))) {
            refreshNavbar();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> sideGestureDetector;
        try {
            sideGestureDetector = findClass(
                    "com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector",
                    lpparam.classLoader);
        } catch (Throwable t) {
            try {
                sideGestureDetector = findClass(
                        "com.oplusos.systemui.navigationbar.gesture.sidegesture.SideGestureDetector",
                        lpparam.classLoader); // OOS13
            } catch (Throwable t2) {
                XposedBridge.log("[ Obsidian ] GestureNavZones: SideGestureDetector not found: " + t2);
                sideGestureDetector = null;
            }
        }

        if (sideGestureDetector != null) {
            hookGestureZone(sideGestureDetector);
        }

        hookNavPill(lpparam);
    }

    private void hookGestureZone(Class<?> sideGestureDetector) {
        if (Build.VERSION.SDK_INT >= 34) {
            XposedBridge.hookAllConstructors(sideGestureDetector, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        mSideGestureConfiguration = getObjectField(param.thisObject, "mSideGestureConfiguration");
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.hookAllMethods(sideGestureDetector, "onMotionEventImpl", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mSideGestureConfiguration == null) return;
                    MotionEvent ev = (MotionEvent) param.args[0];
                    if (isLauncherForeground()) return;

                    if (ev.getActionMasked() == ACTION_DOWN) {
                        Point displaySize = (Point) getObjectField(param.thisObject, "mDisplaySize");
                        int bottomGestureHeight = (int) callMethod(mSideGestureConfiguration, "getBottomGestureAreaHeight");
                        int rotation = (int) getFloatField(param.thisObject, "mRotation");
                        if (notWithinInsets(ev.getX(), ev.getY(), displaySize, bottomGestureHeight, rotation)) {
                            setObjectField(param.thisObject, "mAllowGesture", false);
                            param.setResult(null);
                        }
                    }
                }
            });
        } else {
            XposedBridge.hookAllMethods(sideGestureDetector, "isWithinInsets", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int x = (int) param.args[0];
                    int y = (int) param.args[1];
                    if (isLauncherForeground()) return;

                    Point displaySize = (Point) getObjectField(param.thisObject, "mDisplaySize");
                    int bottomGestureHeight = mContext.getResources().getDimensionPixelSize(
                            mContext.getResources().getIdentifier("bottom_gesture_area_height", "dimen", SYSTEM_UI));
                    int rotation = (int) getFloatField(param.thisObject, "mRotation");
                    if (notWithinInsets(x, y, displaySize, bottomGestureHeight, rotation)) {
                        setObjectField(param.thisObject, "mAllowGesture", false);
                        param.setResult(false);
                    }
                }
            });
        }
    }

    private boolean notWithinInsets(float x, float y, Point displaySize, float bottomGestureHeight, int rotation) {
        if (y >= (displaySize.y - bottomGestureHeight)) return false; // zona base sempre attiva

        boolean isLeftSide = x < (displaySize.x / 3f);
        if (!onRotateToo && (rotation == 1 || rotation == 3)) return false;
        if ((isLeftSide && !leftEnabled) || (!isLeftSide && !rightEnabled)) return true;

        float top = (isLeftSide ? leftMax : rightMax) / 100f;
        float bottom = (isLeftSide ? leftMin : rightMin) / 100f;

        return y < (displaySize.y - bottomGestureHeight - Math.round(displaySize.y * top))
                || y > (displaySize.y - bottomGestureHeight - Math.round(displaySize.y * bottom));
    }

    private void hookNavPill(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> navigationHandle;
        try {
            navigationHandle = findClass(
                    "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle", lpparam.classLoader);
        } catch (Throwable t) {
            try {
                navigationHandle = findClass(
                        "com.oplusos.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle", lpparam.classLoader); // OOS13
            } catch (Throwable t2) {
                XposedBridge.log("[ Obsidian ] GestureNavZones: OplusNavigationHandle not found: " + t2);
                navigationHandle = null;
            }
        }

        Class<?> inflaterView;
        try {
            inflaterView = findClass(
                    "com.oplusos.systemui.navigationbar.OplusNavigationBarInflaterView", lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] GestureNavZones: OplusNavigationBarInflaterView not found: " + t);
            inflaterView = null;
        }

        if (navigationHandle != null) {
            XposedBridge.hookAllMethods(navigationHandle, "setDarkIntensity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!pillColorAccent && !colorReplaced) return;
                    try {
                        setObjectField(param.thisObject, "mLightColor", pillColorAccent
                                ? mContext.getResources().getColor(android.R.color.system_accent1_200, mContext.getTheme())
                                : STOCK_LIGHT);
                        setObjectField(param.thisObject, "mDarkColor", pillColorAccent
                                ? mContext.getResources().getColor(android.R.color.system_accent1_600, mContext.getTheme())
                                : STOCK_DARK);
                        colorReplaced = true;
                    } catch (Throwable ignored) {}
                }
            });

            XposedBridge.hookAllMethods(navigationHandle, "setVertical", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (widthFactor == 1f) return;
                    try {
                        View result = (View) param.thisObject;
                        ViewGroup.LayoutParams lp = result.getLayoutParams();
                        int originalWidth;
                        try {
                            originalWidth = (int) getAdditionalInstanceField(param.thisObject, "originalWidth");
                        } catch (Throwable ignored) {
                            originalWidth = lp.width;
                            setAdditionalInstanceField(param.thisObject, "originalWidth", originalWidth);
                        }
                        lp.width = Math.round(originalWidth * widthFactor);
                    } catch (Throwable ignored) {}
                }
            });
        }

        if (inflaterView != null) {
            XposedBridge.hookAllConstructors(inflaterView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    mNavigationBarInflaterView = param.thisObject;
                    refreshNavbar();
                }
            });

            XposedBridge.hookAllMethods(inflaterView, "createView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (widthFactor == 1f) return;
                    try {
                        String button = (String) callMethod(param.thisObject, "extractButton", param.args[0]);
                        if (!"home_handle".equals(button)) return;
                        View result = (View) param.getResult();
                        ViewGroup.LayoutParams lp = result.getLayoutParams();
                        lp.width = Math.round(lp.width * widthFactor);
                        result.setLayoutParams(lp);
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    private void refreshNavbar() {
        if (mNavigationBarInflaterView == null) return;
        try { callMethod(mNavigationBarInflaterView, "updateLayout"); } catch (Throwable ignored) {}
    }

    private boolean isLauncherForeground() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            String foreground = null;
            for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    foreground = p.processName;
                    break;
                }
            }
            return foreground != null && foreground.equals(getDefaultLauncherPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private String getDefaultLauncherPackageName() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null ? resolveInfo.activityInfo.packageName : null;
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
