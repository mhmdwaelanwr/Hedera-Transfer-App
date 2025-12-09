package anwar.mlsa.hadera.aou;

import android.app.Application;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class MyApplication extends Application {

    private static final String TRANSACTION_WORKER_TAG = "transaction_check_worker";

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyTheme(this);
        setupRecurringWork();
    }

    private void setupRecurringWork() {
        // Define constraints for the worker: it should only run when the network is connected.
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Create a periodic work request to run approximately every 15 minutes.
        PeriodicWorkRequest repeatingRequest = new PeriodicWorkRequest.Builder(
                TransactionNotificationWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        // Enqueue the work, keeping the existing work if it's already scheduled.
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                TRANSACTION_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                repeatingRequest);
    }
}
