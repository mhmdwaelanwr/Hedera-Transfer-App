package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class IdpayActivity extends AppCompatActivity {

    private TextInputEditText recipientIdEditText;
    private TextInputEditText amountEditText;
    private TextInputEditText memoEditText;
    private Button sendButton;
    private ProgressBar progressBar;
    private TextView balanceTextView;
    private TextInputLayout recipientLayout;
    private TextInputLayout amountLayout;
    private TextInputLayout memoLayout;
    private TextView verifiedTextView;

    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkReqListener;

    private double currentBalance = 0.0;

    private final ActivityResultLauncher<Intent> qrScannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.hasExtra("SCANNED_ID")) {
                        String scannedId = data.getStringExtra("SCANNED_ID");
                        recipientIdEditText.setText(scannedId);
                        verifyAccountId(scannedId);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.idpay);
        initializeAndSetupListeners();
    }

    private void initializeAndSetupListeners() {
        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Views
        recipientIdEditText = findViewById(R.id.recipient_field);
        amountEditText = findViewById(R.id.amount_field);
        memoEditText = findViewById(R.id.memo_field);
        sendButton = findViewById(R.id.send_button);
        progressBar = findViewById(R.id.progressBar);
        balanceTextView = findViewById(R.id.balance_textview);
        recipientLayout = findViewById(R.id.recipient_input_layout);
        amountLayout = findViewById(R.id.amount_input_layout);
        memoLayout = findViewById(R.id.memo_input_layout);
        verifiedTextView = findViewById(R.id.verified_text);

        // Network
        networkReq = new RequestNetwork(this);
        setupNetworkListener();

        // Initial State
        currentBalance = WalletStorage.getRawBalance(this);
        balanceTextView.setText(WalletStorage.getFormattedBalance(this));
        sendButton.setEnabled(false);

        // Listeners
        sendButton.setOnClickListener(v -> handleSendTransaction());
        recipientLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(IdpayActivity.this, ScannerqrActivity.class);
            qrScannerLauncher.launch(intent);
        });

        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateInputs();
            }
        };

        recipientIdEditText.addTextChangedListener(textWatcher);
        amountEditText.addTextChangedListener(textWatcher);
    }

    private void verifyAccountId(String accountId) {
        setLoadingState(true);
        RequestNetwork.RequestListener listener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                setLoadingState(false);
                try {
                    Map<String, Object> map = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {
                    }.getType());
                    if (map.containsKey("balance")) {
                        recipientLayout.setVisibility(View.GONE);
                        verifiedTextView.setVisibility(View.VISIBLE);
                    } else {
                        Toast.makeText(IdpayActivity.this, "Invalid QR Code", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(IdpayActivity.this, "Corrupt QR Code", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                setLoadingState(false);
                Toast.makeText(IdpayActivity.this, "Invalid QR Code", Toast.LENGTH_SHORT).show();
            }
        };

        networkReq.startRequestNetwork(RequestNetworkController.GET, ApiConfig.BASE_URL + "/api/v1/balances?account.id=" + accountId, "verify_id", listener);
    }

    private void validateInputs() {
        String recipientId = recipientIdEditText.getText().toString().trim();
        String amountStr = amountEditText.getText().toString().trim();

        boolean isRecipientValid = !recipientId.isEmpty() && recipientId.matches("^0\\.0\\.[0-9]+$");
        boolean isAmountValid = false;

        if (isRecipientValid) {
            recipientLayout.setError(null);
        } else {
            if (!recipientId.isEmpty()) recipientLayout.setError("Valid Account ID is required.");
        }

        if (!amountStr.isEmpty()) {
            try {
                double amount = Double.parseDouble(amountStr);
                if (amount > currentBalance) {
                    amountLayout.setError("Amount exceeds balance.");
                } else if (amount <= 0) {
                    amountLayout.setError("Amount must be positive.");
                } else {
                    amountLayout.setError(null);
                    isAmountValid = true;
                }
            } catch (NumberFormatException e) {
                amountLayout.setError("Invalid amount format.");
            }
        } else {
            if (amountLayout.getError() != null) amountLayout.setError(null);
        }

        memoLayout.setError(null);

        sendButton.setEnabled(isRecipientValid && isAmountValid);
    }

    private void setupNetworkListener() {
        networkReqListener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if ("transaction_tag".equals(tag)) {
                    setLoadingState(false);
                    handleTransactionResponse(response);
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                if ("transaction_tag".equals(tag)) {
                    setLoadingState(false);
                    amountLayout.setError("Network Error: " + message);
                }
            }
        };
    }

    private void handleSendTransaction() {
        setLoadingState(true);

        String receiverAccountId = recipientIdEditText.getText().toString().trim();
        double amount = Double.parseDouble(amountEditText.getText().toString().trim());
        String memo = memoEditText.getText().toString().trim();

        String senderAccountId = WalletStorage.getAccountId(this);
        String senderPrivateKey = WalletStorage.getPrivateKey(this);

        HashMap<String, Object> transactionBody = ApiConfig.getTransactionBody(senderAccountId, senderPrivateKey, amount, receiverAccountId, memo);
        networkReq.setParams(transactionBody, RequestNetworkController.REQUEST_BODY);
        networkReq.startRequestNetwork(RequestNetworkController.POST, ApiConfig.BASE_URL + ApiConfig.TRANSACTION_ENDPOINT, "transaction_tag", networkReqListener);
    }

    private void handleTransactionResponse(String response) {
        try {
            Map<String, Object> map = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {
            }.getType());
            if ("Transfer Success".equals(map.get("status"))) {
                saveTransactionToHistory();
                Intent intent = new Intent(this, SentpayActivity.class);
                intent.putExtra("TRANSACTION_ID", String.valueOf(map.get("transactionId")));
                intent.putExtra("HASHSCAN_URL", String.valueOf(map.get("hashscan")));
                intent.putExtra("MEMO", memoEditText.getText().toString());
                startActivity(intent);
                finish();
            } else {
                String errorMsg = map.get("error") != null ? map.get("error").toString() : "Unknown error";
                amountLayout.setError(errorMsg);
            }
        } catch (Exception e) {
            Log.e("IdpayActivity", "Could not parse transaction response", e);
            amountLayout.setError("An unexpected error occurred.");
        }
    }

    private void saveTransactionToHistory() {
        String amount = amountEditText.getText().toString();
        String receiverId = recipientIdEditText.getText().toString();
        String memo = memoEditText.getText().toString();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        TransferActivity.Transaction transaction = new TransferActivity.Transaction();
        transaction.type = "Sent";
        transaction.amount = "-" + amount + " ℏ";
        transaction.party = receiverId;
        transaction.date = currentDate;
        transaction.status = "Completed";
        transaction.memo = memo;

        WalletStorage.saveTransaction(this, transaction);
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!isLoading);
        recipientIdEditText.setEnabled(!isLoading);
        amountEditText.setEnabled(!isLoading);
        memoEditText.setEnabled(!isLoading);
    }
}
