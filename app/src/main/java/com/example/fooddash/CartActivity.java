package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CartActivity extends AppCompatActivity {

    private final List<CartItem> cartItems = new ArrayList<>();
    private TextView subtotalTextView;
    private LinearLayout cartGroupsContainer;
    private int restaurantId = -1;
    private String cartItemsJson = "[]";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.CART,
                AccessControlManager.Action.READ)) {
            return;
        }

        TextView cartHeaderTextView = findViewById(R.id.cartHeaderTextView);
        subtotalTextView = findViewById(R.id.subtotalTextView);
        cartGroupsContainer = findViewById(R.id.cartGroupsContainer);
        Button btnProceedCheckout = findViewById(R.id.btnProceedCheckout);
        Button btnBackToMenu = findViewById(R.id.btnBackToMenu);

        restaurantId = getIntent().getIntExtra("restaurant_id", -1);
        cartItemsJson = getIntent().getStringExtra("cart_items_json");
        if (cartItemsJson == null || cartItemsJson.trim().isEmpty()) {
            cartItemsJson = "[]";
        }

        parseCartItems(cartItemsJson);

        cartHeaderTextView.setText("Cart Summary");
        refreshCartUI(false);

        btnProceedCheckout.setOnClickListener(v -> {
            JSONArray selectedItemsJson = buildSelectedItemsJson();
            if (selectedItemsJson.length() == 0) {
                Toast.makeText(this, "Please select at least one item", Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedRestaurantCount = countSelectedRestaurants();
            Intent intent = new Intent(this, CheckoutActivity.class);
            intent.putExtra("restaurant_id", selectedRestaurantCount == 1 ? getSingleSelectedRestaurantId() : -1);
            intent.putExtra("subtotal", calculateSelectedSubtotal());
            intent.putExtra("cart_items_json", selectedItemsJson.toString());
            startActivity(intent);
        });

        btnBackToMenu.setOnClickListener(v -> finish());
    }

    private void parseCartItems(String json) {
        cartItems.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                int menuItemId = obj.optInt("menu_item_id", -1);
                String name = obj.optString("name", "Item");
                int quantity = obj.optInt("quantity", 0);
                double price = obj.optDouble("price", 0.0);
                String restaurantName = obj.optString("restaurant_name", "Restaurant");
                int itemRestaurantId = obj.optInt("restaurant_id", restaurantId);
                cartItems.add(new CartItem(itemRestaurantId, menuItemId, name, quantity, price, restaurantName));
            }
        } catch (Exception ignored) {
        }
    }

    private void refreshCartUI(boolean persistCart) {
        renderGroupedCart();
        updateSelectedSummary();
        if (persistCart) {
            persistGlobalCart();
        }
    }

    private void renderGroupedCart() {
        cartGroupsContainer.removeAllViews();
        if (cartItems.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Your cart is empty.");
            emptyView.setTextSize(16f);
            cartGroupsContainer.addView(emptyView);
            return;
        }

        Map<String, RestaurantGroup> groups = new LinkedHashMap<>();
        for (CartItem item : cartItems) {
            String key = buildRestaurantKey(item.restaurantId, item.restaurantName);
            RestaurantGroup group = groups.get(key);
            if (group == null) {
                group = new RestaurantGroup(item.restaurantId, item.restaurantName);
                groups.put(key, group);
            }
            group.items.add(item);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (RestaurantGroup group : groups.values()) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundResource(R.drawable.view_border);
            box.setPadding(16, 16, 16, 16);

            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            boxParams.setMargins(0, 0, 0, 12);
            box.setLayoutParams(boxParams);

            TextView restaurantTitle = new TextView(this);
            restaurantTitle.setText(group.restaurantName);
            restaurantTitle.setTextSize(16f);
            restaurantTitle.setTypeface(restaurantTitle.getTypeface(), android.graphics.Typeface.BOLD);
            box.addView(restaurantTitle);

            for (CartItem item : group.items) {
                View row = inflater.inflate(R.layout.item_cart_entry, box, false);
                CheckBox checkBox = row.findViewById(R.id.cartItemCheckBox);
                TextView itemName = row.findViewById(R.id.cartItemNameTextView);
                TextView itemMeta = row.findViewById(R.id.cartItemMetaTextView);
                Button deleteButton = row.findViewById(R.id.btnDeleteCartItem);

                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(item.isSelected);
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    item.isSelected = isChecked;
                    updateSelectedSummary();
                });

                row.setOnClickListener(v -> {
                    item.isSelected = !item.isSelected;
                    checkBox.setChecked(item.isSelected);
                    updateSelectedSummary();
                });

                itemName.setText(item.name);
                itemMeta.setText(String.format(
                        Locale.getDefault(),
                        "Qty: %d   Unit: P%.2f   Line: P%.2f",
                        item.quantity,
                        item.price,
                        item.quantity * item.price
                ));

                deleteButton.setOnClickListener(v -> {
                    cartItems.remove(item);
                    refreshCartUI(true);
                    Toast.makeText(this, "Item removed from cart", Toast.LENGTH_SHORT).show();
                });

                box.addView(row);
            }

            cartGroupsContainer.addView(box);
        }
    }

    private JSONArray buildSelectedItemsJson() {
        JSONArray array = new JSONArray();
        for (CartItem item : cartItems) {
            if (!item.isSelected || item.quantity <= 0) {
                continue;
            }

            JSONObject obj = new JSONObject();
            try {
                obj.put("restaurant_id", item.restaurantId);
                obj.put("restaurant_name", item.restaurantName);
                obj.put("menu_item_id", item.menuItemId);
                obj.put("name", item.name);
                obj.put("quantity", item.quantity);
                obj.put("price", item.price);
            } catch (Exception ignored) {
            }
            array.put(obj);
        }
        return array;
    }

    private double calculateSelectedSubtotal() {
        double total = 0;
        for (CartItem item : cartItems) {
            if (item.isSelected) {
                total += item.price * item.quantity;
            }
        }
        return total;
    }

    private int countSelectedRestaurants() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (CartItem item : cartItems) {
            if (item.isSelected) {
                keys.add(buildRestaurantKey(item.restaurantId, item.restaurantName));
            }
        }
        return keys.size();
    }

    private int getSingleSelectedRestaurantId() {
        String singleKey = null;
        int id = -1;
        for (CartItem item : cartItems) {
            if (!item.isSelected) {
                continue;
            }

            String key = buildRestaurantKey(item.restaurantId, item.restaurantName);
            if (singleKey == null) {
                singleKey = key;
                id = item.restaurantId;
                continue;
            }

            if (!singleKey.equals(key)) {
                return -1;
            }
            if (id <= 0 && item.restaurantId > 0) {
                id = item.restaurantId;
            }
        }
        return id;
    }

    private String buildRestaurantKey(int id, String name) {
        String normalizedName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (!normalizedName.isEmpty()) {
            return "name:" + normalizedName;
        }
        return "id:" + id;
    }

    private void updateSelectedSummary() {
        int selectedCount = 0;
        for (CartItem item : cartItems) {
            if (item.isSelected) {
                selectedCount += item.quantity;
            }
        }

        double selectedSubtotal = calculateSelectedSubtotal();
        int selectedRestaurants = countSelectedRestaurants();

        subtotalTextView.setText(String.format(
                Locale.getDefault(),
                "Selected Items: %d | Restaurants: %d | Total: P%.2f",
                selectedCount,
                selectedRestaurants,
                selectedSubtotal
        ));
    }

    private void persistGlobalCart() {
        JSONArray array = new JSONArray();
        for (CartItem item : cartItems) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("restaurant_id", item.restaurantId);
                obj.put("restaurant_name", item.restaurantName);
                obj.put("menu_item_id", item.menuItemId);
                obj.put("name", item.name);
                obj.put("quantity", item.quantity);
                obj.put("price", item.price);
            } catch (Exception ignored) {
            }
            array.put(obj);
        }

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        prefs.edit().putString("global_cart_json", array.toString()).apply();
        cartItemsJson = array.toString();
    }

    private static class CartItem {
        int restaurantId;
        int menuItemId;
        String name;
        int quantity;
        double price;
        String restaurantName;
        boolean isSelected = true;

        CartItem(int restaurantId, int menuItemId, String name, int quantity, double price, String restaurantName) {
            this.restaurantId = restaurantId;
            this.menuItemId = menuItemId;
            this.name = name;
            this.quantity = quantity;
            this.price = price;
            this.restaurantName = restaurantName;
        }
    }

    private static class RestaurantGroup {
        int restaurantId;
        String restaurantName;
        List<CartItem> items = new ArrayList<>();

        RestaurantGroup(int restaurantId, String restaurantName) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
        }
    }
}
