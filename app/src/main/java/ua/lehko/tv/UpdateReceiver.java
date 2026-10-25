package ua.lehko.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * The TV's installer answers here about an update Lehko handed it (see Update): first with its confirmation screen to
 * bring up, then, unless the update went in, why not. Not exported: only the installer's PendingIntent reaches it.
 */
public final class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            // Lehko's settings screen is in front (the update was asked for there), so Android lets it start this.
            if (confirm != null && Apps.start(c, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
            // No confirmation screen: the session would wait forever.
            try {
                c.getPackageManager().getPackageInstaller()
                        .abandonSession(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1));
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            status = PackageInstaller.STATUS_FAILURE;
        }
        Update.finished(c, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}
