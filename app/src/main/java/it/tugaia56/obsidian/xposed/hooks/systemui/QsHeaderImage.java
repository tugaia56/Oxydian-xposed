package it.tugaia56.obsidian.xposed.hooks.systemui;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findClass;

import android.util.Log;
import de.robv.android.xposed.XposedBridge;
import static it.tugaia56.obsidian.utils.Constants.Packages.SYSTEM_UI;
import static it.tugaia56.obsidian.xposed.XPrefs.Xprefs;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.os.Environment;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import it.tugaia56.obsidian.xposed.XposedMods;

/**
 * Adds a custom header image at the top of the QS panel.
 *
 * The image is written by the UI to external storage:
 *   /sdcard/.obsidian/qs_header_image
 *
 * Features:
 *  - Adjustable height and opacity
 *  - Image scaling mode (CENTER_CROP / FIT_CENTER / FIT_XY / CENTER)
 *  - Fade-to-bottom gradient (intensity 0-100%) via container.setForeground()
 *  - Side padding and top padding on the ImageView
 */
public class QsHeaderImage extends XposedMods {

    private static final String PREF_ENABLED = "OBS_QS_HEADER_ENABLED";
    private static final String PREF_ALPHA   = "OBS_QS_HEADER_ALPHA";    // 0-100
    private static final String PREF_HEIGHT  = "OBS_QS_HEADER_HEIGHT";   // dp
    private static final String PREF_FADE    = "OBS_QS_HEADER_FADE_A";   // 0-100 (0=off) – new key
    private static final String PREF_PAD_H   = "OBS_QS_HEADER_PAD_H";   // dp, 0-64
    private static final String PREF_PAD_T   = "OBS_QS_HEADER_PAD_T";   // dp, 0-64
    private static final String PREF_SCALE   = "OBS_QS_HEADER_SCALE";    // 0-3
    private static final String PREF_GRAVITY = "OBS_QS_HEADER_GRAVITY";  // 0-100 (0=top, 50=center, 100=bottom) – mode 1 only
    private static final String PREF_CROP_CX   = "OBS_QS_HEADER_CROP_CX";   // 0-100, mode 0 only
    private static final String PREF_CROP_CY   = "OBS_QS_HEADER_CROP_CY";   // 0-100, mode 0 only
    private static final String PREF_CROP_ZOOM = "OBS_QS_HEADER_CROP_ZOOM"; // >=100, 100 = immagine intera, mode 0 only

    /** Relative path under external storage where QsHeaderImageFragment saves the image. */
    private static final String IMAGE_SUBPATH = ".obsidian/qs_header_image";

    /**
     * Scale types — index must match QsHeaderImageFragment.SCALE_NAMES order.
     * Modes 0 and 1 are handled via MATRIX in applyToView; entries here are placeholders.
     *
     *  0 – Riempi (ritaglia)  MATRIX fill-both + gravity crop
     *  1 – Adatta larghezza   MATRIX fill-width + gravity (portrait clipped; landscape full)
     *  2 – Intera             FIT_CENTER: whole image proportional, no crop, may letterbox
     *  3 – Originale          CENTER: pixel 1:1, no scaling
     */
    private static final ImageView.ScaleType[] SCALE_TYPES = {
            ImageView.ScaleType.CENTER_CROP,  // 0 – Riempi    [MATRIX]
            ImageView.ScaleType.CENTER_CROP,  // 1 – Larghezza [MATRIX]
            ImageView.ScaleType.FIT_CENTER,   // 2 – Intera
            ImageView.ScaleType.CENTER,        // 3 – Originale
    };

    private boolean mEnabled   = false;
    private int     mAlpha     = 100;      // 0-100
    private int     mHeight    = 200;      // dp
    private int     mFade      = 0;        // 0-100 (0 = off)
    private int     mFadeColor = Color.BLACK; // target color for fade gradient
    private int     mPadH      = 0;        // dp
    private int     mPadT      = 0;        // dp
    private int     mScale     = 0;        // index into SCALE_TYPES
    private int     mGravity   = 50;       // 0=top, 50=center, 100=bottom – mode 1 only
    private int     mCropCx    = 50;       // 0-100, mode 0 only
    private int     mCropCy    = 50;       // 0-100, mode 0 only
    private int     mCropZoom  = 100;      // >=100, 100 = immagine intera, mode 0 only

