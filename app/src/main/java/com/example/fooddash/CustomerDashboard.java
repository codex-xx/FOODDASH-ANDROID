package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

    // Use the centralized URL from Constants
    private static final String API_URL = Constants.BASE_URL + "orders";

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
            // Clear session/token using Application Context
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
            prefs.edit().clear().apply();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private double calculateTotal() {
        double total = 0;
        for (Product product : productList) {
            total += product.getPrice() * product.getQuantity();
        }
        return total;
    }

    private void calculateTotalPrice() {
        totalPriceTextView.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", calculateTotal()));
    }

    private void placeOrder() {
        List<Product> selectedProducts = adapter.getSelectedProducts();
        if (selectedProducts.isEmpty()) {
            Toast.makeText(this, "Please select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get token from SharedPreferences using Application Context
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String token = prefs.getString("api_token", null);
        Log.d("CustomerDashboard", "Retrieved token for order: " + token);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "You are not logged in. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                conn.setDoInput(true);

                JSONObject jsonPayload = new JSONObject();
                // NOTE: Hardcoding restaurant_id and delivery_address as they are not in the UI
                jsonPayload.put("restaurant_id", 1);
                jsonPayload.put("total_amount", calculateTotal());
                jsonPayload.put("delivery_address", "123 Food Street, App City");

                JSONArray itemsArray = new JSONArray();
                for (Product product : selectedProducts) {
                    JSONObject item = new JSONObject();
                    item.put("name", product.getName());
                    item.put("quantity", product.getQuantity());
                    item.put("price", product.getPrice());
                    itemsArray.put(item);
                }
                jsonPayload.put("items", itemsArray);


                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonPayload.toString());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    runOnUiThread(() -> {
                        Toast.makeText(CustomerDashboard.this, "Order placed successfully!", Toast.LENGTH_LONG).show();
                        // Reset quantities
                        for(Product p : productList) p.setQuantity(0);
                        adapter.notifyDataSetChanged();
                        calculateTotalPrice();
                    });
                } else {
                    final String errorResponse = new java.util.Scanner(conn.getErrorStream()).useDelimiter("\\A").next();
                    Log.e("CustomerDashboard", "Error response from server (" + responseCode + "): " + errorResponse);
                    runOnUiThread(() -> Toast.makeText(CustomerDashboard.this, "Failed to place order. Server code: " + responseCode, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(CustomerDashboard.this, "Error placing order: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
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