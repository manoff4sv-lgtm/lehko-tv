package ua.lehko.tv;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The home screen: the clock in a 24-hour ring of today's power schedule, short power, weather and rates lines (with
 * details on focus), favorite apps and a row of round shortcut buttons.
 */
public final class HomeActivity extends Activity {
    private static final long OUTAGE_MAX_AGE_MS = 10 * 60_000L;
    /** No successful refresh for this long (offline in an outage): the schedule may be out of date, the line says so. */
    private static final long OUTAGE_OLD_MS = 2 * 3600_000L;

    private LinearLayout favoritesRow, quickRow, weatherHours, info;
    private View details, tomorrowBox, header, quickScroll;
    private ScrollRow favoritesScroll;
    private ImageView weatherIcon;
    private TextView weatherTemp, weatherText, weatherSun, powerTitle, powerDetail, notice, focusName, ratesLine;
    /** The rates line's two versions: short, and full while the details are open. */
    private CharSequence ratesShort, ratesLong;
    /** The key of the favorite that had focus last, so Down from the details goes back to it (even after a rebuild). */
    private Object lastFavorite;
    /** A favorite's menu while it is open (Home closes it). */
    private AlertDialog menu;
    private DialView dial;
    /** The next hours under the current weather. */
    private static final int HOURS = 5;
    private final TextView[] hourTime = new TextView[HOURS], hourTemp = new TextView[HOURS];
    private final ImageView[] hourIcon = new ImageView[HOURS];
    /** When the first hour shown begins (then the strip moves on by one). */
    private long firstHourAt;
    private OutageBar tomorrowBar;
    private IconButton memoryButton;
    private boolean cleaning;

    private List<Apps.Entry> apps = new ArrayList<>();
    private List<Inputs.Input> inputs = new ArrayList<>();
    private boolean appsDirty = true, resumed, weatherLoading, outageLoading, appsLoaded, focusedOnce, rebuilding;
    /** The favorites list the row was last built from, so coming back home only rebuilds it when it changed. */
    private String builtFavorites;
    /** What the row shows (see favoritesKey), so a package event for another app doesn't rebuild it. */
    private String builtKey;
    /** A favorite's own package changed since the row was built. */
    private boolean favoriteChanged;
    /** Counts app list loads: two can overlap on the IO pool, and only the latest one counts. */
    private int appsLoads;
    /** Safe mode (see Watchdog): this activity shows only a plain message and does nothing else. */
    private boolean safe;
    private TextView safeText;
    /** After a failed download, wait a while before trying again (offline during an outage, for example). */
    private static final long RETRY_AFTER_FAILURE_MS = 5 * 60_000L;
    private long weatherFailedAt, outageFailedAt, ratesFailedAt;
    private boolean ratesLoading;
    private Outages.Schedule schedule;
    private int tileW, tileH, gap, buttonGap;
    private String focusPkgAfterRebuild;
    /** The last weather shown (for "is it night?"). */
    private Weather.Data weather;

