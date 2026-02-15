package anwar.mlsa.hadera.aou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import anwar.mlsa.hadera.aou.databinding.TransferBinding;
import timber.log.Timber;

public class TransferActivity extends AppCompatActivity {

    private TransferBinding binding;
    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkListener;
    private HistoryAdapter historyAdapter;
    private BlogAdapter blogAdapter;
    private double exchangeRate = 0.0;

    private static final String HEDERA_API_BASE_URL = "https://testnet.mirrornode.hedera.com";
    private static final String HISTORY_API_ENDPOINT = "/api/v1/transactions";
    private static final String BLOG_API_URL = "https://mlsaegypt.org/api/blog";
    private static final String HEDERA_HISTORY_TAG = "hedera_history_tag";
    private static final String BALANCE_TAG = "balance_tag";
    private static final String BLOG_TAG = "blog_tag";
    private static final String EXCHANGE_RATE_TAG = "exchange_rate_tag";

    private static class ExchangeRateResponse {
        Rate current_rate;
    }

    private static class Rate {
        int cent_equivalent;
        int hbar_equivalent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = TransferBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initialize();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        VibrationManager.vibrate(this);
        int itemId = item.getItemId();
        if (itemId == R.id.action_toggle_theme) {
            showThemeDialog();
            return true;
        } else if (itemId == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RequestNetworkController.getInstance().cancelAllRequests(this);
    }

    private void showThemeDialog() {
        String[] themes = {"Light", "Dark", "System Default"};
        SharedPreferences prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE);
        int currentTheme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        int checkedItem = currentTheme == AppCompatDelegate.MODE_NIGHT_NO ? 0 :
                currentTheme == AppCompatDelegate.MODE_NIGHT_YES ? 1 : 2;

