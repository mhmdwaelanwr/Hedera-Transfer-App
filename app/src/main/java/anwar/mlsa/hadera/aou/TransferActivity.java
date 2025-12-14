package anwar.mlsa.hadera.aou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import anwar.mlsa.hadera.aou.databinding.TransferBinding;
import anwar.mlsa.hadera.aou.models.MarketData;
import anwar.mlsa.hadera.aou.models.NetworkAlert;
import anwar.mlsa.hadera.aou.models.PriceAlert;
import anwar.mlsa.hadera.aou.services.CurrencyPreferenceService;
import anwar.mlsa.hadera.aou.services.MarketDataParser;
import anwar.mlsa.hadera.aou.services.PriceAlertService;

public class TransferActivity extends AppCompatActivity {

    private TransferBinding binding;

    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkListener;

    private HistoryAdapter historyAdapter;
    private BlogAdapter blogAdapter;

    private double exchangeRate = 0.0;
    private MarketData currentMarketData;
    private NetworkAlert currentNetworkAlert;

    private static final String HEDERA_API_BASE_URL = "https://testnet.mirrornode.hedera.com";
    private static final String HISTORY_API_ENDPOINT = "/api/v1/transactions";
    private static final String BLOG_API_URL = "https://mlsaegypt.org/api/blog";
    private static final String COINGECKO_API_URL = "https://api.coingecko.com/api/v3/simple/price";
    private static final String HEDERA_HISTORY_TAG = "hedera_history_tag";
    private static final String BALANCE_TAG = "balance_tag";
    private static final String BLOG_TAG = "blog_tag";
    private static final String EXCHANGE_RATE_TAG = "exchange_rate_tag";
    private static final String MARKET_DATA_TAG = "market_data_tag";

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

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    switch (which) {
                        case 0:
                            selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
                            break;
                        case 1:
                            selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
                            break;
                        case 2:
                            selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                            break;
                    }
                    AppCompatDelegate.setDefaultNightMode(selectedTheme);
                    ThemeManager.saveTheme(this, selectedTheme);
                    dialog.dismiss();
                });

        AlertDialog dialog = builder.create();
        dialog.show();
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

        // Currency selector button
        binding.currencySelectorButton.setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            showCurrencySelector();
        });

        // Network status banner close button
        binding.networkStatusClose.setOnClickListener(v -> {
            binding.networkStatusBanner.setVisibility(View.GONE);
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
                if (binding.swipeRefreshLayout.isRefreshing()) {
                    binding.swipeRefreshLayout.setRefreshing(false);
                }
                if (BALANCE_TAG.equals(tag)) {
                    handleBalanceResponse(response);
                } else if (HEDERA_HISTORY_TAG.equals(tag)) {
                    handleHistoryApiResponse(response);
                } else if (BLOG_TAG.equals(tag)) {
                    ProgressBar blogProgressBar = findViewById(R.id.blog_progress_bar);
                    if (blogProgressBar != null) blogProgressBar.setVisibility(View.GONE);
                    ArrayList<Post> posts = BlogApiParser.parse(response);
                    if (blogAdapter != null) blogAdapter.updateData(posts);
                } else if (EXCHANGE_RATE_TAG.equals(tag)) {
                    handleExchangeRateResponse(response);
                } else if (MARKET_DATA_TAG.equals(tag)) {
                    handleMarketDataResponse(response);
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                if (binding.swipeRefreshLayout.isRefreshing()) {
                    binding.swipeRefreshLayout.setRefreshing(false);
                }
                if (BALANCE_TAG.equals(tag)) {
                    showErrorSnackbar("Failed to update balance. Check your connection.", () -> fetchBalance(WalletStorage.getAccountId(TransferActivity.this)));
                } else if (HEDERA_HISTORY_TAG.equals(tag)) {
                    Log.e("TransferHistory", "Failed to fetch history from Hedera: " + message);
                    showErrorSnackbar("Failed to load transaction history.", () -> loadRecentHistory());
                } else if (BLOG_TAG.equals(tag)) {
                    ProgressBar blogProgressBar = findViewById(R.id.blog_progress_bar);
                    if (blogProgressBar != null) blogProgressBar.setVisibility(View.GONE);
                    showErrorSnackbar("Failed to load blog posts.", () -> loadBlogPosts());
                } else if (EXCHANGE_RATE_TAG.equals(tag)) {
                    Log.e("TransferActivity", "Failed to fetch exchange rate: " + message);
                    binding.exchangeRateTextView.setText("Failed to load rate");
                } else if (MARKET_DATA_TAG.equals(tag)) {
                    Log.e("TransferActivity", "Failed to fetch market data: " + message);
                }
            }
        };
    }

    private void showErrorSnackbar(String message, Runnable retryAction) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> retryAction.run())
                .show();
    }

    private void updateUI() {
        String accountId = WalletStorage.getAccountId(this);
        if (accountId == null || accountId.isEmpty()) {
            binding.accountID.setText("N/A");
            binding.balanceTextView.setText("0 ");
            updateHistoryView(new ArrayList<>());
            if (binding.swipeRefreshLayout.isRefreshing()) {
                binding.swipeRefreshLayout.setRefreshing(false);
            }
        } else {
            binding.accountID.setText(accountId);
            binding.balanceTextView.setText(WalletStorage.getFormattedBalance(this));
            fetchBalance(accountId);
            fetchExchangeRate();
            fetchMarketData();
            loadRecentHistory();
        }
        updateBalanceCard();
    }

    private void updateBalanceCard() {
        double balance = WalletStorage.getRawBalance(this);
        if (balance == 0) {
            binding.balanceCard.setCardBackgroundColor(Color.RED);
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
        String url = HEDERA_API_BASE_URL + HISTORY_API_ENDPOINT + "?account.id=" + accountId + "&limit=25";
        networkReq.startRequestNetwork(RequestNetworkController.GET, url, HEDERA_HISTORY_TAG, networkListener);
    }

    private void loadBlogPosts() {
        if (binding.blogSectionStub.getParent() != null) {
            binding.blogSectionStub.inflate();
        }
        RecyclerView blogRecyclerView = findViewById(R.id.recyclerview1);
        ProgressBar blogProgressBar = findViewById(R.id.blog_progress_bar);
        if (blogRecyclerView.getAdapter() == null) {
            blogRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            blogAdapter = new BlogAdapter(new ArrayList<>());
            blogRecyclerView.setAdapter(blogAdapter);
        }
        blogProgressBar.setVisibility(View.VISIBLE);
        networkReq.startRequestNetwork(RequestNetworkController.GET, BLOG_API_URL, BLOG_TAG, networkListener);
    }

    private void handleBalanceResponse(String response) {
        try {
            Map<String, Object> map = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());
            if (map != null && map.containsKey("balance") && map.containsKey("hbars")) {
                WalletStorage.saveRawBalance(this, (Double) map.get("balance"));
                WalletStorage.saveFormattedBalance(this, (String) map.get("hbars"));
                binding.balanceTextView.setText((String) map.get("hbars"));
                updateBalanceCard();
                updateBalanceInUSD(); // Call this to update the USD balance
                loadBlogPosts();
            } else {
                Log.e("BalanceAPI", "API Error: Response does not contain expected keys.");
            }
        } catch (Exception e) {
            Log.e("BalanceAPI_CRASH", "Could not parse balance response", e);
        }
    }

    private void handleExchangeRateResponse(String response) {
        try {
            ExchangeRateResponse rateResponse = new Gson().fromJson(response, ExchangeRateResponse.class);
            if (rateResponse != null && rateResponse.current_rate != null) {
                int cents = rateResponse.current_rate.cent_equivalent;
                int hbars = rateResponse.current_rate.hbar_equivalent;
                if (hbars > 0) {
                    exchangeRate = (double) cents / hbars / 100; // Convert cents to dollars
                    updateBalanceInUSD();
                }
            }
        } catch (JsonSyntaxException e) {
            Log.e("ExchangeRateAPI_CRASH", "Could not parse exchange rate response", e);
            binding.exchangeRateTextView.setText("Invalid rate data");
        }
    }

    private void updateBalanceInUSD() {
        double balance = WalletStorage.getRawBalance(this);
        if (exchangeRate > 0) {
            double balanceInUSD = balance * exchangeRate;
            String formattedBalanceInUSD = String.format(Locale.US, "$%,.2f", balanceInUSD);
            binding.exchangeRateTextView.setText(formattedBalanceInUSD);
        }
    }

    private void handleHistoryApiResponse(String response) {
        HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(response, WalletStorage.getAccountId(this));
        ArrayList<Transaction> recentTransactions = new ArrayList<>();
        if (historyResponse != null && historyResponse.transactions != null) {
            recentTransactions.addAll(historyResponse.transactions.subList(0, Math.min(historyResponse.transactions.size(), 3)));
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

    private void fetchMarketData() {
        String currency = CurrencyPreferenceService.getSelectedCurrency(this);
        String url = COINGECKO_API_URL + "?ids=hedera-hashgraph&vs_currencies=" + currency + 
                     "&include_24hr_change=true";
        networkReq.startRequestNetwork(RequestNetworkController.GET, url, MARKET_DATA_TAG, networkListener);
    }

    private void handleMarketDataResponse(String response) {
        try {
            String currency = CurrencyPreferenceService.getSelectedCurrency(this);
            currentMarketData = MarketDataParser.parseCoinGeckoResponse(response, currency);
            
            if (currentMarketData != null) {
                updateMarketDataDisplay();
                evaluatePriceAlerts();
            }
        } catch (Exception e) {
            Log.e("TransferActivity", "Error handling market data response", e);
        }
    }

    private void updateMarketDataDisplay() {
        if (currentMarketData == null) return;

        runOnUiThread(() -> {
            String symbol = CurrencyPreferenceService.getCurrencySymbol(this);
            
            // Update exchange rate display
            String rateText = String.format(Locale.US, "1 ℏ = %s%.4f", symbol, currentMarketData.getPrice());
            binding.exchangeRateTextView.setText(rateText);
            
            // Update 24h price change
            String changeText = String.format(Locale.US, "%s%.2f%% (24h)", 
                currentMarketData.isPositiveChange() ? "+" : "", 
                currentMarketData.getChangePercent24h());
            binding.priceChange24hTextView.setText(changeText);
            
            // Set color based on positive/negative change
            int color = currentMarketData.isPositiveChange() ? 
                ContextCompat.getColor(this, android.R.color.holo_green_light) : 
                ContextCompat.getColor(this, android.R.color.holo_red_light);
            binding.priceChange24hTextView.setTextColor(color);
            
            // Calculate and update balance in fiat
            // The balance card now shows both HBAR and fiat equivalent
            double balance = WalletStorage.getRawBalance(this);
            double balanceInFiat = balance * currentMarketData.getPrice();
            String formattedBalanceInFiat = String.format(Locale.US, "≈ %s%,.2f", symbol, balanceInFiat);
            // Display this on the balance card subtitle or exchange rate text
            // For now, we show it as the rate, but could add a separate TextView
            String combinedText = rateText + " | " + formattedBalanceInFiat;
            binding.exchangeRateTextView.setText(combinedText);
        });
    }

    private void showCurrencySelector() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Currency");
        
        String currentCurrency = CurrencyPreferenceService.getSelectedCurrency(this);
        int currentIndex = -1;
        for (int i = 0; i < CurrencyPreferenceService.SUPPORTED_CURRENCIES.length; i++) {
            if (CurrencyPreferenceService.SUPPORTED_CURRENCIES[i].equals(currentCurrency)) {
                currentIndex = i;
                break;
            }
        }
        
        builder.setSingleChoiceItems(CurrencyPreferenceService.CURRENCY_NAMES, currentIndex, 
            (dialog, which) -> {
                String selectedCurrency = CurrencyPreferenceService.SUPPORTED_CURRENCIES[which];
                CurrencyPreferenceService.setSelectedCurrency(this, selectedCurrency);
                fetchMarketData();
                dialog.dismiss();
                Toast.makeText(this, "Currency changed to " + CurrencyPreferenceService.CURRENCY_NAMES[which], 
                    Toast.LENGTH_SHORT).show();
            });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void evaluatePriceAlerts() {
        if (currentMarketData == null) return;
        
        List<PriceAlert> triggeredAlerts = PriceAlertService.evaluateAlerts(
            this, 
            currentMarketData.getPrice(), 
            currentMarketData.getFiatCurrency()
        );
        
        for (PriceAlert alert : triggeredAlerts) {
            String message = String.format(Locale.US, 
                "Price Alert: HBAR is now %s %s%.2f", 
                alert.getDirection().equals("ABOVE") ? "above" : "below",
                CurrencyPreferenceService.getCurrencySymbol(this),
                alert.getTargetPrice()
            );
            
            anwar.mlsa.hadera.aou.NotificationManager.sendNotification(
                this, 
                "Price Alert Triggered", 
                message
            );
        }
    }

    private void showNetworkStatusBanner(NetworkAlert alert) {
        if (alert == null || !alert.isActive()) {
            binding.networkStatusBanner.setVisibility(View.GONE);
            return;
        }
        
        runOnUiThread(() -> {
            binding.networkStatusMessage.setText(alert.getMessage());
            
            // Set color based on alert level
            int backgroundColor;
            switch (alert.getLevel()) {
                case "CRITICAL":
                    backgroundColor = ContextCompat.getColor(this, android.R.color.holo_red_dark);
                    break;
                case "WARN":
                    backgroundColor = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
                    break;
                default:
                    backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary);
            }
            binding.networkStatusBanner.setCardBackgroundColor(backgroundColor);
            binding.networkStatusBanner.setVisibility(View.VISIBLE);
        });
    }
}
