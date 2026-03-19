package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerDashboard extends AppCompatActivity {

    private RecyclerView productsRecyclerView;
    private Button btnPlaceOrder;
    private Button btnLogout;
    private RadioGroup vehicleRadioGroup;
    private TextView totalPriceTextView;
    private ProductAdapter adapter;
    private List<Product> productList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_dashboard);

        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        btnLogout = findViewById(R.id.btnLogout);
        vehicleRadioGroup = findViewById(R.id.vehicleRadioGroup);
        totalPriceTextView = findViewById(R.id.totalPriceTextView);

        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        productList = new ArrayList<>();
        productList.add(new Product("Classic Cheeseburger", "Juicy beef patty with cheddar, pickles, and signature burger sauce.", 149.00, R.drawable.classic_cheeseburger, "Extra Cheese", 20.00, "Bacon Strips", 35.00));
        productList.add(new Product("Crispy Chicken Sandwich", "Crispy chicken fillet, lettuce, and mayo on a toasted brioche bun.", 159.00, R.drawable.crispy_chicken_sandwich, "Extra Mayo", 10.00, "Cheese Slice", 20.00));
        productList.add(new Product("Double Bacon Burger", "Two beef patties, smoky bacon strips, melted cheese, and onion jam.", 199.00, R.drawable.double_bacon_burger, "Caramelized Onion", 15.00, "More Bacon", 40.00));
        productList.add(new Product("Loaded Fries", "Seasoned fries topped with cheese sauce, crispy bits, and spring onions.", 119.00, R.drawable.loaded_fries_with_cheese_and_bacon, "Extra Cheese Sauce", 25.00, "Jalapeno", 15.00));
        productList.add(new Product("Chicken Nuggets Combo", "Eight crispy nuggets with dip, fries, and a regular soft drink.", 169.00, R.drawable.crispy_chicken_nuggets_with_fries, "Extra Nuggets", 45.00, "Upgrade Drink", 20.00));

        adapter = new ProductAdapter(productList);
        productsRecyclerView.setAdapter(adapter);
        updateCartSummary();

        btnPlaceOrder.setOnClickListener(v -> openCartReview());

        btnLogout.setOnClickListener(v -> {
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
            prefs.edit().clear().apply();
            OrderFlowManager.clearFlow();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartSummary();
    }

    private void updateCartSummary() {
        totalPriceTextView.setText(String.format(Locale.getDefault(), "₱%.2f", OrderFlowManager.getSubtotal()));
        btnPlaceOrder.setText(String.format(Locale.getDefault(), "Review Cart (%d)", OrderFlowManager.getCartItemCount()));
    }

    private void openCartReview() {
        if (OrderFlowManager.isCartEmpty()) {
            Toast.makeText(this, "Add items to cart first.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, CartReviewActivity.class));
    }

    private void showCustomizeDialog(Product product, int adapterPosition) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_customize_item, null, false);

        TextView productName = dialogView.findViewById(R.id.customizeProductNameTextView);
        TextView basePrice = dialogView.findViewById(R.id.customizeBasePriceTextView);
        TextView quantityText = dialogView.findViewById(R.id.customizeQuantityTextView);
        ImageButton minusButton = dialogView.findViewById(R.id.customizeMinusButton);
        ImageButton plusButton = dialogView.findViewById(R.id.customizePlusButton);
        CheckBox addOnOne = dialogView.findViewById(R.id.addOnOneCheckBox);
        CheckBox addOnTwo = dialogView.findViewById(R.id.addOnTwoCheckBox);
        EditText preferenceEditText = dialogView.findViewById(R.id.customPreferenceEditText);

        productName.setText(product.getName());
        basePrice.setText(String.format(Locale.getDefault(), "Base Price: ₱%.2f", product.getPrice()));

        final int[] selectedQuantity = {Math.max(1, product.getQuantity())};
        quantityText.setText(String.valueOf(selectedQuantity[0]));

        minusButton.setOnClickListener(v -> {
            selectedQuantity[0] = Math.max(1, selectedQuantity[0] - 1);
            quantityText.setText(String.valueOf(selectedQuantity[0]));
        });

        plusButton.setOnClickListener(v -> {
            selectedQuantity[0] = selectedQuantity[0] + 1;
            quantityText.setText(String.valueOf(selectedQuantity[0]));
        });

        addOnOne.setText(String.format(Locale.getDefault(), "%s (+₱%.2f)", product.getAddOnOneName(), product.getAddOnOnePrice()));
        addOnTwo.setText(String.format(Locale.getDefault(), "%s (+₱%.2f)", product.getAddOnTwoName(), product.getAddOnTwoPrice()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Customize Item")
                .setView(dialogView)
                .setPositiveButton("Add to Cart", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<OrderFlowManager.AddOnSelection> addOnSelections = new ArrayList<>();
            if (addOnOne.isChecked()) {
                addOnSelections.add(new OrderFlowManager.AddOnSelection(product.getAddOnOneName(), product.getAddOnOnePrice()));
            }
            if (addOnTwo.isChecked()) {
                addOnSelections.add(new OrderFlowManager.AddOnSelection(product.getAddOnTwoName(), product.getAddOnTwoPrice()));
            }

            String preference = preferenceEditText.getText() == null ? "" : preferenceEditText.getText().toString().trim();
            OrderFlowManager.addItem(product.getName(), product.getPrice(), selectedQuantity[0], addOnSelections, preference);

            product.setQuantity(selectedQuantity[0]);
            adapter.notifyItemChanged(adapterPosition);
            updateCartSummary();

            Toast.makeText(this, "Added to cart.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));

        dialog.show();
    }

    private static class Product {
        private final String name;
        private final String description;
        private final double price;
        private final int imageResId;
        private int quantity = 0;
        private final String addOnOneName;
        private final double addOnOnePrice;
        private final String addOnTwoName;
        private final double addOnTwoPrice;

        Product(String name, String description, double price, int imageResId, String addOnOneName, double addOnOnePrice, String addOnTwoName, double addOnTwoPrice) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.imageResId = imageResId;
            this.addOnOneName = addOnOneName;
            this.addOnOnePrice = addOnOnePrice;
            this.addOnTwoName = addOnTwoName;
            this.addOnTwoPrice = addOnTwoPrice;
        }

        String getName() {
            return name;
        }

        String getDescription() {
            return description;
        }

        double getPrice() {
            return price;
        }

        int getImageResId() {
            return imageResId;
        }

        int getQuantity() {
            return quantity;
        }

        void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        String getAddOnOneName() {
            return addOnOneName;
        }

        double getAddOnOnePrice() {
            return addOnOnePrice;
        }

        String getAddOnTwoName() {
            return addOnTwoName;
        }

        double getAddOnTwoPrice() {
            return addOnTwoPrice;
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

        private final List<Product> products;

        ProductAdapter(List<Product> products) {
            this.products = products;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.product_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Product product = products.get(position);
            holder.productImageView.setImageResource(product.getImageResId());
            holder.productNameTextView.setText(product.getName());
            holder.productDescriptionTextView.setText(product.getDescription());
            holder.productPriceTextView.setText(String.format(Locale.getDefault(), "₱%.2f", product.getPrice()));
            holder.quantityTextView.setText(String.valueOf(product.getQuantity()));

            holder.plusButton.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                Product updatedProduct = products.get(adapterPosition);
                updatedProduct.setQuantity(updatedProduct.getQuantity() + 1);
                notifyItemChanged(adapterPosition);
            });

            holder.minusButton.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }
                Product updatedProduct = products.get(adapterPosition);
                if (updatedProduct.getQuantity() > 0) {
                    updatedProduct.setQuantity(updatedProduct.getQuantity() - 1);
                    notifyItemChanged(adapterPosition);
                }
            });

            holder.addToCartButton.setOnClickListener(v -> {
                int adapterPosition = holder.getAdapterPosition();
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return;
                }

                Product selectedProduct = products.get(adapterPosition);
                if (selectedProduct.getQuantity() <= 0) {
                    Toast.makeText(CustomerDashboard.this, "Set quantity first.", Toast.LENGTH_SHORT).show();
                    return;
                }

                showCustomizeDialog(selectedProduct, adapterPosition);
            });
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView productNameTextView;
            private final TextView productDescriptionTextView;
            private final TextView productPriceTextView;
            private final TextView quantityTextView;
            private final ImageView productImageView;
            private final Button addToCartButton;
            private final ImageButton plusButton;
            private final ImageButton minusButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                productImageView = itemView.findViewById(R.id.productImageView);
                productNameTextView = itemView.findViewById(R.id.productNameTextView);
                productDescriptionTextView = itemView.findViewById(R.id.productDescriptionTextView);
                productPriceTextView = itemView.findViewById(R.id.productPriceTextView);
                quantityTextView = itemView.findViewById(R.id.quantityTextView);
                addToCartButton = itemView.findViewById(R.id.addToCartButton);
                plusButton = itemView.findViewById(R.id.plusButton);
                minusButton = itemView.findViewById(R.id.minusButton);
            }
        }
    }
}
