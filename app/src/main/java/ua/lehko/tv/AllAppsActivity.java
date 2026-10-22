package ua.lehko.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.LauncherApps;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Every app as a grid of tiles. Hold OK (or press Menu) on an app to add it to favorites. */
public final class AllAppsActivity extends Activity {
    private GridView grid;
    private TextView empty;
    private final Adapter adapter = new Adapter();
    private List<Apps.Entry> apps = new ArrayList<>();
    private int selected = -1, tileW, tileH;

    private final LauncherApps.Callback packages = new LauncherApps.Callback() {
        @Override public void onPackageRemoved(String pkg, UserHandle user) {
            Apps.forget(pkg);
            load();
        }

        @Override public void onPackageAdded(String pkg, UserHandle user) {
            load();
        }

        @Override public void onPackageChanged(String pkg, UserHandle user) {
            Apps.forget(pkg);
            load();
        }

        @Override public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) {
            load();
        }

        @Override public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) {
            load();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.all_apps);
        tileW = getResources().getDimensionPixelSize(R.dimen.tile_w);
        tileH = getResources().getDimensionPixelSize(R.dimen.tile_h);
        grid = findViewById(R.id.grid);
        empty = findViewById(R.id.empty);
        grid.setColumnWidth(tileW);
        grid.setSelector(new ColorDrawable(0));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Apps.launch(AllAppsActivity.this, apps.get(position));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                menu(apps.get(position));
                return true;
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                select(position);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
                select(-1);
            }
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                select(hasFocus ? grid.getSelectedItemPosition() : -1);
            }
        });
        LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la != null) la.registerCallback(packages, App.MAIN);
        load();
    }

    @Override protected void onDestroy() {
        LauncherApps la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la != null) la.unregisterCallback(packages);
        super.onDestroy();
    }

    private void load() {
        final Context app = getApplicationContext();
        App.IO.execute(new Runnable() {
            @Override public void run() {
                final List<Apps.Entry> all = Apps.load(app);
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (isDestroyed()) return;
                        apps = all;
                        adapter.notifyDataSetChanged();
                        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
                        if (selected < 0 && !all.isEmpty()) grid.requestFocus();
                    }
                });
            }
        });
    }

    private void select(int position) {
        selected = position;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            boolean on = grid.getFirstVisiblePosition() + i == position;
            if (child.isActivated() != on) child.setActivated(on);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && selected >= 0 && selected < apps.size()) {
            menu(apps.get(selected));
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void menu(final Apps.Entry a) {
        final boolean fav = Apps.isFavorite(this, a.pkg);
        final int[] actions = {R.string.open, fav ? R.string.remove_favorite : R.string.add_favorite, R.string.app_info, R.string.uninstall};
        CharSequence[] labels = new CharSequence[actions.length];
        for (int i = 0; i < actions.length; i++) labels[i] = getString(actions[i]);
        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        // R ids are not constants with Gradle 8, so no switch here.
                        int action = actions[which];
                        if (action == R.string.open) Apps.launch(AllAppsActivity.this, a);
                        else if (action == R.string.add_favorite) Apps.addFavorite(AllAppsActivity.this, a.pkg);
                        else if (action == R.string.remove_favorite) Apps.removeFavorite(AllAppsActivity.this, a.pkg);
                        else if (action == R.string.app_info) Apps.openInfo(AllAppsActivity.this, a.pkg);
                        else if (action == R.string.uninstall) Apps.uninstall(AllAppsActivity.this, a.pkg);
                        adapter.notifyDataSetChanged();
                    }
                })
                .show();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() {
            return apps.size();
        }

        @Override public Object getItem(int position) {
            return apps.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            final Tile t = convertView instanceof Tile ? (Tile) convertView : new Tile(AllAppsActivity.this, tileW, tileH);
            t.setLayoutParams(new GridView.LayoutParams(tileW, tileH));
            // Grid items are not focusable themselves: the grid moves the selection.
            t.setFocusable(false);
            t.setClickable(false);
            t.setOnFocusChangeListener(null);
            final Apps.Entry a = apps.get(position);
            t.key = a.pkg;
            boolean on = position == selected;
            if (t.isActivated() != on) {
                t.setActivated(on);
            }
            t.settle(on);
            Drawable d = Apps.cached(a);
            if (d != null) {
                t.show(d, Apps.cachedIsBanner(a), a.label);
            } else {
                t.placeholder(a.label);
                final Context app = getApplicationContext();
                App.IO.execute(new Runnable() {
                    @Override public void run() {
                        final boolean[] banner = new boolean[1];
                        final Drawable pic = Apps.picture(app, a, banner, tileW, tileH);
                        Apps.remember(a, pic, banner[0]);
                        App.MAIN.post(new Runnable() {
                            @Override public void run() {
                                if (a.pkg.equals(t.key)) t.show(pic, banner[0], a.label);
                            }
                        });
                    }
                });
            }
            t.setContentDescription(Apps.isFavorite(AllAppsActivity.this, a.pkg)
                    ? a.label + ", " + getString(R.string.in_favorites) : a.label);
            return t;
        }
    }
}
