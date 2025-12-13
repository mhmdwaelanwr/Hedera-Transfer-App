package anwar.mlsa.hadera.aou;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import anwar.mlsa.hadera.aou.models.AddressBookEntry;
import anwar.mlsa.hadera.aou.services.AddressBookService;

public class AddressBookActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AddressBookAdapter adapter;
    private TextView emptyView;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_book);

        setupToolbar();
        initializeViews();
        loadAddressBook();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAddressBook();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.address_book_recyclerview);
        emptyView = findViewById(R.id.empty_view);
        fabAdd = findViewById(R.id.fab_add_address);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AddressBookAdapter();
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddAddressDialog());
    }

    private void loadAddressBook() {
        List<AddressBookEntry> entries = AddressBookService.getAddressBook(this);
        adapter.updateData(entries);

        if (entries.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    private void showAddAddressDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_address, null);
        TextInputEditText labelInput = dialogView.findViewById(R.id.label_input);
        TextInputEditText accountIdInput = dialogView.findViewById(R.id.account_id_input);
        TextInputEditText memoInput = dialogView.findViewById(R.id.memo_input);

        new AlertDialog.Builder(this)
                .setTitle("Add Saved Address")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String label = labelInput.getText().toString().trim();
                    String accountId = accountIdInput.getText().toString().trim();
                    String memo = memoInput.getText().toString().trim();

                    if (label.isEmpty() || accountId.isEmpty()) {
                        Toast.makeText(this, "Label and Account ID are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!accountId.matches("^0\\.0\\.[0-9]+$")) {
                        Toast.makeText(this, "Invalid Account ID format", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean success = AddressBookService.addEntry(this, label, accountId, memo);
                    if (success) {
                        Toast.makeText(this, "Address saved", Toast.LENGTH_SHORT).show();
                        loadAddressBook();
                    } else {
                        Toast.makeText(this, "This account ID already exists", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditAddressDialog(AddressBookEntry entry) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_address, null);
        TextInputEditText labelInput = dialogView.findViewById(R.id.label_input);
        TextInputEditText accountIdInput = dialogView.findViewById(R.id.account_id_input);
        TextInputEditText memoInput = dialogView.findViewById(R.id.memo_input);

        labelInput.setText(entry.getLabel());
        accountIdInput.setText(entry.getAccountId());
        memoInput.setText(entry.getMemo());

        new AlertDialog.Builder(this)
                .setTitle("Edit Saved Address")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String label = labelInput.getText().toString().trim();
                    String accountId = accountIdInput.getText().toString().trim();
                    String memo = memoInput.getText().toString().trim();

                    if (label.isEmpty() || accountId.isEmpty()) {
                        Toast.makeText(this, "Label and Account ID are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean success = AddressBookService.updateEntry(this, entry.getId(), label, accountId, memo);
                    if (success) {
                        Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show();
                        loadAddressBook();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class AddressBookAdapter extends RecyclerView.Adapter<AddressBookAdapter.ViewHolder> {
        private List<AddressBookEntry> entries;

        public AddressBookAdapter() {
            this.entries = AddressBookService.getAddressBook(AddressBookActivity.this);
        }

        public void updateData(List<AddressBookEntry> newEntries) {
            this.entries = newEntries;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_address_book, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(entries.get(position));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView labelText, accountIdText, memoText;
            Button editButton, deleteButton, selectButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                labelText = itemView.findViewById(R.id.label_text);
                accountIdText = itemView.findViewById(R.id.account_id_text);
                memoText = itemView.findViewById(R.id.memo_text);
                editButton = itemView.findViewById(R.id.edit_button);
                deleteButton = itemView.findViewById(R.id.delete_button);
                selectButton = itemView.findViewById(R.id.select_button);
            }

            void bind(AddressBookEntry entry) {
                labelText.setText(entry.getLabel());
                accountIdText.setText(entry.getAccountId());
                
                if (entry.getMemo() != null && !entry.getMemo().isEmpty()) {
                    memoText.setText("Memo: " + entry.getMemo());
                    memoText.setVisibility(View.VISIBLE);
                } else {
                    memoText.setVisibility(View.GONE);
                }

                // Check if we're in selection mode (called from send screen)
                boolean isSelectionMode = getIntent().getBooleanExtra("SELECTION_MODE", false);
                
                if (isSelectionMode) {
                    selectButton.setVisibility(View.VISIBLE);
                    editButton.setVisibility(View.GONE);
                    deleteButton.setVisibility(View.GONE);
                    
                    selectButton.setOnClickListener(v -> {
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("SELECTED_ACCOUNT_ID", entry.getAccountId());
                        resultIntent.putExtra("SELECTED_LABEL", entry.getLabel());
                        resultIntent.putExtra("SELECTED_MEMO", entry.getMemo());
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                } else {
                    selectButton.setVisibility(View.GONE);
                    editButton.setVisibility(View.VISIBLE);
                    deleteButton.setVisibility(View.VISIBLE);
                    
                    editButton.setOnClickListener(v -> showEditAddressDialog(entry));
                    
                    deleteButton.setOnClickListener(v -> {
                        new AlertDialog.Builder(AddressBookActivity.this)
                                .setTitle("Delete Address")
                                .setMessage("Are you sure you want to delete this saved address?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    AddressBookService.deleteEntry(AddressBookActivity.this, entry.getId());
                                    Toast.makeText(AddressBookActivity.this, "Address deleted", Toast.LENGTH_SHORT).show();
                                    loadAddressBook();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                }
            }
        }
    }
}
