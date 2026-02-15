package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.TransferTransaction;

import java.util.Locale;
import java.util.Map;

import anwar.mlsa.hadera.aou.domain.util.Result;
import anwar.mlsa.hadera.aou.hardware.HardwareWalletService;
import timber.log.Timber;

public class IdpayActivity extends AppCompatActivity implements HardwareWalletService.HardwareWalletListener {

    private TextInputEditText recipientIdEditText, amountEditText, memoEditText;
    private Button sendButton;
    private ProgressBar progressBar;
    private TextView balanceTextView, exchangeRateTextView, verifiedTextView;
    private TextInputLayout recipientLayout, amountLayout;

    private IdpayViewModel viewModel;
    private double currentBalance = 0.0;
    private double exchangeRate = 0.0;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private HardwareWalletService hardwareWalletService;
    private boolean isHardwareWalletBound = false;
    private boolean awaitingHwConnectionForTx = false;
    
    private TransferTransaction pendingHwTx;
    private String pendingHwPublicKeyHex;

    private final ServiceConnection hardwareWalletConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            hardwareWalletService = ((HardwareWalletService.LocalBinder) service).getService();
            isHardwareWalletBound = true;
            observeHardwareWalletStatus();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isHardwareWalletBound = false;
            hardwareWalletService = null;
        }
    };

    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String scannedId = result.getData().getStringExtra("SCANNED_ID");
                    if (scannedId != null) {
                        recipientIdEditText.setText(scannedId);
                        viewModel.verifyAccountId(scannedId);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> addressBookLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String selectedId = result.getData().getStringExtra("SELECTED_ACCOUNT_ID");
                    if (selectedId != null) {
                        recipientIdEditText.setText(selectedId);
                        viewModel.verifyAccountId(selectedId);
                    }
                }
            }
    );

    private void observeHardwareWalletStatus() {
        if (hardwareWalletService == null) return;
        hardwareWalletService.connectionStatus.observe(this, status -> {
            if (status == HardwareWalletService.ConnectionStatus.CONNECTED && awaitingHwConnectionForTx) {
                awaitingHwConnectionForTx = false;
                Toast.makeText(this, "Ledger connected. Ready to sign.", Toast.LENGTH_SHORT).show();
                setLoadingState(false);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.idpay);
        viewModel = new ViewModelProvider(this, new IdpayViewModelFactory(getApplication())).get(IdpayViewModel.class);
        initializeViews();
        setupListeners();
        observeViewModel();
        loadInitialData();
        setupBiometrics();
    }

    private void setupBiometrics() {
        biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                initiateTransaction();
            }
        });
        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Transfer")
                .setSubtitle("Authenticate to sign transaction")
                .setNegativeButtonText("Cancel").build();
    }

    private void initiateTransaction() {
        WalletStorage.Account currentAccount = WalletStorage.getCurrentAccount(this);
        if (currentAccount == null) return;

        if (currentAccount.isHardware) {
            if (!isHardwareWalletBound || hardwareWalletService == null) return;
            if (hardwareWalletService.connectionStatus.getValue() == HardwareWalletService.ConnectionStatus.CONNECTED) {
                handleHardwareWalletTransaction(currentAccount);
            } else {
                awaitingHwConnectionForTx = true;
                setLoadingState(true);
                Toast.makeText(this, "Connect your Ledger device.", Toast.LENGTH_LONG).show();
                hardwareWalletService.findAndConnectToDevice();
            }
        } else {
            viewModel.sendTransaction(safeGetText(recipientIdEditText), safeGetText(amountEditText), safeGetText(memoEditText).trim(), currentBalance);
        }
    }

    private void handleHardwareWalletTransaction(WalletStorage.Account account) {
        setLoadingState(true);
        pendingHwPublicKeyHex = account.getAccountId();
        pendingHwTx = viewModel.createUnsignedTransaction(
                account.getAccountId(),
                safeGetText(recipientIdEditText),
                safeGetText(amountEditText),
                safeGetText(memoEditText).trim()
        );

        if (pendingHwTx != null) {
            Toast.makeText(this, "Please approve on your Ledger.", Toast.LENGTH_LONG).show();
            hardwareWalletService.signTransaction(pendingHwTx.toBytes(), this);
        } else {
            setLoadingState(false);
            Toast.makeText(this, "Failed to build transaction.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSignatureReceived(byte[] signature) {
        if (pendingHwTx != null && pendingHwPublicKeyHex != null) {
            try {
                PublicKey publicKey = PublicKey.fromString(pendingHwPublicKeyHex);
                pendingHwTx.addSignature(publicKey, signature);
                broadcastTransaction(pendingHwTx);
            } catch (Exception e) {
                onSignatureError(e);
            }
        }
    }

    private void broadcastTransaction(TransferTransaction signedTx) {
        new Thread(() -> {
            try {
                Client client = Client.forTestnet();
                TransactionResponse response = signedTx.execute(client);
                com.hedera.hashgraph.sdk.TransactionReceipt receipt = response.getReceipt(client);
                
                runOnUiThread(() -> {
                    setLoadingState(false);
                    if (receipt.status.toString().equals("SUCCESS")) {
                        performHapticFeedback();
                        Map<String, Object> data = new java.util.HashMap<>();
                        data.put("transactionId", response.transactionId.toString());
                        data.put("hashscan", "https://hashscan.io/testnet/transaction/" + response.transactionId);
                        launchSuccessScreen(data);
                    } else {
                        Toast.makeText(this, "Failed: " + receipt.status, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                onSignatureError(e);
            }
        }).start();
    }

    @Override
    public void onSignatureError(Exception e) {
        runOnUiThread(() -> {
            setLoadingState(false);
            Timber.e(e, "Transaction failed");
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void initializeViews() {
        recipientIdEditText = findViewById(R.id.recipient_field);
        amountEditText = findViewById(R.id.amount_field);
        memoEditText = findViewById(R.id.memo_field);
        sendButton = findViewById(R.id.send_button);
        progressBar = findViewById(R.id.progressBar);
        balanceTextView = findViewById(R.id.balance_textview);
        exchangeRateTextView = findViewById(R.id.exchange_rate_text_view);
        recipientLayout = findViewById(R.id.recipient_input_layout);
        amountLayout = findViewById(R.id.amount_input_layout);
        verifiedTextView = findViewById(R.id.verified_text);
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        sendButton.setOnClickListener(v -> showConfirmationDialog());

        recipientLayout.setStartIconOnClickListener(v -> {
            Intent intent = new Intent(this, AddressBookActivity.class);
            addressBookLauncher.launch(intent);
        });

        recipientLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(this, ScannerqrActivity.class);
            qrScannerLauncher.launch(intent);
        });
        
        recipientIdEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                viewModel.onRecipientInputChanged(s.toString().trim(), safeGetText(amountEditText).trim(), currentBalance);
                verifiedTextView.setVisibility(View.GONE);
                recipientLayout.setVisibility(View.VISIBLE);
            }
        });

        amountEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                viewModel.onAmountInputChanged(s.toString().trim(), currentBalance);
            }
        });
    }

    private void observeViewModel() {
        viewModel.isLoading().observe(this, this::setLoadingState);
        viewModel.isSendButtonEnabled().observe(this, enabled -> sendButton.setEnabled(enabled));
        
        viewModel.getRecipientError().observe(this, error -> {
            recipientLayout.setError(error);
            if (error != null) {
                verifiedTextView.setVisibility(View.GONE);
                recipientLayout.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getVerifiedRecipient().observe(this, accountId -> {
            if (accountId != null && !accountId.isEmpty()) {
                verifiedTextView.setVisibility(View.VISIBLE);
                verifiedTextView.setText("To: " + accountId);
                recipientLayout.setVisibility(View.GONE);
            }
        });

        viewModel.getAmountError().observe(this, amountLayout::setError);

        viewModel.getTransactionResult().observe(this, result -> {
            if (result instanceof Result.Success) {
                performHapticFeedback();
                launchSuccessScreen(((Result.Success<Map<String, Object>>) result).data);
            } else if (result instanceof Result.Error) {
                Toast.makeText(this, "Error: " + ((Result.Error) result).message, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getExchangeRate().observe(this, rate -> {
            if (rate != null && !rate.equalsIgnoreCase("Error")) {
                try {
                    exchangeRate = Double.parseDouble(rate);
                    updateBalanceInUSD();
                } catch (Exception e) {
                    exchangeRateTextView.setText("Rate N/A");
                }
            }
        });
    }

    private void updateBalanceInUSD() {
        if (exchangeRate > 0) {
            double balanceInUSD = currentBalance * exchangeRate;
            exchangeRateTextView.setText(String.format(Locale.US, "$%,.2f USD", balanceInUSD));
        }
    }

    private void loadInitialData() {
        currentBalance = WalletStorage.getRawBalance(this);
        balanceTextView.setText(WalletStorage.getFormattedBalance(this));
        viewModel.fetchExchangeRate();
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!isLoading);
    }

    private void showConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Transfer")
                .setMessage("Are you sure you want to send " + safeGetText(amountEditText) + " HBAR?")
                .setPositiveButton("Send", (dialog, which) -> biometricPrompt.authenticate(promptInfo))
                .setNegativeButton("Cancel", null).show();
    }

    private void performHapticFeedback() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(50);
            }
        }
    }

    private void launchSuccessScreen(Map<String, Object> data) {
        Intent intent = new Intent(this, SentpayActivity.class);
        intent.putExtra("TRANSACTION_ID", String.valueOf(data.get("transactionId")));
        intent.putExtra("HASHSCAN_URL", String.valueOf(data.get("hashscan")));
        intent.putExtra("MEMO", safeGetText(memoEditText));
        startActivity(intent);
        finish();
    }

    private String safeGetText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString() : "";
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, HardwareWalletService.class), hardwareWalletConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isHardwareWalletBound) unbindService(hardwareWalletConnection);
    }
}