    private final List<FrameLayout> mContainers = new ArrayList<>();
    private final List<ImageView>   mImageViews = new ArrayList<>();
    private final List<View>        mFadeViews  = new ArrayList<>();

    public QsHeaderImage(Context context) {
        super(context);
    }

    @Override
    public void updatePrefs(String... Key) {
        if (Xprefs == null) return;
        mEnabled = Xprefs.getBoolean(PREF_ENABLED, false);
        mAlpha   = Xprefs.getInt(PREF_ALPHA,   100);
        mHeight  = Xprefs.getInt(PREF_HEIGHT,  200);
        mFade    = Xprefs.getInt(PREF_FADE, 0);
        mPadH    = Xprefs.getInt(PREF_PAD_H, 0);
        mPadT    = Xprefs.getInt(PREF_PAD_T, 0);
        mScale   = Xprefs.getInt(PREF_SCALE, 0);
        mGravity = Xprefs.getInt(PREF_GRAVITY, 50);
        mCropCx   = Xprefs.getInt(PREF_CROP_CX, 50);
        mCropCy   = Xprefs.getInt(PREF_CROP_CY, 50);
        mCropZoom = Xprefs.getInt(PREF_CROP_ZOOM, 100);
        // Fade target: match the QS solid background color if enabled, else black.
        boolean qsBgEnabled = Xprefs.getBoolean("DST_QS_BG_ENABLED", false);
        mFadeColor = qsBgEnabled
                ? Xprefs.getInt("OBS_QS_BG_COLOR", Color.BLACK)
                : Color.BLACK;
        refreshHeaderImage();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!SYSTEM_UI.equals(lp.packageName)) return;

        Log.e("OBS_QSH", "handleLoadPackage pkg=" + lp.packageName);
        XposedBridge.log("[ Obsidian ] QsHeaderImage: handleLoadPackage");

        Class<?> containerClass = findContainerClass(lp.classLoader);
        Log.e("OBS_QSH", "containerClass=" + (containerClass != null ? containerClass.getName() : "null"));
        XposedBridge.log("[ Obsidian ] QsHeaderImage: container=" + (containerClass != null ? containerClass.getName() : "null"));