        new AlertDialog.Builder(this)
                .setTitle("Choose Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    switch (which) {
                        case 0: selectedTheme = AppCompatDelegate.MODE_NIGHT_NO; break;
                        case 1: selectedTheme = AppCompatDelegate.MODE_NIGHT_YES; break;
                        case 2: selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
                    }
                    AppCompatDelegate.setDefaultNightMode(selectedTheme);
                    ThemeManager.saveTheme(this, selectedTheme);
                    dialog.dismiss();
                }).show();
    }

    private void initialize() {
        setSupportActionBar(binding.toolbar);
        binding.swipeRefreshLayout.setOnRefreshListener(this::updateUI);

        binding.sendButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, IdpayActivity.class));
        });
        
        binding.receiveButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, ReceiveQrActivity.class));
        });
        
        binding.seeAllButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, HistoryActivity.class));
        });

        binding.copyAccountIdButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            String accountId = binding.accountID.getText().toString();
            if (!"N/A".equals(accountId)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Account ID", accountId));
                    Toast.makeText(this, "Account ID copied!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        networkReq = new RequestNetwork(this);
        setupNetworkListener();

        binding.recyclerview2.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter();
        binding.recyclerview2.setAdapter(historyAdapter);
    }

    private void setupNetworkListener() {
        networkListener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if (binding.swipeRefreshLayout.isRefreshing()) binding.swipeRefreshLayout.setRefreshing(false);
                
                switch (tag) {
                    case BALANCE_TAG: handleBalanceResponse(response); break;
                    case HEDERA_HISTORY_TAG: handleHistoryApiResponse(response); break;
                    case BLOG_TAG:
                        ProgressBar blogProgressBar = findViewById(R.id.blog_progress_bar);
                        if (blogProgressBar != null) blogProgressBar.setVisibility(View.GONE);
                        ArrayList<Post> posts = BlogApiParser.parse(response);
                        if (blogAdapter != null) blogAdapter.updateData(posts);
                        break;
                    case EXCHANGE_RATE_TAG: handleExchangeRateResponse(response); break;
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                if (binding.swipeRefreshLayout.isRefreshing()) binding.swipeRefreshLayout.setRefreshing(false);
                
                switch (tag) {
                    case BALANCE_TAG:
                        showErrorSnackbar("Failed to update balance.", () -> fetchBalance(WalletStorage.getAccountId(TransferActivity.this)));
                        break;
                    case HEDERA_HISTORY_TAG:
                        Timber.e("Failed to fetch history: %s", message);
                        showErrorSnackbar("Failed to load history.", this::loadRecentHistory);
                        break;
                    case BLOG_TAG:
                        ProgressBar blogProgressBar = findViewById(R.id.blog_progress_bar);
                        if (blogProgressBar != null) blogProgressBar.setVisibility(View.GONE);
                        showErrorSnackbar("Failed to load blog.", this::loadBlogPosts);
                        break;
                    case EXCHANGE_RATE_TAG:
                        Timber.e("Failed to fetch exchange rate: %s", message);
                        binding.exchangeRateTextView.setText("Rate N/A");
                        break;
                }
            }

            private void loadRecentHistory() { TransferActivity.this.loadRecentHistory(); }
            private void loadBlogPosts() { TransferActivity.this.loadBlogPosts(); }
        };
    }

    private void showErrorSnackbar(String message, Runnable retryAction) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> retryAction.run()).show();
    }

    private void updateUI() {
        String accountId = WalletStorage.getAccountId(this);
        if (accountId == null || accountId.isEmpty()) {
            binding.accountID.setText("N/A");
            binding.balanceTextView.setText("0 HBAR");
            updateHistoryView(new ArrayList<>());
            if (binding.swipeRefreshLayout.isRefreshing()) binding.swipeRefreshLayout.setRefreshing(false);
        } else {
            binding.accountID.setText(accountId);
            binding.balanceTextView.setText(WalletStorage.getFormattedBalance(this));
            fetchBalance(accountId);
            fetchExchangeRate();
            loadRecentHistory();
        }
        updateBalanceCard();
    }

    private void updateBalanceCard() {
        double balance = WalletStorage.getRawBalance(this);
        if (balance <= 0) {
            binding.balanceCard.setCardBackgroundColor(Color.parseColor("#FFEBEE")); // Light red
            binding.sendButton.setEnabled(false);
        } else {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
            binding.balanceCard.setCardBackgroundColor(typedValue.data);
            binding.sendButton.setEnabled(true);
        }
    }

    private void fetchBalance(String accountId) {
        networkReq.startRequestNetwork(RequestNetworkController.GET, ApiConfig.getBalanceUrl(accountId), BALANCE_TAG, networkListener);
    }

    private void fetchExchangeRate() {
        networkReq.startRequestNetwork(RequestNetworkController.GET, ApiConfig.EXCHANGE_RATE_URL, EXCHANGE_RATE_TAG, networkListener);
    }

    private void loadRecentHistory() {
        String accountId = WalletStorage.getAccountId(this);
        if (accountId == null || accountId.isEmpty()) {
            updateHistoryView(new ArrayList<>());
            return;
        }
        String url = HEDERA_API_BASE_URL + HISTORY_API_ENDPOINT + "?account.id=" + accountId + "&limit=5";
        networkReq.startRequestNetwork(RequestNetworkController.GET, url, HEDERA_HISTORY_TAG, networkListener);
    }

    private void loadBlogPosts() {
        if (binding.blogSectionStub.getParent() != null) {
            binding.blogSectionStub.inflate();
        }
        RecyclerView blogRecyclerView = findViewById(R.id.recyclerview1);
        ProgressBar blogProgressBar = findViewById(R.id.blog_progress_bar);
        if (blogRecyclerView != null && blogRecyclerView.getAdapter() == null) {
            blogRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            blogAdapter = new BlogAdapter(new ArrayList<>());
            blogRecyclerView.setAdapter(blogAdapter);
        }
        if (blogProgressBar != null) blogProgressBar.setVisibility(View.VISIBLE);
        networkReq.startRequestNetwork(RequestNetworkController.GET, BLOG_API_URL, BLOG_TAG, networkListener);
    }

    private void handleBalanceResponse(String response) {
        try {
            Map<String, Object> map = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());
            if (map != null && map.containsKey("balance") && map.containsKey("hbars")) {
                Object balanceObj = map.get("balance");
                double balance = (balanceObj instanceof Double) ? (Double) balanceObj : Double.parseDouble(String.valueOf(balanceObj));
                WalletStorage.saveRawBalance(this, balance);
                WalletStorage.saveFormattedBalance(this, String.valueOf(map.get("hbars")));
                binding.balanceTextView.setText(String.valueOf(map.get("hbars")));
                updateBalanceCard();
                updateBalanceInUSD();
                loadBlogPosts();
            }
        } catch (Exception e) {
            Timber.e(e, "Could not parse balance response");
        }
    }

    private void handleExchangeRateResponse(String response) {
        try {
            ExchangeRateResponse rateResponse = new Gson().fromJson(response, ExchangeRateResponse.class);
            if (rateResponse != null && rateResponse.current_rate != null) {
                int cents = rateResponse.current_rate.cent_equivalent;
                int hbars = rateResponse.current_rate.hbar_equivalent;
                if (hbars > 0) {
                    exchangeRate = (double) cents / hbars / 100;
                    updateBalanceInUSD();
                }
            }
        } catch (JsonSyntaxException e) {
            Timber.e(e, "Could not parse exchange rate response");
            binding.exchangeRateTextView.setText("Rate Error");
        }
    }

    private void updateBalanceInUSD() {
        double balance = WalletStorage.getRawBalance(this);
        if (exchangeRate > 0) {
            double balanceInUSD = balance * exchangeRate;
            String formattedBalanceInUSD = String.format(Locale.US, "$%,.2f USD", balanceInUSD);
            binding.exchangeRateTextView.setText(formattedBalanceInUSD);
        }
    }

    private void handleHistoryApiResponse(String response) {
        HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(response, WalletStorage.getAccountId(this));
        ArrayList<Transaction> recentTransactions = new ArrayList<>();
        if (historyResponse != null && historyResponse.transactions != null) {
            recentTransactions.addAll(historyResponse.transactions.subList(0, Math.min(historyResponse.transactions.size(), 5)));
        }
        updateHistoryView(recentTransactions);
    }

    private void updateHistoryView(ArrayList<Transaction> transactions) {
        runOnUiThread(() -> {
            if (transactions == null || transactions.isEmpty()) {
                binding.emptyHistoryMessage.setVisibility(View.VISIBLE);
                binding.recyclerview2.setVisibility(View.GONE);
            } else {
                binding.emptyHistoryMessage.setVisibility(View.GONE);
                binding.recyclerview2.setVisibility(View.VISIBLE);
            }
            historyAdapter.submitList(transactions);
        });
    }
}