    /** Full-screen background: the top-left corner takes a dark tint of the focused app, bottom-right stays dark. */
    private GradientDrawable background;
    private final ValueAnimator ambientFade = ValueAnimator.ofObject(new ArgbEvaluator(), 0, 0);
    private int ambientShown, ambientTarget, bgTop, bgBottom;

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusWatch = new ViewTreeObserver.OnGlobalFocusChangeListener() {
        @Override public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            // While the row is rebuilt, focus passes through other tiles for a moment: not worth a fade.
            if (newFocus != null && !rebuilding) focusChanged(newFocus);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            showOutage();
            maybeRefresh();
            long now = System.currentTimeMillis();
            updateMemoryTile();
            if (weather != null && firstHourAt != 0 && now >= firstHourAt) showWeather(weather);
            View f = getCurrentFocus();
            if (f != null) ambient(f); // sunset may have passed
            App.MAIN.postDelayed(this, 60_000 - now % 60_000 + 200);
        }
    };

    /** After a few minutes without a key press: the fireplace. */
    private final Runnable idle = new Runnable() {
        @Override public void run() {
            if (!resumed || isFinishing() || isDestroyed()) return;
            if (!hasWindowFocus()) {
                // A menu is open (its keys don't reach onUserInteraction): wait for another round.
                scheduleIdle();
                return;
            }
            Apps.start(HomeActivity.this, new Intent(HomeActivity.this, FireplaceActivity.class));
        }
    };

    private final Runnable hideNotice = new Runnable() {
        @Override public void run() {
            App.MAIN.removeCallbacks(this);
            notice.setVisibility(View.GONE);
            focusName.setVisibility(View.VISIBLE);
        }
    };
    /** Until when (uptime) a key press leaves the notice up: it has to be readable first. */
    private long noticeKeepUntil;

    /** Notices written while the home screen is up (the check after a boot, a group change) show at once. */
    private final SharedPreferences.OnSharedPreferenceChangeListener noticeWatch =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (!resumed || !Prefs.NOTICE.equals(key)) return;
            String n = Prefs.takeNotice(HomeActivity.this);
            if (n != null) showNotice(n);
        }
    };

    private final LauncherApps.Callback packages = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String pkg, UserHandle user) {
            Apps.forget(pkg);
            changed(pkg);
        }

        @Override public void onPackageAdded(String pkg, UserHandle user) {
            changed(pkg);
        }

        @Override public void onPackageChanged(String pkg, UserHandle user) {
            Apps.forget(pkg);
            changed(pkg);
        }

        @Override public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) {
            changed(pkgs);
        }

        @Override public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) {
            changed(pkgs);
        }

        private void changed(String... pkgs) {
            appsDirty = true;
            // A favorite's own change (an update may bring a new banner) rebuilds the row even if nothing else differs.
            List<String> favs = Prefs.favorites(HomeActivity.this);
            for (String p : pkgs) if (favs.contains(p)) favoriteChanged = true;
            // Updates and component changes come in bursts: one reload after them.
            if (resumed) {
                App.MAIN.removeCallbacks(reloadApps);
                App.MAIN.postDelayed(reloadApps, 1_000);
            }
        }
    };

    private final Runnable reloadApps = new Runnable() {
        @Override public void run() {
            if (resumed && appsDirty) loadApps();
        }
    };

    /** Inputs that show up late (after a boot the HDMI service may still be starting) or change: the buttons follow. */
    private final TvInputManager.TvInputCallback inputWatch = new TvInputManager.TvInputCallback() {
        @Override public void onInputAdded(String id) {
            changed();
        }

        @Override public void onInputRemoved(String id) {
            changed();
        }

        @Override public void onInputUpdated(String id) {
            changed();
        }

        @Override public void onTvInputInfoUpdated(TvInputInfo info) {
            changed();
        }

        private void changed() {
            // At boot they come one per port: one reload after the burst. Away from home, onResume reloads anyway.
            App.MAIN.removeCallbacks(reloadInputs);
            App.MAIN.postDelayed(reloadInputs, 1_000);
        }
    };

    private final Runnable reloadInputs = new Runnable() {
        @Override public void run() {
            if (resumed) loadInputs();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Watchdog.safeMode(this)) {
            safe = true;
            showSafeMode();
            return;
        }
        setContentView(R.layout.home);
        favoritesRow = findViewById(R.id.favorites);
        favoritesScroll = findViewById(R.id.favorites_scroll);
        quickRow = findViewById(R.id.quick);
        quickScroll = findViewById(R.id.quick_scroll);
        header = findViewById(R.id.header);
        weatherIcon = findViewById(R.id.weather_icon);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherText = findViewById(R.id.weather_text);
        weatherHours = findViewById(R.id.weather_hours);
        weatherSun = findViewById(R.id.weather_sun);
        buildHours();
        dial = findViewById(R.id.dial);
        info = findViewById(R.id.info);
        details = findViewById(R.id.details);
        powerTitle = findViewById(R.id.power_title);
        powerDetail = findViewById(R.id.power_detail);
        tomorrowBox = findViewById(R.id.tomorrow_box);
        tomorrowBar = findViewById(R.id.outage_tomorrow);
        tomorrowBar.showLabels(false);
        notice = findViewById(R.id.notice);
        focusName = findViewById(R.id.focus_name);
        ratesLine = findViewById(R.id.rates);
        // Calm by default; Up from the favorites opens the details.
        info.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                details.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
                if (ratesLong != null) ratesLine.setText(hasFocus ? ratesLong : ratesShort);
            }
        });

        bgTop = getColor(R.color.bg_top);
        bgBottom = getColor(R.color.bg_bottom);
        ambientShown = ambientTarget = bgTop;
        background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{bgTop, bgBottom});
        getWindow().setBackgroundDrawable(background);
        ambientFade.setDuration(450);
        ambientFade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                ambientShown = (Integer) a.getAnimatedValue();
                background.setColors(new int[]{ambientShown, bgBottom});
            }
        });
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(focusWatch);

        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMMM");
        TextClock date = findViewById(R.id.date);
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);

        tileW = getResources().getDimensionPixelSize(R.dimen.tile_w);
        tileH = getResources().getDimensionPixelSize(R.dimen.tile_h);
        // The tiles keep room for their focus ring on both sides, so the visible gap is that plus the margin.
        gap = getResources().getDimensionPixelSize(R.dimen.tile_gap) - 2 * getResources().getDimensionPixelSize(R.dimen.ring_pad);
        buttonGap = Math.round(14 * getResources().getDisplayMetrics().density);

        LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la != null) la.registerCallback(packages, App.MAIN);
        TvInputManager tim = tvInputs();
        if (tim != null) tim.registerCallback(inputWatch, App.MAIN);

        buildQuickRow();
        showWeather(Weather.cached(this));
        showRates();
        schedule = Outages.cached(this);
        showOutage();
    }

    @Override protected void onDestroy() {
        App.MAIN.removeCallbacks(safeWatch);
        if (!safe) {
            LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (la != null) la.unregisterCallback(packages);
            TvInputManager tim = tvInputs();
            if (tim != null) tim.unregisterCallback(inputWatch);
            App.MAIN.removeCallbacks(reloadInputs);
            App.MAIN.removeCallbacks(reloadApps);
            App.MAIN.removeCallbacks(hideNotice);
            App.MAIN.removeCallbacks(ticker);
            App.MAIN.removeCallbacks(idle);
            if (menu != null) menu.dismiss();
            ambientFade.cancel();
            getWindow().getDecorView().getViewTreeObserver().removeOnGlobalFocusChangeListener(focusWatch);
        }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (safe) return;
        resumed = true;
        if (appsDirty) loadApps();
        // Favorites may have changed in All apps. Otherwise the tiles stay as they are: no reloading, no flicker.
        else if (appsLoaded && !Prefs.of(this).getString(Prefs.FAVORITES, "").equals(builtFavorites)) buildFavorites();
        loadInputs();
        showWeather(Weather.cached(this));
        showRates();
        schedule = Outages.cached(this);
        showOutage();
        maybeRefresh();
        App.MAIN.removeCallbacks(ticker);
        App.MAIN.postDelayed(ticker, 60_000 - System.currentTimeMillis() % 60_000 + 200);
        String n = Prefs.takeNotice(this);
        if (n != null) showNotice(n);
        // Held weakly by the preferences: a field.
        Prefs.of(this).registerOnSharedPreferenceChangeListener(noticeWatch);
        updateMemoryTile();
        Memory.maybeAutoClean(this);
        scheduleIdle();
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        if (safe) return;
        scheduleIdle();
        // A key press after the notice has been read puts it away (it also goes by itself).
        if (notice.getVisibility() == View.VISIBLE && SystemClock.uptimeMillis() >= noticeKeepUntil) hideNotice.run();
    }

    /** Back from a menu (whose keys don't reach onUserInteraction): the idle time starts again. */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !safe) scheduleIdle();
    }

    private void scheduleIdle() {
        App.MAIN.removeCallbacks(idle);
        int minutes = FireScene.idleMinutes(this);
        if (resumed && minutes > 0) App.MAIN.postDelayed(idle, minutes * 60_000L);
    }

    /** Results of background checks: up to 20 seconds, a key press puts it away after 4. */
    private void showNotice(String text) {
        showNotice(text, 20_000, 4_000);
    }

    /** Shows {@code text} for {@code ms}; a key press puts it away once it has been up for {@code readMs}. */
    private void showNotice(String text, long ms, long readMs) {
        notice.setText(text);
        notice.setVisibility(View.VISIBLE);
        // The notice takes the free room above the favorites, so nothing moves. When that is too small (a long notice)
        // it borrows the name line's room as well, so the buttons stay inside the screen's safe area.
        focusName.setVisibility(noticeFits() ? View.VISIBLE : View.GONE);
        noticeKeepUntil = SystemClock.uptimeMillis() + readMs;
        App.MAIN.removeCallbacks(hideNotice);
        App.MAIN.postDelayed(hideNotice, ms);
    }

    /** Whether the notice fits between the header and the favorites with the name line still in its place. */
    private boolean noticeFits() {
        View root = (View) notice.getParent();
        int w = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        if (w <= 0) return false; // not laid out yet: take the name line's room to be safe
        int any = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        notice.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), any);
        focusName.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), any);
        int room = quickScroll.getTop() - header.getBottom() - favoritesScroll.getHeight() - focusName.getMeasuredHeight();
        return notice.getMeasuredHeight() + ((ViewGroup.MarginLayoutParams) notice.getLayoutParams()).bottomMargin <= room;
    }

    @Override protected void onPause() {
        if (safe) {
            super.onPause();
            return;
        }
        resumed = false;
        Prefs.of(this).unregisterOnSharedPreferenceChangeListener(noticeWatch);
        App.MAIN.removeCallbacks(reloadApps); // appsDirty stays set: onResume reloads
        App.MAIN.removeCallbacks(ticker);
        App.MAIN.removeCallbacks(idle);
        super.onPause();
    }

    /** Home pressed while already home: a clean home screen, at the first favorite. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Started as the TV's home from now on (onBackPressed tells the home from the preview by this).
        if (intent.hasCategory(Intent.CATEGORY_HOME)) setIntent(intent);
        if (safe) return;
        if (menu != null) menu.dismiss();
        toFirstFavorite();
    }

    @Override public void onBackPressed() {
        // Back closes the details, like Down.
        if (!safe && info.hasFocus() && backToFavorite()) return;
        // "Try the Lehko home screen" from Lehko settings (not the TV's home): Back goes back there.
        if (!getIntent().hasCategory(Intent.CATEGORY_HOME)) {
            finish();
            return;
        }
        // The home screen has nowhere to go back to.
        if (!safe) toFirstFavorite();
    }

    private void toFirstFavorite() {
        favoritesScroll.smoothScrollTo(0, 0);
        if (favoritesRow.getChildCount() > 0) favoritesRow.getChildAt(0).requestFocus();
    }

    /** Focus back to the favorite the details were opened from, or the first one if it is gone. */
    private boolean backToFavorite() {
        View back = favoritesRow.getChildCount() > 0 ? favoritesRow.getChildAt(0) : null;
        for (int i = 0; i < favoritesRow.getChildCount(); i++) {
            if (((Tile) favoritesRow.getChildAt(i)).key.equals(lastFavorite)) back = favoritesRow.getChildAt(i);
        }
        return back != null && back.requestFocus();
    }

    // MARK: - Apps

    private void loadApps() {
        appsDirty = false;
        final int load = ++appsLoads;
        final Context app = getApplicationContext();
        App.IO.execute(new Runnable() {
            @Override public void run() {
                final List<Apps.Entry> all = Apps.load(app);
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || load != appsLoads) return;
                        apps = all;
                        appsLoaded = true;
                        // Most package events are about other apps: the row stays as it is (no rebuild, no flicker).
                        if (favoriteChanged || !favoritesKey(Apps.favorites(HomeActivity.this, all)).equals(builtKey)) {
                            buildFavorites();
                        }
                    }
                });
            }
        });
    }

    /** What the favorites row shows of the apps: which activity each tile opens, and its name. */
    private static String favoritesKey(List<Apps.Entry> favs) {
        StringBuilder b = new StringBuilder();
        for (Apps.Entry a : favs) {
            b.append(a.component.flattenToShortString()).append(' ').append(a.leanback).append(' ').append(a.label).append('\n');
        }
        return b.toString();
    }

    private void buildFavorites() {
        rebuilding = true;
        try {
            fillFavorites();
        } finally {
            rebuilding = false;
        }
        View f = getCurrentFocus();
        if (f != null) focusChanged(f);
    }

    private void fillFavorites() {
        View focused = getCurrentFocus();
        boolean hadFocus = focused != null && focused.getParent() == favoritesRow;
        Object focusedKey = hadFocus ? ((Tile) focused).key : null;
        // If the focused app is gone (removed from the favorites), focus stays at its place in the row.
        int focusedIndex = hadFocus ? favoritesRow.indexOfChild(focused) : -1;
        if (focusPkgAfterRebuild != null) {
            focusedKey = focusPkgAfterRebuild;
            hadFocus = true;
            focusPkgAfterRebuild = null;
        }
        favoritesRow.removeAllViews();
        List<Apps.Entry> favs = Apps.favorites(this, apps);
        builtFavorites = Prefs.of(this).getString(Prefs.FAVORITES, "");
        builtKey = favoritesKey(favs);
        favoriteChanged = false;
        Tile toFocus = null;
        for (final Apps.Entry a : favs) {
            Tile t = appTile(a, tileW, tileH);
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    favoriteMenu(a);
                    return true;
                }
            });
            addWithGap(favoritesRow, t);
            if (a.pkg.equals(focusedKey)) toFocus = t;
        }
        // Always a way to add more.
        Tile add = new Tile(this, tileW, tileH);
        add.key = "add";
        add.show(getDrawable(R.drawable.ic_apps), false, getString(favs.isEmpty() ? R.string.add_apps : R.string.all_apps));
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openAllApps();
            }
        });
        addWithGap(favoritesRow, add);
        // Back from All apps through this tile: focus stays on it, with a newly added app right beside it.
        if (add.key.equals(focusedKey)) toFocus = add;
        if (toFocus != null) {
            toFocus.requestFocus();
        } else if (focusedIndex >= 0) {
            favoritesRow.getChildAt(Math.min(focusedIndex, favoritesRow.getChildCount() - 1)).requestFocus();
        } else if (getCurrentFocus() == null || hadFocus || !focusedOnce) {
            // On a cold start the buttons exist first; the first favorite is where people expect to be.
            favoritesRow.getChildAt(0).requestFocus();
        }
        focusedOnce = true;
        // Only now: otherwise the details panel (first in the layout) would take the window's first focus.
        info.setFocusable(true);
    }

    private Tile appTile(final Apps.Entry a, int w, int h) {
        final Tile t = new Tile(this, w, h);
        t.key = a.pkg;
        Drawable d = Apps.cached(a);
        if (d != null) {
            t.show(d, Apps.cachedIsBanner(a), a.label);
        } else {
            t.placeholder(a.label);
            final Context app = getApplicationContext();
            App.IO.execute(new Runnable() {
                @Override public void run() {
                    final boolean[] banner = new boolean[1];
                    final Drawable pic = Apps.picture(app, a, banner, w, h);
                    Apps.remember(a, pic, banner[0]);
                    App.MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (!a.pkg.equals(t.key)) return;
                            t.show(pic, banner[0], a.label);
                            if (t.isFocused()) focusChanged(t);
                        }
                    });
                }
            });
        }
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Apps.launch(HomeActivity.this, a);
            }
        });
        return t;
    }

    // MARK: - Focus: the background tint

    private void focusChanged(View focused) {
        ambient(focused);
        if (focused.getParent() == favoritesRow) lastFavorite = ((Tile) focused).key;
        boolean named = focused.getParent() == favoritesRow || focused.getParent() == quickRow;
        CharSequence name = named ? focused.getContentDescription() : null;
        showFocusName(name == null ? "" : name);
    }

    private static final android.view.animation.Interpolator ENTER = new android.view.animation.DecelerateInterpolator();

    /** The name under the favorites row; a new name fades in quickly. */
    private void showFocusName(CharSequence name) {
        if (TextUtils.equals(name, focusName.getText())) return;
        focusName.animate().cancel();
        focusName.setText(name);
        if (name.length() == 0) return;
        focusName.setAlpha(0f);
        focusName.animate().alpha(1f).setDuration(160).setInterpolator(ENTER).start();
    }

    /** The package of a favorite's tile, or null for anything else (shortcuts, "All apps"). */
    private String favoritePkg(View v) {
        if (!(v instanceof Tile) || v.getParent() != favoritesRow) return null;
        Object key = ((Tile) v).key;
        return key instanceof String && !"add".equals(key) ? (String) key : null;
    }

    /** Tints the background after the focused favorite's picture; anything else gets the plain background. */
    private void ambient(View focused) {
        String pkg = favoritePkg(focused);
        // The tile's own picture: Android may have emptied the picture cache while an app was in front.
        Integer mean = pkg == null ? null : Ambient.mean(pkg, ((Tile) focused).picture());
        fadeTo(mean == null ? bgTop : Ambient.tone(mean, night()));
        if (pkg != null) ((Tile) focused).glow(mean == null ? 0xFF000000 : Ambient.glow(mean));
    }

    private void fadeTo(int color) {
        if (color == ambientTarget) return;
        ambientTarget = color;
        ambientFade.cancel();
        ambientFade.setObjectValues(ambientShown, color);
        ambientFade.start();
    }

    private boolean night() {
        return Weather.night(weather, System.currentTimeMillis());
    }

    private void addWithGap(LinearLayout row, View v) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(lp.width, lp.height);
        p.setMarginEnd(row == quickRow ? buttonGap : gap);
        row.addView(v, p);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        View current = getCurrentFocus();
        if (current == info) {
            // Down from the details: back to the favorite we came from, not the one nearest the panel's middle.
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && backToFavorite()) return true;
            // Nothing is beside the panel: Left and Right stay put instead of jumping to some tile below.
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        }
        // Right at the end of a row stays there (the wide details panel above would otherwise catch it).
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && current != null && (current.getParent() == quickRow
                || current.getParent() == favoritesRow)) {
            ViewGroup row = (ViewGroup) current.getParent();
            if (row.indexOfChild(current) == row.getChildCount() - 1) return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile && f.getParent() == favoritesRow && ((Tile) f).key instanceof String) {
                Apps.Entry a = Apps.byPackage(apps).get((String) ((Tile) f).key);
                if (a != null) {
                    favoriteMenu(a);
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void favoriteMenu(final Apps.Entry a) {
        if (isFinishing() || isDestroyed()) return;
        final List<String> favs = Prefs.favorites(this);
        final int index = favs.indexOf(a.pkg);
        final List<Integer> actions = new ArrayList<>();
        actions.add(R.string.open);
        if (index > 0) actions.add(R.string.move_left);
        if (index >= 0 && index < favs.size() - 1) actions.add(R.string.move_right);
        actions.add(R.string.remove_favorite);
        actions.add(R.string.app_info);
        actions.add(R.string.uninstall);
        CharSequence[] labels = new CharSequence[actions.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(actions.get(i));
        menu = new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        int action = actions.get(which);
                        if (action == R.string.open) {
                            Apps.launch(HomeActivity.this, a);
                        } else if (action == R.string.move_left || action == R.string.move_right) {
                            Apps.moveFavorite(HomeActivity.this, a.pkg, action == R.string.move_left ? -1 : 1);
                            focusPkgAfterRebuild = a.pkg;
                            buildFavorites();
                        } else if (action == R.string.remove_favorite) {
                            Apps.removeFavorite(HomeActivity.this, a.pkg);
                            buildFavorites();
                        } else if (action == R.string.app_info) {
                            Apps.openInfo(HomeActivity.this, a.pkg);
                        } else if (action == R.string.uninstall) {
                            Apps.uninstall(HomeActivity.this, a.pkg);
                        }
                    }
                })
                .show();
    }

    private void openAllApps() {
        Apps.start(this, new Intent(this, AllAppsActivity.class));
    }

    // MARK: - Shortcuts row: inputs, all apps, TV settings, Lehko

    private void loadInputs() {
        final Context app = getApplicationContext();
        App.IO.execute(new Runnable() {
            @Override public void run() {
                final List<Inputs.Input> list = Inputs.load(app);
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || sameInputs(list)) return;
                        inputs = list;
                        buildQuickRow();
                    }
                });
            }
        });
    }

    /** The application's manager, so a recreated screen doesn't add another one to the system. Null without TV inputs. */
    private TvInputManager tvInputs() {
        return (TvInputManager) getApplicationContext().getSystemService(Context.TV_INPUT_SERVICE);
    }

    private boolean sameInputs(List<Inputs.Input> list) {
        if (list.size() != inputs.size()) return false;
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).id.equals(inputs.get(i).id) || !list.get(i).label.equals(inputs.get(i).label)) return false;
        }
        return true;
    }

    private void buildQuickRow() {
        View focused = getCurrentFocus();
        String focusedKey = focused instanceof IconButton && focused.getParent() == quickRow ? ((IconButton) focused).key : null;
        quickRow.removeAllViews();
        for (final Inputs.Input in : inputs) {
            IconButton t = shortcut("input:" + in.id, in.tuner ? R.drawable.ic_live_tv : R.drawable.ic_input, in.label);
            t.setBadge(in.badge);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Inputs.open(HomeActivity.this, in);
                }
            });
        }
        shortcut("apps", R.drawable.ic_apps, getString(R.string.all_apps)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                openAllApps();
            }
        });
        shortcut("tv_settings", R.drawable.ic_tv_settings, getString(R.string.tv_settings)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Apps.start(HomeActivity.this, new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        memoryButton = shortcut("memory", R.drawable.ic_memory, getString(R.string.memory));
        memoryButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                cleanMemory();
            }
        });
        updateMemoryTile();
        IconButton lehko = new IconButton(this, "lehko", getDrawable(R.drawable.ic_lehko_button), getString(R.string.settings_title), false);
        addWithGap(quickRow, lehko);
        lehko.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Apps.start(HomeActivity.this, new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        if (focusedKey != null) {
            for (int i = 0; i < quickRow.getChildCount(); i++) {
                if (focusedKey.equals(((IconButton) quickRow.getChildAt(i)).key)) quickRow.getChildAt(i).requestFocus();
            }
        }
    }

    private IconButton shortcut(String key, int icon, CharSequence name) {
        IconButton b = new IconButton(this, key, getDrawable(icon), name);
        addWithGap(quickRow, b);
        return b;
    }

    // MARK: - Memory

    private String memoryLabel() {
        return getString(R.string.memory_free_short, Memory.freeMb(this));
    }

    /** "Memory · 412 MB free": the button's name, shown under the favorites while it has focus. */
    private void updateMemoryTile() {
        if (memoryButton == null) return;
        memoryButton.setName(getString(R.string.memory_button, memoryLabel()));
        // Only the number changed: the new text without the fade, which is for a focus change.
        if (memoryButton.isFocused()) focusName.setText(memoryButton.getContentDescription());
    }

    /** The memory tile: closes background apps (asking for debugging if needed) and says how much that freed. */
    private void cleanMemory() {
        if (cleaning) return;
        cleaning = true;
        showNotice(getString(R.string.memory_cleaning), 90_000, 90_000);
        final Context app = getApplicationContext();
        final HomeMode.Progress progress = new HomeMode.Progress() {
            @Override public void step(String text) {}

            @Override public void approval() {
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (!isDestroyed()) showNotice(getString(R.string.adb_waiting), 90_000, 90_000);
                    }
                });
            }
        };
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                String text;
                try {
                    text = Memory.clean(app, progress);
                } catch (RuntimeException e) {
                    // Never let the button take the home screen down.
                    text = app.getString(R.string.memory_nothing);
                }
                final String result = text;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        cleaning = false;
                        if (isDestroyed()) return;
                        showNotice(result, 6_000, 1_500);
                        updateMemoryTile();
                    }
                });
            }
        });
    }

    // MARK: - Weather

    /** Downloads what is stale, but not again within a few minutes of a failure. */
    private void maybeRefresh() {
        // No network yet (just booted or woken, the router still starting): a try would fail at once and hold the
        // next one off for minutes. The minute ticker tries again.
        if (!online()) return;
        long now = System.currentTimeMillis();
        if (Weather.stale(this) && now - weatherFailedAt > RETRY_AFTER_FAILURE_MS) refreshWeather();
        if (Rates.due(this) && now - ratesFailedAt > RETRY_AFTER_FAILURE_MS) refreshRates();
        if (Outages.enabled(this) && now - Outages.fetchedAt(this) > OUTAGE_MAX_AGE_MS
                && now - outageFailedAt > RETRY_AFTER_FAILURE_MS) {
            refreshOutage();
        }
    }

    private boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm == null || cm.getActiveNetwork() != null;
    }

    /** A past moment for "no update since": the time if it was today, else the date too. In the TV's 12/24-hour style. */
    private String since(long t) {
        if (DateUtils.isToday(t)) return DateFormat.getTimeFormat(this).format(t);
        return DateUtils.formatDateTime(this, t,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
    }

    /** For a hint that follows other text after a comma or a dot. */
    private static String lowerFirst(String s) {
        return s.substring(0, 1).toLowerCase(Locale.getDefault()) + s.substring(1);
    }

    private void refreshWeather() {
        if (weatherLoading || Weather.place(this) == null) return;
        weatherLoading = true;
        final Context app = getApplicationContext();
        App.NET.execute(new Runnable() {
            @Override public void run() {
                Weather.Data d = null;
                try {
                    d = Weather.refresh(app);
                } catch (Exception ignored) {
                    // Offline (e.g. during an outage): keep showing the last forecast.
                }
                final Weather.Data result = d;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        weatherLoading = false;
                        weatherFailedAt = result == null ? System.currentTimeMillis() : 0;
                        if (!isDestroyed()) showWeather(result != null ? result : Weather.cached(HomeActivity.this));
                    }
                });
            }
        });
    }

    private void showWeather(Weather.Data d) {
        weather = d;
        firstHourAt = 0;
        if (Weather.place(this) == null || d == null) {
            weatherIcon.setImageDrawable(null);
            weatherTemp.setText("");
            if (Weather.place(this) == null) weatherText.setText(R.string.weather_pick_city);
            // Nothing cached yet: "loading" only while a download can still come.
            else if (!weatherLoading && (weatherFailedAt != 0 || !online())) weatherText.setText(R.string.weather_offline);
            else weatherText.setText(R.string.weather_loading);
            weatherHours.setVisibility(View.GONE);
            weatherSun.setVisibility(View.GONE);
            return;
        }
        weatherIcon.setImageResource(Weather.icon(d.code, d.day));
        weatherTemp.setText(Weather.degrees(d.temp));
        // "Cloudy, rain from 21:00": the hint (or an old forecast's warning) in amber after the sky.
        String hint = null;
        long age = System.currentTimeMillis() - Weather.cacheTime(this);
        if (age > 3 * 3600_000L) {
            hint = getString(R.string.weather_old, since(Weather.cacheTime(this)));
        } else if (d.precipAt != null) {
            hint = getString(d.precipSnow ? R.string.weather_snow_at : R.string.weather_rain_at, d.precipAt);
        }
        String sky = getString(Weather.text(d.code));
        if (hint == null) {
            weatherText.setText(sky);
        } else {
            String h = lowerFirst(hint);
            SpannableString t = new SpannableString(sky + ", " + h);
            t.setSpan(new ForegroundColorSpan(getColor(R.color.accent)), sky.length() + 2, t.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            weatherText.setText(t);
        }

        List<Weather.Hour> next = Weather.next(d, System.currentTimeMillis(), HOURS);
        // In the TV's own 12/24-hour style and time zone, like the clock.
        java.text.DateFormat time = DateFormat.getTimeFormat(this);
        for (int i = 0; i < HOURS; i++) {
            View cell = (View) hourTime[i].getParent();
            if (i >= next.size()) {
                cell.setVisibility(View.GONE);
                continue;
            }
            Weather.Hour h = next.get(i);
            hourTime[i].setText(time.format(new java.util.Date(h.at)));
            hourIcon[i].setImageResource(Weather.icon(h.code, h.day));
            hourTemp[i].setText(Weather.degrees(h.temp));
            cell.setVisibility(View.VISIBLE);
        }
        if (!next.isEmpty()) firstHourAt = next.get(0).at;
        weatherHours.setVisibility(next.isEmpty() ? View.GONE : View.VISIBLE);
        // Details: "Kyiv · ↑+15° ↓+8° · Sunrise 07:23 · sunset 17:41".
        StringBuilder sun = new StringBuilder();
        String city = Weather.cityName(this);
        if (!city.isEmpty()) sun.append(city).append(" · ");
        sun.append('↑').append(Weather.degrees(d.max)).append(" ↓").append(Weather.degrees(d.min));
        if (d.sunrise != null && d.sunset != null) {
            sun.append(" · ").append(getString(R.string.weather_sun, time.format(new java.util.Date(d.sunriseAt)),
                    time.format(new java.util.Date(d.sunsetAt))));
        }
        weatherSun.setText(sun);
        weatherSun.setVisibility(View.VISIBLE);
    }

    /** Five small columns under the current weather: hour, icon, temperature. Made once, filled by showWeather. */
    private void buildHours() {
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < HOURS; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setMinimumWidth(Math.round(44 * dp));
            hourTime[i] = smallText(11, R.color.text_dim);
            cell.addView(hourTime[i]);
            hourIcon[i] = new ImageView(this);
            hourIcon[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Math.round(20 * dp), Math.round(20 * dp));
            ip.topMargin = ip.bottomMargin = Math.round(dp);
            cell.addView(hourIcon[i], ip);
            hourTemp[i] = smallText(13, R.color.text_primary);
            cell.addView(hourTemp[i]);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) cp.setMarginStart(Math.round(2 * dp));
            weatherHours.addView(cell, cp);
        }
    }

    private TextView smallText(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(color));
        t.setIncludeFontPadding(false);
        t.setMaxLines(1);
        return t;
    }

    // MARK: - Rates

    private void refreshRates() {
        if (ratesLoading) return;
        ratesLoading = true;
        final Context app = getApplicationContext();
        App.NET.execute(new Runnable() {
            @Override public void run() {
                boolean ok = true;
                try {
                    Rates.refresh(app);
                } catch (Exception e) {
                    ok = false; // offline or refused: the cached line stays
                }
                final boolean done = ok;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        ratesLoading = false;
                        if (!done) ratesFailedAt = System.currentTimeMillis();
                        if (!isDestroyed()) showRates();
                    }
                });
            }
        });
    }

    /** A short line, and the full one while the details are open. Hidden without data. */
    private void showRates() {
        String[] lines = Rates.lines(this);
        ratesLine.setVisibility(lines == null ? View.GONE : View.VISIBLE);
        if (lines == null) {
            ratesShort = ratesLong = null;
            return;
        }
        ratesShort = lines[0];
        ratesLong = lines[1];
        ratesLine.setText(info.hasFocus() ? ratesLong : ratesShort);
    }

    // MARK: - Outages

    private void refreshOutage() {
        if (outageLoading) return;
        outageLoading = true;
        final Context app = getApplicationContext();
        App.NET.execute(new Runnable() {
            @Override public void run() {
                Outages.Schedule s = null;
                try {
                    s = Outages.refresh(app);
                } catch (Exception ignored) {
                    // Keep the cached schedule; it is still right for today.
                }
                final Outages.Schedule result = s;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        outageLoading = false;
                        if (result == null) outageFailedAt = System.currentTimeMillis();
                        if (isDestroyed()) return;
                        if (result != null) schedule = result;
                        showOutage();
                    }
                });
            }
        });
    }

    /** The ring, the two power lines and tomorrow's bar (details). Without outages: a plain 24-hour ring. */
    private void showOutage() {
        if (!Outages.enabled(this)) {
            Calendar now = Calendar.getInstance();
            dial.set(null, now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE));
            powerTitle.setVisibility(View.GONE);
            powerDetail.setVisibility(View.GONE);
            tomorrowBox.setVisibility(View.GONE);
            return;
        }
        long now = System.currentTimeMillis();
        long fetched = Outages.fetchedAt(this);
        boolean old = schedule != null && fetched > 0 && now - fetched > OUTAGE_OLD_MS;
        Outages.Status st = Outages.status(schedule, now);
        // An old schedule says since when in place of the group (the line has room for one of them).
        String[] lines = OutageText.lines(this, schedule, st, old ? null : Outages.chosenGroup(this));
        // "Power is on  until 22:00": the state large, the time smaller and lighter. No power is ember, maybe amber.
        SpannableString title = new SpannableString(lines[1] == null ? lines[0] : lines[0] + "  " + lines[1]);
        int color = getColor(st.state == Outages.OFF ? R.color.ember : st.state == Outages.MAYBE ? R.color.power_maybe
                : R.color.text_primary);
        title.setSpan(new ForegroundColorSpan(color), 0, lines[0].length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (lines[1] != null) {
            title.setSpan(new ForegroundColorSpan(getColor(R.color.text_secondary)), lines[0].length(), title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setSpan(new android.text.style.RelativeSizeSpan(0.66f), lines[0].length(), title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        powerTitle.setText(title);
        powerTitle.setVisibility(View.VISIBLE);
        CharSequence detail = lines[2];
        if (old) {
            // "Outage in 2 h · no update since 09:00", the warning in amber like the weather's.
            String h = getString(R.string.weather_old, since(fetched));
            if (lines[2] != null) h = lowerFirst(h);
            SpannableString t = new SpannableString(lines[2] == null ? h : lines[2] + " · " + h);
            t.setSpan(new ForegroundColorSpan(getColor(R.color.accent)), t.length() - h.length(), t.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            detail = t;
        }
        powerDetail.setText(detail);
        powerDetail.setVisibility(detail == null ? View.GONE : View.VISIBLE);

        Calendar k = Calendar.getInstance(Outages.KYIV);
        int kyivMinute = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        Outages.Day today = schedule == null ? null : schedule.day(Outages.today());
        Outages.Day tomorrow = schedule == null ? null : schedule.day(Outages.tomorrow());
        dial.set(today == null ? null : today.slots, kyivMinute);
        // Screen readers get today's ring in words with the second line.
        if (today != null && detail != null) powerDetail.setContentDescription(detail + ". " + OutageText.describe(this, today.slots));
        else powerDetail.setContentDescription(null);
        tomorrowBox.setVisibility(tomorrow != null ? View.VISIBLE : View.GONE);
        if (tomorrow != null) {
            tomorrowBar.set(tomorrow.slots, -1);
            tomorrowBar.setContentDescription(OutageText.describe(this, tomorrow.slots));
        }
    }

    // MARK: - Safe mode

    /** A plain screen while the original home screen is being brought back after repeated crashes. */
    private void showSafeMode() {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        int pad = Math.round(48 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        safeText = new TextView(this);
        safeText.setTextSize(18);
        safeText.setTextColor(0xFFF2F4F7);
        safeText.setGravity(android.view.Gravity.CENTER);
        safeText.setText(R.string.safe_mode_text);
        box.addView(safeText);
        android.widget.Button retry = new android.widget.Button(this);
        retry.setText(R.string.safe_mode_retry);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                safeText.setText(R.string.safe_mode_text);
                Watchdog.revertInBackground(getApplicationContext());
            }
        });
        android.widget.Button settings = new android.widget.Button(this);
        settings.setText(R.string.tv_settings);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Apps.start(HomeActivity.this, new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        // When the switch back fails, the text sends people to Lehko settings: its "Home screen" row turns Lehko off
        // and says what went wrong. Nothing else leads there from here (the TV's own home is off or not home).
        android.widget.Button lehko = new android.widget.Button(this);
        lehko.setText(R.string.settings_title);
        lehko.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Apps.start(HomeActivity.this, new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        box.addView(retry, lp);
        box.addView(settings, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(lehko, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        box.setBackgroundColor(0xFF0B0D11);
        setContentView(box);
        retry.requestFocus();
        Watchdog.revertInBackground(getApplicationContext());
        App.MAIN.postDelayed(safeWatch, 3_000);
    }

    /** Follows the switch back: gone when it worked, a hint when it didn't. */
    private final Runnable safeWatch = new Runnable() {
        @Override public void run() {
            if (isDestroyed()) return;
            if (!Watchdog.safeMode(HomeActivity.this)) {
                finish();
                return;
            }
            if (!Watchdog.isReverting()) safeText.setText(R.string.reverted_crash_failed);
            App.MAIN.postDelayed(this, 3_000);
        }
    };
}
