package cn.edu.hpu.powerwidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

final class BackgroundUpdateScheduler {
    private static final int JOB_ID = 47103;
    private static final long MIN_INTERVAL_MS = 15L * 60L * 1000L;

    private BackgroundUpdateScheduler() {}

    static boolean shouldSchedule(Context context) {
        return AutoRefreshStore.enabled(context) || LowBatteryStore.enabled(context);
    }

    static void schedule(Context context) {
        if (!shouldSchedule(context)) { cancel(context); return; }
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (Build.VERSION.SDK_INT >= 24 && scheduler.getPendingJob(JOB_ID) != null) return;
        long interval = AutoRefreshStore.enabled(context)
                ? AutoRefreshStore.intervalMinutes(context) * 60_000L
                : 30L * 60_000L;
        if (interval < MIN_INTERVAL_MS) interval = MIN_INTERVAL_MS;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, BackgroundUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(interval)
                .setPersisted(true)
                .build();
        try { scheduler.schedule(job); } catch (RuntimeException ignored) {}
    }

    static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancel(JOB_ID);
    }
}
