package ua.lehko.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A round shortcut button: just an icon (and a small number for "HDMI 1", "HDMI 2"). Its name shows in the line under
 * the favorites while it has focus, and screen readers read it. Focused it turns solid white and grows a little.
 */
final class IconButton extends FrameLayout {
    /** What the button is (for keeping focus across rebuilds). */
    final String key;
    private final ImageView icon;
    private final TextView badge;

    IconButton(Context c, String key, Drawable picture, CharSequence name) {
        super(c);
        this.key = key;
        int size = getResources().getDimensionPixelSize(R.dimen.icon_button);
        setLayoutParams(new MarginLayoutParams(size, size));
        setBackgroundResource(R.drawable.icon_button_bg);
        ColorStateList fg = c.getColorStateList(R.color.icon_button_fg);
        icon = new ImageView(c);
        icon.setImageDrawable(picture);
        icon.setImageTintList(fg);
        icon.setDuplicateParentStateEnabled(true);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int iconSize = Math.round(24 * getResources().getDisplayMetrics().density);
        addView(icon, new LayoutParams(iconSize, iconSize, Gravity.CENTER));
        badge = new TextView(c);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(fg);
        badge.setDuplicateParentStateEnabled(true);
        badge.setIncludeFontPadding(false);
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        int pad = Math.round(7 * getResources().getDisplayMetrics().density);
        LayoutParams bp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM);
        bp.setMargins(0, 0, pad, pad - Math.round(getResources().getDisplayMetrics().density));
        addView(badge, bp);
        setName(name);
        setFocusable(true);
        setClickable(true);
        setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                animate().scaleX(hasFocus ? 1.1f : 1f).scaleY(hasFocus ? 1.1f : 1f)
                        .translationZ(hasFocus ? 4 * getResources().getDisplayMetrics().density : 0).setDuration(110).start();
            }
        });
    }

    /** The name (read aloud and shown under the favorites); a trailing number becomes the badge. */
    void setName(CharSequence name) {
        setContentDescription(name);
        String b = badgeOf(name == null ? "" : name.toString());
        badge.setText(b);
        badge.setVisibility(b.isEmpty() ? GONE : VISIBLE);
    }

    /** "HDMI 2" gives "2"; a name without a number at the end gives "". */
    static String badgeOf(String name) {
        int end = name.length(), i = end;
        while (i > 0 && Character.isDigit(name.charAt(i - 1))) i--;
        if (i == end || end - i > 2 || i == 0 || name.charAt(i - 1) != ' ') return "";
        return name.substring(i);
    }
}
