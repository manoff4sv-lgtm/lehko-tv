package ua.lehko.tv;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.LruCache;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The apps you can open, their banners, and the default favorites. */
final class Apps {
    private Apps() {}

    static final class Entry {
        final String pkg;
        final ComponentName component;
        final String label;
        /** Has a TV (leanback) launcher activity; phone-only apps open with the regular launcher intent. */
        final boolean leanback;

        Entry(String pkg, ComponentName component, String label, boolean leanback) {
            this.pkg = pkg;
            this.component = component;
            this.label = label;
            this.leanback = leanback;
        }
    }

    /** Streaming apps people expect first, when installed (in this order). Unknown names are simply skipped. */
    private static final String[] DEFAULT_FAVORITES = {
            "com.google.android.youtube.tv", "com.liskovsoft.smarttubetv.beta", "org.smarttube.stable",
            "com.netflix.ninja", "com.megogo.application", "tv.sweet.tvplayer", "ua.kyivstar.tv",
            "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.wbd.stream", "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android", "com.google.android.youtube.tvmusic", "com.plexapp.android", "org.xbmc.kodi",
            "org.videolan.vlc", "com.android.vending",
    };

    /** All openable apps except Lehko itself, sorted by name. Slow: call off the UI thread. */
    static List<Entry> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Entry> byPkg = new LinkedHashMap<>();
        collect(pm, byPkg, Intent.CATEGORY_LEANBACK_LAUNCHER, true);
        collect(pm, byPkg, Intent.CATEGORY_LAUNCHER, false);
        byPkg.remove(c.getPackageName());
        List<Entry> out = new ArrayList<>(byPkg.values());
        final Collator collator = Collator.getInstance();
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                return collator.compare(a.label, b.label);
            }
        });
        return out;
    }

    private static void collect(PackageManager pm, Map<String, Entry> into, String category, boolean leanback) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || into.containsKey(ai.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            into.put(ai.packageName, new Entry(ai.packageName, new ComponentName(ai.packageName, ai.name),
                    label == null ? ai.packageName : label.toString().trim(), leanback));
        }
    }

    static Map<String, Entry> byPackage(List<Entry> apps) {
        Map<String, Entry> m = new HashMap<>();
        for (Entry a : apps) m.put(a.pkg, a);
        return m;
    }

    /** The saved favorites that are still installed; the first time, a sensible default set. */
    static List<Entry> favorites(Context c, List<Entry> all) {
        Map<String, Entry> byPkg = byPackage(all);
        List<Entry> out = new ArrayList<>();
        if (!Prefs.favoritesSet(c)) {
            List<String> pkgs = new ArrayList<>();
            for (String p : DEFAULT_FAVORITES) if (byPkg.containsKey(p)) pkgs.add(p);
            // Fill up with other TV apps so the row is never empty on a fresh TV.
            for (Entry a : all) {
                if (pkgs.size() >= 6) break;
                if (a.leanback && !pkgs.contains(a.pkg) && !isSystemNoise(a.pkg)) pkgs.add(a.pkg);
            }
            Prefs.setFavorites(c, pkgs);
        }
        for (String p : Prefs.favorites(c)) {
            Entry a = byPkg.get(p);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** Settings-like entries that are not worth a favorite slot by default. */
    private static boolean isSystemNoise(String pkg) {
        return pkg.startsWith("com.android.") || pkg.contains("settings") || pkg.contains("setup")
                || pkg.startsWith("com.mediatek.") || pkg.startsWith("com.google.android.tv");
    }

    static boolean isFavorite(Context c, String pkg) {
        return Prefs.favorites(c).contains(pkg);
    }

    static void addFavorite(Context c, String pkg) {
        List<String> f = Prefs.favorites(c);
        if (!f.contains(pkg)) f.add(pkg);
        Prefs.setFavorites(c, f);
    }

    static void removeFavorite(Context c, String pkg) {
        List<String> f = Prefs.favorites(c);
        f.remove(pkg);
        Prefs.setFavorites(c, f);
    }

    /**
     * Moves a favorite one place left (-1) or right (+1) on the row as shown ({@code all}: the openable apps). Saved
     * favorites that aren't shown (uninstalled, turned off) are stepped over and keep their place for when they return.
     */
    static void moveFavorite(Context c, List<Entry> all, String pkg, int delta) {
        Map<String, Entry> shown = byPackage(all);
        List<String> f = Prefs.favorites(c);
        int i = f.indexOf(pkg), j = i + delta;
        if (i < 0) return;
        while (j >= 0 && j < f.size() && !shown.containsKey(f.get(j))) j += delta;
        if (j < 0 || j >= f.size()) return;
        Collections.swap(f, i, j);
        Prefs.setFavorites(c, f);
    }

    // MARK: - Opening

    static void launch(Context c, Entry a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!start(c, i)) {
            Intent fallback = launchIntent(c.getPackageManager(), a.pkg);
            if (fallback == null || !start(c, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                Toast.makeText(c, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** How a launcher opens {@code pkg}: its TV activity, else its phone one. Null when it has neither, or is off. */
    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    static boolean start(Context c, Intent i) {
        try {
            c.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    static void openInfo(Context c, String pkg) {
        start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void uninstall(Context c, String pkg) {
        start(c, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // MARK: - Pictures

    /**
     * Banners and icons of the apps on screen, already scaled down to tile size (some apps ship 1280x720+ banners).
     * Sized by memory: about 8 MB, roughly 30 tiles.
     */
    private static final LruCache<String, Drawable> PICTURES = new LruCache<String, Drawable>(8 * 1024) {
        @Override protected int sizeOf(String key, Drawable d) {
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                return Math.max(1, ((BitmapDrawable) d).getBitmap().getByteCount() / 1024);
            }
            return 64;
        }
    };
    private static final Map<String, Boolean> BANNER = Collections.synchronizedMap(new HashMap<String, Boolean>());

    /** A cached banner or icon, or null if it hasn't been loaded yet. */
    static Drawable cached(Entry a) {
        return cached(a.pkg);
    }

    static Drawable cached(String pkg) {
        return PICTURES.get(pkg);
    }

    static boolean cachedIsBanner(Entry a) {
        Boolean b = BANNER.get(a.pkg);
        return b != null && b;
    }

    /**
     * The app's TV banner scaled to {@code w x h}, or its icon (then {@code banner[0]} is false), as a small bitmap.
     * Call off the UI thread.
     */
    static Drawable picture(Context c, Entry a, boolean[] banner, int w, int h) {
        PackageManager pm = c.getPackageManager();
        Drawable d = null;
        try {
            ActivityInfo ai = pm.getActivityInfo(a.component, 0);
            d = ai.loadBanner(pm);
            if (d == null) d = ai.applicationInfo.loadBanner(pm);
            banner[0] = d != null;
            if (d == null) d = ai.loadIcon(pm);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            banner[0] = false;
        }
        if (d == null) d = pm.getDefaultActivityIcon();
        int icon = Math.round(48 * c.getResources().getDisplayMetrics().density);
        try {
            Bitmap b = banner[0] ? render(d, w, h, true) : render(d, icon, icon, false);
            return new BitmapDrawable(c.getResources(), b);
        } catch (RuntimeException | OutOfMemoryError e) {
            return d;
        }
    }

    /** Draws {@code d} into a w x h bitmap: cropped to fill (banners) or fitted inside (icons). */
    private static Bitmap render(Drawable d, int w, int h, boolean crop) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(b);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            d.setBounds(0, 0, w, h);
        } else {
            float scale = crop ? Math.max(w / (float) iw, h / (float) ih) : Math.min(w / (float) iw, h / (float) ih);
            int dw = Math.round(iw * scale), dh = Math.round(ih * scale);
            int left = (w - dw) / 2, top = (h - dh) / 2;
            d.setBounds(left, top, left + dw, top + dh);
        }
        d.draw(canvas);
        return b;
    }

    static void remember(Entry a, Drawable d, boolean banner) {
        // The flag first: whoever finds the picture (from another thread too) must also find whether it's a banner.
        BANNER.put(a.pkg, banner);
        PICTURES.put(a.pkg, d);
    }

    /**
     * Gives picture memory back: all but the favorites' (their tiles on the home screen hold those bitmaps anyway, so
     * dropping them would free nothing and only cost a reload).
     */
    static void trimPictures(Context c) {
        java.util.Set<String> keep = new java.util.HashSet<>(Prefs.favorites(c));
        for (String pkg : PICTURES.snapshot().keySet()) if (!keep.contains(pkg)) PICTURES.remove(pkg);
    }

    static void forget(String pkg) {
        PICTURES.remove(pkg);
        BANNER.remove(pkg);
        Ambient.forget(pkg);
    }
}
