package anwar.mlsa.hadera.aou;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;

public class BlogActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private MaterialToolbar toolbar;
    private LinearLayout errorLayout;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blog);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        toolbar = findViewById(R.id.toolbar);
        errorLayout = findViewById(R.id.error_layout);
        retryButton = findViewById(R.id.retry_button);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());
        retryButton.setOnClickListener(v -> webView.reload());

        String url = getIntent().getStringExtra("url");
        if (url != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    errorLayout.setVisibility(View.GONE);
                    webView.setVisibility(View.VISIBLE);
                    toolbar.setSubtitle(url);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    swipeRefreshLayout.setRefreshing(false);
                    toolbar.setTitle(view.getTitle());
                    toolbar.setSubtitle(null);
                }

                @Override
                @SuppressWarnings("deprecation")
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    // This is for older APIs
                    showErrorView();
                }

                @RequiresApi(api = Build.VERSION_CODES.M)
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    // This is for newer APIs
                    showErrorView();
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    if (newProgress < 100 && progressBar.getVisibility() == ProgressBar.GONE) {
                        progressBar.setVisibility(ProgressBar.VISIBLE);
                    }
                    progressBar.setProgress(newProgress);
                    if (newProgress == 100) {
                        progressBar.setVisibility(ProgressBar.GONE);
                    }
                }
            });

            webView.loadUrl(url);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private void showErrorView() {
        errorLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
    }
}
