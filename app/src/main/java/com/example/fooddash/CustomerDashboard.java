package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerDashboard extends AppCompatActivity {

    private RecyclerView restaurantsRecyclerView;
    private RecyclerView productsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar loadingProgressBar;
    private TextView selectedRestaurantTextView;
    private TextView emptyMessageTextView;
    private Button btnPlaceOrder, btnLogout;
    private RadioGroup vehicleRadioGroup;
    private TextView totalPriceTextView;
    private RestaurantAdapter restaurantAdapter;
    private ProductAdapter adapter;
    private List<Product> productList;
    private final List<Restaurant> restaurantList = new ArrayList<>();
    private RequestQueue requestQueue;
    private int restaurantId = -1;
    private int selectedRestaurantPosition = -1;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private static final long POLLING_INTERVAL_MS = 10000L;

    // Use the centralized URL from Constants
    private static final String API_URL = Constants.BASE_URL + "orders";
    private static final String ALL_RESTAURANTS_ENDPOINT = Constants.BASE_URL + "get_all_restaurants.php";
    private static final String MENU_BY_RESTAURANT_ENDPOINT = Constants.BASE_URL + "get_menus_by_restaurant.php";
    private static final String LEGACY_MENU_ENDPOINT = Constants.BASE_URL + "get_menus.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_dashboard);

        restaurantsRecyclerView = findViewById(R.id.restaurantsRecyclerView);
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        selectedRestaurantTextView = findViewById(R.id.selectedRestaurantTextView);
        emptyMessageTextView = findViewById(R.id.emptyMessageTextView);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        btnLogout = findViewById(R.id.btnLogout);
        vehicleRadioGroup = findViewById(R.id.vehicleRadioGroup);
        totalPriceTextView = findViewById(R.id.totalPriceTextView);

        restaurantsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        requestQueue = Volley.newRequestQueue(this);
        restaurantId = getIntent().getIntExtra("restaurant_id", -1);

        productList = new ArrayList<>();

        restaurantAdapter = new RestaurantAdapter(restaurantList);
        restaurantsRecyclerView.setAdapter(restaurantAdapter);

        adapter = new ProductAdapter(productList);
        productsRecyclerView.setAdapter(adapter);
        swipeRefreshLayout.setOnRefreshListener(() -> fetchRestaurants(false));
        showEmpty("Loading restaurants...");
        selectedRestaurantTextView.setText("Select a restaurant");

        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        btnLogout.setOnClickListener(v -> {
            // Clear session/token using Application Context
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
            prefs.edit().clear().apply();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchRestaurants(true);
        startMenuPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMenuPolling();
    }

    private void startMenuPolling() {
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (restaurantId > 0) {
                    fetchMenu(false);
                }
                pollingHandler.postDelayed(this, POLLING_INTERVAL_MS);
            }
        }, POLLING_INTERVAL_MS);
    }

    private void stopMenuPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void fetchRestaurants(boolean showBlockingLoader) {
        if (showBlockingLoader) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            emptyMessageTextView.setVisibility(View.GONE);
        }

        JsonArrayRequest arrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                ALL_RESTAURANTS_ENDPOINT,
                null,
                this::applyRestaurantData,
                error -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    if (restaurantList.isEmpty()) {
                        showEmpty("No partner restaurants available.");
                    }
                    Toast.makeText(CustomerDashboard.this, "Failed to fetch restaurants", Toast.LENGTH_SHORT).show();
                    Log.e("CustomerDashboard", "Restaurants fetch failed", error);
                }
        );

        JsonObjectRequest objectRequest = new JsonObjectRequest(
                Request.Method.GET,
                ALL_RESTAURANTS_ENDPOINT,
                null,
                response -> {
                    Log.d("CustomerDashboard", "Restaurants response: " + response);
                    JSONArray restaurants = response.optJSONArray("restaurants");
                    if (restaurants == null) {
                        restaurants = response.optJSONArray("data");
                    }
                    if (restaurants == null) {
                        restaurants = response.optJSONArray("items");
                    }
                    if (restaurants == null) {
                        restaurants = new JSONArray();
                    }
                    applyRestaurantData(restaurants);
                },
                error -> requestQueue.add(arrayRequest)
        );

        requestQueue.add(objectRequest);
    }

    private void applyRestaurantData(JSONArray restaurants) {
        int previousRestaurantId = restaurantId;
        restaurantList.clear();

        for (int i = 0; i < restaurants.length(); i++) {
            JSONObject restaurant = restaurants.optJSONObject(i);
            if (restaurant == null) {
                continue;
            }

            int id = restaurant.optInt("id", restaurant.optInt("restaurant_id", -1));
            if (id <= 0) {
                continue;
            }

            String name = restaurant.optString("name", restaurant.optString("restaurant_name", "Restaurant " + id));
            restaurantList.add(new Restaurant(id, name));
        }

        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        restaurantAdapter.notifyDataSetChanged();

        if (restaurantList.isEmpty()) {
            restaurantId = -1;
            selectedRestaurantPosition = -1;
            productList.clear();
            adapter.notifyDataSetChanged();
            calculateTotalPrice();
            selectedRestaurantTextView.setText("Select a restaurant");
            showEmpty("No partner restaurants available.");
            return;
        }

        int selectedPosition = 0;
        for (int i = 0; i < restaurantList.size(); i++) {
            if (restaurantList.get(i).getId() == previousRestaurantId) {
                selectedPosition = i;
                break;
            }
        }

        selectRestaurant(selectedPosition, true);
    }

    private void selectRestaurant(int position, boolean fetchMenuNow) {
        if (position < 0 || position >= restaurantList.size()) {
            return;
        }

        selectedRestaurantPosition = position;
        Restaurant selectedRestaurant = restaurantList.get(position);
        restaurantId = selectedRestaurant.getId();
        selectedRestaurantTextView.setText("Menu: " + selectedRestaurant.getName());
        restaurantAdapter.notifyDataSetChanged();

        if (fetchMenuNow) {
            fetchMenu(true);
        }
    }

    private void fetchMenu(boolean showBlockingLoader) {
        if (restaurantId <= 0) {
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            showEmpty("Select a restaurant to view menu.");
            return;
        }

        if (showBlockingLoader) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            emptyMessageTextView.setVisibility(View.GONE);
        }

        String menuUrl = MENU_BY_RESTAURANT_ENDPOINT + "?restaurant_id=" + restaurantId;
        String legacyMenuUrl = LEGACY_MENU_ENDPOINT + "?restaurant_id=" + restaurantId;

        Response.Listener<JSONArray> successListener = this::applyMenuData;
        Response.ErrorListener errorListener = error -> {
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            if (productList.isEmpty()) {
                showEmpty("No menu available right now.");
            }
            String errorMessage = "Failed to fetch menu";
            if (error.networkResponse != null) {
                errorMessage = "Failed to fetch menu (" + error.networkResponse.statusCode + ")";
            }
            Toast.makeText(CustomerDashboard.this, errorMessage, Toast.LENGTH_SHORT).show();
            Log.e("CustomerDashboard", "Menu fetch failed", error);
        };

        JsonArrayRequest legacyArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                legacyMenuUrl,
                null,
                successListener,
                errorListener
        );

        JsonArrayRequest arrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                menuUrl,
                null,
                successListener,
                error -> requestQueue.add(legacyArrayRequest)
        );

        JsonObjectRequest objectRequest = new JsonObjectRequest(
                Request.Method.GET,
                menuUrl,
                null,
                response -> {
                    Log.d("CustomerDashboard", "Menu response: " + response);
                    JSONArray menus = response.optJSONArray("menus");
                    if (menus == null) {
                        menus = response.optJSONArray("data");
                    }
                    if (menus == null) {
                        menus = response.optJSONArray("items");
                    }
                    if (menus == null) {
                        JSONObject restaurant = response.optJSONObject("restaurant");
                        if (restaurant != null) {
                            menus = restaurant.optJSONArray("menus");
                        }
                    }
                    if (menus == null) {
                        menus = new JSONArray();
                    }
                    applyMenuData(menus);
                },
                error -> requestQueue.add(arrayRequest)
        );

        requestQueue.add(objectRequest);
    }

    private void applyMenuData(JSONArray menus) {
        productList.clear();
        for (int i = 0; i < menus.length(); i++) {
            JSONObject item = menus.optJSONObject(i);
            if (item == null) {
                continue;
            }

            String name = item.optString("name", "Unnamed Item");
            String description = item.optString("description", "");
            double price = item.optDouble("price", 0.0);
            String imageUrl = item.optString("image_url", "");

            if (TextUtils.isEmpty(imageUrl)) {
                imageUrl = item.optString("image", "");
            }
            if (TextUtils.isEmpty(imageUrl)) {
                imageUrl = item.optString("image_path", "");
            }

            imageUrl = normalizeImageUrl(imageUrl);
            boolean isAvailable = parseItemAvailability(item);

            productList.add(new Product(name, description, price, imageUrl, isAvailable));
        }

        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        adapter.notifyDataSetChanged();
        calculateTotalPrice();

        if (productList.isEmpty()) {
            showEmpty("No menu available for this restaurant.");
        } else {
            emptyMessageTextView.setVisibility(View.GONE);
        }
    }

    private void showEmpty(String message) {
        emptyMessageTextView.setText(message);
        emptyMessageTextView.setVisibility(View.VISIBLE);
    }

    private String normalizeImageUrl(String rawImageUrl) {
        if (TextUtils.isEmpty(rawImageUrl)) {
            return "";
        }

        String imageUrl = rawImageUrl.trim();
        String serverRoot = "http://" + Constants.IP_ADDRESS;

        if (imageUrl.startsWith("http://localhost") || imageUrl.startsWith("https://localhost")) {
            int slashIndex = imageUrl.indexOf('/', imageUrl.indexOf("//") + 2);
            if (slashIndex != -1) {
                return serverRoot + imageUrl.substring(slashIndex);
            }
            return serverRoot;
        }

        if (imageUrl.startsWith("/")) {
            return serverRoot + imageUrl;
        }

        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            return serverRoot + "/" + imageUrl;
        }

        return imageUrl;
    }

    private boolean parseItemAvailability(JSONObject item) {
        if (item == null) {
            return true;
        }

        if (item.has("is_available")) {
            return parseFlexibleBoolean(item.opt("is_available"), true);
        }

        if (item.has("available")) {
            return parseFlexibleBoolean(item.opt("available"), true);
        }

        String status = item.optString("status", "").trim().toLowerCase(Locale.ROOT);
        if (!status.isEmpty()) {
            if ("unavailable".equals(status) || "out_of_stock".equals(status) || "inactive".equals(status)) {
                return false;
            }
            if ("available".equals(status) || "active".equals(status)) {
                return true;
            }
        }

        if (item.has("stock")) {
            return item.optInt("stock", 1) > 0;
        }

        return true;
    }

    private boolean parseFlexibleBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return defaultValue;
        }

        if ("1".equals(text) || "true".equals(text) || "yes".equals(text) || "available".equals(text) || "active".equals(text)) {
            return true;
        }

        if ("0".equals(text) || "false".equals(text) || "no".equals(text) || "unavailable".equals(text) || "inactive".equals(text) || "out_of_stock".equals(text)) {
            return false;
        }

        return defaultValue;
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
        if (restaurantId <= 0) {
            Toast.makeText(this, "Please select a restaurant first", Toast.LENGTH_SHORT).show();
            return;
        }

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
                jsonPayload.put("restaurant_id", restaurantId);
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
    private static class Restaurant {
        int id;
        String name;

        public Restaurant(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    // Product data model
    private static class Product {
        String name;
        String description;
        double price;
        String imageUrl;
        boolean isAvailable;
        int quantity = 0;

        public Product(String name, String description, double price, String imageUrl, boolean isAvailable) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.imageUrl = imageUrl;
            this.isAvailable = isAvailable;
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

        public String getImageUrl() {
            return imageUrl;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    // RecyclerView Adapter
    private class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder> {

        private final List<Restaurant> restaurants;

        RestaurantAdapter(List<Restaurant> restaurants) {
            this.restaurants = restaurants;
        }

        @NonNull
        @Override
        public RestaurantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restaurant, parent, false);
            return new RestaurantViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RestaurantViewHolder holder, int position) {
            Restaurant restaurant = restaurants.get(position);
            boolean isSelected = position == selectedRestaurantPosition;

            holder.restaurantNameTextView.setText(restaurant.getName());
            holder.itemView.setAlpha(isSelected ? 1.0f : 0.8f);
            holder.itemView.setBackgroundResource(isSelected ? R.drawable.button_border : R.drawable.view_border);

            holder.itemView.setOnClickListener(v -> {
                if (selectedRestaurantPosition != position) {
                    selectRestaurant(position, true);
                }
            });
        }

        @Override
        public int getItemCount() {
            return restaurants.size();
        }

        class RestaurantViewHolder extends RecyclerView.ViewHolder {
            TextView restaurantNameTextView;

            RestaurantViewHolder(@NonNull View itemView) {
                super(itemView);
                restaurantNameTextView = itemView.findViewById(R.id.restaurantNameTextView);
            }
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
            boolean unavailable = !product.isAvailable();

            if (unavailable && product.getQuantity() != 0) {
                product.setQuantity(0);
                holder.quantityTextView.setText("0");
                calculateTotalPrice();
            }

            holder.unavailableTextView.setVisibility(unavailable ? View.VISIBLE : View.GONE);
            holder.plusButton.setEnabled(!unavailable);
            holder.minusButton.setEnabled(!unavailable);
            holder.plusButton.setClickable(!unavailable);
            holder.minusButton.setClickable(!unavailable);
            holder.itemView.setEnabled(!unavailable);
            holder.itemView.setClickable(false);
            holder.itemView.setAlpha(unavailable ? 0.65f : 1.0f);
            holder.plusButton.setAlpha(unavailable ? 0.35f : 1.0f);
            holder.minusButton.setAlpha(unavailable ? 0.35f : 1.0f);

            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .into(holder.productImageView);

            holder.plusButton.setOnClickListener(v -> {
                if (!product.isAvailable()) {
                    return;
                }
                product.setQuantity(product.getQuantity() + 1);
                notifyItemChanged(position);
                calculateTotalPrice();
            });

            holder.minusButton.setOnClickListener(v -> {
                if (!product.isAvailable()) {
                    return;
                }
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
                if (product.isAvailable() && product.getQuantity() > 0) {
                    selectedProducts.add(product);
                }
            }
            return selectedProducts;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            ImageView productImageView;
            TextView productNameTextView, productDescriptionTextView, productPriceTextView, quantityTextView, unavailableTextView;
            ImageButton plusButton, minusButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                productImageView = itemView.findViewById(R.id.productImageView);
                productNameTextView = itemView.findViewById(R.id.productNameTextView);
                productDescriptionTextView = itemView.findViewById(R.id.productDescriptionTextView);
                productPriceTextView = itemView.findViewById(R.id.productPriceTextView);
                quantityTextView = itemView.findViewById(R.id.quantityTextView);
                unavailableTextView = itemView.findViewById(R.id.unavailableTextView);
                plusButton = itemView.findViewById(R.id.plusButton);
                minusButton = itemView.findViewById(R.id.minusButton);
            }
        }
    }
}