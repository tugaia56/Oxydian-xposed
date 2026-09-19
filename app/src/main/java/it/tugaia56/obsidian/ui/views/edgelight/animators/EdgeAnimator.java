package it.tugaia56.obsidian.ui.views.edgelight.animators;

// Portato da Oxygen Customizer — vedi EdgeLightView per licenza/crediti.

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

import it.tugaia56.obsidian.ui.views.edgelight.EdgeLightView;

public abstract class EdgeAnimator {

    protected EdgeLightView mEdgeLightView;

    public float mScreenRadius = 20f;

    public ValueAnimator mAnimator;
    public boolean mAnimating;
    protected Paint mEdgePaint;
    protected Paint mEdgeBlurPaint;
    protected long mAnimationDuration = 6300;
    protected long mPulsingDuration = 2100;
    protected long mFinalAnimDuration;

    public float mEdgeLightWidth = 20f;

    protected boolean mDrawBlur = false;
    protected int mBlurType = 0;
    protected int mBlurMode = 0;

    protected boolean rainbowMode = false;
    protected final List<Integer> rainbowColors = Arrays.asList(
            Color.RED,
            Color.parseColor("#FF7F00"),
            Color.YELLOW,
            Color.GREEN,
            Color.BLUE,
            Color.parseColor("#4B0082"),
            Color.parseColor("#8A2BE2")
    );

    private final Runnable hideRunnable = () -> mEdgeLightView.hide();

    protected final Animator.AnimatorListener mAnimationListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(@NonNull Animator animation) {
            try {
                mEdgeLightView.removeCallbacks(hideRunnable);
            } catch (Throwable ignored) {}
            mEdgeLightView.setVisibility(EdgeLightView.VISIBLE);
        }

        @Override
        public void onAnimationEnd(@NonNull Animator animation) {
            if (mEdgeLightView.isSettingsInterface()) return;
            stopAnimation();
            mEdgeLightView.postDelayed(hideRunnable, 500L);
        }

        @Override
        public void onAnimationCancel(@NonNull Animator animation) {
            mEdgeLightView.removeCallbacks(hideRunnable);
        }

        @Override
        public void onAnimationRepeat(@NonNull Animator animation) {}
    };

    public EdgeAnimator(EdgeLightView edgeLightView) {
        mEdgeLightView = edgeLightView;
        mEdgePaint = new Paint();
        mEdgePaint.setStyle(Paint.Style.STROKE);
        mEdgePaint.setStrokeWidth(mEdgeLightWidth);
        mEdgePaint.setAlpha(255);
        mEdgePaint.setAntiAlias(true);
        mEdgePaint.setStrokeCap(Paint.Cap.ROUND);
        mEdgePaint.setStrokeJoin(Paint.Join.ROUND);
        mEdgePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        mEdgePaint.setShader(getShader());
        mEdgePaint.setMaskFilter(null);
        mEdgeBlurPaint = new Paint(mEdgePaint);
    }

    public void setBlurOptions(boolean drawBlur, int blurType, int blurMode) {
        mDrawBlur = drawBlur;
        mBlurType = blurType;
        mBlurMode = blurMode;
        setBlurType(blurType);
    }

    public abstract void draw(Canvas canvas);

    public abstract void startAnimation();

    public void stopAnimation() {
        if (mAnimator != null) mAnimator.cancel();
        mAnimator = null;
        mAnimating = false;
        mEdgePaint.setAlpha(255);
        mEdgeBlurPaint.setAlpha(255);
        postInvalidate();
    }

    public abstract void onSizeChanged(int w, int h, int oldw, int oldh);

    public Shader getShader() {
        if (!rainbowMode) return null;
        return new LinearGradient(
                0f, 0f, (float) mEdgeLightView.getWidth(), (float) mEdgeLightView.getHeight(),
                rainbowColors.stream().mapToInt(Integer::intValue).toArray(),
                null,
                Shader.TileMode.CLAMP
        );
    }

    public void setScreenRadius(float radius) {
        if (mScreenRadius == radius) return;
        mScreenRadius = radius;
    }

    public void setDurations(int anim, long pulsing, long duration) {
        if (mFinalAnimDuration == duration) return;
        this.mAnimationDuration = anim;
        this.mPulsingDuration = pulsing;
        if (duration == 0) duration = mAnimationDuration;
        this.mFinalAnimDuration = duration;
        if (mAnimating) {
            stopAnimation();
            startAnimation();
        }
    }

    public void setPaintColor(int color, boolean isRainbow) {
        rainbowMode = isRainbow;
        mEdgePaint.setColor(color);
        mEdgeBlurPaint.setColor(color);
        refreshShader();
    }

    public void setBlurType(int type) {
        BlurMaskFilter.Blur blurMaskFilter = switch (type) {
            case 1 -> BlurMaskFilter.Blur.SOLID;
            case 2 -> BlurMaskFilter.Blur.INNER;
            case 3 -> BlurMaskFilter.Blur.OUTER;
            default -> BlurMaskFilter.Blur.NORMAL;
        };
        mEdgeBlurPaint.setMaskFilter(new BlurMaskFilter(50, blurMaskFilter));
    }

    private void refreshShader() {
        mEdgePaint.setShader(getShader());
        mEdgeBlurPaint.setShader(getShader());
    }

    public void setStrokeWidth(float width) {
        this.mEdgeLightWidth = width;
        mEdgePaint.setStrokeWidth(mEdgeLightWidth);
        mEdgeBlurPaint.setStrokeWidth(mEdgeLightWidth);
    }

    protected final void postInvalidate() {
        mEdgeLightView.postInvalidate();
    }

    protected final void log(String message) {
        // no-op nell'app (era un dump verboso lato SystemUI)
    }

    public boolean isAnimating() {
        return this.mAnimating;
    }
}
