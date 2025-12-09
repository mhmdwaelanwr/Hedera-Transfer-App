package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class HistoryActivity extends AppCompatActivity {

    private static final String HEDERA_API_BASE_URL = "https://testnet.mirrornode.hedera.com";
    private static final String HISTORY_API_ENDPOINT = "/api/v1/transactions";
    private static final String HISTORY_TAG = "history_tag";

    private final ArrayList<Object> displayList = new ArrayList<>();
    private final ArrayList<TransferActivity.Transaction> masterTransactionList = new ArrayList<>();

    private HistoryAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private LinearLayout errorLayout;
    private Button retryButton;
    private TextView errorMessage;
    private ProgressBar progressBar;
    private SearchView searchView;
    private CardView searchCard;
    private ChipGroup filterChipGroup;
    private MaterialToolbar toolbar;
    private AppBarLayout appbar;

    private String currentFilter = "";
    private RequestNetwork networkReq;
    private RequestNetwork.RequestListener networkListener;
    private String nextUrl = null;
    private CharSequence originalToolbarTitle;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private String currentExportFormat = "csv"; // Default format

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.history);

        initializeViews();
        setupRecyclerView();
        setupNetworkListener();
        setupFilePicker();
        loadTransactionHistory(true);
    }

    private void initializeViews() {
        appbar = findViewById(R.id.appbar);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            originalToolbarTitle = getSupportActionBar().getTitle();
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        emptyView = findViewById(R.id.empty_view);
        errorLayout = findViewById(R.id.error_layout);
        retryButton = findViewById(R.id.retry_button);
        errorMessage = findViewById(R.id.error_message);
        progressBar = findViewById(R.id.progress_bar);
        searchCard = findViewById(R.id.search_card);
        filterChipGroup = findViewById(R.id.filter_chip_group);
        searchView = findViewById(R.id.search_view);

        retryButton.setOnClickListener(v -> loadTransactionHistory(true));
        swipeRefreshLayout.setOnRefreshListener(() -> loadTransactionHistory(true));

        ImageView filterButton = findViewById(R.id.filter_button);
        filterButton.setOnClickListener(v -> filterChipGroup.setVisibility(filterChipGroup.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        
        ImageView closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(v -> toggleSearchBar());

        Chip chipDate = findViewById(R.id.chip_date);
        Chip chipAmount = findViewById(R.id.chip_amount);
        Chip chipId = findViewById(R.id.chip_id);

        chipDate.setOnClickListener(v -> {
            currentFilter = "date";
            showDatePicker();
        });
        chipAmount.setOnClickListener(v -> currentFilter = "amount");
        chipId.setOnClickListener(v -> currentFilter = "id");

        filterChipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                currentFilter = "";
            }
            updateDisplayList();
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                updateDisplayList();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                updateDisplayList();
                return true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.history_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search);
        MenuItem exportItem = menu.findItem(R.id.action_export_history);
        
        boolean hasHistory = !getViewableTransactions().isEmpty();

        if (searchItem != null) {
            searchItem.setEnabled(hasHistory);
            if (searchItem.getIcon() != null) {
                searchItem.getIcon().setAlpha(hasHistory ? 255 : 130);
            }
        }

        if (exportItem != null) {
            exportItem.setEnabled(hasHistory);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_search) {
            toggleSearchBar();
            return true;
        } else if (itemId == R.id.action_export_history) {
            showExportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleSearchBar() {
        if (searchCard.getVisibility() == View.VISIBLE) {
            if (searchView != null && !searchView.getQuery().toString().isEmpty()) {
                searchView.setQuery("", true);
            }
            searchCard.setVisibility(View.GONE);
            appbar.setVisibility(View.VISIBLE);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(originalToolbarTitle);
            }
            filterChipGroup.setVisibility(View.GONE);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
            }
        } else {
            if (getSupportActionBar() != null) {
                originalToolbarTitle = getSupportActionBar().getTitle();
            }
            appbar.setVisibility(View.GONE);
            searchCard.setVisibility(View.VISIBLE);
            searchView.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchView, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.history_recyclerview);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(displayList);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (linearLayoutManager != null && linearLayoutManager.findLastCompletelyVisibleItemPosition() == displayList.size() - 1) {
                    if (nextUrl != null && !nextUrl.isEmpty()) {
                        loadMoreTransactions();
                    }
                }
            }
        });
    }

    private void setupNetworkListener() {
        networkReq = new RequestNetwork(this);
        networkListener = new RequestNetwork.RequestListener() {
            @Override
            public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                errorLayout.setVisibility(View.GONE);
                handleTransactionResponse(response, "history_more_tag".equals(tag));
            }

            @Override
            public void onErrorResponse(String tag, String message) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.GONE);
                errorLayout.setVisibility(View.VISIBLE);
                errorMessage.setText("Network Error: " + message);
            }
        };
    }

    private void loadTransactionHistory(boolean isRefresh) {
        if (!isRefresh) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
             swipeRefreshLayout.setRefreshing(true);
        }
        errorLayout.setVisibility(View.GONE);
        String accountId = WalletStorage.getAccountId(this);
        if (accountId == null || accountId.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
            errorMessage.setText("Account ID not found.");
            return;
        }

        if (isRefresh) {
            masterTransactionList.clear();
            nextUrl = null;
        }

        String url = HEDERA_API_BASE_URL + HISTORY_API_ENDPOINT + "?account.id=" + accountId;
        networkReq.startRequestNetwork(RequestNetworkController.GET, url, HISTORY_TAG, networkListener);
    }

    private void loadMoreTransactions() {
        if (nextUrl == null) return;
        swipeRefreshLayout.setRefreshing(true);
        networkReq.startRequestNetwork(RequestNetworkController.GET, HEDERA_API_BASE_URL + nextUrl, "history_more_tag", networkListener);
    }

    private void handleTransactionResponse(String response, boolean isLoadMore) {
        HistoryApiParser.HistoryResponse historyResponse = HistoryApiParser.parse(response, WalletStorage.getAccountId(this));
        nextUrl = historyResponse.nextUrl;

        if (!isLoadMore) {
            masterTransactionList.clear();
        }

        LinkedHashSet<TransferActivity.Transaction> transactionSet = new LinkedHashSet<>(masterTransactionList);
        transactionSet.addAll(historyResponse.transactions);
        masterTransactionList.clear();
        masterTransactionList.addAll(transactionSet);

        updateDisplayList();
        invalidateOptionsMenu();
    }

    private void updateDisplayList() {
        displayList.clear();
        String query = (searchView != null && searchView.getQuery() != null) ? searchView.getQuery().toString().toLowerCase() : "";

        List<TransferActivity.Transaction> viewableTransactions = getViewableTransactions();
        
        List<TransferActivity.Transaction> filteredList;
        if (query.isEmpty()) {
            filteredList = new ArrayList<>(viewableTransactions);
        } else {
            filteredList = viewableTransactions.stream()
                    .filter(t -> {
                        switch (currentFilter) {
                            case "date": return t.date.toLowerCase().contains(query);
                            case "amount": return t.amount.toLowerCase().contains(query);
                            case "id": return t.party.toLowerCase().contains(query);
                            default: return t.date.toLowerCase().contains(query) || t.amount.toLowerCase().contains(query) || t.party.toLowerCase().contains(query);
                        }
                    })
                    .collect(Collectors.toList());
        }

        if (filteredList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            if (masterTransactionList.isEmpty()) {
                emptyView.setText("You have no transactions yet.");
            } else if (!query.isEmpty()) {
                emptyView.setText("No matching transactions found.");
            } else {
                 emptyView.setText("Transaction history is empty.");
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.GONE);
            displayList.add("Recent Transactions");
            displayList.addAll(filteredList);
        }

        adapter.notifyDataSetChanged();
    }

    private List<TransferActivity.Transaction> getViewableTransactions() {
        return new ArrayList<>(masterTransactionList);
    }
    
    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String selectedDate = dayOfMonth + " " + (monthOfYear + 1) + " " + year1;
                    if (searchView != null) {
                        searchView.setQuery(selectedDate, false);
                    }
                }, year, month, day);
        datePickerDialog.show();
    }

    private void showExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_export_options, null);
        builder.setView(dialogView);

        final RadioGroup formatGroup = dialogView.findViewById(R.id.export_format_group);

        builder.setTitle("Export Options");
        builder.setPositiveButton("Choose Location & Export", (dialog, which) -> {
            int selectedId = formatGroup.getCheckedRadioButtonId();
            RadioButton selectedRadioButton = dialogView.findViewById(selectedId);
            if (selectedRadioButton.getId() == R.id.format_csv) {
                currentExportFormat = "csv";
                openFilePicker("hadera_history.csv", "text/csv");
            } else if (selectedRadioButton.getId() == R.id.format_json) {
                currentExportFormat = "json";
                openFilePicker("hadera_history.json", "application/json");
            } else if (selectedRadioButton.getId() == R.id.format_log) {
                currentExportFormat = "log";
                openFilePicker("hadera_history.log", "text/plain");
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        exportToFile(uri);
                    }
                }
            });
    }

    private void openFilePicker(String defaultFileName, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, defaultFileName);
        filePickerLauncher.launch(intent);
    }

    private void exportToFile(Uri uri) {
        List<TransferActivity.Transaction> transactionsToExport = getViewableTransactions();
        if (transactionsToExport.isEmpty()) {
            Toast.makeText(this, "No history to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        String content = "";
        switch (currentExportFormat) {
            case "csv":
                content = "Date,Party,Amount,Memo,Type,Status,Fee\n" +
                        transactionsToExport.stream()
                                .map(t -> t.date + "," + t.party + "," + t.amount + "," + t.memo + "," + t.type + "," + t.status + "," + t.fee)
                                .collect(Collectors.joining("\n"));
                break;
            case "json":
                content = new Gson().toJson(transactionsToExport);
                break;
            case "log":
                content = transactionsToExport.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n--------------------\n"));
                break;
        }

        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os != null) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "History exported successfully!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    public static class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0, TYPE_ITEM = 1;
        private final ArrayList<Object> items;

        public HistoryAdapter(ArrayList<Object> items) {
            this.items = items;
        }

        @Override
        public int getItemViewType(int position) {
            return (items.get(position) instanceof String) ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                return new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_header, parent, false));
            } else {
                return new ItemViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerview_history_home, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((HeaderViewHolder) holder).bind((String) items.get(position));
            } else {
                ((ItemViewHolder) holder).bind((TransferActivity.Transaction) items.get(position));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView headerTitle;
            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                headerTitle = itemView.findViewById(R.id.header_title);
            }
            void bind(String title) {
                headerTitle.setText(title);
            }
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {
            TextView transactionType, date, amount, party, status, fee;

            ItemViewHolder(@NonNull View itemView) {
                super(itemView);
                transactionType = itemView.findViewById(R.id.textview1);
                date = itemView.findViewById(R.id.textview2);
                amount = itemView.findViewById(R.id.textview3);
                party = itemView.findViewById(R.id.textview4);
                status = itemView.findViewById(R.id.textview5);
                fee = itemView.findViewById(R.id.fee);
            }

            void bind(TransferActivity.Transaction transaction) {
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
                    amount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorError));
                } else {
                    amount.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorPrimary));
                }

                if ("SUCCESS".equalsIgnoreCase(transaction.status)) {
                    status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorPrimary));
                } else {
                    status.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.colorError));
                }

                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(itemView.getContext(), TransactionDetailsActivity.class);
                    intent.putExtra("transaction", new Gson().toJson(transaction));
                    v.getContext().startActivity(intent);
                });
            }
        }
    }
}
