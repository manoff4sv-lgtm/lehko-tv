package ua.lehko.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A 16:9 tile: the app's TV banner when it has one, otherwise its icon with the name. Focus (or "activated" in the
 * all-apps grid) scales it up a little and draws a white ring 4dp outside the picture. The view keeps that room around
 * the picture ({@code R.dimen.ring_pad} on each side), so everything it draws stays inside its own bounds.
 */
final class Tile extends FrameLayout {
    private final ImageView banner;
    private final LinearLayout iconBox;
    private final ImageView icon;
    private final TextView label;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int pad;
    private final float radius;
    /** Which app (or action) the tile shows, so late picture loads don't land on a recycled tile. */
    Object key;

    /** {@code widthPx x heightPx} is the picture; the view is {@link #pad} larger on each side. */
    Tile(Context c, int widthPx, int heightPx) {
        super(c);
        pad = getResources().getDimensionPixelSize(R.dimen.ring_pad);
        setLayoutParams(new ViewGroup.MarginLayoutParams(widthPx + 2 * pad, heightPx + 2 * pad));
        setBackgroundResource(R.drawable.tile_bg);
        radius = getResources().getDimension(R.dimen.radius);
        // The shadow (and its glow when focused) follows the picture, not the ring's room.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(pad, pad, view.getWidth() - pad, view.getHeight() - pad, radius);
            }
        });
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        ring.setColor(getResources().getColor(R.color.stroke_focus, null));
        setWillNotDraw(false);

        banner = new ImageView(c);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        banner.setClipToOutline(true);
        LayoutParams inner = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        inner.setMargins(pad, pad, pad, pad);
        addView(banner, inner);

        iconBox = new LinearLayout(c);
        iconBox.setOrientation(LinearLayout.VERTICAL);
        iconBox.setGravity(Gravity.CENTER);
        int p = dp(8);
        iconBox.setPadding(p, p, p, p);
        icon = new ImageView(c);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = Math.min(dp(40), heightPx / 2);
        iconBox.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        label = new TextView(c);
        label.setTextColor(getResources().getColor(R.color.text_primary, null));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, heightPx < dp(80) ? 12 : 13);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        iconBox.addView(label, lp);
        addView(iconBox, new LayoutParams(inner));

        setFocusable(true);
        setClickable(true);
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                lift(hasFocus);
            }
        });
    }

    /** Shows a banner (fills the tile) or an icon with a label. */
    void show(Drawable picture, boolean isBanner, CharSequence text) {
        if (isBanner) {
            banner.setImageDrawable(picture);
            banner.setVisibility(VISIBLE);
            iconBox.setVisibility(GONE);
        } else {
            banner.setImageDrawable(null);
            banner.setVisibility(GONE);
            icon.setImageDrawable(picture);
            label.setText(text);
            iconBox.setVisibility(VISIBLE);
        }
        setContentDescription(text);
    }

    /** Label only, while the picture is loading. */
    void placeholder(CharSequence text) {
        show(null, false, text);
    }

    /** The colour of the focused tile's shadow: a soft glow in the background's tint (Android 9+). */
    void glow(int color) {
        if (Build.VERSION.SDK_INT >= 28) {
            setOutlineSpotShadowColor(color);
            setOutlineAmbientShadowColor(color);
        }
    }

    @Override public void setActivated(boolean activated) {
        boolean changed = activated != isActivated();
        super.setActivated(activated);
        if (changed) lift(activated);
    }

    @Override public void draw(Canvas canvas) {
        super.draw(canvas);
        if (isFocused() || isActivated()) {
            float half = ring.getStrokeWidth() / 2, r = radius + pad - half;
            canvas.drawRoundRect(half, half, getWidth() - half, getHeight() - half, r, r, ring);
        }
    }

    private void lift(boolean up) {
        animate().scaleX(up ? 1.06f : 1f).scaleY(up ? 1.06f : 1f).translationZ(up ? dp(8) : 0).setDuration(110).start();
    }

    /** No animation: for recycled grid tiles. */
    void settle(boolean up) {
        animate().cancel();
        setScaleX(up ? 1.06f : 1f);
        setScaleY(up ? 1.06f : 1f);
        setTranslationZ(up ? dp(8) : 0);
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
