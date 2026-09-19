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
    // mode is one of "stock" (leave OOS green/red/white alone) / "accent" / "custom" / "split"
    // (due colori distinti, uno per Riavvia uno per Spegni — richiesta esplicita dell'utente).
    private String  mPowerMenuGradientMode = "accent";
    private int     mPowerMenuGradientCustomColor = 0xFF908DFF;
    private int     mPowerMenuGradientRestartColor = 0xFF00BD13;  // default = verde stock
    private int     mPowerMenuGradientShutdownColor = 0xFFEB3B2F; // default = rosso stock
    private String  mPowerMenuBgMode = "stock";
    private int     mPowerMenuBgCustomColor = 0xFF908DFF;
    private boolean mPowerMenuBorderEnabled = false;
    private boolean mPowerMenuBorderUseAccent = true;
    private int     mPowerMenuBorderCustomColor = 0xFF908DFF;

    // Bordo Pallino — 2026-09-13, stessa idea di Bordo Pillolone ma sull'anello del pallino
    // (mHandlerRectF), per poter avere pallino navy (Colore Pallino → Colore) + bordo accento.
    private boolean mPowerMenuHandlerBorderEnabled = false;
    private boolean mPowerMenuHandlerBorderUseAccent = true;
    private int     mPowerMenuHandlerBorderCustomColor = 0xFF908DFF;
    // Pallino (draggable thumb) colour/image — real field names confirmed via a live reflection
    // field dump of OplusShutdownView: "mHandlerColor" (int, default -1/white) + "mHandlerPaint"
    // (already-built Paint, not resource-driven so no getColor hook applies here) +
    // "mHandlerRectF" (its bounding box, same "populated only once actually drawn" behaviour as
    // mBarRectF — empty in a field dump taken before layout, fine once read from onDraw itself).
    private static final String POWER_MENU_HANDLER_IMAGE_SUBPATH = ".obsidian/power_menu_handler_image";
    private String  mPowerMenuHandlerMode = "stock";
    private int     mPowerMenuHandlerCustomColor = 0xFF908DFF;
    private Bitmap  mPowerMenuHandlerBitmap;
    private long    mPowerMenuHandlerBitmapMtime = -1;
    // Ritaglio (cx/cy/zoom) — stessa convenzione di QsHeaderImage/ImageCropOverlayView: cx/cy
    // 0-100 = centro del riquadro come frazione dell'immagine, zoom >=100 (100 = nessun ritaglio
    // extra, riempie al minimo indispensabile come il vecchio centerCropSrcRect; più alto = più
    // zoom). Default 50/50/100 riproduce ESATTAMENTE il vecchio comportamento sempre-centrato,
    // quindi non cambia nulla per chi ha già un'immagine impostata prima di questa feature.
    private int mPowerMenuHandlerCropCx = 50;
    private int mPowerMenuHandlerCropCy = 50;
    private int mPowerMenuHandlerCropZoom = 100;
    private boolean mDiagHandlerRectLogged = false; // log mHandlerRectF's real size once — vedi drawHandlerImage

    // Pillolone background image ("power_menu_bg_mode" == "image") — file written by
    // PowerMenuFragment to the same relative path. Cached and only re-decoded when the file's
    // mtime changes, so a normal prefs sync doesn't re-decode the bitmap every time.
    private static final String POWER_MENU_BG_IMAGE_SUBPATH = ".obsidian/power_menu_bg_image";
    private Bitmap mPowerMenuBgBitmap;
    private long   mPowerMenuBgBitmapMtime = -1;
    private int mPowerMenuBgCropCx = 50;
    private int mPowerMenuBgCropCy = 50;
    private int mPowerMenuBgCropZoom = 100;

    // Sfondo Menù Power — background of the WHOLE popup window (Window.setBackgroundDrawable
    // on the GlobalActions Dialog itself), independent from the pillolone's own background
    // above. Same "stock"/"accent"/"custom"/"image" shape, own pref keys/file.
    private static final String POWER_MENU_MENU_BG_IMAGE_SUBPATH = ".obsidian/power_menu_menu_bg_image";
    private String  mPowerMenuMenuBgMode = "stock";
    private int     mPowerMenuMenuBgCustomColor = 0xFF908DFF;
    private int mPowerMenuMenuBgCropCx = 50;
    private int mPowerMenuMenuBgCropCy = 50;
    private int mPowerMenuMenuBgCropZoom = 100;
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
        mPowerMenuGradientRestartColor = Xprefs.getInt("power_menu_gradient_restart_color", 0xFF00BD13);
        mPowerMenuGradientShutdownColor = Xprefs.getInt("power_menu_gradient_shutdown_color", 0xFFEB3B2F);
        mPowerMenuBgMode = Xprefs.getString("power_menu_bg_mode", "stock");
        mPowerMenuBgCustomColor = Xprefs.getInt("power_menu_bg_custom_color", 0xFF908DFF);
        mPowerMenuBgCropCx = Xprefs.getInt("power_menu_bg_crop_cx", 50);
        mPowerMenuBgCropCy = Xprefs.getInt("power_menu_bg_crop_cy", 50);
        mPowerMenuBgCropZoom = Xprefs.getInt("power_menu_bg_crop_zoom", 100);
        mPowerMenuBorderEnabled = Xprefs.getBoolean("power_menu_border_enabled", false);
        mPowerMenuBorderUseAccent = Xprefs.getBoolean("power_menu_border_use_accent", true);
        mPowerMenuBorderCustomColor = Xprefs.getInt("power_menu_border_custom_color", 0xFF908DFF);
        mPowerMenuHandlerMode = Xprefs.getString("power_menu_handler_mode", "stock");
        mPowerMenuHandlerCustomColor = Xprefs.getInt("power_menu_handler_custom_color", 0xFF908DFF);
        mPowerMenuHandlerCropCx = Xprefs.getInt("power_menu_handler_crop_cx", 50);
        mPowerMenuHandlerCropCy = Xprefs.getInt("power_menu_handler_crop_cy", 50);
        mPowerMenuHandlerCropZoom = Xprefs.getInt("power_menu_handler_crop_zoom", 100);
        mPowerMenuHandlerBorderEnabled = Xprefs.getBoolean("power_menu_handler_border_enabled", false);
        mPowerMenuHandlerBorderUseAccent = Xprefs.getBoolean("power_menu_handler_border_use_accent", true);
        mPowerMenuHandlerBorderCustomColor = Xprefs.getInt("power_menu_handler_border_custom_color", 0xFF908DFF);
        mPowerMenuMenuBgMode = Xprefs.getString("power_menu_menu_bg_mode", "stock");
        mPowerMenuMenuBgCustomColor = Xprefs.getInt("power_menu_menu_bg_custom_color", 0xFF908DFF);
        mPowerMenuMenuBgCropCx = Xprefs.getInt("power_menu_menu_bg_crop_cx", 50);
        mPowerMenuMenuBgCropCy = Xprefs.getInt("power_menu_menu_bg_crop_cy", 50);
        mPowerMenuMenuBgCropZoom = Xprefs.getInt("power_menu_menu_bg_crop_zoom", 100);
        refreshPowerMenuBgBitmap();
        refreshPowerMenuMenuBgBitmap();
        refreshPowerMenuHandlerBitmap();
        if (Key.length > 0 && "misc_remove_rotate_floating".equals(Key[0])) {
            applyButtonVisibility();
        }
    }

    /** 2026-09-07: segnalato dall'utente ("il pillolone e lo sfondo spesso tornano stock") —
     *  a differenza di SettingsCardBackgroundMod/DstCpbStyle/DstNotifStyle, questa classe non
     *  aveva MAI avuto un fallback: fino a che updatePrefs() non arrivava dal ContentProvider
     *  (la stessa corsa al boot già vista altrove in questo progetto), il menù accensione
     *  mostrava sempre lo stock — SystemUI parte prestissimo al boot, quindi la finestra in cui
     *  l'utente può aprirlo prima che Xprefs sia pronto è tutt'altro che rara. Stesso canale
     *  (system properties, scritte da DstFabricatedUtil.saveBootProps() quando si esce dalla
     *  schermata "Menù accensione") usato ovunque altro in questo file per lo stesso motivo. */
    @Override
    public void preloadFallback() {
        try {
            mPowerMenuGradientMode = readSysProp("persist.obsidian.dst.pm_gradient_mode", "accent");
            mPowerMenuGradientCustomColor = parseIntProp(
                    readSysProp("persist.obsidian.dst.pm_gradient_color", ""), 0xFF908DFF);
            mPowerMenuBgMode = readSysProp("persist.obsidian.dst.pm_bg_mode", "stock");
            mPowerMenuBgCustomColor = parseIntProp(
                    readSysProp("persist.obsidian.dst.pm_bg_color", ""), 0xFF908DFF);
            mPowerMenuBorderEnabled = "1".equals(readSysProp("persist.obsidian.dst.pm_border_on", "0"));
            mPowerMenuBorderUseAccent = "1".equals(readSysProp("persist.obsidian.dst.pm_border_accent", "1"));
            mPowerMenuBorderCustomColor = parseIntProp(
                    readSysProp("persist.obsidian.dst.pm_border_color", ""), 0xFF908DFF);
            mPowerMenuHandlerMode = readSysProp("persist.obsidian.dst.pm_handler_mode", "stock");
            mPowerMenuHandlerCustomColor = parseIntProp(
                    readSysProp("persist.obsidian.dst.pm_handler_color", ""), 0xFF908DFF);
            mPowerMenuHandlerBorderEnabled = "1".equals(readSysProp("persist.obsidian.dst.pm_handler_border_on", "0"));
            mPowerMenuHandlerBorderUseAccent = "1".equals(readSysProp("persist.obsidian.dst.pm_handler_border_accent", "1"));
            mPowerMenuHandlerBorderCustomColor = parseIntProp(
                    readSysProp("persist.obsidian.dst.pm_handler_border_color", ""), 0xFF908DFF);
            mPowerMenuMenuBgMode = readSysProp("persist.obsidian.dst.pm_menu_bg_mode", "stock");
            mPowerMenuMenuBgCustomColor = parseIntProp(
                    readSysProp("persist.obsidian.dst.pm_menu_bg_color", ""), 0xFF908DFF);
            refreshPowerMenuBgBitmap();
            refreshPowerMenuMenuBgBitmap();
            refreshPowerMenuHandlerBitmap();
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods.preloadFallback failed: " + t);
        }
    }

    private static String readSysProp(String key, String def) {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass("android.os.SystemProperties", null);
            Object val = de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "get", key, def);
            return val != null ? (String) val : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static int parseIntProp(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return def; }
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
                    try { applyBarAlpha(p.thisObject); } catch (Throwable ignored) {}
                    try { applyHandlerColor(p.thisObject); } catch (Throwable ignored) {}
                    try { drawPillBackgroundImage((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { drawPillBorder((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                    try { drawHandlerImage((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
                    try { drawHandlerBorder((Canvas) p.args[0], p.thisObject); } catch (Throwable ignored) {}
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
                        if (!(p.thisObject instanceof android.app.Dialog)) return;
                        String cls = p.thisObject.getClass().getName();
                        if (!cls.contains("GlobalAction")) return;
                        // 2026-09-13: il controllo "stock" era QUI, prima del log dentro
                        // applyMenuBackground() — se mPowerMenuMenuBgMode leggeva "stock" per una
                        // corsa al boot (updatePrefs/preloadFallback non ancora passati), il log
                        // diagnostico non scattava mai. Spostato il check dentro, così ogni show
                        // del vero dialog GlobalActions viene loggato indipendentemente dal modo.
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
                                Integer c = powerMenuGradientColor(resId == greenId);
                                if (c != null) p.setResult(c);
                            } else if (resId == barColorId) {
                                if ("image".equals(mPowerMenuBgMode) && mPowerMenuBgBitmap != null) {
                                    // Fully transparent: let the bitmap drawn in onDraw's
                                    // before-hook show through instead of being painted over.
                                    p.setResult(0x00000000);
                                } else {
                                    // 2026-09-17: forzava SEMPRE l'alfa a 0xCC (80%), ignorando
                                    // l'opacità piena di Accento/Personalizzato scelta
                                    // dall'utente (segnalato: "rimangono con trasparenza alta").
                                    // Usa il colore esattamente come scelto — sharedAccentColor()
                                    // è già FF pieno, mPowerMenuBgCustomColor porta la propria
                                    // alfa scelta dal picker (incluso il preset "trasparente"
                                    // esplicito 0x00000000 in BG_PRESET_COLORS, se scelto apposta).
                                    Integer c = powerMenuBgColor();
                                    if (c != null) p.setResult(c);
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
                        int c1 = (Integer) p.args[4];
                        int c2 = (Integer) p.args[5];
                        if (isStockPowerMenuColor(c1)) {
                            Integer c = powerMenuGradientColor(isStockGreen(c1));
                            if (c != null) p.args[4] = c;
                        }
                        if (isStockPowerMenuColor(c2)) {
                            Integer c = powerMenuGradientColor(isStockGreen(c2));
                            if (c != null) p.args[5] = c;
                        }
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
     *  from the Riavvio Avanzato button's own colour (advancedRebootColor() above). isRestart
     *  only matters for mode "split" (Riavvia/Spegni con 2 colori distinti, richiesta esplicita
     *  "due picker, uno per Riavvia e uno per Spegni") — accent/custom/stock ignorano il parametro,
     *  stesso colore per entrambi come prima. */
    private Integer powerMenuGradientColor(boolean isRestart) {
        switch (mPowerMenuGradientMode) {
            case "accent": return sharedAccentColor();
            case "custom": return mPowerMenuGradientCustomColor;
            case "split":  return isRestart ? mPowerMenuGradientRestartColor : mPowerMenuGradientShutdownColor;
            default:       return null; // stock
        }
    }

    /** Distingue quale delle due metà (verde=Riavvia, rosso=Spegni) uno stock color rappresenta —
     *  serve solo alla modalità "split" per sapere quale dei due colori applicare. */
    private static boolean isStockGreen(int c) {
        return c == 0xFF00BD13 || c == 0xFF2AD181;
    }

    /** Forza mBarAlpha (il campo che OOS anima da 0.0 fino a un plateau di 0.2, indipendente
     *  dal canale alfa del nostro colore) a piena opacità quando Sfondo Pillolone è su
     *  Accento/Personalizzato — trovato via diagnostica 2026-09-17: era proprio questo campo,
     *  non l'alfa del colore, a rendere trasparente il pillolone anche a colore pieno.
     *  In "image"/"stock" NON lo tocchiamo: lì la trasparenza stock va lasciata com'è. */
    private void applyBarAlpha(Object shutdownView) {
        if (!"accent".equals(mPowerMenuBgMode) && !"custom".equals(mPowerMenuBgMode)) return;
        try {
            setFloatField(shutdownView, "mBarAlpha", 1f);
        } catch (Throwable ignored) {}
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

    /** Same Accento/Personalizzato choice, independent from Colore Pallino's own fill —
     *  lets the handle be e.g. navy fill + accent ring. */
    private int handlerBorderColor() {
        return mPowerMenuHandlerBorderUseAccent ? sharedAccentColor() : mPowerMenuHandlerBorderCustomColor;
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

    /** 2026-09-13: "Sfondo Menù Power" segnalato dall'utente come sparito dopo un riavvio di
     *  SystemUI pur restando impostato su "image" (pref e file entrambi corretti, confermato dal
     *  vivo — /sdcard/.obsidian/power_menu_menu_bg_image esisteva, ~1.9MB, nessun errore visibile
     *  finché il processo restava lo stesso). Root cause: i 3 loader di immagini di questo file
     *  (pillolone/sfondo menù/pallino) decodificavano SEMPRE la foto a piena risoluzione
     *  (BitmapFactory.decodeStream senza inSampleSize) — una foto reale da fotocamera può
     *  decodificare a decine di MB in ARGB_8888, che nel processo SystemUI (già carico di altre
     *  cache) rischia OutOfMemoryError silenzioso: il catch(Throwable) qui sotto lo assorbe senza
     *  crash visibile, il bitmap resta null, e l'immagine torna stock — ESATTAMENTE il pattern
     *  "appare giusta subito, sparisce dopo un riavvio" segnalato (il primo decode riesce con
     *  memoria fresca, un decode successivo su un processo già più carico no). Fix generale:
     *  decodifica in 2 passaggi (inJustDecodeBounds per leggere le dimensioni reali senza allocare
     *  pixel, poi inSampleSize alla potenza di 2 più vicina per restare entro la risoluzione dello
     *  schermo — inutile tenere più pixel di quelli che verranno mai disegnati, l'immagine viene
     *  comunque center-crop sull'area di destinazione). Applicato a tutti e 3 i loader di questo
     *  file, stesso rischio anche se solo uno è stato segnalato finora. */
    private static Bitmap decodeSampledBitmap(File f) {
        try {
            android.util.DisplayMetrics dm = android.content.res.Resources.getSystem().getDisplayMetrics();
            int reqW = dm.widthPixels;
            int reqH = dm.heightPixels;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (FileInputStream fis = new FileInputStream(f)) {
                BitmapFactory.decodeStream(fis, null, bounds);
            }
            int sample = 1;
            int halfW = bounds.outWidth / 2, halfH = bounds.outHeight / 2;
            while (halfW / sample >= reqW && halfH / sample >= reqH) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (FileInputStream fis = new FileInputStream(f)) {
                return BitmapFactory.decodeStream(fis, null, opts);
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] MiscMods decodeSampledBitmap: " + t);
            return null;
        }
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
        Bitmap bmp = decodeSampledBitmap(f);
        if (bmp != null) {
            mPowerMenuBgBitmap = bmp;
            mPowerMenuBgBitmapMtime = mtime;
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

            canvas.save();
            Path clip = new Path();
            clip.addRoundRect(rectF, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            drawCustomCropped(canvas, mPowerMenuBgBitmap, rectF,
                    mPowerMenuBgCropCx / 100f, mPowerMenuBgCropCy / 100f, mPowerMenuBgCropZoom);
            canvas.restore();
        } catch (Throwable ignored) {}
    }

    /** Ritaglio con posizione/zoom regolabili — stessa formula/convenzione di ImageCropOverlayView
     *  (duplicata qui per indipendenza, processo diverso): 100 = riempie al minimo che copre la
     *  destinazione (a cx=cy=50 riproduce ESATTAMENTE il vecchio centerCropSrcRect, sempre
     *  centrato); sopra 100 ritaglia/ingrandisce di più; SOTTO 100 rimpicciolisce verso "adatta"
     *  (foto intera visibile, margine vuoto) invece di restare bloccato al minimo "riempi" — una
     *  foto molto più "lunga" del riquadro altrimenti mostrerebbe sempre solo una fetta centrale,
     *  richiesta esplicita dell'utente ("se un'img è già grande di suo... andrebbe diminuita").
     *  Il chiamante deve aver già impostato il clip (forma pillola/ovale/rettangolo) — qui si
     *  disegna solo il bitmap via Matrix, pieno bounds compreso l'eventuale margine sotto 100. */
    private static void drawCustomCropped(Canvas canvas, Bitmap bmp, RectF dstRect, float cx, float cy, int zoomPercent) {
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        float dstW = dstRect.width(), dstH = dstRect.height();
        float scale, tx, ty;
        if (zoomPercent >= 100) {
            float srcAspect = bw / bh, dstAspect = dstW / dstH;
            float maxCropW, maxCropH;
            if (srcAspect > dstAspect) { maxCropH = bh; maxCropW = bh * dstAspect; }
            else { maxCropW = bw; maxCropH = bw / dstAspect; }
            float frac = Math.max(0.05f, Math.min(1f, 100f / Math.max(1, zoomPercent)));
            float cropW = maxCropW * frac, cropH = maxCropH * frac;
            scale = dstW / cropW;
            float leftSrc = Math.max(0f, Math.min(bw - cropW, bw * cx - cropW / 2f));
            float topSrc  = Math.max(0f, Math.min(bh - cropH, bh * cy - cropH / 2f));
            tx = dstRect.left - leftSrc * scale;
            ty = dstRect.top - topSrc * scale;
        } else {
            float coverScale = Math.max(dstW / bw, dstH / bh);
            // 2026-09-17: quando la foto ha (quasi) lo stesso aspect ratio del riquadro (es.
            // Pallino: riquadro quadrato + preset "impronta" quadrato), "adatta" e "riempi"
            // combaciano matematicamente (min==max) — lo zoom sotto 100% non aveva ALCUN effetto
            // visibile ("anche se uso 20% rimane a 100%"). Forza un rimpicciolimento minimo reale
            // (60% del riempimento) anche in quel caso — stessa correzione in ImageCropOverlayView.
            float containScale = Math.min(Math.min(dstW / bw, dstH / bh), coverScale * 0.6f);
            // 20 = minimo reale dello slider in tutti e 3 gli schermi — interpolare su
            // zoomPercent/100 (0.2..1.0) non arriva mai a t=0, quindi anche al minimo restava
            // un residuo di "riempi" (l'immagine sconfinava ancora leggermente). Normalizzato
            // sul range effettivo 20-100 così il minimo dello slider è VERO "adatta" (t=0).
            float t = Math.max(0f, Math.min(1f, (zoomPercent - 20) / 80f));
            scale = containScale + (coverScale - containScale) * t;
            tx = dstRect.centerX() - (bw * scale) / 2f;
            ty = dstRect.centerY() - (bh * scale) / 2f;
        }
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scale, scale);
        m.postTranslate(tx, ty);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bmp, m, paint);
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
        Bitmap bmp = decodeSampledBitmap(f);
        if (bmp != null) {
            mPowerMenuMenuBgBitmap = bmp;
            mPowerMenuMenuBgBitmapMtime = mtime;
        }
    }

    /** Applies "Sfondo Menù Power" to the whole GlobalActions Dialog window — a plain solid
     *  colour for accent/custom, a center-cropped image Drawable for image mode. Unlike the
     *  pillolone's own background (a Canvas draw clipped to the pill's rounded rect), this sets
     *  the WHOLE window's background directly since there's no single shape to clip to here. */
    private void applyMenuBackground(android.app.Dialog d) {
        android.view.Window w = d.getWindow();
        if (w == null) return;
        XposedBridge.log("[ Obsidian ] MiscMods menu-bg: show mode=" + mPowerMenuMenuBgMode
                + " bmp=" + (mPowerMenuMenuBgBitmap == null ? "null" : "ok"));
        if ("stock".equals(mPowerMenuMenuBgMode)) return;
        if ("image".equals(mPowerMenuMenuBgMode)) {
            // 2026-09-13: segnalato dall'utente come intermittente ("sparita di nuovo") anche
            // dopo il fix del decode a piena risoluzione — non ancora chiaro se sia ancora un
            // OOM occasionale o una corsa al boot (updatePrefs()/preloadFallback() non ancora
            // passati la primissima volta che il men si apre). Retry sincrono una tantum qui,
            // sul thread che mostra il dialog — decodificare un bitmap già ridimensionato allo
            // schermo è rapido, vale la pena tentare piuttosto che arrendersi subito — più un log
            // per distinguere finalmente le due cause la prossima volta che si ripresenta.
            if (mPowerMenuMenuBgBitmap == null) {
                XposedBridge.log("[ Obsidian ] MiscMods menu-bg: null al momento dello show, retry sincrono");
                refreshPowerMenuMenuBgBitmap();
            }
            if (mPowerMenuMenuBgBitmap == null) {
                XposedBridge.log("[ Obsidian ] MiscMods menu-bg: ancora null dopo il retry, resta stock");
                return;
            }
            w.setBackgroundDrawable(new CenterCropBitmapDrawable(mPowerMenuMenuBgBitmap,
                    mPowerMenuMenuBgCropCx / 100f, mPowerMenuMenuBgCropCy / 100f, mPowerMenuMenuBgCropZoom));
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
        private final float mCx, mCy;
        private final int mZoomPercent;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CenterCropBitmapDrawable(Bitmap bmp, float cx, float cy, int zoomPercent) {
            mBmp = bmp; mCx = cx; mCy = cy; mZoomPercent = zoomPercent;
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            drawCustomCropped(canvas, mBmp, new RectF(b), mCx, mCy, mZoomPercent);
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
        Bitmap bmp = decodeSampledBitmap(f);
        if (bmp != null) {
            mPowerMenuHandlerBitmap = bmp;
            mPowerMenuHandlerBitmapMtime = mtime;
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

            if (!mDiagHandlerRectLogged) {
                mDiagHandlerRectLogged = true;
                XposedBridge.log("[ Obsidian ] MiscMods DIAG mHandlerRectF w=" + rectF.width()
                        + " h=" + rectF.height() + " aspect=" + (rectF.width() / rectF.height()));
            }

            // "Scala Immagine" (mPowerMenuHandlerScale) rimossa 2026-09-15 — sovrapposta allo
            // stesso effetto già ottenibile con "Zoom" (che sotto 100% rimpicciolisce già
            // l'immagine visibile), richiesta esplicita dell'utente: "se fanno la stessa cosa,
            // togli Scala immagine".
            RectF dst = rectF;

            canvas.save();
            Path clip = new Path();
            clip.addOval(rectF, Path.Direction.CW);
            canvas.clipPath(clip);
            // 2026-09-17: il Pallino ora non ritaglia MAI (richiesta esplicita: "mai tagliare,
            // mostra tutta l'immagine") — usa un formula dedicata invece di drawCustomCropped
            // (quella condivisa resta invariata per Pillolone/Sfondo Menù, che vogliono il
            // comportamento "riempi" normale). Vedi anche ImageCropOverlayView.setContainOnly,
            // stessa formula duplicata lì per indipendenza (processo diverso).
            drawContainedOnly(canvas, mPowerMenuHandlerBitmap, dst, mPowerMenuHandlerCropZoom);
            canvas.restore();
        } catch (Throwable ignored) {}
    }

    /** Adatta SEMPRE l'immagine intera dentro dstRect, mai ritagliata — 20-100%: 20=più piccola
     *  con più margine, 100=adatta naturale (immagine intera, margine minimo ma mai zero, vedi
     *  MARGIN_FACTOR). Centrato, nessun pan (cx/cy non hanno senso quando si vede sempre tutto). */
    private static void drawContainedOnly(Canvas canvas, Bitmap bmp, RectF dstRect, int zoomPercent) {
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        final float MARGIN_FACTOR = 0.92f;
        float fitScale = Math.min(dstRect.width() / bw, dstRect.height() / bh) * MARGIN_FACTOR;
        float t = Math.max(0f, Math.min(1f, (zoomPercent - 20) / 80f));
        float scale = fitScale * (0.5f + 0.5f * t);
        float tx = dstRect.centerX() - (bw * scale) / 2f;
        float ty = dstRect.centerY() - (bh * scale) / 2f;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scale, scale);
        m.postTranslate(tx, ty);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bmp, m, paint);
    }

    /** 2026-09-13: user reported the ring came out the same diameter as the pill itself —
     *  mHandlerRectF is the handle's full TOUCH-target bounds (same rect drawHandlerImage() clips
     *  an image to, confirmed working full-size there per [[project_power_menu_customization]]),
     *  visibly larger than the small dot OOS itself actually paints inside it. No separate
     *  "visual radius" field was found without another live reflection dump, so shrunk by a fixed
     *  factor (tuned against the user's screenshot, not measured) — same idea as the "Scala
     *  Immagine" scale-around-centre math already used for image mode, just a fixed default
     *  instead of a user-facing slider since the border has no such control. */
    private static final float HANDLER_BORDER_SCALE = 0.62f;

    /** Bordo Pallino — a stroked ring around the handle, same mHandlerRectF the fill/image use
     *  (shrunk, see HANDLER_BORDER_SCALE above), same "Canvas#drawOval, real stroke-capable API"
     *  approach as drawPillBorder() (drawing BEFORE the stock circle wouldn't show a stroke on
     *  top of it; drawn in afterHookedMethod like drawHandlerImage/drawPillBorder so it always
     *  renders above the fill). */
    private void drawHandlerBorder(Canvas canvas, Object shutdownView) {
        if (!mPowerMenuHandlerBorderEnabled) return;
        try {
            Object rectFObj = getObjectField(shutdownView, "mHandlerRectF");
            if (!(rectFObj instanceof RectF)) return;
            RectF full = (RectF) rectFObj;
            if (full.width() <= 0 || full.height() <= 0) return;
            float cx = full.centerX(), cy = full.centerY();
            // 2026-09-17 (3° giro, ripensato): il bordo torna FISSO — un vero cerchio, mai ovale
            // — esattamente come nel preview dell'app (dove il riquadro/bordo ha sempre l'aspect
            // 1:1 fisso, indipendente dall'immagine; è l'immagine dentro a contenersi con
            // margine). Farlo "seguire" le proporzioni dell'immagine (tentativo precedente)
            // produceva un bordo ovale sproporzionato per foto non quadrate — sbagliato, il
            // margine naturale tra immagine e bordo (drawContainedOnly, MARGIN_FACTOR) basta da
            // solo, non serve anche ridimensionare il bordo.
            // In modalità immagine il cerchio resta a piena dimensione (l'immagine stessa arriva
            // già solo al 92% grazie a drawContainedOnly, il margine naturale è già lì) — 0.62
            // resta solo per la modalità colore/accento (il piccolo pallino dipinto dallo stock).
            float scale = "image".equals(mPowerMenuHandlerMode) ? 1f : HANDLER_BORDER_SCALE;
            float hw = full.width() / 2f * scale;
            float hh = full.height() / 2f * scale;
            RectF rectF = new RectF(cx - hw, cy - hh, cx + hw, cy + hh);
            Paint borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(handlerBorderColor());
            canvas.drawOval(rectF, borderPaint);
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
