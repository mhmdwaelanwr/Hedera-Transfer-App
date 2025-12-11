package anwar.mlsa.hadera.aou;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

import anwar.mlsa.hadera.aou.BuildConfig;
import timber.log.Timber;

public class MyApplication extends Application {

    private static final String TRANSACTION_WORKER_TAG = "transaction_check_worker";

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new ReleaseTree());
        }

        ThemeManager.applyTheme(this);
        setupRecurringWork();
    }

    private void setupRecurringWork() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest repeatingRequest = new PeriodicWorkRequest.Builder(
                TransactionNotificationWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                TRANSACTION_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                repeatingRequest);
    }

    private static class ReleaseTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
        }
    }
}
