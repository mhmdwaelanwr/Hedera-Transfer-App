package anwar.mlsa.hadera.aou;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;

public class AddContactDialogFragment extends DialogFragment {

    private IdpayViewModel viewModel;
    private ActivityResultLauncher<Intent> qrScannerLauncher;
    private EditText accountIdEditText;

    public interface AddContactDialogListener {
        void onContactAdded();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IdpayViewModelFactory factory = new IdpayViewModelFactory(requireActivity().getApplication());
        viewModel = new ViewModelProvider(requireActivity(), factory).get(IdpayViewModel.class);

        qrScannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String scannedId = result.getData().getStringExtra("SCANNED_ID");
                        if (scannedId != null) {
                            viewModel.verifyAccountId(scannedId);
                        }
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_add_contact, null);

        final EditText nameEditText = view.findViewById(R.id.edit_text_name);
        accountIdEditText = view.findViewById(R.id.edit_text_account_id);
        final TextInputLayout accountIdLayout = view.findViewById(R.id.account_id_layout);
        final TextView verifiedTextView = view.findViewById(R.id.verified_text_dialog);

        final TextWatcher dialogTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.onRecipientInputChanged(s.toString().trim(), "0", 0);
            }
        };
        accountIdEditText.addTextChangedListener(dialogTextWatcher);

        accountIdLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ScannerqrActivity.class);
            qrScannerLauncher.launch(intent);
        });

        viewModel.getVerifiedRecipient().observe(this, accountId -> {
            if (accountId != null && !accountId.isEmpty()) {
                verifiedTextView.setVisibility(View.VISIBLE);
                accountIdLayout.setVisibility(View.GONE);
                accountIdEditText.removeTextChangedListener(dialogTextWatcher);
                accountIdEditText.setText(accountId);
                accountIdEditText.addTextChangedListener(dialogTextWatcher);
            } else {
                verifiedTextView.setVisibility(View.GONE);
                if (accountIdLayout.getVisibility() == View.GONE) {
                   accountIdLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getRecipientError().observe(this, accountIdLayout::setError);
        viewModel.getRecipientHelperText().observe(this, accountIdLayout::setHelperText);

        // Reset ViewModel state for the new dialog
        viewModel.onRecipientInputChanged("", "0", 0);

        builder.setView(view)
                .setTitle("Add New Contact")
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameEditText.getText().toString().trim();
                    String accountId = accountIdEditText.getText().toString().trim();

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(accountId)) {
                        Toast.makeText(getContext(), "Name and Account ID cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!accountId.matches("^0\\.0\\.[0-9]{7}$")) {
                        Toast.makeText(getContext(), "Invalid Account ID format. Expected 0.0.XXXXXXX", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (WalletStorage.addContact(requireContext(), name, accountId)) {
                        Toast.makeText(getContext(), "Contact saved", Toast.LENGTH_SHORT).show();
                        ((AddContactDialogListener) requireActivity()).onContactAdded();
                    } else {
                        Toast.makeText(getContext(), "A contact with this Account ID already exists", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null);

        return builder.create();
    }
}
