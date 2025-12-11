package anwar.mlsa.hadera.aou;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

public class TransactionNotificationWorker extends Worker {

    private static final String CHANNEL_ID = "hadera_channel";
    private static final String LAST_TRANSACTION_TIMESTAMP_KEY = "last_transaction_timestamp";

    public TransactionNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String accountId = WalletStorage.getAccountId(getApplicationContext());
        if (accountId == null || accountId.isEmpty()) {
            Timber.d("No account ID found, skipping work.");
            return Result.success();
        }

        OkHttpClient client = new OkHttpClient();
        String url = "https://testnet.mirrornode.hedera.com/api/v1/transactions?account.id=" + accountId + "&limit=1";
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Timber.e("API call failed with code: %d", response.code());
                return Result.retry();
            }

            String responseBody = response.body().string();
            HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(responseBody, accountId);

            if (historyResponse != null && !historyResponse.transactions.isEmpty()) {
                Transaction latestTransaction = historyResponse.transactions.get(0);
                String lastNotifiedTimestamp = getLastNotifiedTimestamp();

                if (lastNotifiedTimestamp == null || latestTransaction.date.compareTo(lastNotifiedTimestamp) > 0) {
                    if ("Received".equals(latestTransaction.type)) {
                        Timber.i("New transaction received, sending notification.");
                        sendNotification("Transaction Received", "You received " + latestTransaction.amount);
                        saveLastNotifiedTimestamp(latestTransaction.date);
                    }
                }
            }
        } catch (IOException e) {
            Timber.e(e, "IOException during API call");
            return Result.retry();
        }

        return Result.success();
    }

    private void sendNotification(String title, String messageBody) {
        Intent intent = new Intent(getApplicationContext(), TransferActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Hadera Channel", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0, notificationBuilder.build());
    }

    private void saveLastNotifiedTimestamp(String timestamp) {
        getApplicationContext().getSharedPreferences("worker_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(LAST_TRANSACTION_TIMESTAMP_KEY, timestamp)
                .apply();
    }

    private String getLastNotifiedTimestamp() {
        return getApplicationContext().getSharedPreferences("worker_prefs", Context.MODE_PRIVATE)
                .getString(LAST_TRANSACTION_TIMESTAMP_KEY, null);
    }
}
