package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartReviewActivity extends AppCompatActivity {

    private LinearLayout cartItemsContainer;
    private TextView subtotalTextView;
    private TextView deliveryFeeTextView;
    private TextView discountTextView;
    private TextView totalTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart_review);

        cartItemsContainer = findViewById(R.id.cartItemsContainer);
        subtotalTextView = findViewById(R.id.subtotalTextView);
        deliveryFeeTextView = findViewById(R.id.deliveryFeeTextView);
        discountTextView = findViewById(R.id.discountTextView);
        totalTextView = findViewById(R.id.totalTextView);

        Button backToMenuButton = findViewById(R.id.backToMenuButton);
        Button continueToDetailsButton = findViewById(R.id.continueToDetailsButton);

        backToMenuButton.setOnClickListener(v -> finish());

        continueToDetailsButton.setOnClickListener(v -> {
            if (OrderFlowManager.isCartEmpty()) {
                Toast.makeText(this, "Your cart is empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, DeliveryDetailsActivity.class));
        });

        renderCart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCart();
    }

    private void renderCart() {
        cartItemsContainer.removeAllViews();
        List<OrderFlowManager.CartItem> items = new ArrayList<>(OrderFlowManager.getCartItems());

        if (items.isEmpty()) {
            TextView emptyTextView = new TextView(this);
            emptyTextView.setText("No items in cart yet.");
            emptyTextView.setTextSize(16f);
            cartItemsContainer.addView(emptyTextView);
            updateTotals();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (OrderFlowManager.CartItem item : items) {
            View rowView = inflater.inflate(R.layout.item_cart_row, cartItemsContainer, false);

            TextView nameTextView = rowView.findViewById(R.id.cartItemNameTextView);
            TextView addOnsTextView = rowView.findViewById(R.id.cartItemAddOnsTextView);
            TextView preferenceTextView = rowView.findViewById(R.id.cartItemPreferenceTextView);
            TextView lineTotalTextView = rowView.findViewById(R.id.cartItemLineTotalTextView);
            TextView quantityTextView = rowView.findViewById(R.id.cartQuantityTextView);
            ImageButton minusButton = rowView.findViewById(R.id.cartMinusButton);
            ImageButton plusButton = rowView.findViewById(R.id.cartPlusButton);

            nameTextView.setText(item.getName());
            addOnsTextView.setText(String.format(Locale.getDefault(), "Add-ons: %s", item.getAddOnSummary()));

            if (item.getPreference().isEmpty()) {
                preferenceTextView.setText("Preference: none");
            } else {
                preferenceTextView.setText(String.format(Locale.getDefault(), "Preference: %s", item.getPreference()));
            }

            lineTotalTextView.setText(String.format(Locale.getDefault(), "₱%.2f", item.getLineTotal()));
            quantityTextView.setText(String.valueOf(item.getQuantity()));

            minusButton.setOnClickListener(v -> {
                item.setQuantity(item.getQuantity() - 1);
                if (item.getQuantity() <= 0) {
                    OrderFlowManager.removeItem(item);
                }
                renderCart();
            });

            plusButton.setOnClickListener(v -> {
                item.setQuantity(item.getQuantity() + 1);
                renderCart();
            });

            cartItemsContainer.addView(rowView);
        }

        updateTotals();
    }

    private void updateTotals() {
        subtotalTextView.setText(String.format(Locale.getDefault(), "Subtotal: ₱%.2f", OrderFlowManager.getSubtotal()));
        deliveryFeeTextView.setText(String.format(Locale.getDefault(), "Delivery Fee: ₱%.2f", OrderFlowManager.getDeliveryFee()));
        discountTextView.setText(String.format(Locale.getDefault(), "Discount: -₱%.2f", OrderFlowManager.getDiscountAmount()));
        totalTextView.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", OrderFlowManager.getTotalAmount()));
    }
}
