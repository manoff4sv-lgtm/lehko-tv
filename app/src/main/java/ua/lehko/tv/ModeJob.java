package ua.lehko.tv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Background checks that need the TV's debugging port: after boot/updates, and the crash safety net. */
public final class ModeJob extends JobService {
    static final int VERIFY = 1, REVERT = 2;

    static void schedule(Context c, int what, long delayMs) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(what, new ComponentName(c, ModeJob.class))
                .setMinimumLatency(delayMs)
                .setOverrideDeadline(delayMs + 60_000)
                .build();
        try {
            js.schedule(job);
        } catch (RuntimeException ignored) {
            // Too many jobs or a vendor quirk: the next start of Lehko checks again.
        }
    }

    @Override public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        App.ADB.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (params.getJobId() == REVERT) HomeMode.revertAfterCrash(app, null);
                    else HomeMode.verify(app);
                } catch (RuntimeException e) {
                    // A background check must never take the app down (that would count as a crash).
                } finally {
                    jobFinished(params, false);
                }
            }
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}
