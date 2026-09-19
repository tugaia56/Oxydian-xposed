package it.tugaia56.obsidian.ui.views.edgelight;

/*
 * Portato da Oxygen Customizer (it.dhd.oxygencustomizer.xposed.views.edgelight.EdgeLightView).
 * Copyright (C) 2022 FlamingoOS Project
 * Copyright (C) 2023-2025 the risingOS Android Project
 * Licensed under the Apache License, Version 2.0.
 *
 * Adattamenti per l'anteprima nell'app Obsidian (nessun contesto SystemUI):
 *  - risoluzione colore accento/notifica/wallpaper -> mFallbackColor iniettato dal Fragment
 *  - niente XposedBridge / OpUtils / ThemeUtils
 *  - sempre "settings interface" (l'animazione non si auto-nasconde)
 */

import android.annotation.SuppressLint;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeAnimator;
import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeBlinkAnimator;
import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeDottedLineAnimator;
import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeEchoAnimator;
import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeLineAnimator;
import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeSideLinesAnimator;
import it.tugaia56.obsidian.ui.views.edgelight.animators.EdgeSnakeAnimator;

@SuppressLint("ViewConstructor")
public class EdgeLightView extends View {

    public static final String TAG = "EdgeLightView";

    private final Context mContext;

    // Settings
    private float mEdgeLightWidth = 20f;
    private int mEdgeLightStyle = 0;
    private int mCustomColor = Color.RED;
    private ColorMode mColorMode = ColorMode.ACCENT;
    private boolean mDrawBlur = false;
    private int mBlurMode = 0;
    private int mBlurType = 0;
    private float mCornerRadius = 20f;
    private int mNotificationColor = -2;
    private EdgeAnimator mEdgeAnimator;

    /** Colore usato per ACCENT / NOTIFICATION-fallback / WALLPAPER-fallback (dato dal Fragment). */
    private int mFallbackColor = Color.WHITE;

    // Styles
    private static final int EDGE_STYLE_BLINK = 1;
    private static final int EDGE_STYLE_SIDE_LINES = 2;
    private static final int EDGE_STYLE_SNAKE = 3;
    private static final int EDGE_STYLE_ECHO = 4;
    private static final int EDGE_STYLE_DOTTED_LINE = 5;

    private boolean mPulsing = false;
    public final int PULSE_REASON_NOTIFICATION = 1;

    private final WallpaperManager wallpaperManager;

    private long mAnimationDuration = 6300;
    private long animationDuration = 6300;
    private long pulsingDuration = 2100;

    public enum ColorMode {
        ACCENT, NOTIFICATION, WALLPAPER, GRADIENT, CUSTOM
    }

    private boolean mSettingsInterface = true;

    public void setSettingsInterface(boolean settingsInterface) { this.mSettingsInterface = settingsInterface; }
    public boolean isSettingsInterface() { return mSettingsInterface; }

    public EdgeLightView(Context context, boolean settingsInterface) {
        super(context);
        mContext = context;
        mSettingsInterface = settingsInterface;
        this.wallpaperManager = (WallpaperManager) mContext.getSystemService(Context.WALLPAPER_SERVICE);
        loadEdgeAnimator();
    }

    public void setFallbackColor(int color) {
        mFallbackColor = color;
        if (mEdgeAnimator != null) setColorMode(mColorMode);
    }

    public EdgeAnimator getCurrentEdgeAnimator() { return mEdgeAnimator; }

    public void setOptions(int edgeLightStyle, float edgeLightWidth, ColorMode colorMode,
                           int customColor, boolean drawBlur, int blurMode, int blurType) {
        this.mEdgeLightWidth = edgeLightWidth;
        this.mDrawBlur = drawBlur;
        this.mBlurType = blurType;
        this.mBlurMode = blurMode;
        setEdgeLightStyle(edgeLightStyle); // imposta anche stroke width
        this.mCustomColor = customColor;
        setColorMode(colorMode);
    }

