package ua.lehko.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lehko's settings: the "Lehko" tile in the app list and the last tile on the home screen. */
public final class SettingsActivity extends Activity {
    private static final int HOME = 1, PREVIEW = 2, CITY = 3, OUTAGES = 4, SAVER = 5, CLEANUP = 6, ABOUT = 7,
            RATES = 8, MEMORY = 10, AUTO_CLEAN = 11, FIRE_IDLE = 12, FIRE_SOUND = 13, APPS_ON = 14;
    /** A section title in the list: {HEADER, string id}. Not focusable, the remote skips it. */
    private static final int HEADER = 100;

    private ListView list;
    private TextView status;
    private final List<int[]> rows = new ArrayList<>();
    private final RowAdapter adapter = new RowAdapter();
    private boolean busy;
    private long lastClick;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.settings);
        list = findViewById(R.id.list);
        status = findViewById(R.id.status);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // On a slow TV a quick second OK still lands here before the dialog or screen it opened takes focus.
                long now = SystemClock.uptimeMillis();
                if (busy || rows.get(position)[0] == HEADER || now - lastClick < 700) return;
                lastClick = now;
                click(rows.get(position)[0]);
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        buildRows();
        list.requestFocus();
    }

    private void buildRows() {
        memorySummary = null;
        rows.clear();
        rows.add(new int[]{HOME});
        // Apps turned off with Lehko while it isn't the home screen: "Original" isn't offered, this turns them on.
        if (!HomeMode.isOn(this) && !HomeMode.disabled(this).isEmpty()) rows.add(new int[]{APPS_ON});
        if (!HomeMode.isOn(this)) rows.add(new int[]{PREVIEW});
        rows.add(new int[]{HEADER, R.string.section_show});
        rows.add(new int[]{CITY});
        rows.add(new int[]{OUTAGES});
        rows.add(new int[]{RATES});
        rows.add(new int[]{HEADER, R.string.section_rest});
        rows.add(new int[]{SAVER});
        rows.add(new int[]{FIRE_IDLE});
        rows.add(new int[]{FIRE_SOUND});
        rows.add(new int[]{HEADER, R.string.section_memory});
        rows.add(new int[]{CLEANUP});
        rows.add(new int[]{MEMORY});
        rows.add(new int[]{AUTO_CLEAN});
        rows.add(new int[]{HEADER, 0});
        rows.add(new int[]{ABOUT});
        adapter.notifyDataSetChanged();
    }

    private String title(int row) {
        switch (row) {
            case HOME: return getString(R.string.home_screen);
            case APPS_ON: return getString(R.string.apps_on);
            case PREVIEW: return getString(R.string.preview_home);
            case CITY: return getString(R.string.weather_city);
            case OUTAGES: return getString(R.string.outages);
            case RATES: return getString(R.string.rates_fiat);
            case SAVER: return getString(R.string.screensaver);
            case FIRE_IDLE: return getString(R.string.fireplace);
            case FIRE_SOUND: return getString(R.string.fireplace_sound);
            case CLEANUP: return getString(R.string.cleanup);
            case MEMORY: return getString(R.string.memory);
            case AUTO_CLEAN: return getString(R.string.memory_auto);
            default: return getString(R.string.about);
        }
    }

    private String summary(int row) {
        switch (row) {
            case HOME:
                if (!HomeMode.isOn(this)) return getString(R.string.home_status_off);
                return getString(HomeMode.isFull(this) ? R.string.home_status_on_full : R.string.home_status_on);
            case APPS_ON: return getString(R.string.apps_on_summary, HomeMode.disabled(this).size());
            case PREVIEW: return getString(R.string.preview_home_summary);
            case CITY: {
                String city = Weather.cityName(this);
                return city.isEmpty() ? getString(R.string.not_set) : city;
            }
            case OUTAGES: {
                Outages.Region r = Outages.chosenRegion(this);
                if (r == null || !Outages.enabled(this)) return getString(R.string.outages_off);
                String s = getString(r.label) + " · " + getString(R.string.outage_group, Outages.chosenGroup(this));
                String address = Prefs.of(this).getString(Outages.P_ADDRESS, "");
                return address.isEmpty() ? s : s + " · " + address;
            }
            case RATES: return getString(Rates.fiatEnabled(this) ? R.string.rates_fiat_on : R.string.state_off);
            case SAVER: {
                int saver = saver();
                return getString(saver == HomeMode.SAVER_FIRE ? R.string.screensaver_fire
                        : saver == HomeMode.SAVER_CLOCK ? R.string.screensaver_lehko : R.string.screensaver_system);
            }
            case FIRE_IDLE: {
                int min = FireScene.idleMinutes(this);
                return min > 0 ? getString(R.string.fireplace_after, min) : getString(R.string.state_off);
            }
            case FIRE_SOUND: return getString(FireScene.sound(this) ? R.string.fireplace_sound_on : R.string.state_off);
            case CLEANUP: return getString(R.string.cleanup_summary);
            case MEMORY: return memorySummary();
            case AUTO_CLEAN: return getString(Memory.autoEnabled(this) ? R.string.memory_auto_on : R.string.state_off);
            default: return getString(R.string.about_summary, App.version, App.REPO_URL.replace("https://", ""));
        }
    }

    private void click(int row) {
        switch (row) {
            case HOME:
                if (HomeMode.isOn(this)) confirmDisable(); else enableDialog();
                break;
            case APPS_ON:
                run(new Task() {
                    @Override public String run(HomeMode.Progress p) throws IOException {
                        return HomeMode.enableApps(getApplicationContext(), p);
                    }
                });
                break;
            case PREVIEW:
                Apps.start(this, new Intent(this, HomeActivity.class));
                break;
            case CITY:
                citySearch("");
                break;
            case OUTAGES:
                outageRegion();
                break;
            case RATES:
                Prefs.of(this).edit().putBoolean(Rates.P_FIAT, !Rates.fiatEnabled(this)).apply();
                buildRows();
                break;
            case SAVER:
                screensaver();
                break;
            case FIRE_IDLE:
                fireIdle();
                break;
            case FIRE_SOUND:
                Prefs.of(this).edit().putBoolean(FireScene.P_SOUND, !FireScene.sound(this)).apply();
                buildRows();
                break;
            case CLEANUP:
                Apps.start(this, new Intent(this, CleanupActivity.class));
                break;
            case MEMORY:
                run(new Task() {
                    @Override public String run(HomeMode.Progress p) {
                        return Memory.clean(getApplicationContext(), p);
                    }
                });
                break;
            case AUTO_CLEAN:
                Prefs.of(this).edit().putBoolean(Memory.P_AUTO, !Memory.autoEnabled(this)).apply();
                buildRows();
                break;
            default:
                about();
        }
    }

    /** "Lehko 38 MB · the stock launcher used 240 MB · 412 of 1024 MB free", measured off the UI thread. */
    private String memorySummary;
    private boolean measuring;

    private String memorySummary() {
        if (memorySummary != null) return memorySummary;
        if (!measuring) {
            measuring = true;
            final Context app = getApplicationContext();
            App.IO.execute(new Runnable() {
                @Override public void run() {
                    StringBuilder b = new StringBuilder();
                    long own = Memory.ownPssKb(app) / 1024;
                    if (own > 0) b.append(app.getString(R.string.memory_lehko, own));
                    long launcher = Prefs.of(app).getLong(HomeMode.K_LAUNCHER_PSS, 0) / 1024;
                    if (launcher > 0) {
                        b.append(b.length() > 0 ? " · " : "").append(app.getString(R.string.memory_launcher, launcher));
                    }
                    android.app.ActivityManager.MemoryInfo mi = Memory.info(app);
                    b.append(b.length() > 0 ? " · " : "").append(app.getString(R.string.memory_free_of,
                            mi.availMem / Memory.MB, mi.totalMem / Memory.MB));
                    final String text = b.toString();
                    App.MAIN.post(new Runnable() {
                        @Override public void run() {
                            measuring = false;
                            if (isDestroyed()) return;
                            memorySummary = text;
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            });
        }
        return getString(R.string.loading);
    }

    // MARK: - Home screen on/off

    private void enableDialog() {
        boolean dev = Settings.Global.getInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        boolean adb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        View v = LayoutInflater.from(this).inflate(R.layout.enable_dialog, null);
        ((TextView) v.findViewById(R.id.step_dev)).setText((dev ? "✓  " : "1.  ") + getString(R.string.step_dev));
        ((TextView) v.findViewById(R.id.step_adb)).setText((adb ? "✓  " : "2.  ") + getString(R.string.step_adb));
        final CheckBox full = v.findViewById(R.id.full);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.enable_title)
                .setView(v)
                .setPositiveButton(R.string.enable_button, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        final boolean fullMode = full.isChecked();
                        run(new Task() {
                            @Override public String run(HomeMode.Progress p) throws IOException {
                                return HomeMode.enable(getApplicationContext(), fullMode, p);
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (!dev) {
            b.setNeutralButton(R.string.open_about, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS);
                }
            });
        } else if (!adb) {
            b.setNeutralButton(R.string.open_developer, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                }
            });
        }
        b.show();
    }

    private void openSettings(String action) {
        if (!Apps.start(this, new Intent(action))) Apps.start(this, new Intent(Settings.ACTION_SETTINGS));
    }

    private void confirmDisable() {
        String message = getString(R.string.disable_message);
        // "Original" puts the TV's own screensaver back too, if Lehko's is the one set.
        if (saver() != HomeMode.SAVER_SYSTEM && Prefs.of(this).getBoolean(HomeMode.K_SAVER_CHANGED, false)) {
            message += " " + getString(R.string.disable_message_saver);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.disable_title)
                .setMessage(message)
                .setPositiveButton(R.string.disable_button, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        run(new Task() {
                            @Override public String run(HomeMode.Progress p) throws IOException {
                                return HomeMode.disable(getApplicationContext(), p);
                            }
                        });
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                // For "the TV forgot Lehko's debugging permission": allows it again and keeps Lehko on.
                .setNeutralButton(R.string.allow_again, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        run(new Task() {
                            @Override public String run(HomeMode.Progress p) throws IOException {
                                return HomeMode.allowAgain(getApplicationContext(), p);
                            }
                        });
                    }
                })
                .show();
    }

    // MARK: - Weather city

    private void citySearch(String initial) {
        final EditText input = textInput(initial, getString(R.string.city_search_hint), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.city_search_title)
                .setView(padded(input))
                .setPositiveButton(R.string.search, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (Prefs.hasCity(this)) {
            b.setNeutralButton(R.string.city_reset, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    Prefs.clearCity(SettingsActivity.this);
                    buildRows();
                }
            });
        }
        final AlertDialog d = b.create();
        Runnable search = new Runnable() {
            @Override public void run() {
                final String q = input.getText().toString().trim();
                if (q.length() < 2) return;
                d.dismiss();
                final String lang = Locale.getDefault().getLanguage();
                background(new Work<List<Weather.Place>>() {
                    @Override public List<Weather.Place> run() throws Exception {
                        return Weather.search(q, lang);
                    }

                    @Override public void done(List<Weather.Place> places) {
                        pickCity(q, places);
                    }
                });
            }
        };
        submitOnEnter(d, input, search);
        d.show();
    }

    private void pickCity(final String query, final List<Weather.Place> places) {
        if (places.isEmpty()) {
            Toast.makeText(this, R.string.nothing_found, Toast.LENGTH_SHORT).show();
            citySearch(query);
            return;
        }
        CharSequence[] labels = new CharSequence[places.size()];
        for (int i = 0; i < labels.length; i++) {
            Weather.Place p = places.get(i);
            labels[i] = p.detail.isEmpty() ? p.name : p.name + " · " + p.detail;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.city_search_title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        Weather.Place p = places.get(which);
                        Prefs.setCity(SettingsActivity.this, p.name, p.lat, p.lon);
                        buildRows();
                        final Context app = getApplicationContext();
                        App.NET.execute(new Runnable() {
                            @Override public void run() {
                                try {
                                    Weather.refresh(app);
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                })
                .show();
    }

    // MARK: - Outage schedule

    private void outageRegion() {
        final Outages.Region[] regions = Outages.REGIONS;
        CharSequence[] labels = new CharSequence[regions.length + 1];
        labels[0] = getString(R.string.outages_off);
        for (int i = 0; i < regions.length; i++) labels[i + 1] = getString(regions[i].label);
        new AlertDialog.Builder(this)
                .setTitle(R.string.outage_pick_region)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Outages.turnOff(SettingsActivity.this);
                            buildRows();
                        } else {
                            outageMethod(regions[which - 1]);
                        }
                    }
                })
                .show();
    }

    private void outageMethod(final Outages.Region r) {
        if (!r.yasno()) {
            pickGroup(r);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(r.label))
                .setItems(new CharSequence[]{getString(R.string.outage_by_address), getString(R.string.outage_by_group)},
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                if (which == 0) streetSearch(r, "");
                                else pickGroup(r);
                            }
                        })
                .show();
    }

    private void pickGroup(final Outages.Region r) {
        background(new Work<List<String>>() {
            @Override public List<String> run() throws Exception {
                return Outages.groups(r);
            }

            @Override public void done(final List<String> groups) {
                if (groups.isEmpty()) {
                    Toast.makeText(SettingsActivity.this, R.string.outage_no_groups, Toast.LENGTH_LONG).show();
                    return;
                }
                CharSequence[] labels = new CharSequence[groups.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = getString(R.string.outage_group, groups.get(i));
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(R.string.outage_pick_group)
                        .setItems(labels, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                chooseOutage(r, groups.get(which), null, 0, 0);
                            }
                        })
                        .show();
            }
        });
    }

    private void streetSearch(final Outages.Region r, String initial) {
        final EditText input = textInput(initial, getString(R.string.street_hint), InputType.TYPE_CLASS_TEXT);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(R.string.street_title)
                .setView(padded(input))
                .setPositiveButton(R.string.search, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        submitOnEnter(d, input, new Runnable() {
            @Override public void run() {
                final String q = input.getText().toString().trim();
                if (q.length() < 2) return;
                d.dismiss();
                background(new Work<List<Outages.Item>>() {
                    @Override public List<Outages.Item> run() throws Exception {
                        return Outages.streets(r, q);
                    }

                    @Override public void done(final List<Outages.Item> streets) {
                        if (streets.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, R.string.nothing_found, Toast.LENGTH_SHORT).show();
                            streetSearch(r, q);
                            return;
                        }
                        pickItem(R.string.street_title, streets, new Picked() {
                            @Override public void picked(Outages.Item street) {
                                houseSearch(r, street);
                            }
                        });
                    }
                });
            }
        });
        d.show();
    }

    private void houseSearch(final Outages.Region r, final Outages.Item street) {
        final EditText input = textInput("", getString(R.string.house_hint), InputType.TYPE_CLASS_TEXT);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(street.name)
                .setView(padded(input))
                .setPositiveButton(R.string.search, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        submitOnEnter(d, input, new Runnable() {
            @Override public void run() {
                final String q = input.getText().toString().trim();
                if (q.isEmpty()) return;
                d.dismiss();
                background(new Work<List<Outages.Item>>() {
                    @Override public List<Outages.Item> run() throws Exception {
                        return Outages.houses(r, street.id, q);
                    }

                    @Override public void done(List<Outages.Item> houses) {
                        if (houses.isEmpty()) {
                            Toast.makeText(SettingsActivity.this, R.string.nothing_found, Toast.LENGTH_SHORT).show();
                            houseSearch(r, street);
                            return;
                        }
                        pickItem(R.string.house_title, houses, new Picked() {
                            @Override public void picked(final Outages.Item house) {
                                background(new Work<String>() {
                                    @Override public String run() throws Exception {
                                        return Outages.groupByAddress(r, street.id, house.id);
                                    }

                                    @Override public void done(String group) {
                                        if (group == null) {
                                            Toast.makeText(SettingsActivity.this, R.string.outage_address_unknown, Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        chooseOutage(r, group, street.name + ", " + house.name, street.id, house.id);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
        d.show();
    }

    private interface Picked {
        void picked(Outages.Item item);
    }

    private void pickItem(int title, final List<Outages.Item> items, final Picked picked) {
        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = items.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        picked.picked(items.get(which));
                    }
                })
                .show();
    }

    private void chooseOutage(Outages.Region r, String group, String address, long street, long house) {
        Outages.choose(this, r, group, address, street, house);
        buildRows();
        showStatus(getString(R.string.outage_saved, getString(r.label), group), false);
        final Context app = getApplicationContext();
        App.NET.execute(new Runnable() {
            @Override public void run() {
                try {
                    Outages.refresh(app);
                } catch (Exception ignored) {}
            }
        });
    }

    // MARK: - Screensaver

    /** The screensaver the TV really has: it can be changed in the TV's settings, or outlive a reinstall of Lehko. */
    private int saver() {
        try {
            String now = Settings.Secure.getString(getContentResolver(), "screensaver_components");
            if (now == null || !now.startsWith(HomeMode.PKG + "/")
                    || Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 1) == 0) {
                return HomeMode.SAVER_SYSTEM;
            }
            return now.startsWith(HomeMode.FIRE_DREAM) ? HomeMode.SAVER_FIRE : HomeMode.SAVER_CLOCK;
        } catch (RuntimeException e) {
            // Not readable on this TV: what Lehko set last.
            android.content.SharedPreferences p = Prefs.of(this);
            if (!p.getBoolean(Prefs.SCREENSAVER_LEHKO, false)) return HomeMode.SAVER_SYSTEM;
            return p.getBoolean(Prefs.SCREENSAVER_FIRE, false) ? HomeMode.SAVER_FIRE : HomeMode.SAVER_CLOCK;
        }
    }

    private void screensaver() {
        // In the order of HomeMode.SAVER_SYSTEM, SAVER_CLOCK, SAVER_FIRE.
        CharSequence[] labels = {getString(R.string.screensaver_system), getString(R.string.screensaver_lehko),
                getString(R.string.screensaver_fire)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.screensaver)
                .setSingleChoiceItems(labels, saver(), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, final int which) {
                        d.dismiss();
                        // Even the checked one: setting it again is harmless, and where the TV's setting can't be
                        // read the check is only what Lehko set last.
                        run(new Task() {
                            @Override public String run(HomeMode.Progress p) throws IOException {
                                return HomeMode.setScreensaver(getApplicationContext(), which, p);
                            }
                        });
                    }
                })
                .show();
    }

    private void fireIdle() {
        final int[] choices = FireScene.IDLE_CHOICES;
        int current = FireScene.idleMinutes(this), checked = choices.length - 1;
        CharSequence[] labels = new CharSequence[choices.length];
        for (int i = 0; i < choices.length; i++) {
            labels[i] = choices[i] > 0 ? getString(R.string.dur_min, choices[i]) : getString(R.string.state_off);
            if (choices[i] == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.fireplace)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        Prefs.of(SettingsActivity.this).edit().putInt(FireScene.P_IDLE, choices[which]).apply();
                        buildRows();
                    }
                })
                .show();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name) + " " + App.version)
                .setMessage(getString(R.string.about_text, App.REPO_URL))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // MARK: - Running things in the background

    interface Task {
        String run(HomeMode.Progress p) throws IOException;
    }

    /** Runs a switch on the ADB thread, showing its steps in the status card; the list is locked meanwhile. */
    private void run(final Task task) {
        // One at a time, also from a second dialog that was open before the first one's button was pressed.
        if (busy) return;
        setBusy(true);
        showStatus(getString(R.string.working), false);
        final HomeMode.Progress progress = new HomeMode.Progress() {
            @Override public void step(final String text) {
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        showStatus(text, false);
                    }
                });
            }

            @Override public void approval() {
                step(getString(R.string.adb_waiting));
            }
        };
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                String message;
                boolean error = false;
                try {
                    message = task.run(progress);
                } catch (Exception e) {
                    message = explain(e);
                    error = true;
                }
                final String m = message;
                final boolean err = error;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || isFinishing()) {
                            // Only Lehko's home shows notices: with Lehko off this one would pop up, stale, next time.
                            if (HomeMode.isOn(getApplicationContext())) Prefs.notice(getApplicationContext(), m);
                            return;
                        }
                        setBusy(false);
                        showStatus(m, err);
                        buildRows();
                    }
                });
            }
        });
    }

    private String explain(Exception e) {
        if (e instanceof Adb.Failure) {
            switch (((Adb.Failure) e).reason) {
                case Adb.Failure.PORT_CLOSED: return getString(R.string.adb_closed);
                case Adb.Failure.NOT_ALLOWED: return getString(R.string.adb_rejected);
                case Adb.Failure.TLS_ONLY: return getString(R.string.adb_tls);
                default: return getString(R.string.adb_no_answer);
            }
        }
        if (e instanceof HomeMode.WifiOwnerException) return getString(R.string.wifi_owner_error, e.getMessage());
        if (e instanceof HomeMode.WifiCheckException) return getString(R.string.wifi_check_error);
        if (e instanceof HomeMode.Refused) return getString(R.string.home_not_accepted);
        // The TV stopped answering halfway: a timeout or a dropped connection.
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.io.EOFException
                || e instanceof java.net.SocketException) {
            return getString(R.string.adb_no_answer);
        }
        // Exception texts are English: only the kind of error goes into the translated sentence.
        return getString(R.string.error_generic, e.getClass().getSimpleName());
    }

    private void setBusy(boolean b) {
        busy = b;
        list.setAlpha(b ? 0.5f : 1f);
    }

    private void showStatus(String text, boolean error) {
        status.setText(text);
        status.setTextColor(getColor(error ? R.color.error_text : R.color.text_primary));
        status.setVisibility(View.VISIBLE);
    }

    abstract static class Work<T> {
        abstract T run() throws Exception;

        abstract void done(T result);
    }

    /** Network lookups for the pickers; failures become a toast. */
    private <T> void background(final Work<T> work) {
        showStatus(getString(R.string.loading), false);
        App.NET.execute(new Runnable() {
            @Override public void run() {
                T result = null;
                Exception error = null;
                try {
                    result = work.run();
                } catch (Exception e) {
                    error = e;
                }
                final T r = result;
                final Exception err = error;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed() || isFinishing()) return;
                        status.setVisibility(View.GONE);
                        if (err != null) {
                            Toast.makeText(SettingsActivity.this, getString(R.string.network_error), Toast.LENGTH_LONG).show();
                        } else {
                            work.done(r);
                        }
                    }
                });
            }
        });
    }

    // MARK: - Small dialog helpers

    private EditText textInput(String initial, String hint, int type) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setHint(hint);
        e.setText(initial);
        e.setSelection(e.getText().length());
        e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        return e;
    }

    private View padded(View v) {
        FrameLayout f = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        f.setPadding(pad, pad / 2, pad, 0);
        f.addView(v);
        return f;
    }

    /** The positive button and the keyboard's search/enter key both submit; the dialog stays open on short input. */
    private void submitOnEnter(final AlertDialog d, EditText input, final Runnable submit) {
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // A hardware Enter sends both a down and an up event: act once.
                if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) return true;
                if (d.isShowing()) submit.run();
                return true;
            }
        });
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface dialog) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        submit.run();
                    }
                });
            }
        });
    }

    /** A small section title (or, with no text, a gap) that groups the rows below it. */
    private View header(int text, View convertView) {
        TextView t = convertView instanceof TextView ? (TextView) convertView : new TextView(this);
        float dp = getResources().getDisplayMetrics().density;
        t.setPadding(Math.round(16 * dp), Math.round((text == 0 ? 2 : 16) * dp), Math.round(16 * dp), Math.round(2 * dp));
        t.setTextSize(13);
        t.setTextColor(getColor(R.color.text_dim));
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setText(text == 0 ? "" : getString(text));
        if (android.os.Build.VERSION.SDK_INT >= 28) t.setAccessibilityHeading(text != 0);
        return t;
    }

    private final class RowAdapter extends BaseAdapter {
        @Override public int getCount() {
            return rows.size();
        }

        @Override public Object getItem(int position) {
            return rows.get(position);
        }

        @Override public long getItemId(int position) {
            return rows.get(position)[0];
        }

        @Override public int getViewTypeCount() {
            return 2;
        }

        @Override public int getItemViewType(int position) {
            return rows.get(position)[0] == HEADER ? 1 : 0;
        }

        @Override public boolean areAllItemsEnabled() {
            return false;
        }

        @Override public boolean isEnabled(int position) {
            return rows.get(position)[0] != HEADER;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (rows.get(position)[0] == HEADER) return header(rows.get(position)[1], convertView);
            View v = convertView != null ? convertView
                    : LayoutInflater.from(SettingsActivity.this).inflate(R.layout.settings_row, parent, false);
            int row = rows.get(position)[0];
            ((TextView) v.findViewById(R.id.title)).setText(title(row));
            ((TextView) v.findViewById(R.id.summary)).setText(summary(row));
            return v;
        }
    }
}
