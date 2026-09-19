package it.tugaia56.obsidian.ui.views.edgelight.animators;

// Portato da Oxygen Customizer.

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.animation.LinearInterpolator;

import it.tugaia56.obsidian.ui.views.edgelight.EdgeLightView;

public class EdgeSnakeAnimator extends EdgeLineAnimator {

    private final float snakeLengthFactor = 0.2f;
    private float snakeStart = 0;
    private float snakeEnd = 0;
    private final Path drawnPath = new Path();

    public EdgeSnakeAnimator(EdgeLightView edgeLightView) {
        super(edgeLightView);
    }

    @Override
    public void draw(Canvas canvas) {
        edgeLightPath.reset();
        drawnPath.reset();

        RectF rect = new RectF(
                mEdgePaint.getStrokeWidth(),
                mEdgePaint.getStrokeWidth(),
                mEdgeLightView.getWidth() - mEdgePaint.getStrokeWidth(),
                mEdgeLightView.getHeight() - mEdgePaint.getStrokeWidth()
        );

        edgeLightPath.addRoundRect(rect, mScreenRadius, mScreenRadius, Path.Direction.CW);

        if (pathMeasure == null) {
            pathMeasure = new PathMeasure(edgeLightPath, false);
        }

        pathMeasure.getSegment(snakeStart, snakeEnd, drawnPath, true);
        canvas.drawPath(drawnPath, mDrawBlur && mBlurMode == 1 ? mEdgeBlurPaint : mEdgePaint);
        if (mDrawBlur && mBlurMode == 0) canvas.drawPath(drawnPath, mEdgeBlurPaint);
    }

    @Override
    public void startAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }

        pathMeasure = new PathMeasure(edgeLightPath, false);
        float pathLength = pathMeasure.getLength();
        if (pathLength == 0) {
            onSizeChanged(mEdgeLightView.getWidth(), mEdgeLightView.getHeight(), 0, 0);
            pathMeasure = new PathMeasure(edgeLightPath, false);
            pathLength = pathMeasure.getLength();
            if (pathLength == 0) return;
        }
        float snakeLength = pathLength * snakeLengthFactor;

        mAnimator = ValueAnimator.ofFloat(0, pathLength + snakeLength);
        mAnimator.setDuration(mFinalAnimDuration);
        mAnimator.addListener(mAnimationListener);
        mAnimator.setInterpolator(new LinearInterpolator());
        final float pl = pathLength, sl = snakeLength;
        mAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            snakeStart = progress - sl;
            snakeEnd = progress;
            if (snakeStart < 0) snakeStart = 0;
            if (snakeEnd > pl) snakeEnd = pl;
            postInvalidate();
        });
        mAnimator.start();
        mAnimating = true;
    }
}
