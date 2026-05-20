package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RestaurantMenuActivity extends AppCompatActivity {

    private static final String ALL_RESTAURANTS_ENDPOINT = Constants.URL_GET_ALL_RESTAURANTS;

    private RecyclerView restaurantsRecyclerView;
    private RecyclerView productsRecyclerView;
    private ProgressBar loadingProgressBar;
    private TextView appTitleTextView;
    private TextView titleTextView;
    private TextView partnerRestaurantsTitleTextView;
    private TextView emptyTextView;
    private EditText searchEditText;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private TextView tabNotificationsBadgeTextView;
    private RequestQueue requestQueue;
    private final List<Restaurant> restaurantList = new ArrayList<>();
    private final List<Product> allProductList = new ArrayList<>();
    private final List<Product> productList = new ArrayList<>();
    private final Map<String, CartEntry> globalCart = new LinkedHashMap<>();
    private ProductAdapter adapter;
    private RestaurantAdapter restaurantAdapter;
    private int restaurantId = -1;
    private String restaurantName = "";
    private String currentSearchQuery = "";
    private int selectedRestaurantPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restaurant_products);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.CUSTOMER_DASHBOARD,
                AccessControlManager.Action.READ)) {
            return;
        }

        productsRecyclerView = findViewById(R.id.restaurantProductsRecyclerView);
        restaurantsRecyclerView = findViewById(R.id.restaurantProductsRestaurantsRecyclerView);
        loadingProgressBar = findViewById(R.id.restaurantProductsLoading);
        appTitleTextView = findViewById(R.id.restaurantProductsAppTitle);
        titleTextView = findViewById(R.id.restaurantProductsTitle);
        partnerRestaurantsTitleTextView = findViewById(R.id.restaurantProductsPartnerTitle);
        emptyTextView = findViewById(R.id.restaurantProductsEmptyTextView);
        searchEditText = findViewById(R.id.restaurantProductsSearchEditText);

        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);

        restaurantsRecyclerView.setLayoutManager(new    LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        restaurantsRecyclerView.setNestedScrollingEnabled(false);
        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        productsRecyclerView.setNestedScrollingEnabled(false);
        requestQueue = Volley.newRequestQueue(this);
        adapter = new ProductAdapter(productList);
        productsRecyclerView.setAdapter(adapter);
        restaurantAdapter = new RestaurantAdapter(restaurantList);
        restaurantsRecyclerView.setAdapter(restaurantAdapter);

        if (appTitleTextView != null) {
            appTitleTextView.setText("FoodDash");
        }

        restaurantId = getIntent().getIntExtra("restaurant_id", -1);
        restaurantName = getIntent().getStringExtra("restaurant_name");
        if (TextUtils.isEmpty(restaurantName)) restaurantName = "Restaurant Menu";
        titleTextView.setText(String.format(Locale.US, "%s - Menu", restaurantName));

        setupBottomNavigation();
        updateCartBadge();
        updateNotificationsTabCount();

        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    currentSearchQuery = s.toString().trim();
                    applySearchFilter();
                }
            });
        }

        loadGlobalCart();
        fetchRestaurants();
        fetchMenu();
    }

    private void fetchRestaurants() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, ALL_RESTAURANTS_ENDPOINT, null,
                response -> {
                    JSONArray restaurants = response.optJSONArray("restaurants");
                    if (restaurants == null) restaurants = response.optJSONArray("data");
                    applyRestaurantData(restaurants != null ? restaurants : new JSONArray());
                },
                error -> {
                    JsonArrayRequest legacyRequest = new JsonArrayRequest(Request.Method.GET, ALL_RESTAURANTS_ENDPOINT, null,
                            this::applyRestaurantData,
                            legacyError -> {
                                restaurantList.clear();
                                restaurantAdapter.notifyDataSetChanged();
                                if (partnerRestaurantsTitleTextView != null) {
                                    partnerRestaurantsTitleTextView.setVisibility(View.GONE);
                                }
                                restaurantsRecyclerView.setVisibility(View.GONE);
                            }
                    );
                    requestQueue.add(legacyRequest);
                }
        );
        requestQueue.add(request);
    }

    private void applyRestaurantData(JSONArray restaurants) {
        restaurantList.clear();
        for (int i = 0; i < restaurants.length(); i++) {
            JSONObject res = restaurants.optJSONObject(i);
            if (res == null) continue;

            int id = res.optInt("id", res.optInt("restaurant_id", -1));
            String name = res.optString("name", "Restaurant");
            restaurantList.add(new Restaurant(id, name));
            if (id == restaurantId) {
                selectedRestaurantPosition = restaurantList.size() - 1;
            }
        }

        if (partnerRestaurantsTitleTextView != null) {
            partnerRestaurantsTitleTextView.setVisibility(restaurantList.isEmpty() ? View.GONE : View.VISIBLE);
        }
        restaurantsRecyclerView.setVisibility(restaurantList.isEmpty() ? View.GONE : View.VISIBLE);
        restaurantAdapter.notifyDataSetChanged();

        if (selectedRestaurantPosition != -1) {
            restaurantsRecyclerView.scrollToPosition(selectedRestaurantPosition);
        }
    }

    private void fetchMenu() {
        if (restaurantId <= 0) {
            Toast.makeText(this, "Invalid restaurant", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadingProgressBar.setVisibility(View.VISIBLE);
        String url = Constants.URL_GET_MENU_BY_RESTAURANT + "?restaurant_id=" + restaurantId;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    JSONArray menus = response.optJSONArray("menus");
                    if (menus == null) menus = response.optJSONArray("data");
                    if (menus == null) menus = new JSONArray();
                    bindMenuArray(menus);
                }, error -> {
                    JsonArrayRequest legacy = new JsonArrayRequest(Request.Method.GET,
                            Constants.URL_GET_MENU_LEGACY + "?restaurant_id=" + restaurantId,
                            null,
                            this::bindMenuArray,
                            legacyError -> {
                                loadingProgressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Failed to load menu", Toast.LENGTH_SHORT).show();
                            });
                    requestQueue.add(legacy);
                });
        requestQueue.add(req);
    }

    private void bindMenuArray(JSONArray menus) {
        allProductList.clear();
        productList.clear();
        for (int i = 0; i < menus.length(); i++) {
            JSONObject obj = menus.optJSONObject(i);
            if (obj == null) continue;
            Product p = parseProduct(obj);
            CartEntry ce = globalCart.get("res:" + restaurantId + ":id:" + p.id);
            if (ce != null) p.quantity = ce.quantity;
            allProductList.add(p);
        }
        loadingProgressBar.setVisibility(View.GONE);
        applySearchFilter();
    }

    private void applySearchFilter() {
        productList.clear();

        if (TextUtils.isEmpty(currentSearchQuery)) {
            productList.addAll(allProductList);
        } else {
            String normalizedQuery = currentSearchQuery.toLowerCase(Locale.ROOT);
            for (Product product : allProductList) {
                String searchable = (product.name + " " + product.description + " " + product.restaurantName)
                        .toLowerCase(Locale.ROOT);
                if (searchable.contains(normalizedQuery)) {
                    productList.add(product);
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (emptyTextView == null) return;
        boolean hasItems = !productList.isEmpty();
        emptyTextView.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        productsRecyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    private void openRestaurantMenu(Restaurant restaurant) {
        if (restaurant == null || restaurant.id <= 0) return;
        restaurantId = restaurant.id;
        restaurantName = restaurant.name;
        selectedRestaurantPosition = findRestaurantPosition(restaurant.id);
        titleTextView.setText(String.format(Locale.US, "%s - Menu", restaurantName));
        currentSearchQuery = "";
        if (searchEditText != null) {
            searchEditText.setText("");
        }
        restaurantAdapter.notifyDataSetChanged();
        fetchMenu();
    }

    private void setupBottomNavigation() {
        highlightBottomTab(null); // Menu is not a primary tab

        if (tabHomeButton != null) {
            tabHomeButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, CustomerDashboard.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }

        if (tabOrdersButton != null) {
            tabOrdersButton.setOnClickListener(v -> {
                startActivity(new Intent(this, OrderTrackingActivity.class));
            });
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> {
                openCartFromPrefs();
            });
        }

        if (tabNotificationsButton != null) {
            tabNotificationsButton.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationActivity.class));
            });
        }

        if (tabProfileButton != null) {
            tabProfileButton.setOnClickListener(v -> {
                startActivity(new Intent(this, ProfileActivity.class));
            });
        }
    }

    private void highlightBottomTab(Button selected) {
        applyBottomTabStyle(tabHomeButton, selected == tabHomeButton);
        applyBottomTabStyle(tabOrdersButton, selected == tabOrdersButton);
        applyBottomTabStyle(tabCartButton, selected == tabCartButton);
        applyBottomTabStyle(tabNotificationsButton, selected == tabNotificationsButton);
        applyBottomTabStyle(tabProfileButton, selected == tabProfileButton);
    }

    private void applyBottomTabStyle(Button tab, boolean isSelected) {
        if (tab == null) return;
        if (isSelected) {
            tab.setBackgroundResource(R.drawable.bottom_nav_tab_selected);
            tab.setTextColor(getResources().getColor(R.color.white));
        } else {
            tab.setBackgroundResource(R.drawable.bottom_nav_tab_unselected);
            tab.setTextColor(getResources().getColor(R.color.black));
        }
    }

    private void updateCartBadge() {
        if (tabCartBadgeTextView == null) return;
        int count = 0;
        for (CartEntry e : globalCart.values()) count += e.quantity;
        if (count <= 0) {
            tabCartBadgeTextView.setVisibility(View.GONE);
            return;
        }
        tabCartBadgeTextView.setVisibility(View.VISIBLE);
        tabCartBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private void updateNotificationsTabCount() {
        int count = NotificationStore.getUnreadGroupCount(this);
        if (tabNotificationsBadgeTextView != null) {
            if (count <= 0) {
                tabNotificationsBadgeTextView.setVisibility(View.GONE);
            } else {
                tabNotificationsBadgeTextView.setVisibility(View.VISIBLE);
                tabNotificationsBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
            }
        }
    }

    private void openCartFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String cartJson = prefs.getString("global_cart_json", "[]");
        Intent intent = new Intent(this, CartActivity.class);
        intent.putExtra("cart_items_json", cartJson);
        startActivity(intent);
    }

    private int findRestaurantPosition(int targetRestaurantId) {
        for (int i = 0; i < restaurantList.size(); i++) {
            if (restaurantList.get(i).id == targetRestaurantId) {
                return i;
            }
        }
        return -1;
    }

    private Product parseProduct(JSONObject item) {
        int id = item.optInt("id", -1);
        String name = item.optString("name", "Item");
        String description = item.optString("description", "");
        double price = item.optDouble("price", 0.0);
        String imageUrl = normalizeImageUrl(item.optString("image_url", item.optString("image", "")));
        boolean available = isItemAvailable(item);
        return new Product(id, name, description, price, imageUrl, available, restaurantId, restaurantName);
    }

    private boolean isItemAvailable(JSONObject item) {
        if (item == null) return true;
        if (item.has("is_available")) {
            String val = item.optString("is_available", "1");
            return val.equals("1") || val.equalsIgnoreCase("true");
        }
        if (item.has("available")) {
            String val = item.optString("available", "1");
            return val.equals("1") || val.equalsIgnoreCase("true");
        }
        if (item.has("status")) {
            String val = item.optString("status", "").toLowerCase(Locale.ROOT);
            return val.equals("available") || val.equals("active") || val.equals("1");
        }
        if (item.has("stock")) {
            return item.optInt("stock", 1) > 0;
        }
        return true;
    }

    private String normalizeImageUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http")) return url;
        return "http://" + Constants.IP_ADDRESS + "/" + url;
    }

    private void loadGlobalCart() {
        globalCart.clear();
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString("global_cart_json", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                CartEntry e = new CartEntry(
                        obj.optInt("restaurant_id"),
                        obj.optString("restaurant_name"),
                        obj.optInt("menu_item_id"),
                        obj.optString("name"),
                        obj.optDouble("price"),
                        obj.optInt("quantity")
                );
                globalCart.put("res:" + e.restaurantId + ":id:" + e.id, e);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveGlobalCart() {
        JSONArray array = new JSONArray();
        for (CartEntry e : globalCart.values()) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("restaurant_id", e.restaurantId);
                obj.put("restaurant_name", e.restaurantName);
                obj.put("menu_item_id", e.id);
                obj.put("name", e.name);
                obj.put("price", e.price);
                obj.put("quantity", e.quantity);
                array.put(obj);
            } catch (Exception ignored) {
            }
        }
        getSharedPreferences("fooddash_prefs", MODE_PRIVATE).edit().putString("global_cart_json", array.toString()).apply();
    }

    private void syncProductWithGlobalCart(Product product) {
        String key = "res:" + product.restaurantId + ":id:" + product.id;
        if (product.quantity <= 0) globalCart.remove(key);
        else globalCart.put(key, new CartEntry(product.restaurantId, product.restaurantName, product.id, product.name, product.price, product.quantity));
        saveGlobalCart();
        updateCartBadge();
    }

    private class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.ViewHolder> {
        private final List<Restaurant> list;

        RestaurantAdapter(List<Restaurant> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restaurant, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Restaurant restaurant = list.get(position);
            holder.name.setText(restaurant.name);
            boolean selected = position == selectedRestaurantPosition;
            holder.itemView.setAlpha(selected ? 1.0f : 0.85f);
            holder.itemView.setOnClickListener(v -> openRestaurantMenu(restaurant));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.restaurantNameTextView);
            }
        }
    }

    private static class Restaurant {
        int id;
        String name;

        Restaurant(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class CartEntry {
        int restaurantId, id, quantity;
        String restaurantName, name;
        double price;

        CartEntry(int restaurantId, String restaurantName, int id, String name, double price, int quantity) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.id = id;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
        private final List<Product> list;

        ProductAdapter(List<Product> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.product_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Product pr = list.get(position);
            holder.name.setText(pr.name);
            holder.desc.setText(pr.description);
            holder.restaurant.setText(pr.restaurantName);
            holder.price.setText(String.format(Locale.US, "₱%.2f", pr.price));
            holder.qty.setText(String.valueOf(pr.quantity));
            Glide.with(holder.itemView).load(pr.imageUrl).into(holder.img);

            holder.restaurant.setVisibility(TextUtils.isEmpty(pr.restaurantName) ? View.GONE : View.VISIBLE);

            if (pr.isAvailable) {
                holder.itemView.setAlpha(1.0f);
                holder.unavailableText.setVisibility(View.GONE);
                holder.controlsLayout.setVisibility(View.VISIBLE);

                holder.plus.setOnClickListener(v -> {
                    pr.quantity++;
                    holder.qty.setText(String.valueOf(pr.quantity));
                    syncProductWithGlobalCart(pr);
                });
                holder.minus.setOnClickListener(v -> {
                    if (pr.quantity > 0) {
                        pr.quantity--;
                        holder.qty.setText(String.valueOf(pr.quantity));
                        syncProductWithGlobalCart(pr);
                    }
                });
            } else {
                holder.itemView.setAlpha(0.6f);
                holder.unavailableText.setVisibility(View.VISIBLE);
                holder.controlsLayout.setVisibility(View.GONE);
                holder.plus.setOnClickListener(null);
                holder.minus.setOnClickListener(null);
                if (pr.quantity > 0) {
                    pr.quantity = 0;
                    holder.qty.setText("0");
                    syncProductWithGlobalCart(pr);
                }
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            TextView name, desc, restaurant, price, qty, unavailableText;
            ImageButton plus, minus;
            View controlsLayout;

            ViewHolder(View v) {
                super(v);
                img = v.findViewById(R.id.productImageView);
                name = v.findViewById(R.id.productNameTextView);
                desc = v.findViewById(R.id.productDescriptionTextView);
                restaurant = v.findViewById(R.id.productRestaurantNameTextView);
                price = v.findViewById(R.id.productPriceTextView);
                qty = v.findViewById(R.id.quantityTextView);
                plus = v.findViewById(R.id.plusButton);
                minus = v.findViewById(R.id.minusButton);
                unavailableText = v.findViewById(R.id.unavailableTextView);
                controlsLayout = v.findViewById(R.id.quantityControlsLayout);
            }
        }
    }
}



