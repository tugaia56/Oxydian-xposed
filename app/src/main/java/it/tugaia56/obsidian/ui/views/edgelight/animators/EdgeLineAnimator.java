package it.tugaia56.obsidian.ui.views.edgelight.animators;

// Portato da Oxygen Customizer.

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.animation.LinearInterpolator;

import it.tugaia56.obsidian.ui.views.edgelight.EdgeLightView;

public class EdgeLineAnimator extends EdgeAnimator {

    public float animatedPathLength;
    public Path edgeLightPath = new Path();
    public Path drawnPath = new Path();
    public PathMeasure pathMeasure;

    public EdgeLineAnimator(EdgeLightView edgeLightView) {
        super(edgeLightView);
    }

    @Override
    public void setStrokeWidth(float width) {
        super.setStrokeWidth(width);
        mEdgeBlurPaint.setStrokeWidth(width);
        initPath();
    }

    @Override
    public void setScreenRadius(float radius) {
        super.setScreenRadius(radius);
        initPath();
    }

    @Override
    public void draw(Canvas canvas) {
        edgeLightPath.reset();
        drawnPath.reset();

        RectF rect = new RectF(
                mEdgePaint.getStrokeWidth() / 2,
                mEdgePaint.getStrokeWidth() / 2,
                mEdgeLightView.getWidth() - mEdgePaint.getStrokeWidth() / 2,
                mEdgeLightView.getHeight() - mEdgePaint.getStrokeWidth() / 2
        );

        edgeLightPath.addRoundRect(rect, mScreenRadius, mScreenRadius, Path.Direction.CW);

        if (pathMeasure == null) {
            pathMeasure = new PathMeasure(edgeLightPath, false);
        }

        pathMeasure.getSegment(0, animatedPathLength, drawnPath, true);

        canvas.drawPath(drawnPath, mDrawBlur && mBlurMode == 1 ? mEdgeBlurPaint : mEdgePaint);
        if (mDrawBlur && mBlurMode == 0) canvas.drawPath(drawnPath, mEdgeBlurPaint);
    }

    @Override
    public void startAnimation() {
        if (mAnimating) return;

        if (pathMeasure == null) {
            initPath();
        }

        if (pathMeasure == null || pathMeasure.getLength() == 0) {
            return;
        }

        float pathLength = pathMeasure.getLength();

        mAnimator = ValueAnimator.ofFloat(0, pathLength);
        mAnimator.setDuration(mFinalAnimDuration);
        mAnimator.addListener(mAnimationListener);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            animatedPathLength = (float) animation.getAnimatedValue();
            postInvalidate();
        });
        mAnimator.start();
        mAnimating = true;
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        initPath();
    }

    private void initPath() {
        edgeLightPath.reset();
        RectF rect = new RectF(
                mEdgePaint.getStrokeWidth(),
                mEdgePaint.getStrokeWidth(),
                mEdgeLightView.getWidth() - mEdgePaint.getStrokeWidth(),
                mEdgeLightView.getHeight() - mEdgePaint.getStrokeWidth()
        );
        edgeLightPath.addRoundRect(rect, mScreenRadius, mScreenRadius, Path.Direction.CW);

        pathMeasure = new PathMeasure(edgeLightPath, false);
    }
}
