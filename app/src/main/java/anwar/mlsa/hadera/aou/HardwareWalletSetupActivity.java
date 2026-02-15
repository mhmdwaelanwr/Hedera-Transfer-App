package anwar.mlsa.hadera.aou;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import anwar.mlsa.hadera.aou.hardware.HardwareWalletService;
import timber.log.Timber;

public class HardwareWalletSetupActivity extends AppCompatActivity implements HardwareWalletService.AccountInfoListener {

    private static final String TAG = "HWSetupActivity";
    private static final int MAX_ACCOUNTS_TO_SCAN = 5;

    private Button scanButton;
    private ProgressBar progressBar;
    private View instructionsView;
    private TextView instructionsText;
    private RecyclerView recyclerView;
    private HwAccountAdapter adapter;

    private HardwareWalletService hardwareWalletService;
    private boolean isBound = false;
    private int accountsFound = 0;
    private int accountsScanned = 0;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            HardwareWalletService.LocalBinder binder = (HardwareWalletService.LocalBinder) service;
            hardwareWalletService = binder.getService();
            isBound = true;
            observeHardwareWalletStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            hardwareWalletService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hardware_wallet_setup);
        setupToolbar();
        initializeViews();
        setupRecyclerView();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        scanButton = findViewById(R.id.scan_accounts_button);
        progressBar = findViewById(R.id.setup_progress_bar);
        instructionsView = findViewById(R.id.hw_instructions_view);
        instructionsText = findViewById(R.id.hw_instructions_text);
        recyclerView = findViewById(R.id.hw_accounts_recyclerview);

        scanButton.setOnClickListener(v -> {
            if (isBound) {
                adapter.clear();
                hardwareWalletService.findAndConnectToDevice();
            } else {
                Toast.makeText(this, "Service not connected yet.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HwAccountAdapter(new ArrayList<>(), this::importAccount);
        recyclerView.setAdapter(adapter);
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
                    scanButton.setEnabled(true);
                    instructionsView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    instructionsText.setText("Connect your hardware wallet and press 'Scan' to find your accounts.");
                    break;
                case SEARCHING:
                    progressBar.setVisibility(View.VISIBLE);
                    scanButton.setEnabled(false);
                    instructionsView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    instructionsText.setText("Connecting to device...");
                    break;
                case CONNECTED:
                    startAccountScan();
                    break;
                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    scanButton.setEnabled(true);
                    instructionsView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    instructionsText.setText("Connection failed. Please reconnect and try again.");
                    break;
            }
        });
    }

    private void startAccountScan() {
        instructionsText.setText("Scanning for accounts...");
        progressBar.setVisibility(View.VISIBLE);
        scanButton.setEnabled(false);
        instructionsView.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        accountsFound = 0;
        accountsScanned = 0;
        scanNextAccount();
    }

    private void scanNextAccount() {
        if (accountsScanned < MAX_ACCOUNTS_TO_SCAN) {
            hardwareWalletService.requestAccountInfo(accountsScanned, this);
        } else {
            onScanFinished();
        }
    }

    private void onScanFinished() {
        progressBar.setVisibility(View.GONE);
        scanButton.setEnabled(true);
        if (accountsFound == 0) {
            instructionsText.setText("No accounts found. Ensure the Hedera app is open on your device.");
            instructionsView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            instructionsView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAccountInfoReceived(int accountIndex, byte[] publicKey) {
        String publicKeyHex = bytesToHex(publicKey);
        Timber.d("Account %d public key found: %s", accountIndex, publicKeyHex);
        
        // In a real scenario, you would query Hedera Mirror Node to find the AccountID associated with this public key.
        // For now, we will use a placeholder or the Hex representation for display.
        String placeholderAccountId = "0.0.HIDDEN-" + accountIndex; 

        runOnUiThread(() -> {
            accountsFound++;
            adapter.addAccount(publicKeyHex); // Displaying public key hex for now
            accountsScanned++;
            scanNextAccount();
        });
    }

    @Override
    public void onAccountInfoError(int accountIndex, Exception e) {
        Timber.e(e, "Could not get account info for index %d", accountIndex);
        runOnUiThread(() -> {
            accountsScanned++;
            scanNextAccount();
        });
    }

    private void importAccount(String accountId) {
        if (WalletStorage.addHardwareAccount(this, accountId)) {
            int newAccountIndex = WalletStorage.getAccounts(this).size() - 1;
            WalletStorage.setCurrentAccountIndex(this, newAccountIndex);
            Toast.makeText(this, "Account imported successfully!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, TransferActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Could not import account. It may already exist.", Toast.LENGTH_LONG).show();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Adapter for the RecyclerView
    private static class HwAccountAdapter extends RecyclerView.Adapter<HwAccountAdapter.ViewHolder> {
        private final List<String> accountIds;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(String accountId);
        }

        HwAccountAdapter(List<String> accountIds, OnItemClickListener listener) {
            this.accountIds = accountIds;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.hw_account_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(accountIds.get(position), listener);
        }

        @Override
        public int getItemCount() {
            return accountIds.size();
        }

        void addAccount(String accountId) {
            accountIds.add(accountId);
            notifyDataSetChanged();
        }

        void clear() {
            accountIds.clear();
            notifyDataSetChanged();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView accountIdTextView;
            Button importButton;

            ViewHolder(View view) {
                super(view);
                accountIdTextView = view.findViewById(R.id.hw_account_id_textview);
                importButton = view.findViewById(R.id.import_hw_account_button);
            }

            void bind(final String accountId, final OnItemClickListener listener) {
                // Shorten public key for display
                String displayId = accountId.length() > 20 ? accountId.substring(0, 10) + "..." + accountId.substring(accountId.length() - 10) : accountId;
                accountIdTextView.setText(displayId);
                itemView.setOnClickListener(v -> listener.onItemClick(accountId));
                importButton.setOnClickListener(v -> listener.onItemClick(accountId));
            }
        }
    }
}
