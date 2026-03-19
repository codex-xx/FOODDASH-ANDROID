package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class DeliveryDetailsActivity extends AppCompatActivity {

    private EditText addressEditText;
    private EditText notesEditText;
    private Spinner promoSpinner;

    private TextView subtotalTextView;
    private TextView deliveryFeeTextView;
    private TextView discountTextView;
    private TextView totalTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_details);

        addressEditText = findViewById(R.id.addressEditText);
        notesEditText = findViewById(R.id.notesEditText);
        promoSpinner = findViewById(R.id.promoSpinner);

        subtotalTextView = findViewById(R.id.subtotalTextView);
        deliveryFeeTextView = findViewById(R.id.deliveryFeeTextView);
        discountTextView = findViewById(R.id.discountTextView);
        totalTextView = findViewById(R.id.totalTextView);

        Button backToCartButton = findViewById(R.id.backToCartButton);
        Button goToPaymentButton = findViewById(R.id.goToPaymentButton);

        addressEditText.setText(OrderFlowManager.getDeliveryAddress());
        notesEditText.setText(OrderFlowManager.getDeliveryNotes());

        String[] promoItems = new String[]{
                "No Promo",
                "SAVE10 - 10% OFF",
                "FREEDEL - Free Delivery",
                "LESS30 - 30 OFF"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, promoItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        promoSpinner.setAdapter(adapter);

        setPromoSpinnerSelection();

        promoSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1) {
                    OrderFlowManager.setPromoCode("SAVE10");
                } else if (position == 2) {
                    OrderFlowManager.setPromoCode("FREEDEL");
                } else if (position == 3) {
                    OrderFlowManager.setPromoCode("LESS30");
                } else {
                    OrderFlowManager.setPromoCode("NONE");
                }
                updateTotals();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                OrderFlowManager.setPromoCode("NONE");
                updateTotals();
            }
        });

        backToCartButton.setOnClickListener(v -> finish());

        goToPaymentButton.setOnClickListener(v -> {
            String address = addressEditText.getText() == null ? "" : addressEditText.getText().toString().trim();
            String notes = notesEditText.getText() == null ? "" : notesEditText.getText().toString().trim();

            if (address.isEmpty()) {
                Toast.makeText(this, "Please enter delivery address.", Toast.LENGTH_SHORT).show();
                return;
            }

            OrderFlowManager.setDeliveryAddress(address);
            OrderFlowManager.setDeliveryNotes(notes);

            startActivity(new Intent(this, PaymentActivity.class));
        });

        updateTotals();
    }

    private void setPromoSpinnerSelection() {
        String promo = OrderFlowManager.getPromoCode();
        int selection = 0;
        if ("SAVE10".equals(promo)) {
            selection = 1;
        } else if ("FREEDEL".equals(promo)) {
            selection = 2;
        } else if ("LESS30".equals(promo)) {
            selection = 3;
        }
        promoSpinner.setSelection(selection);
    }

    private void updateTotals() {
        subtotalTextView.setText(String.format(Locale.getDefault(), "Subtotal: ₱%.2f", OrderFlowManager.getSubtotal()));
        deliveryFeeTextView.setText(String.format(Locale.getDefault(), "Delivery Fee: ₱%.2f", OrderFlowManager.getDeliveryFee()));
        discountTextView.setText(String.format(Locale.getDefault(), "Discount: -₱%.2f", OrderFlowManager.getDiscountAmount()));
        totalTextView.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", OrderFlowManager.getTotalAmount()));
    }
}