    public void setColorMode(ColorMode colorMode) {
        mColorMode = colorMode;
        switch (colorMode) {
            case ACCENT:
                setColor(mFallbackColor);
                break;
            case NOTIFICATION:
                setColor(mNotificationColor == -1 || mNotificationColor == -2 ? mFallbackColor : mNotificationColor);
                break;
            case CUSTOM:
                setColor(mCustomColor);
                break;
            case GRADIENT:
                setColor(-1);
                break;
            case WALLPAPER:
                WallpaperColors wpColors = null;
                try { wpColors = wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_LOCK); } catch (Throwable ignored) {}
                if (wpColors == null) setColor(mFallbackColor);
                else setColor(wpColors.getPrimaryColor().toArgb());
                break;
        }
    }

    private void loadEdgeAnimator() {
        mEdgeAnimator = getEdgeAnimator();
        mEdgeAnimator.setBlurOptions(mDrawBlur, mBlurType, mBlurMode);
        mEdgeAnimator.setScreenRadius(mCornerRadius);
        mEdgeAnimator.setDurations((int) animationDuration, pulsingDuration, mAnimationDuration);
    }

    private EdgeAnimator getEdgeAnimator() {
        return switch (mEdgeLightStyle) {
            case EDGE_STYLE_BLINK -> new EdgeBlinkAnimator(this);
            case EDGE_STYLE_SIDE_LINES -> new EdgeSideLinesAnimator(this);
            case EDGE_STYLE_SNAKE -> new EdgeSnakeAnimator(this);
            case EDGE_STYLE_ECHO -> new EdgeEchoAnimator(this);
            case EDGE_STYLE_DOTTED_LINE -> new EdgeDottedLineAnimator(this);
            default -> new EdgeLineAnimator(this);
        };
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mEdgeAnimator == null) return;
        mEdgeAnimator.onSizeChanged(w, h, oldw, oldh);
    }

    public void startAnimation() {
        if (mEdgeAnimator == null) return;
        mEdgeAnimator.startAnimation();
    }

    public void stopAnimation() {
        if (mEdgeAnimator == null) return;
        mEdgeAnimator.stopAnimation();
    }

    public void setEdgeLightStyle(int style) {
        mEdgeLightStyle = style;
        loadEdgeAnimator();
        setStrokeWidth(mEdgeLightWidth);
        mEdgeAnimator.setScreenRadius(mCornerRadius);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (mEdgeAnimator == null || !mEdgeAnimator.isAnimating()) return;
        mEdgeAnimator.draw(canvas);
    }

    public void show() {
        if (mEdgeAnimator.isAnimating()) return;
        if (getVisibility() == GONE) setVisibility(VISIBLE);
        startAnimation();
    }

    public void hide() {
        if (getVisibility() == VISIBLE) setVisibility(GONE);
        if (!mEdgeAnimator.isAnimating()) return;
        stopAnimation();
    }

    public void setColor(int color) {
        mEdgeAnimator.setPaintColor(color, color == -1);
        invalidate();
    }

    public void setScreenRadius(float radius) {
        mCornerRadius = radius;
        if (mEdgeAnimator != null) mEdgeAnimator.setScreenRadius(mCornerRadius);
    }

    public void setDurations(int anim, long pulsing, long duration) {
        this.animationDuration = anim;
        this.pulsingDuration = pulsing;
        if (duration == 0) duration = animationDuration;
        this.mAnimationDuration = duration;
        if (mEdgeAnimator != null) {
            mEdgeAnimator.setDurations((int) animationDuration, (int) pulsingDuration, mAnimationDuration);
        }
    }

    public void setStrokeWidth(float width) {
        this.mEdgeLightWidth = width;
        mEdgeAnimator.setStrokeWidth(mEdgeLightWidth);
    }

    public void setPulsing(boolean pulsing, int reason) {
        try {
            if (pulsing) {
                this.mPulsing = true;
                if (mColorMode == ColorMode.NOTIFICATION && reason != PULSE_REASON_NOTIFICATION) {
                    setColor(mFallbackColor);
                }
                show();
            } else {
                this.mPulsing = false;
                hide();
            }
        } catch (Throwable t) {
            Log.w(TAG, "setPulsing error: " + Log.getStackTraceString(t));
        }
    }

    public void logD(String msg) {
        Log.w(TAG, msg);
    }

    public void setNotificationColor(int newColor) {
        mNotificationColor = newColor;
        setColorMode(mColorMode);
        invalidate();
    }
}
