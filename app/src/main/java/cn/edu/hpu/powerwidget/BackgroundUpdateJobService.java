package cn.edu.hpu.powerwidget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BackgroundUpdateJobService extends JobService {
    private volatile JobParameters activeParams;
    private volatile AtomicBoolean activeFinished;

    @Override public boolean onStartJob(final JobParameters params) {
        final Context context = getApplicationContext();
        AppLogger.init(context);
        final boolean autoRefresh = AutoRefreshStore.enabled(context);
        final boolean lowBattery = LowBatteryStore.enabled(context);
        if (!autoRefresh && !lowBattery) {
            BackgroundUpdateScheduler.cancel(context);
            jobFinished(params, false);
            return false;
        }
        if (activeParams != null && activeFinished != null && !activeFinished.get()) {
            jobFinished(params, false);
            return false;
        }
        final AtomicBoolean flag = new AtomicBoolean(false);
        activeParams = params;
        activeFinished = flag;
        final boolean refreshData = autoRefresh || lowBattery;
        if (refreshData) {
            boolean started = PowerRefreshManager.refresh(context, new PowerRefreshManager.Callback() {
                @Override public void onComplete(boolean success, String message) {
                    if (lowBattery) LowBatteryNotifier.check(context);
                    finishOnce(params, flag, false);
                }
            }, true);
            if (!started) {
                if (lowBattery) LowBatteryNotifier.check(context);
                finishOnce(params, flag, false);
            }
        } else {
            finishOnce(params, flag, false);
        }
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (params == activeParams && activeFinished != null) finishOnce(params, activeFinished, true);
        return true;
    }

    private void finishOnce(JobParameters params, AtomicBoolean flag, boolean reschedule) {
        if (flag.compareAndSet(false, true)) jobFinished(params, reschedule);
    }
}
