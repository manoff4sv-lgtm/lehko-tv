package ua.lehko.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot (TV updates install on reboot) or an update of Lehko: check Lehko is still the home screen. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (!HomeMode.isOn(c)) return;
        // Give the system a minute to settle after boot.
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction());
        ModeJob.schedule(c, ModeJob.VERIFY, boot ? 60_000 : 5_000);
    }
}
