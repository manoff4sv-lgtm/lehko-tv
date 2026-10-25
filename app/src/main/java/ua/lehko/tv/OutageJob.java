package ua.lehko.tv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;

/**
 * Downloads the outage schedule every half hour while other apps are in front, so the helper's warning
 * ({@link OutageWarning}) follows a changed schedule though the home screen hasn't been seen for hours. It goes the
 * home screen's way (one download at a time, the pause after a failure, nothing while the schedule is fresh), and it
 * wakes the home screen's process, so it does nothing else, and nothing at all while the TV is in standby.
 */
public final class OutageJob extends JobService {
    /** Job ids are the app's own: ModeJob has 1 and 2. */
    static final int ID = 30;
    static final long EVERY_MS = 30 * 60_000L;

    /** Scheduled while it is of use, cancelled otherwise. After each download, and when the warning is turned on. */
    static void sync(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        try {
            if (!wanted(c)) {
                js.cancel(ID);
            } else if (js.getPendingJob(ID) == null) {
                // Only when it isn't there yet: scheduling it again would start the half hour over.
                js.schedule(new JobInfo.Builder(ID, new ComponentName(c, OutageJob.class))
                        .setPeriodic(EVERY_MS)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .build());
            }
        } catch (RuntimeException ignored) {
            // Too many jobs or a vendor quirk: the next download tries again.
        }
    }

    /** The schedule set up, the warning on, Lehko and its helper on. */
    private static boolean wanted(Context c) {
        return Outages.enabled(c) && OutageWarning.on(c) && TvHelper.running(c);
    }

    @Override public boolean onStartJob(final JobParameters params) {
        Context app = getApplicationContext();
        // Turned off since (the schedule, the warning or Lehko): the job goes too.
        if (!wanted(app)) {
            sync(app);
            return false;
        }
        // In standby the helper shows nothing, so a night of downloads would be for no one. The outages already
        // handed to it keep their moments; the first run after the TV comes on, or the home screen, catches up.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return false;
        // The download hands the outages ahead to the helper itself (Outages.refresh).
        return Outages.refreshIfStale(app, new Runnable() {
            @Override public void run() {
                jobFinished(params, false);
            }
        });
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}
