package ua.lehko.tv;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
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
 * all-apps grid) scales it up a little with an outline; that is the only animation on the home screen.
 */
final class Tile extends FrameLayout {
    private final ImageView banner;
    private final LinearLayout iconBox;
    private final ImageView icon;
    private final TextView label;
    /** Which app (or action) the tile shows, so late picture loads don't land on a recycled tile. */
    Object key;

    Tile(Context c, int widthPx, int heightPx) {
        super(c);
        setLayoutParams(new ViewGroup.MarginLayoutParams(widthPx, heightPx));
        setBackgroundResource(R.drawable.tile_bg);
        setForeground(c.getDrawable(R.drawable.tile_frame));
        final float radius = getResources().getDimension(R.dimen.radius);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);

        banner = new ImageView(c);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(banner, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        iconBox = new LinearLayout(c);
        iconBox.setOrientation(LinearLayout.VERTICAL);
        iconBox.setGravity(Gravity.CENTER);
        int pad = dp(8);
        iconBox.setPadding(pad, pad, pad, pad);
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
        addView(iconBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

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

    /** A new label for an icon tile (the memory shortcut's free memory). */
    void label(CharSequence text) {
        label.setText(text);
        setContentDescription(text);
    }

    /** Label only, while the picture is loading. */
    void placeholder(CharSequence text) {
        show(null, false, text);
    }

    @Override public void setActivated(boolean activated) {
        boolean changed = activated != isActivated();
        super.setActivated(activated);
        if (changed) lift(activated);
    }

    private void lift(boolean up) {
        animate().scaleX(up ? 1.08f : 1f).scaleY(up ? 1.08f : 1f).translationZ(up ? dp(6) : 0).setDuration(110).start();
    }

    /** No animation: for recycled grid tiles. */
    void settle(boolean up) {
        animate().cancel();
        setScaleX(up ? 1.08f : 1f);
        setScaleY(up ? 1.08f : 1f);
        setTranslationZ(up ? dp(6) : 0);
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
