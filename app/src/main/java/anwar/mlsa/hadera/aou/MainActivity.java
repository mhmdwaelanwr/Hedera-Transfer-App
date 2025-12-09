package anwar.mlsa.hadera.aou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextInputEditText usernameEditText;
    private TextInputEditText passwordEditText;
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView welcomeMessage;

    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkListener;

    private static class VerificationResponse {
        boolean valid;
        String message;
        String error;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeAndSetupListeners();
        updateWelcomeMessage();
    }

    private void initializeAndSetupListeners() {
        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        usernameLayout = findViewById(R.id.username_layout);
        passwordLayout = findViewById(R.id.password_layout);
        loginButton = findViewById(R.id.login_button);
        progressBar = findViewById(R.id.progress_bar);
        TextView registerNow = findViewById(R.id.register_now);
        TextView mlsaEg = findViewById(R.id.mlsa_eg);
        welcomeMessage = findViewById(R.id.welcome_message);

        networkReq = new RequestNetwork(this);
        setupNetworkListener();

        loginButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            handleLogin();
        });
        registerNow.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            openUrl("https://portal.hedera.com/register");
        });
        mlsaEg.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            openUrl("https://mlsaegypt.org/");
        });

        usernameLayout.setEndIconOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null) {
                    usernameEditText.setText(item.getText());
                }
            }
        });

        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().trim().matches("^0\\.0\\.[0-9]+$")) {
                    usernameLayout.setError("Valid Account ID is required.");
                } else {
                    usernameLayout.setError(null);
                }
            }
        });
    }

    private void setupNetworkListener() {
        networkListener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if ("verify_tag".equals(tag)) {
                    setLoadingState(false);
                    handleVerificationResponse(response);
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                if ("verify_tag".equals(tag)) {
                    setLoadingState(false);
                    passwordLayout.setError("Network Error: " + message);
                }
            }
        };
    }
    
    private void updateWelcomeMessage() {
        if (WalletStorage.getAccounts(this).isEmpty()) {
            welcomeMessage.setText("Welcome!");
        } else {
            welcomeMessage.setText("Welcome Back");
        }
    }

    private void handleLogin() {
        String accountId = usernameEditText.getText().toString().trim();
        String privateKey = passwordEditText.getText().toString().trim();

        if (!validateInputs(accountId, privateKey)) {
            return;
        }

        setLoadingState(true);
        HashMap<String, Object> verificationBody = ApiConfig.getVerificationBody(accountId, privateKey);
        networkReq.setParams(verificationBody, RequestNetworkController.REQUEST_BODY);
        networkReq.startRequestNetwork(RequestNetworkController.POST, ApiConfig.BASE_URL + ApiConfig.VERIFY_ENDPOINT, "verify_tag", networkListener);
    }

    private boolean validateInputs(String accountId, String privateKey) {
        boolean isValid = true;
        if (accountId.isEmpty() || !accountId.matches("^0\\.0\\.[0-9]+$")) {
            usernameLayout.setError("Valid Account ID is required.");
            isValid = false;
        } else {
            usernameLayout.setError(null);
        }

        if (privateKey.isEmpty()) {
            passwordLayout.setError("Private Key is required.");
            isValid = false;
        } else {
            passwordLayout.setError(null);
        }
        return isValid;
    }

    private void handleVerificationResponse(String response) {
        try {
            VerificationResponse verificationResponse = new Gson().fromJson(response, VerificationResponse.class);
            if (verificationResponse != null && verificationResponse.valid) {
                String accountId = usernameEditText.getText().toString().trim();
                String privateKey = passwordEditText.getText().toString().trim();

                List<WalletStorage.Account> accounts = WalletStorage.getAccounts(this);
                int accountIndex = -1;
                for (int i = 0; i < accounts.size(); i++) {
                    if (accounts.get(i).getAccountId().equals(accountId)) {
                        accountIndex = i;
                        break;
                    }
                }

                if (accountIndex != -1) {
                    // Account exists, set it as current
                    WalletStorage.setCurrentAccountIndex(this, accountIndex);
                    startActivity(new Intent(this, TransferActivity.class));
                    finishAffinity();
                } else {
                    // Account doesn't exist, try to add it
                    if (WalletStorage.addAccount(this, accountId, privateKey)) {
                        // Set the new account as current
                        WalletStorage.setCurrentAccountIndex(this, WalletStorage.getAccounts(this).size() - 1);
                        startActivity(new Intent(this, TransferActivity.class));
                        finishAffinity();
                    } else {
                        // Failed to add account (max limit reached)
                        passwordLayout.setError("Cannot add more accounts. Maximum of 6 accounts reached.");
                    }
                }
            } else {
                String errorMessage = verificationResponse != null ? (verificationResponse.message != null ? verificationResponse.message : verificationResponse.error) : "Invalid credentials.";
                passwordLayout.setError(errorMessage != null ? errorMessage : "Invalid credentials.");
            }
        } catch (JsonSyntaxException e) {
            Log.e("MainActivity_JsonError", "Server sent invalid JSON.", e);
            passwordLayout.setError("Error: Could not understand server response.");
        } catch (Exception e) {
            Log.e("MainActivity_ResponseError", "Unexpected error during response handling.", e);
            passwordLayout.setError("An unexpected error occurred.");
        }
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            loginButton.setEnabled(true);
        }
    }
}
