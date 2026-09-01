package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;
import static de.robv.android.xposed.XposedHelpers.setFloatField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.io.FileInputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.R;
import it.tugaia56.obsidian.xposed.ResourceManager;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Misc mods (mirrors OC's "Varie" section):
 *  - misc_remove_rotate_floating: hides the floating rotate-screen button
 *  - remove_usb_dialog: suppresses the USB connection dialog
 *  - power_menu_hide_sos: hides the SOS/emergency entry in the power menu
 *  - show_advanced_reboot / advanced_reboot_auth: extra reboot button on the shutdown menu
 *    (Y-offset intentionally omitted — not requested)
 *  - block_clipboard_overlay: suppresses SystemUI's native "ClipboardOverlay" window (the
 *    "Invia al dispositivo" popup shown whenever any app copies text) — confirmed via logcat
 *    (WindowManager: addWindow ... window=Window{... ClipboardOverlay}, callingPackage=
 *    com.android.systemui) that this is a stock SystemUI window, not Google Play Services or
 *    any single app, and has no direct Settings toggle on this build.
 */
public class MiscMods extends XposedMods {

    private boolean mHideRotationButton  = false;
    private View    mRotationButton;
    private boolean mRemoveUsbDialog     = false;
    private boolean mHideSosPowerMenu    = false;
    private boolean mShowAdvancedReboot  = false;
    private boolean mAdvancedRebootAuth  = false;
    private int     mAdvancedRebootYOffset = 0;
    private boolean mBlockClipboardOverlay = false;
    private boolean mAdvRebootUseAccent  = true;
    private int     mAdvRebootCustomColor = 0xFF908DFF; // matches ObsidianTheme.DEFAULT_ACCENT

    // Riavvia/Spegni pill — independent from the Riavvio Avanzato button's own colour above.
    // mode is one of "stock" (leave OOS green/red/white alone) / "accent" / "custom".
    private String  mPowerMenuGradientMode = "accent";
    private int     mPowerMenuGradientCustomColor = 0xFF908DFF;
    private String  mPowerMenuBgMode = "stock";
    private int     mPowerMenuBgCustomColor = 0xFF908DFF;
    private boolean mPowerMenuBorderEnabled = false;
    private boolean mPowerMenuBorderUseAccent = true;
    private int     mPowerMenuBorderCustomColor = 0xFF908DFF;
    // Pallino (draggable thumb) colour/image — real field names confirmed via a live reflection
    // field dump of OplusShutdownView: "mHandlerColor" (int, default -1/white) + "mHandlerPaint"
    // (already-built Paint, not resource-driven so no getColor hook applies here) +
    // "mHandlerRectF" (its bounding box, same "populated only once actually drawn" behaviour as
    // mBarRectF — empty in a field dump taken before layout, fine once read from onDraw itself).
    private static final String POWER_MENU_HANDLER_IMAGE_SUBPATH = ".obsidian/power_menu_handler_image";
    private String  mPowerMenuHandlerMode = "stock";
    private int     mPowerMenuHandlerCustomColor = 0xFF908DFF;
    private float   mPowerMenuHandlerScale = 1.0f;
    private Bitmap  mPowerMenuHandlerBitmap;
    private long    mPowerMenuHandlerBitmapMtime = -1;

    // Pillolone background image ("power_menu_bg_mode" == "image") — file written by
    // PowerMenuFragment to the same relative path. Cached and only re-decoded when the file's
    // mtime changes, so a normal prefs sync doesn't re-decode the bitmap every time.
    private static final String POWER_MENU_BG_IMAGE_SUBPATH = ".obsidian/power_menu_bg_image";
    private Bitmap mPowerMenuBgBitmap;
    private long   mPowerMenuBgBitmapMtime = -1;

    // Sfondo Menù Power — background of the WHOLE popup window (Window.setBackgroundDrawable
    // on the GlobalActions Dialog itself), independent from the pillolone's own background
    // above. Same "stock"/"accent"/"custom"/"image" shape, own pref keys/file.
    private static final String POWER_MENU_MENU_BG_IMAGE_SUBPATH = ".obsidian/power_menu_menu_bg_image";
    private String  mPowerMenuMenuBgMode = "stock";
    private int     mPowerMenuMenuBgCustomColor = 0xFF908DFF;
    private Bitmap  mPowerMenuMenuBgBitmap;
    private long    mPowerMenuMenuBgBitmapMtime = -1;

    private Drawable mAdvancedRebootDrawable;
    private int mCenterX, mCenterY, mRadius;

