package ua.lehko.tv;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

/**
 * The home screen's rows. They run edge to edge, so what scrolls away just leaves the screen. The fading edge length
 * is kept only as the room left beside the focused item when scrolling; the fade itself (an offscreen layer that made
 * the tiles at the ends look half melted) is never drawn.
 */
public final class ScrollRow extends HorizontalScrollView {
    public ScrollRow(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    @Override protected float getLeftFadingEdgeStrength() {
        return 0;
    }

    @Override protected float getRightFadingEdgeStrength() {
        return 0;
    }
}
