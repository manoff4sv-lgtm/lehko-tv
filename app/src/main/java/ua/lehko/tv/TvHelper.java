package ua.lehko.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * The remote's star, YouTube and "all apps" buttons without the stock launcher. Google TV hands those buttons to its
 * launcher, so with the launcher turned off the system took the presses and nothing happened. An accessibility
 * service's key filter sees a key before the system hands it out, so this one opens what {@link Keys} says and keeps
 * the press; every other key, and any press it can't open anything for, goes on untouched. It reads no screen
 * content. HomeMode turns it on with Lehko where it can act (Google TV's launcher installed, or a star button on the
 * remote), over the debugging connection, and off with "Bring back".
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

    /**
     * Finds the first thing the button stands for that can be opened, and opens it once the key is answered: Android
     * waits only half a second for that answer before handing the key on anyway. The settings are read at each press
     * (a few bytes).
     */
    private boolean open(int key) {
        try {
            PackageManager pm = getPackageManager();
            String star = key == Keys.STAR ? Keys.read(getFilesDir()).get(Keys.P_STAR) : null;
            List<String> targets = Keys.opens(key, star, Keys.launcherOff(pm));
            // Left on after Lehko stopped being the home screen some other way (by hand, say): out of the way.
            if (targets.isEmpty() || !lehkoHome(pm)) return false;
            for (String target : targets) {
                Intent i = Keys.ALL_APPS.equals(target) ? new Intent(this, AllAppsActivity.class)
                        : Apps.launchIntent(pm, target);
                if (i == null) continue;
                // Allowed from the background: Android lets an accessibility service start activities.
                final Intent start = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                App.MAIN.post(new Runnable() {
                    @Override public void run() {
                        Apps.start(TvHelper.this, start);
                    }
                });
                return true;
            }
        } catch (RuntimeException e) {
            // The package manager failed (the system busy or restarting): the key goes on as before.
        }
        return false;
    }

    private boolean lehkoHome(PackageManager pm) {
        ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && home.activityInfo != null && getPackageName().equals(home.activityInfo.packageName);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Keys only. Its config has to name some event type: announcements, which it ignores.
    }

    @Override public void onInterrupt() {}
}
