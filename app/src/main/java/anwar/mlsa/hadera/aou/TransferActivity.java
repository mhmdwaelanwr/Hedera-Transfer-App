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
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TransferActivity extends AppCompatActivity {

    private TextView balanceTextView;
    private TextView accountIDTextView;
    private RecyclerView historyRecyclerView;
    private TextView emptyHistoryMessage;
    private RecyclerView blogRecyclerView;
    private MaterialCardView balanceCard;
    private Button sendButton;
    private ProgressBar blogProgressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkListener;

    private HistoryAdapter historyAdapter;
    private BlogAdapter blogAdapter;

    private static final String HEDERA_API_BASE_URL = "https://testnet.mirrornode.hedera.com";
    private static final String HISTORY_API_ENDPOINT = "/api/v1/transactions";
    private static final String BLOG_API_URL = "https://mlsaegypt.org/api/blog";
    private static final String HEDERA_HISTORY_TAG = "hedera_history_tag";
    private static final String BALANCE_TAG = "balance_tag";
    private static final String BLOG_TAG = "blog_tag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transfer);
        initialize();
        loadBlogPosts();
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

    private void showThemeDialog() {
        String[] themes = {"Light", "Dark", "System Default"};
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        
        int checkedItem = -1;
        // Find which item to check based on current theme
        SharedPreferences prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE);
        int currentTheme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            checkedItem = 0;
        } else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            checkedItem = 1;
        } else {
            checkedItem = 2;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Theme")
                .setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
                    int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    switch (which) {
                        case 0: // Light
                            selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
                            break;
                        case 1: // Dark
                            selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
                            break;
                        case 2: // System Default
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
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        balanceTextView = findViewById(R.id.balanceTextView);
        accountIDTextView = findViewById(R.id.accountID);
        historyRecyclerView = findViewById(R.id.recyclerview2);
        emptyHistoryMessage = findViewById(R.id.emptyHistoryMessage);
        blogRecyclerView = findViewById(R.id.recyclerview1);
        balanceCard = findViewById(R.id.balance_card);
        sendButton = findViewById(R.id.send_button);
        blogProgressBar = findViewById(R.id.blog_progress_bar);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);

        swipeRefreshLayout.setOnRefreshListener(this::updateUI);

        findViewById(R.id.send_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, IdpayActivity.class));
        });
        findViewById(R.id.receive_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, ReceiveQrActivity.class));
        });
        findViewById(R.id.see_all_button).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            startActivity(new Intent(this, HistoryActivity.class));
        });

        findViewById(R.id.walletid).setOnClickListener(v -> {
            VibrationManager.vibrate(this);
            String accountId = accountIDTextView.getText().toString();
            if (!"N/A".equals(accountId)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Account ID", accountId));
                Toast.makeText(this, "Account ID copied!", Toast.LENGTH_SHORT).show();
            }
        });

        networkReq = new RequestNetwork(this);
        setupNetworkListener();

        blogRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        blogAdapter = new BlogAdapter(new ArrayList<>());
        blogRecyclerView.setAdapter(blogAdapter);

        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter(new ArrayList<>());
        historyRecyclerView.setAdapter(historyAdapter);
    }

    private void setupNetworkListener() {
        networkListener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                if (BALANCE_TAG.equals(tag)) {
                    handleBalanceResponse(response);
                } else if (HEDERA_HISTORY_TAG.equals(tag)) {
                    handleHistoryApiResponse(response);
                } else if (BLOG_TAG.equals(tag)) {
                    blogProgressBar.setVisibility(View.GONE);
                    ArrayList<Post> posts = BlogApiParser.parse(response);
                    blogAdapter.updateData(posts);
                }
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                if (BALANCE_TAG.equals(tag)) {
                    balanceTextView.setText("Error");
                } else if (HEDERA_HISTORY_TAG.equals(tag)) {
                    Log.e("TransferHistory", "Failed to fetch history from Hedera: " + message);
                    handleHistoryApiResponse(null);
                } else if (BLOG_TAG.equals(tag)) {
                    blogProgressBar.setVisibility(View.GONE);
                    Log.e("BlogAPI", "Failed to fetch blog posts: " + message);
                }
            }
        };
    }

    private void updateUI() {
        String accountId = WalletStorage.getAccountId(this);
        if (accountId == null || accountId.isEmpty()) {
            accountIDTextView.setText("N/A");
            balanceTextView.setText("0 ℏ");
            updateHistoryView(new ArrayList<>());
            if (swipeRefreshLayout.isRefreshing()) { // Stop animation if not logged in
                swipeRefreshLayout.setRefreshing(false);
            }
        } else {
            accountIDTextView.setText(accountId);
            balanceTextView.setText(WalletStorage.getFormattedBalance(this));
            fetchBalance(accountId);
            loadRecentHistory();
        }
        updateBalanceCard();
    }

    private void updateBalanceCard() {
        double balance = WalletStorage.getRawBalance(this);
        if (balance == 0) {
            balanceCard.setCardBackgroundColor(Color.RED);
            sendButton.setEnabled(false);
        } else {
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
            balanceCard.setCardBackgroundColor(typedValue.data);
            sendButton.setEnabled(true);
        }
    }

    private void fetchBalance(String accountId) {
        networkReq.startRequestNetwork(RequestNetworkController.GET, ApiConfig.getBalanceUrl(accountId), BALANCE_TAG, networkListener);
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
        blogProgressBar.setVisibility(View.VISIBLE);
        networkReq.startRequestNetwork(RequestNetworkController.GET, BLOG_API_URL, BLOG_TAG, networkListener);
    }

    private void handleBalanceResponse(String response) {
        try {
            Map<String, Object> map = new Gson().fromJson(response, new TypeToken<HashMap<String, Object>>() {}.getType());
            if (map != null && map.containsKey("balance") && map.containsKey("hbars")) {
                WalletStorage.saveRawBalance(this, (Double) map.get("balance"));
                WalletStorage.saveFormattedBalance(this, (String) map.get("hbars"));
                balanceTextView.setText((String) map.get("hbars"));
                updateBalanceCard();
            } else {
                Log.e("BalanceAPI", "API Error: Response does not contain expected keys.");
            }
        } catch (Exception e) {
            Log.e("BalanceAPI_CRASH", "Could not parse balance response", e);
        }
    }

    private void handleHistoryApiResponse(String response) {
        HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(response, WalletStorage.getAccountId(this));
        ArrayList<Transaction> recentTransactions = new ArrayList<>();
        if (historyResponse != null && historyResponse.transactions != null) {
            for (int i = 0; i < historyResponse.transactions.size() && i < 3; i++) {
                recentTransactions.add(historyResponse.transactions.get(i));
            }
        }
        updateHistoryView(recentTransactions);
    }

    private void updateHistoryView(ArrayList<Transaction> transactions) {
        runOnUiThread(() -> {
            if (transactions == null || transactions.isEmpty()) {
                emptyHistoryMessage.setVisibility(View.VISIBLE);
                historyRecyclerView.setVisibility(View.GONE);
            } else {
                emptyHistoryMessage.setVisibility(View.GONE);
                historyRecyclerView.setVisibility(View.VISIBLE);
            }
            historyAdapter.updateData(transactions);
        });
    }

    static class Transaction implements Parcelable {
        String type, date, amount, party, status, fee, memo;
        boolean isCredit;

        public Transaction() {}

        protected Transaction(Parcel in) {
            type = in.readString();
            date = in.readString();
            amount = in.readString();
            party = in.readString();
            status = in.readString();
            fee = in.readString();
            memo = in.readString();
            isCredit = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(type);
            dest.writeString(date);
            dest.writeString(amount);
            dest.writeString(party);
            dest.writeString(status);
            dest.writeString(fee);
            dest.writeString(memo);
            dest.writeByte((byte) (isCredit ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<Transaction> CREATOR = new Creator<Transaction>() {
            @Override
            public Transaction createFromParcel(Parcel in) {
                return new Transaction(in);
            }

            @Override
            public Transaction[] newArray(int size) {
                return new Transaction[size];
            }
        };

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Transaction that = (Transaction) o;
            return isCredit == that.isCredit &&
                    Objects.equals(date, that.date) &&
                    Objects.equals(amount, that.amount) &&
                    Objects.equals(party, that.party) &&
                    Objects.equals(memo, that.memo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(date, amount, party, memo, isCredit);
        }
    }

    static class Post {
        String id, title, excerpt, image, author, authorImage, date;
        int views;
    }

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final ArrayList<Transaction> data;

        public HistoryAdapter(ArrayList<Transaction> data) {
            this.data = data;
        }

        public void updateData(ArrayList<Transaction> newData) {
            data.clear();
            data.addAll(newData);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_history_home, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView transactionType, date, amount, party, status, fee;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                transactionType = itemView.findViewById(R.id.textview1);
                date = itemView.findViewById(R.id.textview2);
                amount = itemView.findViewById(R.id.textview3);
                party = itemView.findViewById(R.id.textview4);
                status = itemView.findViewById(R.id.textview5);
                fee = itemView.findViewById(R.id.fee);
            }

            void bind(Transaction transaction) {
                if (transaction == null) return;
                transactionType.setText(transaction.type);
                date.setText(transaction.date);
                amount.setText(transaction.amount);
                party.setText(transaction.party);
                status.setText(transaction.status);

                if (transaction.fee != null && !transaction.fee.isEmpty()) {
                    fee.setText(transaction.fee);
                    fee.setVisibility(View.VISIBLE);
                } else {
                    fee.setVisibility(View.GONE);
                }

                if ("Sent".equalsIgnoreCase(transaction.type)) {
                    amount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorSent));
                } else {
                    amount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorReceived));
                } 
                if ("SUCCESS".equalsIgnoreCase(transaction.status)) {
                    status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorReceived));
                } else {
                    status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorSent));
                }

                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), TransactionDetailsActivity.class);
                    intent.putExtra("transaction", transaction);
                    itemView.getContext().startActivity(intent);
                });
            }
        }
    }

    static class BlogAdapter extends RecyclerView.Adapter<BlogAdapter.ViewHolder> {
        private final ArrayList<Post> data;

        public BlogAdapter(ArrayList<Post> data) {
            this.data = data;
        }

        public void updateData(ArrayList<Post> newData) {
            data.clear();
            data.addAll(newData);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recylerview_offers_home, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView titleView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.imageview1);
                titleView = itemView.findViewById(R.id.textview1);
            }

            void bind(Post post) {
                titleView.setText(post.title);
                Glide.with(itemView.getContext()).load(post.image).into(imageView);
                itemView.setOnClickListener(v -> {
                    VibrationManager.vibrate(itemView.getContext());
                    Intent intent = new Intent(itemView.getContext(), BlogActivity.class);
                    intent.putExtra("url", "https://mlsaegypt.org/blog/" + post.id);
                    itemView.getContext().startActivity(intent);
                });
            }
        }
    }
}
