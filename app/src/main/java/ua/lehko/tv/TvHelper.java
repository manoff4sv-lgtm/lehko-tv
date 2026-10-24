package ua.lehko.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The remote's star, YouTube and "all apps" buttons without the stock launcher. Google TV hands those buttons to its
 * launcher, so with the launcher turned off the system took the presses and nothing happened. An accessibility
 * service's key filter sees a key before the system hands it out, so this one opens what {@link Keys} says and keeps
 * the press; every other key, and any press it can't open anything for, goes on untouched. It reads no screen
 * content. HomeMode turns it on with Lehko (over the debugging connection) and off with "Bring back".
 */
public final class TvHelper extends AccessibilityService {
    /** The button whose press opened something: its repeats and its release are kept too, never half a press. */
    private int taken = KeyEvent.KEYCODE_UNKNOWN;

    @Override protected boolean onKeyEvent(KeyEvent e) {
        int key = e.getKeyCode();
        if (!Keys.handled(key, e.getSource())) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            taken = open(key) ? key : KeyEvent.KEYCODE_UNKNOWN;
            return taken == key;
        }
        boolean ours = key == taken;
        if (ours && e.getAction() == KeyEvent.ACTION_UP) taken = KeyEvent.KEYCODE_UNKNOWN;
        return ours;
    }

    /** Opens the first thing the button stands for that can be opened. Read at each press: it's a few bytes. */
    private boolean open(int key) {
        PackageManager pm = getPackageManager();
        String star = key == Keys.STAR ? Keys.read(getFilesDir()).get(Keys.P_STAR) : null;
        for (String target : Keys.opens(key, star, launcherOff(pm))) {
            Intent i = Keys.ALL_APPS.equals(target) ? new Intent(this, AllAppsActivity.class)
                    : Apps.launchIntent(pm, target);
            // Allowed from the background: Android lets an accessibility service start activities.
            if (i != null && Apps.start(this, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Google TV's launcher is installed and turned off (Lehko's full mode). Asked at each press, as it can change any
     * time. Not installed counts as on: other TVs answer these buttons their own way.
     */
    private static boolean launcherOff(PackageManager pm) {
        try {
            return !pm.getApplicationInfo(Keys.LAUNCHERX, PackageManager.MATCH_DISABLED_COMPONENTS).enabled;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Keys only. Its config has to name some event type: announcements, which it ignores.
    }

    @Override public void onInterrupt() {}
}
