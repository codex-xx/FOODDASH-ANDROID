package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerDashboard extends AppCompatActivity {

    private RecyclerView productsRecyclerView;
    private Button btnPlaceOrder, btnLogout;
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

        productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        productList = new ArrayList<>();
        productList.add(new Product("Burger", "A delicious beef burger", 550.00));
        productList.add(new Product("Pizza", "Cheesy pepperoni pizza", 800.00));
        productList.add(new Product("Salad", "A healthy green salad", 400.00));

        adapter = new ProductAdapter(productList);
        productsRecyclerView.setAdapter(adapter);

        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void calculateTotalPrice() {
        double total = 0;
        for (Product product : productList) {
            total += product.getPrice() * product.getQuantity();
        }
        totalPriceTextView.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", total));
    }

    private void placeOrder() {
        List<Product> selectedProducts = adapter.getSelectedProducts();
        if (selectedProducts.isEmpty()) {
            Toast.makeText(this, "Please select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedVehicleId = vehicleRadioGroup.getCheckedRadioButtonId();
        RadioButton selectedVehicle = findViewById(selectedVehicleId);
        String vehicle = selectedVehicle.getText().toString();

        StringBuilder orderSummary = new StringBuilder("Ordered: ");
        for (Product product : selectedProducts) {
            orderSummary.append(product.getQuantity())
                .append("x ")
                .append(product.getName())
                .append(", ");
        }
        orderSummary.setLength(orderSummary.length() - 2); // Remove last comma and space
        orderSummary.append(" with a ").append(vehicle);

        Toast.makeText(this, orderSummary.toString(), Toast.LENGTH_LONG).show();
    }

    // Product data model
    private static class Product {
        String name;
        String description;
        double price;
        int quantity = 0;

        public Product(String name, String description, double price) {
            this.name = name;
            this.description = description;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public double getPrice() {
            return price;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    // RecyclerView Adapter
    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

        private List<Product> products;

        public ProductAdapter(List<Product> products) {
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
            holder.productNameTextView.setText(product.getName());
            holder.productDescriptionTextView.setText(product.getDescription());
            holder.productPriceTextView.setText(String.format(Locale.getDefault(), "₱%.2f", product.getPrice()));
            holder.quantityTextView.setText(String.valueOf(product.getQuantity()));

            holder.plusButton.setOnClickListener(v -> {
                product.setQuantity(product.getQuantity() + 1);
                notifyItemChanged(position);
                calculateTotalPrice();
            });

            holder.minusButton.setOnClickListener(v -> {
                if (product.getQuantity() > 0) {
                    product.setQuantity(product.getQuantity() - 1);
                    notifyItemChanged(position);
                    calculateTotalPrice();
                }
            });
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        public List<Product> getSelectedProducts() {
            List<Product> selectedProducts = new ArrayList<>();
            for (Product product : products) {
                if (product.getQuantity() > 0) {
                    selectedProducts.add(product);
                }
            }
            return selectedProducts;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            TextView productNameTextView, productDescriptionTextView, productPriceTextView, quantityTextView;
            ImageButton plusButton, minusButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                productNameTextView = itemView.findViewById(R.id.productNameTextView);
                productDescriptionTextView = itemView.findViewById(R.id.productDescriptionTextView);
                productPriceTextView = itemView.findViewById(R.id.productPriceTextView);
                quantityTextView = itemView.findViewById(R.id.quantityTextView);
                plusButton = itemView.findViewById(R.id.plusButton);
                minusButton = itemView.findViewById(R.id.minusButton);
            }
        }
    }
}
