package com.example.fooddash;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.util.Locale;

public class GcashPaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    
    private int orderId;
    private RequestQueue requestQueue;
    private Handler pollingHandler;
    private Runnable pollingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gcash_payment);

        double amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);
        orderId = getIntent().getIntExtra("order_id", -1);
        
        requestQueue = Volley.newRequestQueue(this);
        pollingHandler = new Handler(Looper.getMainLooper());

        TextView amountTextView = findViewById(R.id.gcashAmountTextView);
        amountTextView.setText(String.format(Locale.US, "₱%.2f", amount));

        Button payButton = findViewById(R.id.btnOpenGcash);
        payButton.setOnClickListener(v -> {
            try {
                String gcashUrl = "gcash://pay?amount=" + amount;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(gcashUrl));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.globe.gcash.android");
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(this, "GCash app not found.", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Could not open GCash app.", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnCancelPayment).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // Start checking for payment automatically if we have an order ID
        if (orderId > 0) {
            startPollingStatus();
        }
    }

    private void startPollingStatus() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                checkPaymentStatusOnServer();
                // Schedule next check in 5 seconds
                pollingHandler.postDelayed(this, 5000);
            }
        };
        pollingHandler.postDelayed(pollingRunnable, 5000);
    }

    private void checkPaymentStatusOnServer() {
        String url = Constants.BASE_URL + "get_order_status.php?order_id=" + orderId;
        
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    String status = response.optString("status", "");
                    if (status.equalsIgnoreCase("paid") || status.equalsIgnoreCase("accepted") || status.equalsIgnoreCase("ready")) {
                        onPaymentSuccessful();
                    }
                },
                error -> {
                    // Fail silently
                }
        );
        requestQueue.add(request);
    }

    private void onPaymentSuccessful() {
        stopPolling();
        Toast.makeText(this, "Payment detected! Processing your order...", Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    private void stopPolling() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        super.onDestroy();
    }
}
