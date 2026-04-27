package com.example.fooddash;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Random;

public class PaymentSimulationActivity extends AppCompatActivity {

    public static final String EXTRA_CHECKOUT_URL = "checkout_url";
    public static final String EXTRA_PAYMENT_METHOD = "payment_method";
    public static final String EXTRA_AMOUNT = "amount";

    private WebView paymentWebView;
    private View paymentLoadingContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_simulation);

        paymentWebView = findViewById(R.id.paymentWebView);
        paymentLoadingContainer = findViewById(R.id.paymentLoadingContainer);
        LinearLayout paymentHeaderContainer = findViewById(R.id.paymentHeaderContainer);
        TextView paymentHeaderTitleTextView = findViewById(R.id.paymentHeaderTitleTextView);
        TextView paymentHeaderSubtitleTextView = findViewById(R.id.paymentHeaderSubtitleTextView);

        String paymentMethod = getIntent().getStringExtra(EXTRA_PAYMENT_METHOD);
        if (TextUtils.isEmpty(paymentMethod)) paymentMethod = "gcash";
        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);
        String checkoutUrl = getIntent().getStringExtra(EXTRA_CHECKOUT_URL);

        boolean isMaya = "maya".equalsIgnoreCase(paymentMethod);
        int headerColor = Color.parseColor(isMaya ? "#0B7A53" : "#0057FF");
        paymentHeaderContainer.setBackgroundColor(headerColor);
        paymentHeaderTitleTextView.setText(isMaya ? "Secure Payment" : "Pay with GCash");
        paymentHeaderSubtitleTextView.setText(isMaya ? "Maya Checkout Simulation" : "GCash Checkout Simulation");

        configureWebView();

        if (TextUtils.isEmpty(checkoutUrl)) {
            loadFallbackPaymentPage(paymentMethod, amount);
        } else {
            paymentWebView.loadUrl(checkoutUrl);
        }
    }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = paymentWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        paymentWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleRedirect(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleRedirect(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                paymentLoadingContainer.setVisibility(View.GONE);
            }
        });
    }

    private boolean handleRedirect(String url) {
        String normalized = url == null ? "" : url.toLowerCase(Locale.US);
        if (normalized.contains("payment-success")) {
            setResult(RESULT_OK, new Intent().putExtra("payment_result", "success"));
            finish();
            return true;
        }
        if (normalized.contains("payment-failed")) {
            setResult(RESULT_CANCELED, new Intent().putExtra("payment_result", "failed"));
            finish();
            return true;
        }
        return false;
    }

    private void loadFallbackPaymentPage(String paymentMethod, double amount) {
        String reference = generateReferenceNumber(paymentMethod);
        String html = buildFallbackHtml(paymentMethod, amount, reference);
        paymentWebView.loadDataWithBaseURL("https://fooddash.local/", html, "text/html", "UTF-8", null);
    }

    private String generateReferenceNumber(String paymentMethod) {
        Random random = new Random();
        int left = 100000 + random.nextInt(900000);
        int right = 100000 + random.nextInt(900000);
        String prefix = "maya".equalsIgnoreCase(paymentMethod) ? "MY" : "GC";
        return prefix + "-" + left + "-" + right;
    }

    private String buildFallbackHtml(String paymentMethod, double amount, String reference) {
        boolean isMaya = "maya".equalsIgnoreCase(paymentMethod);
        String primary = isMaya ? "#0B7A53" : "#0057FF";
        String accent = isMaya ? "#0EA36D" : "#2E74FF";
        String title = isMaya ? "Secure Payment" : "Pay with GCash";
        String subtitle = isMaya ? "Maya Payment Gateway" : "GCash Secure Checkout";
        String brandBadge = isMaya ? "MAYA" : "GCASH";

        return "<!doctype html>"
                + "<html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + title + "</title>"
                + "<style>"
                + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f4f7fb;margin:0;padding:18px;color:#122;}"
                + ".card{max-width:420px;margin:20px auto;background:#fff;border-radius:18px;box-shadow:0 14px 34px rgba(0,0,0,.12);overflow:hidden;}"
                + ".header{background:" + primary + ";padding:20px;color:#fff;}"
                + ".badge{display:inline-block;background:rgba(255,255,255,.22);padding:6px 10px;border-radius:999px;font-weight:700;font-size:12px;letter-spacing:.8px;}"
                + "h1{font-size:24px;margin:10px 0 4px;}"
                + "p{margin:0;opacity:.95;}"
                + ".body{padding:18px;}"
                + ".row{display:flex;justify-content:space-between;align-items:center;margin:12px 0;padding:12px;border:1px solid #e8edf5;border-radius:10px;}"
                + ".label{color:#556;}"
                + ".value{font-weight:700;color:#112;}"
                + ".amount{font-size:24px;color:" + primary + ";font-weight:800;}"
                + ".actions{display:grid;gap:10px;margin-top:18px;}"
                + "a.btn{display:block;text-align:center;text-decoration:none;padding:14px 16px;border-radius:12px;font-weight:700;}"
                + ".pay{background:" + accent + ";color:#fff;}"
                + ".cancel{background:#eef2f8;color:#244;}"
                + "</style></head><body>"
                + "<div class='card'><div class='header'><span class='badge'>" + brandBadge + "</span><h1>" + title + "</h1><p>" + subtitle + "</p></div>"
                + "<div class='body'>"
                + "<div class='row'><span class='label'>Reference Number</span><span class='value'>" + reference + "</span></div>"
                + "<div class='row'><span class='label'>Amount to Pay</span><span class='amount'>P" + String.format(Locale.US, "%.2f", amount) + "</span></div>"
                + "<div class='actions'>"
                + "<a class='btn pay' href='https://fooddash.local/payment-success?ref=" + Uri.encode(reference) + "'>Pay Now</a>"
                + "<a class='btn cancel' href='https://fooddash.local/payment-failed?ref=" + Uri.encode(reference) + "'>Cancel Payment</a>"
                + "</div></div></div></body></html>";
    }

    @Override
    protected void onDestroy() {
        if (paymentWebView != null) {
            paymentWebView.destroy();
        }
        super.onDestroy();
    }
}
