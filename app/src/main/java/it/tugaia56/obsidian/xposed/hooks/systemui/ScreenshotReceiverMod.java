package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.UUID;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Screenshot nativo OxygenOS richiesto da fuori SystemUI (Riavvio Avanzato > Screenshot): registra
 * un ricevitore protetto dal permesso DUMP, raggiungibile solo da shell/root.
 * (Prima stava dentro la mod "Sostituisci gesto indietro", ora rimossa.)
 */
public class ScreenshotReceiverMod extends XposedMods {

    public ScreenshotReceiverMod(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {}

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        registerScreenshotReceiver();
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
            XposedBridge.log("[ Obsidian ] ScreenshotReceiverMod: screenshot receiver registered");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] ScreenshotReceiverMod: screenshot receiver FAILED: " + t);
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
                XposedBridge.log("[ Obsidian ] ScreenshotReceiverMod.takeScreenshot ERROR: " + t);
            }
        }, 250L);
    }

    @Override
    public boolean listensTo(String packageName) {
        return SYSTEM_UI.equals(packageName);
    }
}
