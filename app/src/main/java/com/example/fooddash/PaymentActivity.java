package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class PaymentActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        RadioGroup paymentMethodRadioGroup = findViewById(R.id.paymentMethodRadioGroup);
        TextView paymentTotalTextView = findViewById(R.id.paymentTotalTextView);

        Button backToDetailsButton = findViewById(R.id.backToDetailsButton);
        Button placeOrderButton = findViewById(R.id.placeOrderButton);

        paymentTotalTextView.setText(String.format(Locale.getDefault(), "Total to Pay: ₱%.2f", OrderFlowManager.getTotalAmount()));

        backToDetailsButton.setOnClickListener(v -> finish());

        placeOrderButton.setOnClickListener(v -> {
            int selectedId = paymentMethodRadioGroup.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Select a payment method.", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selectedButton = findViewById(selectedId);
            OrderFlowManager.setPaymentMethod(selectedButton.getText().toString());
            OrderFlowManager.generateOrderNumber();

            Toast.makeText(this, "Order confirmed. Processing started.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, OrderTrackingActivity.class));
            finish();
        });
    }
}
