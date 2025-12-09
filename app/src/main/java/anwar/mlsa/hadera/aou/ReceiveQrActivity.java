package anwar.mlsa.hadera.aou;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ReceiveQrActivity extends AppCompatActivity {

    private ImageView qrCodeImageView;
    private TextView accountIdTextView;
    private Button copyButton;
    private Button shareButton;
    private Bitmap qrCodeBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.receive_qr);

        initializeViews();
        setupListeners();
        loadAndDisplayData();
    }

    private void initializeViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        qrCodeImageView = findViewById(R.id.qr_code_imageview);
        accountIdTextView = findViewById(R.id.account_id_textview);
        copyButton = findViewById(R.id.copy_button);
        shareButton = findViewById(R.id.share_button);
    }

    private void setupListeners() {
        String accountId = WalletStorage.getAccountId(this);

        copyButton.setOnClickListener(v -> {
            if (accountId != null && !accountId.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Account ID", accountId);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Account ID copied!", Toast.LENGTH_SHORT).show();
            }
        });

        shareButton.setOnClickListener(v -> {
            if (qrCodeBitmap != null && accountId != null && !accountId.isEmpty()) {
                shareQrCode(qrCodeBitmap, accountId);
            }
        });
    }

    private void loadAndDisplayData() {
        String accountId = WalletStorage.getAccountId(this);

        if (accountId == null || accountId.isEmpty()) {
            findViewById(R.id.qr_code_imageview).setVisibility(View.GONE);
            accountIdTextView.setText("Account ID not found.");
            copyButton.setEnabled(false);
            shareButton.setEnabled(false);
            return;
        }

        accountIdTextView.setText(accountId);

        try {
            qrCodeBitmap = generateQrCode(accountId);
            qrCodeImageView.setImageBitmap(qrCodeBitmap);
        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap generateQrCode(String content) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    private void shareQrCode(Bitmap bitmap, String accountId) {
        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            File imageFile = new File(cachePath, "qr_code.png");
            FileOutputStream stream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri imageUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", imageFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_STREAM, imageUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, "My Hedera Account ID: " + accountId);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share Account ID"));

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to share QR code.", Toast.LENGTH_SHORT).show();
        }
    }
}