        if (containerClass != null) {
            // onFinishInflate on OplusQSContainerImpl — works on OOS 14/15 where the class
            // declares this method directly. On OOS 16 it may not fire (not overridden).
            hookAllMethods(containerClass, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Log.e("OBS_QSH", "OplusQSContainerImpl.onFinishInflate fired");
                    tryAddHeaderView(p.thisObject);
                }
            });

            // updateResources — OOS-specific method on the QS container; fires on refresh.
            try {
                hookAllMethods(containerClass, "updateResources", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Log.e("OBS_QSH", "OplusQSContainerImpl.updateResources fired");
                        tryAddHeaderView(p.thisObject);
                        refreshHeaderImage();
                    }
                });
            } catch (Throwable ignored) {}
        }

        // OplusQSRootView.onFinishInflate — primary hook on OOS 15-16 (new Control Center).
        // This class explicitly declares onFinishInflate, so hookAllMethods finds it.
        try {
            Class<?> rootViewClass = findClass(
                    "com.oplus.systemui.plugins.qs.OplusQSRootView", lp.classLoader);
            Log.e("OBS_QSH", "OplusQSRootView found: " + rootViewClass.getName());
            XposedBridge.log("[ Obsidian ] QsHeaderImage: OplusQSRootView found");
            hookAllMethods(rootViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Log.e("OBS_QSH", "OplusQSRootView.onFinishInflate fired cls=" + p.thisObject.getClass().getName());
                    XposedBridge.log("[ Obsidian ] QsHeaderImage: OplusQSRootView.onFinishInflate fired");
                    tryAddHeaderView(p.thisObject);
                }
            });
        } catch (Throwable t) {
            Log.e("OBS_QSH", "OplusQSRootView NOT found: " + t);
            XposedBridge.log("[ Obsidian ] QsHeaderImage: OplusQSRootView NOT found");
        }
    }

    /**
     * Add the header container to root, guarding against duplicate additions.
     * Called from multiple possible hook points; only the first call per root instance acts.
     */
    private void tryAddHeaderView(Object thisObj) {
        Log.e("OBS_QSH", "tryAddHeaderView cls=" + (thisObj != null ? thisObj.getClass().getName() : "null"));
        XposedBridge.log("[ Obsidian ] QsHeaderImage: tryAddHeaderView cls=" +
                (thisObj != null ? thisObj.getClass().getName() : "null"));
        if (!(thisObj instanceof FrameLayout)) {
            Log.e("OBS_QSH", "NOT a FrameLayout, skipping");
            XposedBridge.log("[ Obsidian ] QsHeaderImage: not a FrameLayout, skipping");
            return;
        }
        FrameLayout root = (FrameLayout) thisObj;
        // Guard: skip if we already added a container to this root.
        for (FrameLayout c : mContainers) {
            if (root.equals(c.getParent())) {
                Log.e("OBS_QSH", "already added, skipping");
                XposedBridge.log("[ Obsidian ] QsHeaderImage: already added, skipping");
                return;
            }
        }
        addHeaderViewTo(root);
    }

    private Class<?> findContainerClass(ClassLoader cl) {
        try {
            return findClass("com.oplus.systemui.qs.OplusQSContainerImpl", cl);
        } catch (Throwable ignored) {}
        try {
            return findClass("com.oplusos.systemui.qs.OplusQSContainerImpl", cl);
        } catch (Throwable ignored) {}
        return null;
    }

    // ── View creation ─────────────────────────────────────────────────────────

    private void addHeaderViewTo(FrameLayout root) {
        Log.e("OBS_QSH", "addHeaderViewTo root=" + root.getClass().getName());
        XposedBridge.log("[ Obsidian ] QsHeaderImage: addHeaderViewTo " + root.getClass().getName());
        FrameLayout container = new FrameLayout(mContext);
        container.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(mHeight)));

        ImageView iv = new ImageView(mContext);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(scaleType());
        container.addView(iv);

        // Dedicated fade view drawn on top of the ImageView (more reliable than setForeground).
        View fadeView = new View(mContext);
        fadeView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fadeView.setVisibility(View.GONE);
        container.addView(fadeView);

        // Add at index 0 so container is BEHIND tiles (same as OC approach).
        root.addView(container, 0);

        mContainers.add(container);
        mImageViews.add(iv);
        mFadeViews.add(fadeView);

        applyToView(container, iv, fadeView);
    }

    // ── Image refresh ─────────────────────────────────────────────────────────

    private void refreshHeaderImage() {
        if (mContainers.isEmpty()) return;
        for (int i = 0; i < mContainers.size(); i++) {
            FrameLayout c  = mContainers.get(i);
            ImageView   iv = mImageViews.get(i);
            View        fv = mFadeViews.get(i);
            c.post(() -> applyToView(c, iv, fv));
        }
    }

    private void applyToView(FrameLayout container, ImageView iv, View fadeView) {
        Log.e("OBS_QSH", "applyToView enabled=" + mEnabled);
        XposedBridge.log("[ Obsidian ] QsHeaderImage: applyToView enabled=" + mEnabled);
        if (!mEnabled) {
            container.setVisibility(View.GONE);
            return;
        }

        Bitmap bmp = loadBitmap();
        if (bmp == null) {
            container.setVisibility(View.GONE);
            return;
        }

        // Height
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        lp.height = dp(mHeight);
        container.setLayoutParams(lp);

        // Image: padding + bitmap
        int padH = dp(mPadH);
        int padT = dp(mPadT);
        iv.setPadding(padH, padT, padH, 0);
        iv.setImageBitmap(bmp);
        iv.setImageAlpha(alphaToInt(mAlpha));

        // Scale: modes 0 and 1 both use MATRIX for crop-aware positioning.
        //   0 – Riempi (ritaglia): fixed-box/pannable-photo crop (cx/cy/zoom), same math as
        //       ImageCropOverlayView's computeCropSizeSrcPx — kept in sync by hand, see there.
        //   1 – Adatta larghezza:  fill width only, single-axis vertical gravity (legacy)
        if (mScale == 0) {
            final Bitmap finalBmp = bmp;
            final float  cx = mCropCx / 100f, cy = mCropCy / 100f;
            final int    zoom = mCropZoom;
            iv.post(() -> {
                float viewW = iv.getWidth();
                float viewH = iv.getHeight();
                if (viewW <= 0 || viewH <= 0) return;
                float bmpW = finalBmp.getWidth(), bmpH = finalBmp.getHeight();
                float dstAspect = viewW / viewH;
                float srcAspect = bmpW / bmpH;
                float maxCropW, maxCropH;
                if (srcAspect > dstAspect) { maxCropH = bmpH; maxCropW = bmpH * dstAspect; }
                else { maxCropW = bmpW; maxCropH = bmpW / dstAspect; }
                float frac = Math.max(0.05f, Math.min(1f, 100f / Math.max(1, zoom)));
                float cropW = maxCropW * frac, cropH = maxCropH * frac;
                float scale = viewW / cropW;
                float leftSrc = Math.max(0f, Math.min(bmpW - cropW, bmpW * cx - cropW / 2f));
                float topSrc  = Math.max(0f, Math.min(bmpH - cropH, bmpH * cy - cropH / 2f));
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate(-leftSrc * scale, -topSrc * scale);
                iv.setScaleType(ImageView.ScaleType.MATRIX);
                iv.setImageMatrix(m);
            });
        } else if (mScale == 1) {
            final Bitmap finalBmp = bmp;
            final int    gravity  = mGravity;
            iv.post(() -> {
                float viewW = iv.getWidth();
                float viewH = iv.getHeight();
                if (viewW <= 0 || viewH <= 0) return;
                float scale = viewW / finalBmp.getWidth(); // fill width
                float tx = (viewW - finalBmp.getWidth()  * scale) / 2f;
                float ty = (viewH - finalBmp.getHeight() * scale) * (gravity / 100f);
                Matrix m = new Matrix();
                m.setScale(scale, scale);
                m.postTranslate(tx, ty);
                iv.setScaleType(ImageView.ScaleType.MATRIX);
                iv.setImageMatrix(m);
            });
        } else {
            iv.setScaleType(scaleType());
        }

        // Fade-to-bottom: gradient overlay from transparent to black, anchored to the
        // container bottom.  Height = mFade% of container height, so the slider controls
        // how far up the fade reaches.  Fading to black avoids the hard container-edge
        // artifact that DST_OUT produced, and blends seamlessly with the dark QS panel.
        if (mFade > 0) {
            int fadeH = Math.max(1, dp(mHeight) * mFade / 100);
            FrameLayout.LayoutParams fadeLp = (FrameLayout.LayoutParams) fadeView.getLayoutParams();
            fadeLp.width   = ViewGroup.LayoutParams.MATCH_PARENT;
            fadeLp.height  = fadeH;
            fadeLp.gravity = Gravity.BOTTOM;
            fadeView.setLayoutParams(fadeLp);
            fadeView.setLayerType(View.LAYER_TYPE_NONE, null);
            container.setLayerType(View.LAYER_TYPE_NONE, null);
            GradientDrawable grad = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.TRANSPARENT, mFadeColor});
            fadeView.setBackground(grad);
            fadeView.setVisibility(View.VISIBLE);
        } else {
            fadeView.setLayerType(View.LAYER_TYPE_NONE, null);
            container.setLayerType(View.LAYER_TYPE_NONE, null);
            fadeView.setVisibility(View.GONE);
        }

        container.setVisibility(View.VISIBLE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Bitmap loadBitmap() {
        File f = new File(Environment.getExternalStorageDirectory(), IMAGE_SUBPATH);
        if (!f.exists()) {
            XposedBridge.log("[ Obsidian ] QsHeaderImage: image not found at " + f.getAbsolutePath());
            return null;
        }
        // decodeStream is more reliable than decodeFile() in a system process context.
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
            Bitmap bmp = BitmapFactory.decodeStream(fis);
            if (bmp == null) XposedBridge.log("[ Obsidian ] QsHeaderImage: decodeStream returned null");
            return bmp;
        } catch (Throwable t) {
            XposedBridge.log("[ Obsidian ] QsHeaderImage: error reading image: " + t);
            return null;
        }
    }

    private ImageView.ScaleType scaleType() {
        int idx = (mScale >= 0 && mScale < SCALE_TYPES.length) ? mScale : 0;
        return SCALE_TYPES[idx];
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                mContext.getResources().getDisplayMetrics());
    }

    private static int alphaToInt(int percent) {
        return (int) Math.round(percent / 100.0 * 255);
    }

    @Override public boolean listensTo(String packageName) { return SYSTEM_UI.equals(packageName); }
}
