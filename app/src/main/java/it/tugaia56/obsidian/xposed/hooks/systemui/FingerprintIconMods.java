package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllConstructors;
import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.BuildConfig;
import it.tugaia56.obsidian.xposed.ResourceManager;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Lock screen fingerprint icon mods (mirrors OC's "Icona Impronta Digitale" section):
 *  - lockscreen_fp_remove_icon: hide the icon entirely
 *  - lockscreen_fp_custom_icon: replace with a preset (fingerprint_0..N in drawable-nodpi)
 *    or a custom image at /.obsidian/lockscreen_fp_icon.png
 *  - lockscreen_fp_icon_scaling: scale factor for the custom/preset icon
 */
public class FingerprintIconMods extends XposedMods {

    public static final String CUSTOM_ICON_FILE =
            Environment.getExternalStorageDirectory() + "/.obsidian/lockscreen_fp_icon.png";

    /** Colore dell'icona: 0 = spento (come prima), 1 = accento, 2 = opacita' tocco, 3 = personalizzato (PREF_COLOR). */
    public static final String PREF_COLOR_MODE = "lockscreen_fp_color_mode";
    public static final String PREF_COLOR      = "lockscreen_fp_color";

    private int     mColorMode         = 0;
    private int     mCustomColor       = Color.WHITE;

    private boolean mHideFingerprint   = false;
    private boolean mHideInAod         = false;
    private boolean mCustomFingerprint = false;
    private int     mFingerprintStyle  = 0;
    private float   mFpScale           = 1.0f;

    private Drawable mFpDrawable;

    private String mFpIconField;
    private String mImMobileDrawableField;
    private String mFadeInAnimDrawableField;
    private String mFadeOutAnimDrawableField;

    public FingerprintIconMods(Context context) { super(context); }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mHideFingerprint   = Xprefs.getBoolean("lockscreen_fp_remove_icon", false);
        mHideInAod         = Xprefs.getBoolean("lockscreen_fp_hide_aod", false);
        mCustomFingerprint = Xprefs.getBoolean("lockscreen_fp_custom_icon", false);
        mFingerprintStyle  = Xprefs.getInt("lockscreen_fp_icon_custom", 0);
        mFpScale           = Xprefs.getFloat("lockscreen_fp_icon_scaling", 1.0f);
        mColorMode         = Xprefs.getInt(PREF_COLOR_MODE, 0);
        mCustomColor       = Xprefs.getInt(PREF_COLOR, Color.WHITE);
        updateDrawable();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        Class<?> cls = tryFindClass(lp,
                "com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintUiMech",  // OOS15
                "com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintUiMach",  // OOS14
                "com.oplus.systemui.keyguard.finger.onscreenfingerprint.OnScreenFingerprintUiMech"); // OOS13
        if (cls == null) return;

        // Icona impronta nascosta solo con lo schermo spento (AOD): lì può finire sotto al blocco
        // orologio/meteo che l'AOD sposta. Sulla schermata di blocco resta visibile.
        try {
            Class<?> iconCls = tryFindClass(lp, "com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintIcon");
            if (iconCls != null) {
                hookAllMethods(iconCls, "setVisibility", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!mHideInAod || p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
                        if ((Integer) p.args[0] == 0
                                && it.tugaia56.obsidian.xposed.utils.KeyguardUiState.isDozing(mContext)) {
                            p.args[0] = 8;
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] FingerprintIconMods hide-in-AOD hook failed: " + t);
        }

        hookAllConstructors(cls, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                resolveFieldNames(p.thisObject);
            }
        });

        hookAllMethods(cls, "loadAnimDrawables", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (mHideFingerprint || mCustomFingerprint) updateFingerprintIcon(p, false);
            }
        });

        hookAllMethods(cls, "startFadeInAnimation", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (mHideFingerprint || mCustomFingerprint) updateFingerprintIcon(p, true);
            }
        });

        if (Build.VERSION.SDK_INT == 33) {
            hookAllMethods(cls, "updateFpIconColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!mCustomFingerprint || mHideFingerprint) return;
                    try {
                        Drawable d = (Drawable) getObjectField(p.thisObject, "mImMobileDrawable");
                        if (d != null) d.clearColorFilter();
                    } catch (Throwable ignored) {}
                }
            });
        }

        if (Build.VERSION.SDK_INT >= 34) {
            hookAllMethods(cls, "updateFpColor", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (mHideFingerprint) return;
                    int tint = chosenColor();
                    if (tint != 0) { p.args[0] = tint; return; }
                    if (!mCustomFingerprint) return;
                    p.args[0] = Color.TRANSPARENT;
                }
            });
        }


        // Colore scelto: dopo ogni aggiornamento dell'icona lo si applica direttamente a vista e disegno (il sistema ricolora
        // per suo conto con piu' percorsi: animazione a gradiente, sfondo bianco, ecc.)
        for (String name : new String[]{"updateFpColor", "updateFpIconColor", "updateFpColorForWhiteBackground",
                "startFingerprintColorGradientsAnim", "startFadeInAnimation", "loadAnimDrawables"}) {
            try {
                hookAllMethods(cls, name, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        applyChosenColor(p.thisObject, name);
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] FingerprintIconMods colour hook " + name + " failed: " + t);
            }
        }

        // Con icona personalizzata attiva, ci spacciamo per la modalità "immagine
        // personalizzata" nativa (isCustomFpIcon) così OOS stesso salta la costruzione
        // di fadeInAnimDrawable/fadeOutAnimDrawable in loadAnimDrawables() — niente
        // animazione nativa dell'icona preset da nascondere, invece di sovrapporci un fade.
        Class<?> switchHelperCls = tryFindClass(lp,
                "com.oplus.systemui.biometrics.finger.udfps.OnScreenFpSwitchHelper");
        if (switchHelperCls != null) {
            try {
                Class<?> companionCls = tryFindClass(lp,
                        "com.oplus.systemui.biometrics.finger.udfps.OnScreenFpSwitchHelper$Companion");
                if (companionCls != null) {
                    hookAllMethods(companionCls, "getInstance", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            forceCustomFpIconFlag(p.getResult());
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] FingerprintIconMods getInstance hook failed: " + t);
            }

            try {
                hookAllMethods(switchHelperCls, "access$handleOSCustomFPSwitchChage", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        forceCustomFpIconFlag(p.args[0]);
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[ Obsidian ] FingerprintIconMods handleOSCustomFPSwitchChage hook failed: " + t);
            }
        }
    }

    /** Colore scelto per l'icona, 0 se la scelta e' "disattivata" (resta il comportamento di prima). */
    private int chosenColor() {
        switch (mColorMode) {
            case 1: return accentColor();
            case 2: return it.tugaia56.obsidian.xposed.utils.AccentUtil.rippleColor();
            case 3: return mCustomColor;
            default: return 0;
        }
    }

    private boolean mColorLogged;

    private void applyChosenColor(Object mech, String source) {
        int c = chosenColor();
        if (c == 0 || mHideFingerprint) return;
        try {
            Object icon = mFpIconField != null ? getObjectField(mech, mFpIconField) : null;
            if (icon instanceof android.widget.ImageView) {
                android.widget.ImageView iv = (android.widget.ImageView) icon;
                iv.setImageTintList(android.content.res.ColorStateList.valueOf(c));
                iv.setImageTintMode(android.graphics.PorterDuff.Mode.SRC_IN);
                iv.invalidate();
            }
            if (mImMobileDrawableField != null) {
                Object d = getObjectField(mech, mImMobileDrawableField);
                if (d instanceof Drawable) ((Drawable) d).setTint(c);
            }
            if (!mColorLogged) {
                mColorLogged = true;
                XposedBridge.log("[ Obsidian ] FingerprintIconMods colour 0x" + Integer.toHexString(c) + " applied via " + source
                        + " icon=" + (icon == null ? "null" : icon.getClass().getName()));
            }
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] FingerprintIconMods applyChosenColor: " + t);
        }
    }

    private int accentColor() {
        if (Xprefs.getBoolean("DST_ACCENT1_on", false)) return Xprefs.getInt("DST_ACCENT1", 0xFF908DFF) | 0xFF000000;
        return it.tugaia56.obsidian.xposed.utils.AccentUtil.systemAccent();
    }

    private void forceCustomFpIconFlag(Object switchHelper) {
        if (switchHelper == null || !mCustomFingerprint) return;
        try { setObjectField(switchHelper, "isCustomFpIcon", true); } catch (Throwable ignored) {}
    }

    private void updateFingerprintIcon(XC_MethodHook.MethodHookParam param, boolean isStartMethod) {
        try {
            Object fpIcon = mFpIconField != null ? getObjectField(param.thisObject, mFpIconField) : null;

            if (mFpDrawable == null) {
                if (mFadeInAnimDrawableField != null) setObjectField(param.thisObject, mFadeInAnimDrawableField, null);
                if (mFadeOutAnimDrawableField != null) setObjectField(param.thisObject, mFadeOutAnimDrawableField, null);
            }
            if (mImMobileDrawableField != null) setObjectField(param.thisObject, mImMobileDrawableField, mFpDrawable);
            if (fpIcon != null) callMethod(fpIcon, "setImageDrawable", mFpDrawable);

            if (isStartMethod) param.setResult(null);
            else callMethod(param.thisObject, "updateFpIconColor");
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] FingerprintIconMods updateFingerprintIcon: " + t);
        }
    }

    private void resolveFieldNames(Object target) {
        mFpIconField             = resolveField(target, "fpIcon", "mFpIcon");
        mImMobileDrawableField   = resolveField(target, "imMobileDrawable", "mImMobileDrawable");
        mFadeInAnimDrawableField = resolveField(target, "fadeInAnimDrawable", "mFadeInAnimDrawable");
        mFadeOutAnimDrawableField= resolveField(target, "fadeOutAnimDrawable", "mFadeOutAnimDrawable");
    }

    private String resolveField(Object target, String... candidates) {
        for (String name : candidates) {
            try { getObjectField(target, name); return name; } catch (Throwable ignored) {}
        }
        return null;
    }

    private void updateDrawable() {
        Drawable d = null;
        if (mCustomFingerprint) {
            if (mFingerprintStyle != -1 && ResourceManager.modRes != null) {
                int resId = ResourceManager.modRes.getIdentifier(
                        "fingerprint_" + mFingerprintStyle, "drawable", BuildConfig.APPLICATION_ID);
                if (resId != 0) {
                    d = ResourcesCompat.getDrawable(ResourceManager.modRes, resId, mContext.getTheme());
                }
            } else {
                try {
                    File file = new File(CUSTOM_ICON_FILE);
                    if (file.exists()) {
                        ImageDecoder.Source source = ImageDecoder.createSource(file);
                        d = ImageDecoder.decodeDrawable(source);
                        if (d instanceof AnimatedImageDrawable) {
                            ((AnimatedImageDrawable) d).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                            ((AnimatedImageDrawable) d).start();
                        }
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[ Obsidian ] FingerprintIconMods: custom icon load failed: " + t);
                }
            }
        }
        if (d != null && mFpScale != 1.0f) d = scaleDrawable(d, mFpScale);
        mFpDrawable = d;
    }

    private Drawable scaleDrawable(Drawable drawable, float scale) {
        try {
            int width  = Math.max(1, Math.round(drawable.getIntrinsicWidth()  * scale));
            int height = Math.max(1, Math.round(drawable.getIntrinsicHeight() * scale));
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return new BitmapDrawable(mContext.getResources(), bitmap);
        } catch (Throwable t) {
            return drawable;
        }
    }

    private Class<?> tryFindClass(XC_LoadPackage.LoadPackageParam lp, String... names) {
        for (String name : names) {
            try { return de.robv.android.xposed.XposedHelpers.findClass(name, lp.classLoader); }
            catch (Throwable ignored) {}
        }
        XposedBridge.log("[ Obsidian ] FingerprintIconMods: none of " + java.util.Arrays.toString(names) + " found");
        return null;
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
