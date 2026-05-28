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

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONObject;

import java.util.Locale;

public class GcashPaymentActivity extends AppCompatActivity {

    public static final String EXTRA_AMOUNT = "amount";
    
    private int orderId;
    private double amount;
    private ApiService apiService;
    private Handler pollingHandler;
    private Runnable pollingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gcash_payment);

        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0.0);
        orderId = getIntent().getIntExtra("order_id", -1);
        
        apiService = RetrofitClient.getApiService();
        pollingHandler = new Handler(Looper.getMainLooper());

        TextView amountTextView = findViewById(R.id.gcashAmountTextView);
        amountTextView.setText(String.format(Locale.US, "₱%.2f", amount));

        Button payButton = findViewById(R.id.btnOpenGcash);
        payButton.setOnClickListener(v -> {
            try {
                // GCash deep link with amount and mobile number
                String gcashUrl = "gcash://pay?amount=" + amount + "&number=09068736392";
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(gcashUrl));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    // Try launching by package name if deep link action fails
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage("com.globe.gcash.android");
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                        Toast.makeText(this, "Opening GCash... Please pay ₱" + String.format(Locale.US, "%.2f", amount) + " to 09068736392", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "GCash app not found. Please install it or scan the QR.", Toast.LENGTH_SHORT).show();
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
        apiService.getOrderStatusLegacy(orderId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    String status = jsonResponse.optString("status", "");
                    if (status.equalsIgnoreCase("paid") || status.equalsIgnoreCase("accepted") || status.equalsIgnoreCase("ready")) {
                        onPaymentSuccessful();
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Fail silently
            }
        });
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
