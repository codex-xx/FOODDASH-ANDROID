package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class OrderTrackingActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView currentStatusTextView;
    private TextView statusPreparingTextView;
    private TextView statusPickedUpTextView;
    private TextView statusOnTheWayTextView;
    private TextView statusDeliveredTextView;
    private TextView liveMapStatusTextView;
    private ProgressBar orderProgressBar;
    private Button confirmReceivedButton;

    private int currentStatusIndex = 0;

    private final String[] statuses = new String[]{"Preparing", "Picked up", "On the way", "Delivered"};
    private final String[] liveMapUpdates = new String[]{
            "Rider is waiting at the restaurant.",
            "Rider has picked up your order.",
            "Rider is 1.4 km away from your location.",
            "Rider arrived at your location."
    };

    private final Runnable statusRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentStatusIndex < statuses.length - 1) {
                currentStatusIndex++;
                updateTrackingUI();
                handler.postDelayed(this, 4000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        TextView orderNumberTextView = findViewById(R.id.orderNumberTextView);
        TextView paymentMethodTextView = findViewById(R.id.paymentMethodTextView);

        currentStatusTextView = findViewById(R.id.currentStatusTextView);
        statusPreparingTextView = findViewById(R.id.statusPreparingTextView);
        statusPickedUpTextView = findViewById(R.id.statusPickedUpTextView);
        statusOnTheWayTextView = findViewById(R.id.statusOnTheWayTextView);
        statusDeliveredTextView = findViewById(R.id.statusDeliveredTextView);
        liveMapStatusTextView = findViewById(R.id.liveMapStatusTextView);
        orderProgressBar = findViewById(R.id.orderProgressBar);
        confirmReceivedButton = findViewById(R.id.confirmReceivedButton);

        orderNumberTextView.setText("Order #: " + OrderFlowManager.getOrderNumber());
        paymentMethodTextView.setText("Payment: " + OrderFlowManager.getPaymentMethod());

        confirmReceivedButton.setOnClickListener(v -> {
            if (currentStatusIndex < statuses.length - 1) {
                Toast.makeText(this, "Order is not delivered yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, RateReviewActivity.class));
            finish();
        });

        updateTrackingUI();
        handler.postDelayed(statusRunnable, 4000);
    }

    private void updateTrackingUI() {
        currentStatusTextView.setText(statuses[currentStatusIndex]);
        liveMapStatusTextView.setText(liveMapUpdates[currentStatusIndex]);
        orderProgressBar.setProgress(currentStatusIndex);

        setStepState(statusPreparingTextView, currentStatusIndex >= 0);
        setStepState(statusPickedUpTextView, currentStatusIndex >= 1);
        setStepState(statusOnTheWayTextView, currentStatusIndex >= 2);
        setStepState(statusDeliveredTextView, currentStatusIndex >= 3);

        confirmReceivedButton.setEnabled(currentStatusIndex >= 3);
    }

    private void setStepState(TextView textView, boolean reached) {
        if (reached) {
            textView.setTextColor(getColor(R.color.product_price));
        } else {
            textView.setTextColor(getColor(R.color.product_subtitle));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(statusRunnable);
    }
}
