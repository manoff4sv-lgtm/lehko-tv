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
        int id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        // A try Lehko gave up on: its "aborted" is no news.
        if (!Update.current(c, id)) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null && Update.confirm(c, confirm)) return;
            // No confirmation screen: the session would wait forever. Answered first, so its "aborted" is ignored.
            Update.finished(c, PackageInstaller.STATUS_FAILURE, null);
            Update.abandon(c.getPackageManager().getPackageInstaller(), id);
            return;
        }
        Update.finished(c, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}
