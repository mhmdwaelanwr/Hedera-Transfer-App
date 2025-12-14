package anwar.mlsa.hadera.aou;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import anwar.mlsa.hadera.aou.hardware.HardwareWalletService;

public class HardwareWalletSetupActivity extends AppCompatActivity implements HardwareWalletService.AccountInfoListener {

    private static final String TAG = "HWSetupActivity";

    private Button findDeviceButton;
    private ProgressBar progressBar;
    private TextView instructionsText;

    private HardwareWalletService hardwareWalletService;
    private boolean isBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            HardwareWalletService.LocalBinder binder = (HardwareWalletService.LocalBinder) service;
            hardwareWalletService = binder.getService();
            isBound = true;
            Log.d(TAG, "HardwareWalletService connected");
            observeHardwareWalletStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            hardwareWalletService = null;
            Log.d(TAG, "HardwareWalletService disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware_wallet_setup);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findDeviceButton = findViewById(R.id.find_device_button);
        progressBar = findViewById(R.id.setup_progress_bar);
        instructionsText = findViewById(R.id.instructions_text);

        findDeviceButton.setOnClickListener(v -> {
            if (isBound) {
                hardwareWalletService.findAndConnectToDevice();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, HardwareWalletService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void observeHardwareWalletStatus() {
        if (!isBound) return;
        hardwareWalletService.connectionStatus.observe(this, status -> {
            switch (status) {
                case DISCONNECTED:
                    progressBar.setVisibility(View.GONE);
                    findDeviceButton.setEnabled(true);
                    instructionsText.setText("Please connect your Ledger device and open the Hedera app.");
                    break;
                case SEARCHING:
                    progressBar.setVisibility(View.VISIBLE);
                    findDeviceButton.setEnabled(false);
                    instructionsText.setText("Searching for device...");
                    break;
                case CONNECTED:
                    progressBar.setVisibility(View.VISIBLE); // Keep it visible
                    findDeviceButton.setEnabled(false);
                    instructionsText.setText("Device connected! Now getting your account ID...");
                    hardwareWalletService.requestAccountInfo(this);
                    break;
                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    findDeviceButton.setEnabled(true);
                    instructionsText.setText("Connection failed. Please reconnect your device and try again.");
                    Toast.makeText(this, "Could not connect to the device.", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    @Override
    public void onAccountInfoReceived(String accountId) {
        Log.d(TAG, "Account ID received: " + accountId);
        runOnUiThread(() -> {
            if (WalletStorage.addHardwareAccount(this, accountId)) {
                // Set the new account as current
                int newAccountIndex = WalletStorage.getAccounts(this).size() - 1;
                WalletStorage.setCurrentAccountIndex(this, newAccountIndex);
                
                Toast.makeText(this, "Wallet connected successfully!", Toast.LENGTH_SHORT).show();
                
                Intent intent = new Intent(this, TransferActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Could not add hardware wallet. It might already exist or storage is full.", Toast.LENGTH_LONG).show();
                progressBar.setVisibility(View.GONE);
                findDeviceButton.setEnabled(true);
                instructionsText.setText("An error occurred. Please try again.");
            }
        });
    }

    @Override
    public void onAccountInfoError(Exception e) {
        Log.e(TAG, "Failed to get account info", e);
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            findDeviceButton.setEnabled(true);
            instructionsText.setText("Failed to get account info. Please ensure the Hedera app is open on your device and try again.");
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
