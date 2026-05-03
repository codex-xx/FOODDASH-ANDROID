package com.example.fooddash;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

    private RecyclerView productsRecyclerView;
    private ProgressBar loadingProgressBar;
    private TextView titleTextView;
    private Button btnBackToDashboard;
    private RequestQueue requestQueue;
    private final List<Product> productList = new ArrayList<>();
    private final Map<String, CartEntry> globalCart = new LinkedHashMap<>();
    private ProductAdapter adapter;
    private int restaurantId = -1;
    private String restaurantName = "";

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
        loadingProgressBar = findViewById(R.id.restaurantProductsLoading);
        titleTextView = findViewById(R.id.restaurantProductsTitle);
        btnBackToDashboard = findViewById(R.id.restaurantProductsBackButton);

        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        productsRecyclerView.setNestedScrollingEnabled(false);
        requestQueue = Volley.newRequestQueue(this);
        adapter = new ProductAdapter(productList);
        productsRecyclerView.setAdapter(adapter);

        restaurantId = getIntent().getIntExtra("restaurant_id", -1);
        restaurantName = getIntent().getStringExtra("restaurant_name");
        if (TextUtils.isEmpty(restaurantName)) restaurantName = "Restaurant Menu";
        titleTextView.setText(String.format(Locale.US, "%s - Menu", restaurantName));
        btnBackToDashboard.setOnClickListener(v -> finish());

        loadGlobalCart();
        fetchMenu();
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
        productList.clear();
        for (int i = 0; i < menus.length(); i++) {
            JSONObject obj = menus.optJSONObject(i);
            if (obj == null) continue;
            Product p = parseProduct(obj);
            CartEntry ce = globalCart.get("res:" + restaurantId + ":id:" + p.id);
            if (ce != null) p.quantity = ce.quantity;
            productList.add(p);
        }
        loadingProgressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
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



