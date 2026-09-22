package com.local.focusfence.update;
import android.app.job.JobParameters;
import android.app.job.JobService;
/** Periodic checks survive ordinary process death/reboot, independently of accessibility. */
public final class UpdateJobService extends JobService {
    private volatile boolean stopped;
    @Override public boolean onStartJob(JobParameters params) {
        stopped=false;
        UpdateManager.checkAutomatically(this,state->{if(!stopped)jobFinished(params,false);});
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { stopped=true;return true; }
}
