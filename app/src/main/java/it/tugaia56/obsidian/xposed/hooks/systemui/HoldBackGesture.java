package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Sostituisci gesto indietro (hold-back override) — real hook mirroring OC's
 * GestureNavbarManager, trimmed to the back-gesture-override portion only (pill
 * color/width/gesture-height sliders in the same UI screen are not yet hooked).
 *
 * Intercepts SideGestureDetector.switchApp() (fired by OOS when the hold-back
 * gesture completes) and, if enabled, runs our own action instead of the stock
 * "switch to previous app" behaviour.
 *
 * Action indices match dst_notif-style ordering: they are OUR OWN array position
 * (see R.array.gesture_holdback_commands_entries / NavbarStyleFragment), NOT OC's
 * internal action codes.
 *   0 = Passa all'app precedente  → no-op, let stock switchApp() run
 *   1 = Chiudi app attiva          → killForegroundApp()
 *   2 = Screenshot                 → takeScreenshot(FULL)
 *   3 = Screenshot scorrevole      → takeScreenshot(SCROLL)
 *   4 = Screenshot parziale        → takeScreenshot(PARTIAL)
 *   5 = Apri Impostazioni Rapide   → not yet implemented
 *   6 = Attiva modalità a una mano → not yet implemented
 *   7 = Apri pannello notifiche    → not yet implemented
 *   8 = Spegni schermo             → goToSleep()
 *   9 = Cerchia per cercare        → not yet implemented
 *  10 = App personalizzata         → not yet implemented
 */
public class HoldBackGesture extends XposedMods {

    private static final String PREF_ON    = "OBS_NAV_HOLDBACK_ON";
    private static final String PREF_MODE  = "OBS_NAV_HOLDBACK_MODE";
    private static final String PREF_LEFT  = "OBS_NAV_HOLDBACK_LEFT";
    private static final String PREF_RIGHT = "OBS_NAV_HOLDBACK_RIGHT";

    private boolean mEnabled = false;
    private int mMode  = 0;
    private int mLeft  = 0;
    private int mRight = 0;

    public HoldBackGesture(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mEnabled = Xprefs.getBoolean(PREF_ON, false);
        mMode    = parseInt(Xprefs.getString(PREF_MODE, "0"), 0);
        mLeft    = parseInt(Xprefs.getString(PREF_LEFT, "0"), 0);
        mRight   = parseInt(Xprefs.getString(PREF_RIGHT, "0"), 0);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        registerScreenshotReceiver();
        Class<?> sideGestureDetector;
        try {
            sideGestureDetector = findClass(
                    "com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector",
                    lpparam.classLoader);
        } catch (Throwable t) {
            try {
                sideGestureDetector = findClass(
                        "com.oplusos.systemui.navigationbar.gesture.sidegesture.SideGestureDetector",
                        lpparam.classLoader); // OOS13 path
            } catch (Throwable t2) {
                XposedBridge.log("[ Obsidian ] HoldBackGesture: SideGestureDetector not found: " + t2);
                return;
            }
        }

        XposedBridge.hookAllMethods(sideGestureDetector, "switchApp", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (!mEnabled) return;
                    int side;
                    try { side = (int) callMethod(param.thisObject, "getSide"); }
                    catch (Throwable t) { side = 0; }

                    int action = (mMode == 0) ? mLeft : (side == 0 ? mLeft : mRight);
                    if (action == 0) return; // passthrough: let stock switchApp() run

                    param.setResult(null); // suppress stock behaviour, we handle it
                    runAction(action);
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] HoldBackGesture.switchApp hook ERROR: " + t);
                }
            }
        });
        XposedBridge.log("[ Obsidian ] HoldBackGesture: hooked SideGestureDetector.switchApp");
    }

    private void runAction(int action) {
        switch (action) {
            case 1: killForegroundApp(); break;
            case 2: takeScreenshot("systemQuickTileScreenshotIn"); break;
            case 3: takeScreenshot("systemQuickTileLongshot"); break;
            case 4: takeScreenshot("systemQuickTileArea"); break;
            case 8: goToSleep(); break;
            default: notAvailable(); break;
        }
    }

    private void notAvailable() {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(mContext, "Non ancora disponibile", Toast.LENGTH_SHORT).show());
    }

    // ── Kill foreground app ───────────────────────────────────────────────────

    private void killForegroundApp() {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                String foreground = null;
                for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
                    if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        foreground = p.processName;
                        break;
                    }
                }
                if (foreground == null || foreground.equals(SYSTEM_UI)) {
                    Toast.makeText(mContext, "Niente da chiudere", Toast.LENGTH_SHORT).show();
                    return;
                }
                Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop " + foreground});
                Toast.makeText(mContext, "Chiusa: " + foreground, Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] HoldBackGesture.killForegroundApp ERROR: " + t);
            }
        });
    }

    // ── Screenshot da fuori SystemUI (Riavvio Avanzato → Screenshot) ───────────
    // Il comando root "screencap" non basta su tutti i telefoni OOS: l'app lancia un broadcast
    // da shell root e SystemUI scatta lo screenshot nativo OPLUS (come il gesto). Il permesso
    // DUMP sul ricevitore lo rende raggiungibile solo da shell/root, non da app normali.
    public static final String ACTION_TAKE_SCREENSHOT = "it.tugaia56.oxydian.ACTION_TAKE_SCREENSHOT";
    private static boolean sScreenshotReceiverRegistered = false;

    private void registerScreenshotReceiver() {
        if (sScreenshotReceiverRegistered) return;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    takeScreenshot("systemQuickTileScreenshotIn");
                    if (isOrderedBroadcast()) setResultCode(42); // l'app sa che SystemUI ha risposto
                }
            };
            mContext.registerReceiver(receiver, new IntentFilter(ACTION_TAKE_SCREENSHOT),
                    "android.permission.DUMP", null, Context.RECEIVER_EXPORTED);
            sScreenshotReceiverRegistered = true;
            XposedBridge.log("[ Obsidian ] HoldBackGesture: screenshot receiver registered");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] HoldBackGesture: screenshot receiver FAILED: " + t);
        }
    }

    // ── Screenshot (OPLUS internal screenshot manager, same mechanism as OC) ───

    private void takeScreenshot(String source) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Context screenshotCtx = mContext.createPackageContext(
                        "com.oplus.screenshot", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                Class<?> utils = Class.forName("com.oplus.screenshot.OplusLongshotUtils",
                        false, screenshotCtx.getClassLoader());
                Object manager = utils.getMethod("getScreenshotManager", Context.class)
                        .invoke(null, mContext);
                if (manager == null) return;
                Bundle bundle = new Bundle();
                bundle.putString("screenshot_source", source);
                bundle.putString("screenshot_relation_id", UUID.randomUUID().toString().replace("-", ""));
                bundle.putLong("screenshot_start_time", SystemClock.uptimeMillis());
                bundle.putBoolean("statusbar_visible", false);
                bundle.putBoolean("navigationbar_visible", false);
                bundle.putBoolean("global_action_visible", false);
                bundle.putBoolean("screenshot_orientation",
                        mContext.getResources().getConfiguration().orientation == 2);
                callMethod(manager, "takeScreenshot", bundle);
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] HoldBackGesture.takeScreenshot ERROR: " + t);
            }
        }, 250L);
    }

    // ── Screen off ────────────────────────────────────────────────────────────

    private void goToSleep() {
        try {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] HoldBackGesture.goToSleep ERROR: " + t);
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
