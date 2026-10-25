package cn.edu.hpu.powerwidget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the same direct HTTPS refresh path used by the app and widget refresh button. */
public final class WidgetRefreshJobService extends JobService {
    private volatile JobParameters activeParams;
    private volatile AtomicBoolean activeFinished;

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context context = getApplicationContext();
        AppLogger.init(context);
        if (!PowerRefreshManager.hasWidgets(context)) {
            WidgetJobScheduler.cancel(context);
            jobFinished(params, false);
            return false;
        }

        if (activeParams != null && activeFinished != null && !activeFinished.get()) {
            jobFinished(params, false);
            return false;
        }

        final AtomicBoolean finished = new AtomicBoolean(false);
        activeParams = params;
        activeFinished = finished;
        boolean started = PowerRefreshManager.refresh(context, new PowerRefreshManager.Callback() {
            @Override
            public void onComplete(boolean success, String message) {
                finishOnce(params, finished, false);
            }
        });
        if (!started) finishOnce(params, finished, false);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (params == activeParams && activeFinished != null) {
            finishOnce(params, activeFinished, true);
        }
        return true;
    }

    private void finishOnce(JobParameters params, AtomicBoolean finished, boolean reschedule) {
        if (finished.compareAndSet(false, true)) {
            jobFinished(params, reschedule);
        }
    }
}