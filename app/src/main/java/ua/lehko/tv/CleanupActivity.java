package ua.lehko.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turn off apps you don't use, to free memory on a weak TV. Nothing is uninstalled: a turned-off app is still there
 * and comes back with one press (or all at once with "original home screen").
 */
public final class CleanupActivity extends Activity {
    /** Optional system apps worth offering even though they have no tile in the app list. Value: a hint string. */
    private static final Map<String, Integer> SUGGESTED = new HashMap<>();

    static {
        SUGGESTED.put("com.google.android.play.games", 0);
        SUGGESTED.put("com.google.android.youtube.tvmusic", 0);
        SUGGESTED.put("com.google.android.tvrecommendations", R.string.cleanup_hint_recommendations);
        SUGGESTED.put("com.google.android.katniss", R.string.cleanup_hint_assistant);
        SUGGESTED.put("com.google.android.apps.tv.dreamx", R.string.cleanup_hint_ambient);
        SUGGESTED.put("com.google.android.feedback", 0);
    }

    private static final class Row {
        String pkg, label;
        boolean disabled, wifiOwner, suggested;
        int hint;
    }

    private ListView list;
    private TextView status;
    private final List<Row> rows = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.cleanup);
        list = findViewById(R.id.list);
        status = findViewById(R.id.status);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!busy) toggle(rows.get(position));
            }
        });
        load();
    }

    private void load() {
        busy = true;
        status.setText(R.string.step_connecting);
        status.setVisibility(View.VISIBLE);
        final Context app = getApplicationContext();
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                List<Row> found = new ArrayList<>();
                try {
                    Set<String>[] state = HomeMode.systemState(app);
                    found = candidates(app, state[0], state[1]);
                } catch (Adb.Failure e) {
                    error = getString(e.reason == Adb.Failure.PORT_CLOSED ? R.string.cleanup_need_debugging : R.string.adb_rejected);
                } catch (Exception e) {
                    error = getString(R.string.error_generic, e.getMessage());
                }
                final List<Row> result = found;
                final String err = error;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed()) return;
                        busy = false;
                        rows.clear();
                        rows.addAll(result);
                        adapter.notifyDataSetChanged();
                        if (err != null) {
                            status.setText(err);
                        } else {
                            status.setVisibility(View.GONE);
                            list.requestFocus();
                        }
                    }
                });
            }
        });
    }

    /** Apps with a tile, suggested system apps, and everything Lehko turned off before. */
    private static List<Row> candidates(Context c, Set<String> disabledNow, Set<String> wifiOwners) {
        PackageManager pm = c.getPackageManager();
        Set<String> pkgs = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                pkgs.add(ri.activityInfo.packageName);
            }
        }
        pkgs.addAll(SUGGESTED.keySet());
        Set<String> ours = HomeMode.disabled(c);
        pkgs.addAll(ours);
        // Managed by the home screen switch, not here.
        String originalHome = HomeMode.packageOf(Prefs.of(c).getString(HomeMode.K_HOME, ""));
        pkgs.remove(originalHome);
        pkgs.remove(HomeMode.SETUP);
        pkgs.remove(HomeMode.ATV_RECOMMENDATIONS);
        pkgs.remove(c.getPackageName());

        List<Row> out = new ArrayList<>();
        for (String pkg : pkgs) {
            if (pkg == null || HomeMode.isProtected(pkg)) continue;
            ApplicationInfo ai;
            try {
                ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS);
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            Row r = new Row();
            r.pkg = pkg;
            CharSequence label = ai.loadLabel(pm);
            r.label = label == null ? pkg : label.toString();
            r.disabled = disabledNow.contains(pkg) || !ai.enabled;
            r.wifiOwner = wifiOwners.contains(pkg);
            r.suggested = SUGGESTED.containsKey(pkg);
            Integer hint = SUGGESTED.get(pkg);
            r.hint = hint == null ? 0 : hint;
            out.add(r);
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(out, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) {
                if (a.suggested != b.suggested) return a.suggested ? -1 : 1;
                return collator.compare(a.label, b.label);
            }
        });
        return out;
    }

    private void toggle(final Row r) {
        if (r.wifiOwner && !r.disabled) {
            status.setText(getString(R.string.wifi_owner_error, r.label));
            status.setVisibility(View.VISIBLE);
            return;
        }
        busy = true;
        final boolean enable = r.disabled;
        status.setText(R.string.working);
        status.setVisibility(View.VISIBLE);
        final Context app = getApplicationContext();
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                String error = null;
                try {
                    HomeMode.setAppEnabled(app, r.pkg, enable);
                } catch (HomeMode.WifiOwnerException e) {
                    error = getString(R.string.wifi_owner_error, r.label);
                } catch (Adb.Failure e) {
                    error = getString(R.string.adb_rejected);
                } catch (Exception e) {
                    error = getString(R.string.cleanup_failed, r.label);
                }
                final String err = error;
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed()) return;
                        busy = false;
                        if (err == null) {
                            r.disabled = !enable;
                            Apps.forget(r.pkg);
                            status.setVisibility(View.GONE);
                        } else {
                            status.setText(err);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() {
            return rows.size();
        }

        @Override public Object getItem(int position) {
            return rows.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(CleanupActivity.this).inflate(R.layout.settings_row, parent, false);
            Row r = rows.get(position);
            TextView title = v.findViewById(R.id.title), summary = v.findViewById(R.id.summary);
            title.setText(r.label);
            title.setAlpha(r.disabled ? 0.55f : 1f);
            String state = getString(r.disabled ? R.string.state_off : R.string.state_on);
            if (r.wifiOwner) state += " · " + getString(R.string.cleanup_wifi_owner);
            if (r.hint != 0) state += " · " + getString(r.hint);
            summary.setText(state);
            summary.setTextColor(getColor(r.disabled ? R.color.power_maybe : R.color.text_secondary));
            return v;
        }
    }
}