    public MiscMods(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mHideRotationButton = Xprefs.getBoolean("misc_remove_rotate_floating", false);
        mRemoveUsbDialog    = Xprefs.getBoolean("remove_usb_dialog", false);
        mHideSosPowerMenu   = Xprefs.getBoolean("power_menu_hide_sos", false);
        mShowAdvancedReboot = Xprefs.getBoolean("show_advanced_reboot", false);
        mAdvancedRebootAuth = Xprefs.getBoolean("advanced_reboot_auth", false);
        mAdvancedRebootYOffset = Xprefs.getInt("advanced_reboot_y_offset", 0);
        mBlockClipboardOverlay = Xprefs.getBoolean("block_clipboard_overlay", false);
        mAdvRebootUseAccent   = Xprefs.getBoolean("advanced_reboot_use_accent", true);
        mAdvRebootCustomColor = Xprefs.getInt("advanced_reboot_custom_color", 0xFF908DFF);
        mPowerMenuGradientMode = Xprefs.getString("power_menu_gradient_mode", "accent");
        mPowerMenuGradientCustomColor = Xprefs.getInt("power_menu_gradient_custom_color", 0xFF908DFF);
        mPowerMenuBgMode = Xprefs.getString("power_menu_bg_mode", "stock");
        mPowerMenuBgCustomColor = Xprefs.getInt("power_menu_bg_custom_color", 0xFF908DFF);
        mPowerMenuBorderEnabled = Xprefs.getBoolean("power_menu_border_enabled", false);
        mPowerMenuBorderUseAccent = Xprefs.getBoolean("power_menu_border_use_accent", true);
        mPowerMenuBorderCustomColor = Xprefs.getInt("power_menu_border_custom_color", 0xFF908DFF);
        mPowerMenuHandlerMode = Xprefs.getString("power_menu_handler_mode", "stock");
        mPowerMenuHandlerCustomColor = Xprefs.getInt("power_menu_handler_custom_color", 0xFF908DFF);
        mPowerMenuHandlerScale = Xprefs.getFloat("power_menu_handler_scale", 1.0f);
        mPowerMenuMenuBgMode = Xprefs.getString("power_menu_menu_bg_mode", "stock");
        mPowerMenuMenuBgCustomColor = Xprefs.getInt("power_menu_menu_bg_custom_color", 0xFF908DFF);
        refreshPowerMenuBgBitmap();
        refreshPowerMenuMenuBgBitmap();
        refreshPowerMenuHandlerBitmap();
        if (Key.length > 0 && "misc_remove_rotate_floating".equals(Key[0])) {
            applyButtonVisibility();
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        try {
            Class<?> cls = tryFindClass(lp, "com.android.systemui.shared.rotation.FloatingRotationButton");
            if (cls == null) return;
            hookAllConstructors(cls, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        mRotationButton = (View) getObjectField(p.thisObject, "mKeyButtonView");
                        applyButtonVisibility();
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods rotation button: " + t);
        }

        try {
            Class<?> usbCls = tryFindClass(lp, "com.oplus.systemui.usb.UsbService");
            if (usbCls == null) return;

            hookAllMethods(usbCls, "onUsbConnected", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mRemoveUsbDialog) return;
                    try {
                        Context c = (Context) p.args[0];
                        p.setResult(null);
                        callMethod(p.thisObject, "onUsbSelect", 1);
                        callMethod(p.thisObject, "updateAdbNotification", c);
                        callMethod(p.thisObject, "updateUsbNotification", c, 1);
                        callMethod(p.thisObject, "changeUsbConfig", c, 1);
                    } catch (Throwable ignored) {}
                }
            });

            hookAllMethods(usbCls, "helpUpdateUsbNotification", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mRemoveUsbDialog) return;
                    try { setBooleanField(p.thisObject, "mNeedShowUsbDialog", false); } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods usb dialog: " + t);
        }

