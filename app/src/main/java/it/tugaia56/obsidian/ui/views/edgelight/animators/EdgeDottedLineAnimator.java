package it.tugaia56.obsidian.ui.views.edgelight.animators;

// Portato da Oxygen Customizer.

import android.graphics.DashPathEffect;

import it.tugaia56.obsidian.ui.views.edgelight.EdgeLightView;

public class EdgeDottedLineAnimator extends EdgeLineAnimator {

    public EdgeDottedLineAnimator(EdgeLightView edgeLightView) {
        super(edgeLightView);
        mEdgePaint.setPathEffect(new DashPathEffect(new float[]{40, 40}, 0));
        mEdgeBlurPaint.setPathEffect(new DashPathEffect(new float[]{40, 40}, 0));
    }
}
