package anwar.mlsa.hadera.aou;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

public class SentpayActivity extends AppCompatActivity {

    private TextView transactionIdTextView;
    private Button viewOnHashScanButton;
    private Button shareButton;
    private Button doneButton;

    private String transactionId;
    private String hashscanUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sentpay);

        initializeViews();

        Intent intent = getIntent();
        transactionId = intent.getStringExtra("TRANSACTION_ID");
        hashscanUrl = intent.getStringExtra("HASHSCAN_URL");

        if (transactionId == null || hashscanUrl == null) {
            Toast.makeText(this, "Error: Transaction details not found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        transactionIdTextView.setText(transactionId);

        setupListeners();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goToMainScreen();
            }
        });
    }

    private void initializeViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> goToMainScreen());

        transactionIdTextView = findViewById(R.id.transaction_id_textview);
        viewOnHashScanButton = findViewById(R.id.view_on_hashscan_button);
        shareButton = findViewById(R.id.share_button);
        doneButton = findViewById(R.id.done_button);
    }

    private void setupListeners() {
        doneButton.setOnClickListener(v -> goToMainScreen());

        viewOnHashScanButton.setOnClickListener(v -> {
            Intent intent = new Intent(SentpayActivity.this, HashScanActivity.class);
            intent.putExtra("url", hashscanUrl);
            startActivity(intent);
        });

        shareButton.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareBody = "Transaction Details:\nTransaction ID: " + transactionId + "\nView on HashScan: " + hashscanUrl;
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            startActivity(Intent.createChooser(shareIntent, "Share Transaction Details"));
        });
    }

    private void goToMainScreen() {
        Intent mainWalletIntent = new Intent(SentpayActivity.this, TransferActivity.class);
        mainWalletIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mainWalletIntent);
        finish();
    }
}