        try {
            Class<?> shutdownCls = tryFindClass(lp,
                    "com.oplus.systemui.shutdown.OplusShutdownView",   // OOS14-15
                    "com.oplusos.systemui.controls.OplusShutdownView"); // OOS13
            if (shutdownCls == null) return;

            hookAllMethods(shutdownCls, "isShowEmergency", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mHideSosPowerMenu) p.setResult(false);
                }
            });

            mAdvancedRebootDrawable = ResourcesCompat.getDrawable(mContext.getResources(),
                    mContext.getResources().getIdentifier("oplus_reboot", "drawable", SYSTEM_UI),
                    mContext.getTheme());

            hookAllMethods(shutdownCls, "onDraw", new XC_MethodHook() {
                // Drawn BEFORE the stock onDraw runs, so it sits behind the pill's own fill
                // (forced transparent in "image" mode via the getColor hook below) and behind
                // the icons/text OOS draws afterwards — a real background, not an overlay.
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try { applyHandlerColor(p.thisObject); } catch (Throwable ignored) {}
                    try { drawPillBackgroundImage((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { drawPillBorder((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                    try { drawHandlerImage((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                    if (!mShowAdvancedReboot) return;
                    try { drawAdvancedReboot((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                }
            });

            hookAllMethods(shutdownCls, "onTouchEvent", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mShowAdvancedReboot) return;
                    try {
                        MotionEvent event = (MotionEvent) p.args[0];
                        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
                        Rect bounds = new Rect(mCenterX - mRadius, mCenterY - mRadius,
                                mCenterX + mRadius, mCenterY + mRadius);
                        if (!bounds.contains((int) event.getX(), (int) event.getY())) return;
                        p.setResult(true);

                        launchAdvancedReboot(mAdvancedRebootAuth && canDoBiometric());
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods power menu: " + t);
        }

        try {
            // "Sfondo Menù Power" — background of the WHOLE popup window, independent from the
            // pillolone's own background above. The dialog class is
            // com.oplus.systemui.shutdown.OplusGlobalActionsDialog$ActionsDialog, a real
            // android.app.Dialog — found via DstDialogStyle's own global Dialog.show() log,
            // which deliberately SKIPS any class containing "GlobalAction" (that generic
            // dark-dialog-preset styling isn't meant for the power menu). Hooking Dialog.show()
            // again here, scoped to SystemUI only, is safe — Xposed supports multiple hooks on
            // the same method and they all fire independently.
            hookAllMethods(android.app.Dialog.class, "show", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if ("stock".equals(mPowerMenuMenuBgMode)) return;
                        if (!(p.thisObject instanceof android.app.Dialog)) return;
                        String cls = p.thisObject.getClass().getName();
                        if (!cls.contains("GlobalAction")) return;
                        applyMenuBackground((android.app.Dialog) p.thisObject);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods power menu background: " + t);
        }

        try {
            // Same "Usa Autenticazione" pref, now also gating the STOCK Riavvia/Spegni slider —
            // not just the custom Riavvio Avanzato button above. Found via decompiling
            // ShutdownViewControl.java: every reboot/shutdown trigger in the stock power menu
            // (the slider included) ultimately calls GlobalActionsComponent.reboot(boolean)/
            // .shutdown() — a stable AOSP class (com.android.systemui.globalactions), not
            // OnePlus-specific, so hooking it directly here is more robust than trying to catch
            // every possible UI gesture that can lead to it. Blocking the call and re-authing
            // then re-running the SAME command via root shell (like the advanced-reboot chooser
            // already does) avoids the cross-process headache of resuming the original blocked
            // SystemUI call after auth succeeds in our own app's AuthActivity.
            Class<?> gacCls = tryFindClass(lp, "com.android.systemui.globalactions.GlobalActionsComponent");
            if (gacCls != null) {
                hookAllMethods(gacCls, "reboot", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mAdvancedRebootAuth || !canDoBiometric()) return;
                        try {
                            boolean safeMode = p.args.length > 0 && Boolean.TRUE.equals(p.args[0]);
                            p.setResult(null);
                            launchStockAuth(safeMode ? "reboot_safe" : "reboot");
                        } catch (Throwable ignored) {}
                    }
                });
                hookAllMethods(gacCls, "shutdown", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mAdvancedRebootAuth || !canDoBiometric()) return;
                        try {
                            p.setResult(null);
                            launchStockAuth("shutdown");
                        } catch (Throwable ignored) {}
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods stock reboot auth: " + t);
        }

        try {
            // Riavvia/Spegni are NOT painted from oplus_reboot_color/oplus_shutdown_color —
            // decompiling OplusShutdownView showed those two color resources are dead (read into
            // Paint objects that are never actually used to draw). The live gradient is built at
            // onDraw() time with android.graphics.LinearGradient, fed the COUI theme resources
            // coui_color_container_theme_green/_red (portrait — this device's orientation) via
            // Resources.getColor(id). Matching on the RETURNED VALUE (0xff00bd13/0xffeb3b2f, the
            // apk-compiled defaults from aapt2 dump) turned out unreliable — likely COUI dynamic/
            // Monet theming shifts the resolved color away from that static default at runtime, so
            // an exact-value match silently never fires. Hooking by RESOURCE ID instead is robust
            // to that: we override the return value before any downstream theming is visible to us.
            int greenId = mContext.getResources().getIdentifier(
                    "coui_color_container_theme_green", "color", SYSTEM_UI);
            int redId = mContext.getResources().getIdentifier(
                    "coui_color_container_theme_red", "color", SYSTEM_UI);
            // Track background — oplus_bar_color, read once when the slider view is constructed
            // (each power-menu open builds a fresh view). A live getColor() hook (rather than the
            // XResources.setReplacement approach used elsewhere in the app) sidesteps a process-init
            // timing race and, as a bonus, picks up pref changes without needing a SystemUI restart.
            int barColorId = mContext.getResources().getIdentifier(
                    "oplus_bar_color", "color", SYSTEM_UI);
            if (greenId != 0 || redId != 0 || barColorId != 0) {
                hookAllMethods(android.content.res.Resources.class, "getColor", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            if (!(p.args[0] instanceof Integer)) return;
                            int resId = (Integer) p.args[0];
                            if (resId == greenId || resId == redId) {
                                Integer c = powerMenuGradientColor();
                                if (c != null) p.setResult(c);
                            } else if (resId == barColorId) {
                                if ("image".equals(mPowerMenuBgMode) && mPowerMenuBgBitmap != null) {
                                    // Fully transparent: let the bitmap drawn in onDraw's
                                    // before-hook show through instead of being painted over.
                                    p.setResult(0x00000000);
                                } else {
                                    Integer c = powerMenuBgColor();
                                    if (c != null) p.setResult((c & 0x00FFFFFF) | 0xCC000000);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }

            // Landscape branch skips resources entirely and bakes two literal ARGB ints
            // (-13971071/-1428409, i.e. #ff2ad181/#ffea3447) straight into the LinearGradient
            // call — a resource hook can't reach that, so match by exact value here instead.
            hookAllConstructors(android.graphics.LinearGradient.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (p.args.length < 6) return;
                        if (!(p.args[4] instanceof Integer) || !(p.args[5] instanceof Integer)) return;
                        Integer c = powerMenuGradientColor();
                        if (c == null) return;
                        if (isStockPowerMenuColor((Integer) p.args[4])) p.args[4] = c;
                        if (isStockPowerMenuColor((Integer) p.args[5])) p.args[5] = c;
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods power menu gradient: " + t);
        }

        try {
            // WindowManagerGlobal is a stable AOSP-internal class (not OEM-specific), so this
            // survives OOS version changes better than hooking the clipboard overlay's own
            // controller class by name. hookAllMethods matches every addView overload across
            // API levels without needing to pin an exact signature.
            Class<?> wmgCls = tryFindClass(lp, "android.view.WindowManagerGlobal");
            if (wmgCls == null) return;

            hookAllMethods(wmgCls, "addView", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!mBlockClipboardOverlay) return;
                    try {
                        for (Object arg : p.args) {
                            if (arg instanceof android.view.WindowManager.LayoutParams params) {
                                CharSequence title = params.getTitle();
                                if (title != null && title.toString().contains("ClipboardOverlay")) {
                                    p.setResult(null);
                                    return;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods clipboard overlay: " + t);
        }
    }

    /** Was hardcoded to the stock "oplus_road_color" grey — now follows the same Accento/
     *  Personalizzato choice as everywhere else in the app, reading the same shared
     *  DST_ACCENT1/_on prefs "Accento" resolves to elsewhere. */
    private int advancedRebootColor() {
        return mAdvRebootUseAccent ? sharedAccentColor() : mAdvRebootCustomColor;
    }

    /** Stock ARGB ints fed into LinearGradient for the Riavvia/Spegni halves — portrait uses
     *  coui_color_container_theme_green/_red (#ff00bd13/#ffeb3b2f), landscape uses two literal
     *  ints baked directly in bytecode (-13971071/-1428409, i.e. #ff2ad181/#ffea3447). */
    private static boolean isStockPowerMenuColor(int c) {
        return c == 0xFF00BD13 || c == 0xFFEB3B2F || c == 0xFF2AD181 || c == 0xFFEA3447;
    }

    /** Riavvia/Spegni gradient colour — null means "stock" (leave OOS green/red alone). Independent
     *  from the Riavvio Avanzato button's own colour (advancedRebootColor() above). */
    private Integer powerMenuGradientColor() {
        switch (mPowerMenuGradientMode) {
            case "accent": return sharedAccentColor();
            case "custom": return mPowerMenuGradientCustomColor;
            default:       return null; // stock
        }
    }

    /** Pill track background colour — null means "stock" (leave oplus_bar_color's plain white). */
    private Integer powerMenuBgColor() {
        switch (mPowerMenuBgMode) {
            case "accent": return sharedAccentColor();
            case "custom": return mPowerMenuBgCustomColor;
            default:       return null; // stock
        }
    }

    private int sharedAccentColor() {
        boolean accentOn = Xprefs != null && Xprefs.getBoolean("DST_ACCENT1_on", false);
        return accentOn ? Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) : 0xFF908DFF;
    }

    /** Own independent Accento/Personalizzato choice — NOT tied to the gradient colour, so the
     *  two can be set to genuinely different colours (e.g. dark background + accent border). */
    private int borderColor() {
        return mPowerMenuBorderUseAccent ? sharedAccentColor() : mPowerMenuBorderCustomColor;
    }

    /** Colore Pallino — null means "stock" (leave OOS's default white handler alone). */
    private Integer handlerColor() {
        switch (mPowerMenuHandlerMode) {
            case "accent": return sharedAccentColor();
            case "custom": return mPowerMenuHandlerCustomColor;
            default:       return null; // stock or image (image draws over it instead)
        }
    }

    /** Applies "Colore Pallino" to the draggable thumb. mHandlerColor isn't resource-driven
     *  (no getColor hook applies, unlike oplus_bar_color) — it's a plain instance field read
     *  into mHandlerPaint by OOS itself, so both are set directly: the field (in case OOS
     *  re-syncs the Paint from it later in the same draw) and the Paint object in place
     *  (guarantees this exact frame is coloured regardless of that timing).
     *  In "image" mode this instead forces the stock circle FULLY TRANSPARENT (same trick as
     *  oplus_bar_color for the pill background) — without this, a custom image with transparent
     *  pixels let the stock white circle show through underneath it, since drawHandlerImage()
     *  only draws OVER the already-drawn stock circle, never erases it. */
    private void applyHandlerColor(Object shutdownView) {
        if ("image".equals(mPowerMenuHandlerMode) && mPowerMenuHandlerBitmap != null) {
            try {
                setIntField(shutdownView, "mHandlerColor", 0x00000000);
                Object paintObj = getObjectField(shutdownView, "mHandlerPaint");
                if (paintObj instanceof Paint) {
                    Paint p = (Paint) paintObj;
                    p.setColor(0x00000000);
                    p.setAlpha(0);
                    p.setShader(null);
                    p.setShadowLayer(0f, 0f, 0f, 0x00000000);
                }
                // Separate scrim layer behind the whole action bar, drawn by stock code
                // independently of mHandlerColor/mHandlerPaint (found via live field dump,
                // 2026-09-01) — left opaque it painted a dark disc behind any transparent image.
                try {
                    Object scrim = getObjectField(shutdownView, "mActionsBackgroundDrawable");
                    if (scrim instanceof Drawable) ((Drawable) scrim).setAlpha(0);
                } catch (Throwable ignored4) {}
                // mDefaultHandlerColor (int, default -1/white) — the colour actually used for the
                // handle's resting/idle paint, separate from mHandlerColor.
                try {
                    setIntField(shutdownView, "mDefaultHandlerColor", 0x00000000);
                } catch (Throwable ignored5) {}
                // mHandlerAlpha (float) — stock re-applies mHandlerPaint.setAlpha(255*mHandlerAlpha)
                // from its own entrance-animation value INSIDE onDraw, after this before-hook runs,
                // silently overriding paint.setAlpha(0) above. Force it to 0 too.
                try {
                    setFloatField(shutdownView, "mHandlerAlpha", 0f);
                } catch (Throwable ignored6) {}
            } catch (Throwable ignored) {}
            return;
        }
        Integer c = handlerColor();
        if (c == null) return;
        try {
            setIntField(shutdownView, "mHandlerColor", c);
            Object paintObj = getObjectField(shutdownView, "mHandlerPaint");
            if (paintObj instanceof Paint) ((Paint) paintObj).setColor(c);
        } catch (Throwable ignored) {}
    }

    /** Re-decodes the pillolone background image only when the file's mtime actually changed
     *  (called from every updatePrefs(), which fires far more often than the image itself
     *  changes) — avoids decoding a full bitmap on every prefs sync. Cleared when the mode
     *  isn't "image" or the file is missing, so drawPillBackgroundImage() cheaply no-ops. */
    private void refreshPowerMenuBgBitmap() {
        if (!"image".equals(mPowerMenuBgMode)) {
            mPowerMenuBgBitmap = null;
            mPowerMenuBgBitmapMtime = -1;
            return;
        }
        File f = new File(Environment.getExternalStorageDirectory(), POWER_MENU_BG_IMAGE_SUBPATH);
        if (!f.exists()) {
            mPowerMenuBgBitmap = null;
            mPowerMenuBgBitmapMtime = -1;
            return;
        }
        long mtime = f.lastModified();
        if (mPowerMenuBgBitmap != null && mtime == mPowerMenuBgBitmapMtime) return; // unchanged
        // decodeStream (not decodeFile) — more reliable from a system process, same reasoning
        // as QsHeaderImage's loadBitmap().
        try (FileInputStream fis = new FileInputStream(f)) {
            Bitmap bmp = BitmapFactory.decodeStream(fis);
            if (bmp != null) {
                mPowerMenuBgBitmap = bmp;
                mPowerMenuBgBitmapMtime = mtime;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods power menu bg image: " + t);
        }
    }

    /** Pillolone background image, drawn BEFORE the stock onDraw so it sits behind the pill's
     *  own fill (forced transparent in image mode, see the oplus_bar_color getColor hook) and
     *  behind the icons/text drawn afterwards. Center-cropped + clipped to the same rounded
     *  rect drawPillBorder() uses, so it always matches the pill's real shape exactly. */
    private void drawPillBackgroundImage(Canvas canvas, Object shutdownView) {
        if (!"image".equals(mPowerMenuBgMode) || mPowerMenuBgBitmap == null) return;
        try {
            Object rectFObj = getObjectField(shutdownView, "mBarRectF");
            Object radiusObj = getObjectField(shutdownView, "mBarRadius");
            if (!(rectFObj instanceof RectF) || !(radiusObj instanceof Integer)) return;
            RectF rectF = (RectF) rectFObj;
            float radius = (Integer) radiusObj;
            if (rectF.width() <= 0 || rectF.height() <= 0) return;

            Rect src = centerCropSrcRect(mPowerMenuBgBitmap, rectF.width(), rectF.height());

            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(rectF, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(mPowerMenuBgBitmap, src, rectF, null);
            canvas.restore();
        } catch (Throwable ignored) {}
    }

    /** Classic center-crop math: picks the largest same-aspect-ratio rect out of the source
     *  bitmap that covers the destination area without distortion. */
    private static Rect centerCropSrcRect(Bitmap bmp, float dstW, float dstH) {
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        float srcAspect = (float) bw / bh;
        float dstAspect = dstW / dstH;
        if (srcAspect > dstAspect) {
            int cropW = Math.round(bh * dstAspect);
            int left = Math.max(0, (bw - cropW) / 2);
            return new Rect(left, 0, left + cropW, bh);
        } else {
            int cropH = Math.round(bw / dstAspect);
            int top = Math.max(0, (bh - cropH) / 2);
            return new Rect(0, top, bw, top + cropH);
        }
    }

    /** Re-decodes "Sfondo Menù Power"'s image only when the file's mtime changed — same
     *  reasoning as refreshPowerMenuBgBitmap() above, independent cache/file. */
    private void refreshPowerMenuMenuBgBitmap() {
        if (!"image".equals(mPowerMenuMenuBgMode)) {
            mPowerMenuMenuBgBitmap = null;
            mPowerMenuMenuBgBitmapMtime = -1;
            return;
        }
        File f = new File(Environment.getExternalStorageDirectory(), POWER_MENU_MENU_BG_IMAGE_SUBPATH);
        if (!f.exists()) {
            mPowerMenuMenuBgBitmap = null;
            mPowerMenuMenuBgBitmapMtime = -1;
            return;
        }
        long mtime = f.lastModified();
        if (mPowerMenuMenuBgBitmap != null && mtime == mPowerMenuMenuBgBitmapMtime) return;
        try (FileInputStream fis = new FileInputStream(f)) {
            Bitmap bmp = BitmapFactory.decodeStream(fis);
            if (bmp != null) {
                mPowerMenuMenuBgBitmap = bmp;
                mPowerMenuMenuBgBitmapMtime = mtime;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods power menu menu-bg image: " + t);
        }
    }

    /** Applies "Sfondo Menù Power" to the whole GlobalActions Dialog window — a plain solid
     *  colour for accent/custom, a center-cropped image Drawable for image mode. Unlike the
     *  pillolone's own background (a Canvas draw clipped to the pill's rounded rect), this sets
     *  the WHOLE window's background directly since there's no single shape to clip to here. */
    private void applyMenuBackground(android.app.Dialog d) {
        android.view.Window w = d.getWindow();
        if (w == null) return;
        if ("image".equals(mPowerMenuMenuBgMode)) {
            if (mPowerMenuMenuBgBitmap == null) return;
            w.setBackgroundDrawable(new CenterCropBitmapDrawable(mPowerMenuMenuBgBitmap));
        } else {
            int color = "accent".equals(mPowerMenuMenuBgMode) ? sharedAccentColor() : mPowerMenuMenuBgCustomColor;
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(color);
            w.setBackgroundDrawable(gd);
        }
    }

    /** Draws a bitmap center-cropped to whatever bounds it's given — used as a Window background
     *  Drawable, where the bounds are the window's own client rect (set by the framework). */
    private static class CenterCropBitmapDrawable extends Drawable {
        private final Bitmap mBmp;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CenterCropBitmapDrawable(Bitmap bmp) { mBmp = bmp; }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            Rect src = centerCropSrcRect(mBmp, b.width(), b.height());
            canvas.drawBitmap(mBmp, src, b, mPaint);
        }
        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { mPaint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Re-decodes the pallino's image only when the file's mtime changed — same reasoning as
     *  refreshPowerMenuBgBitmap() above, independent cache/file. */
    private void refreshPowerMenuHandlerBitmap() {
        if (!"image".equals(mPowerMenuHandlerMode)) {
            mPowerMenuHandlerBitmap = null;
            mPowerMenuHandlerBitmapMtime = -1;
            return;
        }
        File f = new File(Environment.getExternalStorageDirectory(), POWER_MENU_HANDLER_IMAGE_SUBPATH);
        if (!f.exists()) {
            mPowerMenuHandlerBitmap = null;
            mPowerMenuHandlerBitmapMtime = -1;
            return;
        }
        long mtime = f.lastModified();
        if (mPowerMenuHandlerBitmap != null && mtime == mPowerMenuHandlerBitmapMtime) return;
        try (FileInputStream fis = new FileInputStream(f)) {
            Bitmap bmp = BitmapFactory.decodeStream(fis);
            if (bmp != null) {
                mPowerMenuHandlerBitmap = bmp;
                mPowerMenuHandlerBitmapMtime = mtime;
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods power menu handler image: " + t);
        }
    }

    /** Pallino image — drawn AFTER the stock onDraw (like the border), so it paints directly
     *  over the already-drawn default circle instead of being hidden underneath it. Center-
     *  cropped + clipped to an OVAL matching mHandlerRectF, the same field OOS itself uses to
     *  draw/hit-test the handle, so the image always lines up with the real draggable area. */
    private void drawHandlerImage(Canvas canvas, Object shutdownView) {
        if (!"image".equals(mPowerMenuHandlerMode) || mPowerMenuHandlerBitmap == null) return;
        try {
            Object rectFObj = getObjectField(shutdownView, "mHandlerRectF");
            if (!(rectFObj instanceof RectF)) return;
            RectF rectF = (RectF) rectFObj;
            if (rectF.width() <= 0 || rectF.height() <= 0) return;

            // "Scala Immagine" shrinks/grows the drawn area around the handle's own centre —
            // the clip path stays the full handle circle, only the image itself gets smaller.
            RectF dst = rectF;
            if (mPowerMenuHandlerScale != 1.0f) {
                float cx = rectF.centerX(), cy = rectF.centerY();
                float hw = rectF.width() / 2f * mPowerMenuHandlerScale;
                float hh = rectF.height() / 2f * mPowerMenuHandlerScale;
                dst = new RectF(cx - hw, cy - hh, cx + hw, cy + hh);
            }

            Rect src = centerCropSrcRect(mPowerMenuHandlerBitmap, dst.width(), dst.height());

            canvas.save();
            Path clip = new Path();
            clip.addOval(rectF, Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(mPowerMenuHandlerBitmap, src, dst, null);
            canvas.restore();
        } catch (Throwable ignored) {}
    }

    /** Outline around the Riavvia/Spegni pill. Reuses OplusShutdownView's own public
     *  drawSmoothRoundRect(Canvas, RectF, int, Paint) helper via reflection so the corner radius
     *  always matches the stock pill exactly. */
    private void drawPillBorder(Canvas canvas, Object shutdownView) {
        if (!mPowerMenuBorderEnabled) return;
        try {
            Object rectFObj = getObjectField(shutdownView, "mBarRectF");
            Object radiusObj = getObjectField(shutdownView, "mBarRadius");
            if (!(rectFObj instanceof RectF) || !(radiusObj instanceof Integer)) return;
            // NOT OplusShutdownView's own drawSmoothRoundRect() — that helper always calls
            // canvas.drawPaint() internally, which fills the whole clipped region regardless of
            // the Paint's Style (STROKE is ignored), so it painted a solid block over everything.
            // Canvas#drawRoundRect is a real stroke-capable Android API and does the right thing.
            RectF rectF = (RectF) rectFObj;
            float radius = (Integer) radiusObj;
            Paint borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(borderColor());
            canvas.drawRoundRect(rectF, radius, radius, borderPaint);
        } catch (Throwable ignored) {}
    }

    private void drawAdvancedReboot(Canvas canvas, Object shutdownView) {
        try {
            Paint buttonPaint = new Paint();
            buttonPaint.setColor(advancedRebootColor());
            buttonPaint.setStyle(Paint.Style.FILL);

            Paint textPaint = new Paint();
            textPaint.setColor(Color.GRAY);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(mContext.getResources().getDisplayMetrics().densityDpi / 13f);

            int viewWidth = (int) callMethod(shutdownView, "getWidth");
            mRadius = mContext.getResources().getDimensionPixelSize(
                    mContext.getResources().getIdentifier("oplus_default_bar_radius", "dimen", SYSTEM_UI)) / 2;
            mCenterX = viewWidth / 2;
            mCenterY = mRadius + dp(50) + dp(mAdvancedRebootYOffset);

            canvas.drawCircle(mCenterX, mCenterY, mRadius, buttonPaint);

            if (mAdvancedRebootDrawable != null) {
                Rect iconBounds = new Rect(mCenterX - mRadius / 2, mCenterY - mRadius / 2,
                        mCenterX + mRadius / 2, mCenterY + mRadius / 2);
                mAdvancedRebootDrawable.setBounds(iconBounds);
                mAdvancedRebootDrawable.draw(canvas);
            }

            String buttonText = ResourceManager.modRes != null
                    ? ResourceManager.modRes.getString(R.string.advanced_reboot_title) : "";
            canvas.drawText(buttonText, viewWidth / 2f, mCenterY + mRadius + dp(20), textPaint);
        } catch (Throwable ignored) {}
    }

    private void launchAdvancedReboot(boolean shouldAuth) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID,
                BuildConfig.APPLICATION_ID + ".ui.activity.AuthActivity"));
        intent.putExtra("shouldAuth", shouldAuth);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    /** Fail-safe: only true when the device can actually complete an auth challenge, so a phone
     *  with no biometric/PIN configured never gets its reboot/shutdown silently blocked. WEAK
     *  alone already covers STRONG-class sensors too (it's the lower bar) — combining STRONG
     *  with WEAK is explicitly rejected by Android's BiometricManager (throws
     *  IllegalArgumentException), which is what made an earlier version of this check a no-op. */
    private boolean canDoBiometric() {
        try {
            return mContext.getSystemService(BiometricManager.class)
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK
                            | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Blocked GlobalActionsComponent.reboot()/shutdown() call — same AuthActivity used by the
     *  advanced-reboot button, but with a "stockAction" extra so it skips straight to the
     *  biometric prompt (no chooser dialog) and re-runs the SAME command via root shell on
     *  success, instead of showing the Recovery/Bootloader/etc. chooser. */
    private void launchStockAuth(String stockAction) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID,
                BuildConfig.APPLICATION_ID + ".ui.activity.AuthActivity"));
        intent.putExtra("stockAction", stockAction);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private int dp(int v) {
        return Math.round(v * mContext.getResources().getDisplayMetrics().density);
    }

    private void applyButtonVisibility() {
        if (mRotationButton != null) {
            mRotationButton.setVisibility(mHideRotationButton ? View.GONE : View.VISIBLE);
        }
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return de.robv.android.xposed.XposedHelpers.findClass(name, lp.classLoader); }
            catch (Throwable ignored) {}
        }
        XposedBridge.log("[ Obsidian ] MiscMods: none of " + java.util.Arrays.toString(names) + " found");
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
