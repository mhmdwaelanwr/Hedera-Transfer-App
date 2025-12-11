package anwar.mlsa.hadera.aou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public class TransactionDetailsActivity extends AppCompatActivity {

    private MaterialCardView transactionDetailsCard;
    private Transaction transaction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_details);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        transactionDetailsCard = findViewById(R.id.transaction_details_card);
        Button copyButton = findViewById(R.id.copy_button);
        Button exportButton = findViewById(R.id.export_button);

        String transactionJson = getIntent().getStringExtra("transaction");
        if (transactionJson != null) {
            transaction = new Gson().fromJson(transactionJson, Transaction.class);
        }

        if (transaction != null) {
            populateViews();
        }

        copyButton.setOnClickListener(v -> copyTransactionDetails());
        exportButton.setOnClickListener(v -> exportAsImage());
    }

    private void populateViews() {
        TextView type = findViewById(R.id.details_transaction_type);
        TextView partyLabel = findViewById(R.id.details_party_label);
        TextView party = findViewById(R.id.details_party);
        TextView date = findViewById(R.id.details_date);
        TextView amount = findViewById(R.id.details_amount);
        TextView status = findViewById(R.id.details_status);
        TextView feeLabel = findViewById(R.id.details_fee_label);
        TextView fee = findViewById(R.id.details_fee);
        TextView memoLabel = findViewById(R.id.details_memo_label);
        TextView memo = findViewById(R.id.details_memo);

        type.setText(transaction.type);

        String cleanParty = transaction.party.replaceFirst("(?i)to: ", "").replaceFirst("(?i)from: ", "");
        party.setText(cleanParty);

        if ("Sent".equals(transaction.type)) {
            partyLabel.setText("To:");
        } else {
            partyLabel.setText("From:");
        }

        date.setText(transaction.date);
        amount.setText(transaction.amount);
        status.setText(transaction.status);

        if (transaction.fee != null && !transaction.fee.isEmpty()) {
            String cleanFee = transaction.fee.replaceFirst("(?i)fee: ", "");
            fee.setText(cleanFee);
            feeLabel.setVisibility(View.VISIBLE);
            fee.setVisibility(View.VISIBLE);
        } else {
            feeLabel.setVisibility(View.GONE);
            fee.setVisibility(View.GONE);
        }

        if (transaction.memo != null && !transaction.memo.isEmpty()) {
            memo.setText(transaction.memo);
            memoLabel.setVisibility(View.VISIBLE);
            memo.setVisibility(View.VISIBLE);
        } else {
            memoLabel.setVisibility(View.GONE);
            memo.setVisibility(View.GONE);
        }
    }

    private void copyTransactionDetails() {
        if (transaction == null) return;

        String party = transaction.party.replaceFirst("(?i)to: ", "").replaceFirst("(?i)from: ", "");
        String fee = transaction.fee != null ? transaction.fee.replaceFirst("(?i)fee: ", "") : null;

        String details = "Transaction Details:\n"
                + "Type: " + transaction.type + "\n"
                + ("Sent".equals(transaction.type) ? "To: " : "From: ") + party + "\n"
                + "Date: " + transaction.date + "\n"
                + "Amount: " + transaction.amount + "\n"
                + "Status: " + transaction.status + "\n";
        if (fee != null) {
            details += "Fee: " + fee + "\n";
        }
        if (transaction.memo != null && !transaction.memo.isEmpty()) {
            details += "Memo: " + transaction.memo;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("Transaction Details", details.trim());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Transaction details copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportAsImage() {
        Bitmap bitmap = takeScreenShot(transactionDetailsCard);
        if (bitmap != null) {
            File imageFile = saveBitmap(bitmap);
            if (imageFile != null) {
                shareImage(imageFile);
            }
        }
    }

    private Bitmap takeScreenShot(View view) {
        view.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);
        return bitmap;
    }

    private File saveBitmap(Bitmap bitmap) {
        File imagePath = new File(getCacheDir(), "images");
        boolean isPathCreated = true;
        if (!imagePath.exists()) {
            isPathCreated = imagePath.mkdirs();
        }

        if(isPathCreated) {
            File imageFile = new File(imagePath, "transaction_details.png");
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                return imageFile;
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error saving image", Toast.LENGTH_SHORT).show();
            }
        }
        return null;
    }

    private void shareImage(File imageFile) {
        Uri imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", imageFile);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
        startActivity(Intent.createChooser(shareIntent, "Export As Image"));
    }
}
