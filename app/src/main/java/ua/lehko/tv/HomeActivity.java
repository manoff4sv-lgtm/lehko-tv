package ua.lehko.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The home screen: clock, weather, today's outage schedule, favorite apps and a row of shortcuts. */
public final class HomeActivity extends Activity {
    private static final long OUTAGE_MAX_AGE_MS = 10 * 60_000L;

    private LinearLayout favoritesRow, quickRow, outageCard, outageDays, tomorrowBox;
    private HorizontalScrollView favoritesScroll;
    private ImageView weatherIcon;
    private TextView weatherTemp, weatherText, weatherExtra, outageStatus, outageGroup, notice;
    private View outageDot;
    private OutageBar todayBar, tomorrowBar;

    private List<Apps.Entry> apps = new ArrayList<>();
    private List<Inputs.Input> inputs = new ArrayList<>();
    private boolean appsDirty = true, resumed, weatherLoading, outageLoading;
    private Outages.Schedule schedule;
    private int tileW, tileH, smallW, smallH, gap;
    private String focusPkgAfterRebuild;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            showOutage();
            if (Weather.stale(HomeActivity.this)) refreshWeather();
            if (Outages.enabled(HomeActivity.this)
                    && System.currentTimeMillis() - Outages.fetchedAt(HomeActivity.this) > OUTAGE_MAX_AGE_MS) {
                refreshOutage();
            }
            long now = System.currentTimeMillis();
            App.MAIN.postDelayed(this, 60_000 - now % 60_000 + 200);
        }
    };

    private final Runnable hideNotice = new Runnable() {
        @Override public void run() {
            notice.setVisibility(View.GONE);
        }
    };

    private final LauncherApps.Callback packages = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String pkg, UserHandle user) {
            Apps.forget(pkg);
            changed();
        }

        @Override public void onPackageAdded(String pkg, UserHandle user) {
            changed();
        }

        @Override public void onPackageChanged(String pkg, UserHandle user) {
            Apps.forget(pkg);
            changed();
        }

        @Override public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) {
            changed();
        }

        @Override public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) {
            changed();
        }

        private void changed() {
            appsDirty = true;
            if (resumed) loadApps();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.home);
        favoritesRow = findViewById(R.id.favorites);
        favoritesScroll = findViewById(R.id.favorites_scroll);
        quickRow = findViewById(R.id.quick);
        weatherIcon = findViewById(R.id.weather_icon);
        weatherTemp = findViewById(R.id.weather_temp);
        weatherText = findViewById(R.id.weather_text);
        weatherExtra = findViewById(R.id.weather_extra);
        outageCard = findViewById(R.id.outage);
        outageDays = findViewById(R.id.outage_days);
        tomorrowBox = findViewById(R.id.outage_tomorrow_box);
        outageStatus = findViewById(R.id.outage_status);
        outageGroup = findViewById(R.id.outage_group);
        outageDot = findViewById(R.id.outage_dot);
        todayBar = findViewById(R.id.outage_today);
        tomorrowBar = findViewById(R.id.outage_tomorrow);
        notice = findViewById(R.id.notice);

        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        TextClock date = findViewById(R.id.date);
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);

        tileW = getResources().getDimensionPixelSize(R.dimen.tile_w);
        tileH = getResources().getDimensionPixelSize(R.dimen.tile_h);
        smallW = getResources().getDimensionPixelSize(R.dimen.small_tile_w);
        smallH = getResources().getDimensionPixelSize(R.dimen.small_tile_h);
        gap = getResources().getDimensionPixelSize(R.dimen.tile_gap);

        LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la != null) la.registerCallback(packages, App.MAIN);

        buildQuickRow();
        showWeather(Weather.cached(this));
        schedule = Outages.cached(this);
        showOutage();
    }

    @Override protected void onDestroy() {
        LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la != null) la.unregisterCallback(packages);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (appsDirty) loadApps();
        loadInputs();
        showWeather(Weather.cached(this));
        if (Weather.stale(this)) refreshWeather();
        schedule = Outages.cached(this);
        showOutage();
        if (Outages.enabled(this) && System.currentTimeMillis() - Outages.fetchedAt(this) > OUTAGE_MAX_AGE_MS) refreshOutage();
        App.MAIN.removeCallbacks(ticker);
        App.MAIN.postDelayed(ticker, 60_000 - System.currentTimeMillis() % 60_000 + 200);
        String n = Prefs.takeNotice(this);
        if (n != null) {
            notice.setText(n);
            notice.setVisibility(View.VISIBLE);
            App.MAIN.removeCallbacks(hideNotice);
            App.MAIN.postDelayed(hideNotice, 20_000);
        }
    }

    @Override protected void onPause() {
        resumed = false;
        App.MAIN.removeCallbacks(ticker);
        super.onPause();
    }

    /** Home pressed while already home: back to the first favorite. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        favoritesScroll.smoothScrollTo(0, 0);
        if (favoritesRow.getChildCount() > 0) favoritesRow.getChildAt(0).requestFocus();
    }

    @Override public void onBackPressed() {
        // The home screen has nowhere to go back to.
        onNewIntent(getIntent());
    }

    // MARK: - Apps

    private void loadApps() {
        appsDirty = false;
        final Context app = getApplicationContext();
        App.IO.execute(new Runnable() {
            @Override public void run() {
                final List<Apps.Entry> all = Apps.load(app);
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed()) return;
                        apps = all;
                        buildFavorites();
                    }
                });
            }
        });
    }

    private void buildFavorites() {
        View focused = getCurrentFocus();
        boolean hadFocus = focused != null && focused.getParent() == favoritesRow;
        Object focusedKey = hadFocus ? ((Tile) focused).key : null;
        if (focusPkgAfterRebuild != null) {
            focusedKey = focusPkgAfterRebuild;
            hadFocus = true;
            focusPkgAfterRebuild = null;
        }
        favoritesRow.removeAllViews();
        List<Apps.Entry> favs = Apps.favorites(this, apps);
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
        if (toFocus != null) {
            toFocus.requestFocus();
        } else if (getCurrentFocus() == null || hadFocus) {
            favoritesRow.getChildAt(0).requestFocus();
        }
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
                    final Drawable pic = Apps.picture(app, a, banner);
                    Apps.remember(a, pic, banner[0]);
                    App.MAIN.post(new Runnable() {
                        @Override public void run() {
                            if (a.pkg.equals(t.key)) t.show(pic, banner[0], a.label);
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

    private void addWithGap(LinearLayout row, View v) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(lp.width, lp.height);
        p.setMarginEnd(gap);
        row.addView(v, p);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
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
        new AlertDialog.Builder(this)
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

    private boolean sameInputs(List<Inputs.Input> list) {
        if (list.size() != inputs.size()) return false;
        for (int i = 0; i < list.size(); i++) {
            if (!list.get(i).id.equals(inputs.get(i).id) || !list.get(i).label.equals(inputs.get(i).label)) return false;
        }
        return true;
    }

    private void buildQuickRow() {
        View focused = getCurrentFocus();
        Object focusedKey = focused instanceof Tile && focused.getParent() == quickRow ? ((Tile) focused).key : null;
        quickRow.removeAllViews();
        for (final Inputs.Input in : inputs) {
            Tile t = shortcut("input:" + in.id, in.tuner ? R.drawable.ic_live_tv : R.drawable.ic_input, in.label);
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
        shortcut("lehko", R.drawable.ic_lehko, getString(R.string.lehko_settings)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Apps.start(HomeActivity.this, new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        if (focusedKey != null) {
            for (int i = 0; i < quickRow.getChildCount(); i++) {
                if (focusedKey.equals(((Tile) quickRow.getChildAt(i)).key)) quickRow.getChildAt(i).requestFocus();
            }
        }
    }

    private Tile shortcut(String key, int icon, CharSequence label) {
        Tile t = new Tile(this, smallW, smallH);
        t.key = key;
        t.show(getDrawable(icon), false, label);
        addWithGap(quickRow, t);
        return t;
    }

    // MARK: - Weather

    private void refreshWeather() {
        if (weatherLoading || Weather.place(this) == null) return;
        weatherLoading = true;
        final Context app = getApplicationContext();
        App.IO.execute(new Runnable() {
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
                        if (!isDestroyed()) showWeather(result != null ? result : Weather.cached(HomeActivity.this));
                    }
                });
            }
        });
    }

    private void showWeather(Weather.Data d) {
        if (Weather.place(this) == null) {
            weatherIcon.setImageDrawable(null);
            weatherTemp.setText("");
            weatherText.setText(R.string.weather_pick_city);
            weatherExtra.setVisibility(View.GONE);
            return;
        }
        if (d == null) {
            weatherIcon.setImageDrawable(null);
            weatherTemp.setText("");
            weatherText.setText(R.string.weather_loading);
            weatherExtra.setVisibility(View.GONE);
            return;
        }
        weatherIcon.setImageResource(Weather.icon(d.code, d.day));
        weatherTemp.setText(Weather.degrees(d.temp));
        weatherText.setText(getString(Weather.text(d.code)) + "  ↑" + Weather.degrees(d.max) + "  ↓" + Weather.degrees(d.min));
        String extra = null;
        long age = System.currentTimeMillis() - Weather.cacheTime(this);
        if (age > 3 * 3600_000L) {
            extra = getString(R.string.weather_old, DateFormat.getTimeFormat(this).format(Weather.cacheTime(this)));
        } else if (d.precipAt != null) {
            extra = getString(d.precipSnow ? R.string.weather_snow_at : R.string.weather_rain_at, d.precipAt);
        }
        String city = Weather.cityName(this);
        if (extra == null && !city.isEmpty()) {
            weatherExtra.setTextColor(getColor(R.color.text_dim));
            extra = city;
        } else {
            weatherExtra.setTextColor(getColor(R.color.accent));
        }
        weatherExtra.setText(extra);
        weatherExtra.setVisibility(extra == null ? View.GONE : View.VISIBLE);
    }

    // MARK: - Outages

    private void refreshOutage() {
        if (outageLoading) return;
        outageLoading = true;
        final Context app = getApplicationContext();
        App.IO.execute(new Runnable() {
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
                        if (isDestroyed()) return;
                        if (result != null) schedule = result;
                        showOutage();
                    }
                });
            }
        });
    }

    private void showOutage() {
        if (!Outages.enabled(this)) {
            outageCard.setVisibility(View.GONE);
            return;
        }
        outageCard.setVisibility(View.VISIBLE);
        outageGroup.setText(getString(R.string.outage_group, Outages.chosenGroup(this)));
        long now = System.currentTimeMillis();
        Outages.Status st = Outages.status(schedule, now);
        outageStatus.setText(OutageText.status(this, schedule, st));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(getColor(st.state == Outages.ON ? R.color.power_on : st.state == Outages.OFF ? R.color.power_off
                : st.state == Outages.MAYBE ? R.color.power_maybe : R.color.text_dim));
        outageDot.setBackground(dot);

        Outages.Day today = schedule == null ? null : schedule.day(Outages.today());
        Outages.Day tomorrow = schedule == null ? null : schedule.day(Outages.tomorrow());
        boolean show = today != null && !(st.clearAhead && schedule.noSchedules);
        outageDays.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            Calendar k = Calendar.getInstance(Outages.KYIV);
            todayBar.set(today.slots, k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE));
            tomorrowBox.setVisibility(tomorrow != null ? View.VISIBLE : View.INVISIBLE);
            if (tomorrow != null) tomorrowBar.set(tomorrow.slots, -1);
        }
    }
}
