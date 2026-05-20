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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CartActivity extends AppCompatActivity {

    private final List<CartItem> cartItems = new ArrayList<>();
    private TextView subtotalTextView;
    private LinearLayout cartGroupsContainer;
    private Button btnProceedCheckout;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private TextView tabNotificationsBadgeTextView;
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
        btnProceedCheckout = findViewById(R.id.btnProceedCheckout);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);

        restaurantId = getIntent().getIntExtra("restaurant_id", -1);
        cartItemsJson = getIntent().getStringExtra("cart_items_json");
        if (cartItemsJson == null || cartItemsJson.trim().isEmpty()) {
            cartItemsJson = "[]";
        }

        parseCartItems(cartItemsJson);
        setupBottomNavigation();
        updateNotificationsTabCount();

        cartHeaderTextView.setText("Grouped Cart Summary");
        refreshCartUI(false);

        btnProceedCheckout.setText("Checkout All Selected");
        btnProceedCheckout.setOnClickListener(v -> {
            JSONArray selectedItemsJson = buildSelectedItemsJson();
            if (selectedItemsJson.length() == 0) {
                Toast.makeText(this, "Please select at least one item", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, CheckoutActivity.class);
            intent.putExtra("restaurant_id", -1);
            intent.putExtra("subtotal", calculateSelectedSubtotal());
            intent.putExtra("cart_items_json", selectedItemsJson.toString());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
        updateNotificationsTabCount();
    }

    private void parseCartItems(String json) {
        cartItems.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;

                int itemRestaurantId = obj.optInt("restaurant_id", restaurantId);
                int menuItemId = obj.optInt("menu_item_id", obj.optInt("id", -1));
                String name = obj.optString("name", "Item");
                int quantity = obj.optInt("quantity", 1);
                double price = obj.optDouble("price", 0.0);
                String restaurantName = obj.optString("restaurant_name", "Restaurant #" + itemRestaurantId);
                
                cartItems.add(new CartItem(itemRestaurantId, menuItemId, name, quantity, price, restaurantName));
            }
        } catch (Exception ignored) {}
    }

    private void refreshCartUI(boolean persistCart) {
        renderGroupedCart();
        updateSelectedSummary();
        updateCartBadge();
        if (persistCart) {
            persistGlobalCart();
        }
        
        updateCheckoutButtonVisibility();
    }

    private void updateCheckoutButtonVisibility() {
        Set<Integer> selectedRestaurantIds = new HashSet<>();
        for (CartItem item : cartItems) {
            if (item.isSelected) {
                selectedRestaurantIds.add(item.restaurantId);
            }
        }
        
        // Hide "Checkout All" if items from more than one restaurant are selected
        if (selectedRestaurantIds.size() > 1) {
            btnProceedCheckout.setVisibility(View.GONE);
        } else if (selectedRestaurantIds.size() == 1) {
            btnProceedCheckout.setVisibility(View.VISIBLE);
        } else {
            btnProceedCheckout.setVisibility(View.GONE);
        }
    }

    private void renderGroupedCart() {
        cartGroupsContainer.removeAllViews();
        if (cartItems.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Your cart is empty.");
            emptyView.setGravity(android.view.Gravity.CENTER);
            emptyView.setPadding(0, 50, 0, 0);
            cartGroupsContainer.addView(emptyView);
            return;
        }

        Map<String, RestaurantGroup> groups = new LinkedHashMap<>();
        for (CartItem item : cartItems) {
            String key = "id:" + item.restaurantId + ":" + item.restaurantName.toLowerCase();
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
            box.setPadding(24, 24, 24, 24);

            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            boxParams.setMargins(0, 0, 0, 32);
            box.setLayoutParams(boxParams);

            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            header.setPadding(0, 0, 0, 16);

            TextView restaurantTitle = new TextView(this);
            restaurantTitle.setText(group.restaurantName);
            restaurantTitle.setTextSize(18f);
            restaurantTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            restaurantTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            header.addView(restaurantTitle);

            Button btnStoreCheckout = new Button(this, null, android.R.attr.buttonStyleSmall);
            btnStoreCheckout.setText("Checkout Store");
            btnStoreCheckout.setOnClickListener(v -> proceedToCheckoutForGroup(group));
            header.addView(btnStoreCheckout);

            box.addView(header);

            double groupSubtotal = 0;
            for (CartItem item : group.items) {
                View row = inflater.inflate(R.layout.item_cart_entry, box, false);
                CheckBox checkBox = row.findViewById(R.id.cartItemCheckBox);
                TextView itemName = row.findViewById(R.id.cartItemNameTextView);
                TextView itemMeta = row.findViewById(R.id.cartItemMetaTextView);
                Button deleteButton = row.findViewById(R.id.btnDeleteCartItem);

                checkBox.setOnCheckedChangeListener(null);
                checkBox.setChecked(item.isSelected);
                checkBox.setOnCheckedChangeListener((bv, isChecked) -> {
                    item.isSelected = isChecked;
                    refreshCartUI(false);
                });

                row.setOnClickListener(v -> {
                    item.isSelected = !item.isSelected;
                    checkBox.setChecked(item.isSelected);
                    refreshCartUI(false);
                });

                itemName.setText(item.name);
                double linePrice = item.quantity * item.price;
                itemMeta.setText(String.format(Locale.getDefault(), "Qty: %d | P%.2f ea | Total: P%.2f", item.quantity, item.price, linePrice));
                
                if (item.isSelected) groupSubtotal += linePrice;

                deleteButton.setOnClickListener(v -> {
                    cartItems.remove(item);
                    refreshCartUI(true);
                    Toast.makeText(this, "Item removed", Toast.LENGTH_SHORT).show();
                });

                box.addView(row);
            }

            TextView groupFooter = new TextView(this);
            groupFooter.setText(String.format(Locale.getDefault(), "Subtotal: P%.2f", groupSubtotal));
            groupFooter.setGravity(android.view.Gravity.END);
            groupFooter.setPadding(0, 16, 0, 0);
            groupFooter.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
            box.addView(groupFooter);

            cartGroupsContainer.addView(box);
        }
    }

    private void proceedToCheckoutForGroup(RestaurantGroup group) {
        JSONArray selectedArray = new JSONArray();
        double subtotal = 0;
        for (CartItem item : group.items) {
            if (item.isSelected) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("restaurant_id", item.restaurantId);
                    obj.put("restaurant_name", item.restaurantName);
                    obj.put("menu_item_id", item.menuItemId);
                    obj.put("name", item.name);
                    obj.put("quantity", item.quantity);
                    obj.put("price", item.price);
                    selectedArray.put(obj);
                    subtotal += item.quantity * item.price;
                } catch (Exception ignored) {}
            }
        }

        if (selectedArray.length() == 0) {
            Toast.makeText(this, "No items selected from this restaurant", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, CheckoutActivity.class);
        intent.putExtra("restaurant_id", group.restaurantId);
        intent.putExtra("subtotal", subtotal);
        intent.putExtra("cart_items_json", selectedArray.toString());
        startActivity(intent);
    }

    private JSONArray buildSelectedItemsJson() {
        JSONArray array = new JSONArray();
        for (CartItem item : cartItems) {
            if (item.isSelected) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("restaurant_id", item.restaurantId);
                    obj.put("restaurant_name", item.restaurantName);
                    obj.put("menu_item_id", item.menuItemId);
                    obj.put("name", item.name);
                    obj.put("quantity", item.quantity);
                    obj.put("price", item.price);
                    array.put(obj);
                } catch (Exception ignored) {}
            }
        }
        return array;
    }

    private double calculateSelectedSubtotal() {
        double total = 0;
        for (CartItem item : cartItems) {
            if (item.isSelected) total += item.price * item.quantity;
        }
        return total;
    }

    private void updateSelectedSummary() {
        int count = 0;
        for (CartItem item : cartItems) if (item.isSelected) count += item.quantity;
        subtotalTextView.setText(String.format(Locale.getDefault(), "Grand Total (Selected): P%.2f", calculateSelectedSubtotal()));
    }

    private void persistGlobalCart() {
        JSONArray array = new JSONArray();
        for (CartItem item : cartItems) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("restaurant_id", item.restaurantId);
                obj.put("restaurant_name", item.restaurantName);
                obj.put("menu_item_id", item.menuItemId);
                obj.put("name", item.name);
                obj.put("quantity", item.quantity);
                obj.put("price", item.price);
                array.put(obj);
            } catch (Exception ignored) {}
        }
        getSharedPreferences("fooddash_prefs", MODE_PRIVATE).edit().putString("global_cart_json", array.toString()).apply();
    }

    private void setupBottomNavigation() {
        highlightBottomTab(tabCartButton);

        if (tabHomeButton != null) {
            tabHomeButton.setOnClickListener(v -> {
                highlightBottomTab(tabHomeButton);
                Intent intent = new Intent(this, CustomerDashboard.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }

        if (tabOrdersButton != null) {
            tabOrdersButton.setOnClickListener(v -> {
                highlightBottomTab(tabOrdersButton);
                startActivity(new Intent(this, OrderTrackingActivity.class));
                finish();
            });
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> highlightBottomTab(tabCartButton));
        }

        if (tabNotificationsButton != null) {
            tabNotificationsButton.setOnClickListener(v -> {
                highlightBottomTab(tabNotificationsButton);
                startActivity(new Intent(this, NotificationActivity.class));
                finish();
            });
        }

        if (tabProfileButton != null) {
            tabProfileButton.setOnClickListener(v -> {
                highlightBottomTab(tabProfileButton);
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
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
        int totalItems = 0;
        for (CartItem item : cartItems) totalItems += item.quantity;
        if (totalItems <= 0) {
            tabCartBadgeTextView.setVisibility(View.GONE);
            return;
        }
        tabCartBadgeTextView.setVisibility(View.VISIBLE);
        tabCartBadgeTextView.setText(totalItems > 99 ? "99+" : String.valueOf(totalItems));
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

    private int getNotificationCountFromPrefs() {
        return NotificationStore.getUnreadGroupCount(this);
    }

    private static class CartItem {
        int restaurantId, menuItemId, quantity;
        String name, restaurantName;
        double price;
        boolean isSelected = true;
        CartItem(int resId, int menuId, String name, int qty, double pr, String resName) {
            this.restaurantId = resId; this.menuItemId = menuId; this.name = name; this.quantity = qty; this.price = pr; this.restaurantName = resName;
        }
    }

    private static class RestaurantGroup {
        int restaurantId;
        String restaurantName;
        List<CartItem> items = new ArrayList<>();
        RestaurantGroup(int id, String name) { this.restaurantId = id; this.restaurantName = name; }
    }
}
