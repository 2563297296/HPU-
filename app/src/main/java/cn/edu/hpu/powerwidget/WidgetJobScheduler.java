package cn.edu.hpu.powerwidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

final class WidgetJobScheduler {
    private static final int PERIODIC_JOB_ID = 47101;
    private static final int IMMEDIATE_JOB_ID = 47102;
    private static final long REFRESH_INTERVAL_MS = 60L * 60L * 1000L;

    private WidgetJobScheduler() {}

    private static ComponentName service(Context context) {
        return new ComponentName(context, WidgetRefreshJobService.class);
    }

    static void schedule(Context context) {
        if (!PowerRefreshManager.hasWidgets(context)) {
            cancel(context);
            return;
        }

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || periodicPending(scheduler)) return;

        JobInfo job = new JobInfo.Builder(PERIODIC_JOB_ID, service(context))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(REFRESH_INTERVAL_MS)
                .setPersisted(true)
                .build();
        try {
            scheduler.schedule(job);
        } catch (RuntimeException ignored) {
            // The package may still be settling after an update or boot.
        }
    }

    static void requestImmediateRefresh(Context context) {
        if (!PowerRefreshManager.hasWidgets(context)) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        scheduler.cancel(IMMEDIATE_JOB_ID);
        JobInfo job = new JobInfo.Builder(IMMEDIATE_JOB_ID, service(context))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
                .build();
        try {
            scheduler.schedule(job);
        } catch (RuntimeException ignored) {
            // Network jobs can briefly be unavailable while the system is booting.
        }
    }

    static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        scheduler.cancel(PERIODIC_JOB_ID);
        scheduler.cancel(IMMEDIATE_JOB_ID);
    }

    private static boolean periodicPending(JobScheduler scheduler) {
        if (Build.VERSION.SDK_INT >= 24) return scheduler.getPendingJob(PERIODIC_JOB_ID) != null;
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (job.getId() == PERIODIC_JOB_ID) return true;
        }
        return false;
    }
}