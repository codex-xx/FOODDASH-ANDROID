package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private RequestQueue requestQueue;
    private int restaurantId = -1;
    private double subtotal = 0.0;
    private String cartItemsJson = "[]";
    private final List<OrderGroup> orderGroups = new ArrayList<>();
    private final List<Integer> placedOrderIds = new ArrayList<>();
    private int successfulGroupCount = 0;

    private EditText checkoutAddressEditText;
    private RadioGroup checkoutVehicleRadioGroup;
    private TextView checkoutSummaryTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        requestQueue = Volley.newRequestQueue(this);

        checkoutAddressEditText = findViewById(R.id.checkoutAddressEditText);
        checkoutVehicleRadioGroup = findViewById(R.id.checkoutVehicleRadioGroup);
        checkoutSummaryTextView = findViewById(R.id.checkoutSummaryTextView);
        Button btnConfirmPlaceOrder = findViewById(R.id.btnConfirmPlaceOrder);
        Button btnBackToCart = findViewById(R.id.btnBackToCart);

        restaurantId = getIntent().getIntExtra("restaurant_id", -1);
        subtotal = getIntent().getDoubleExtra("subtotal", 0.0);
        cartItemsJson = getIntent().getStringExtra("cart_items_json");
        if (cartItemsJson == null || cartItemsJson.trim().isEmpty()) {
            cartItemsJson = "[]";
        }

        buildOrderGroups();

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String savedAddress = prefs.getString("delivery_address", "");
        if (!TextUtils.isEmpty(savedAddress)) {
            checkoutAddressEditText.setText(savedAddress);
        }

        updateSummary();
        checkoutVehicleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> updateSummary());

        btnConfirmPlaceOrder.setOnClickListener(v -> placeOrder());
        btnBackToCart.setOnClickListener(v -> finish());
    }

    private void updateSummary() {
        if (orderGroups.isEmpty()) {
            checkoutSummaryTextView.setText("No selected items.");
            return;
        }

        double fee = getSelectedFee();
        StringBuilder builder = new StringBuilder();
        double allSubtotal = 0;
        for (OrderGroup group : orderGroups) {
            double groupTotal = group.subtotal + fee;
            allSubtotal += group.subtotal;
            builder.append(String.format(
                    Locale.getDefault(),
                    "%s\nSubtotal: P%.2f | Delivery: P%.2f | Total: P%.2f\n\n",
                    group.restaurantName,
                    group.subtotal,
                    fee,
                    groupTotal
            ));
        }

        double grand = allSubtotal + (fee * orderGroups.size());
        builder.append(String.format(Locale.getDefault(), "Orders: %d\nCombined Total To Pay: P%.2f", orderGroups.size(), grand));
        checkoutSummaryTextView.setText(builder.toString());
    }

    private String getSelectedDeliveryType() {
        int checkedId = checkoutVehicleRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.checkoutRadioTricycle) {
            return Constants.DELIVERY_TRICYCLE;
        }
        if (checkedId == R.id.checkoutRadioCab) {
            return Constants.DELIVERY_CAB;
        }
        return Constants.DELIVERY_MOTORCYCLE;
    }

    private double getSelectedFee() {
        String type = getSelectedDeliveryType();
        if (Constants.DELIVERY_TRICYCLE.equals(type)) {
            return Constants.FEE_TRICYCLE;
        }
        if (Constants.DELIVERY_CAB.equals(type)) {
            return Constants.FEE_CAB;
        }
        return Constants.FEE_MOTORCYCLE;
    }

    private void placeOrder() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        String token = prefs.getString("api_token", "");

        if (userId <= 0 || TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        String address = checkoutAddressEditText.getText().toString().trim();
        if (address.isEmpty()) {
            Toast.makeText(this, "Please enter delivery address", Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit().putString("delivery_address", address).apply();

        if (orderGroups.isEmpty()) {
            Toast.makeText(this, "No selected items found", Toast.LENGTH_SHORT).show();
            return;
        }

        placeOrderGroup(0, userId, token, address, getSelectedDeliveryType(), getSelectedFee());
    }

    private void placeOrderGroup(int index, int userId, String token, String address, String deliveryType, double deliveryFee) {
        if (index >= orderGroups.size()) {
            onAllGroupsPlaced();
            return;
        }

        OrderGroup group = orderGroups.get(index);
        double total = group.subtotal + deliveryFee;

        JSONObject payload = new JSONObject();
        try {
            payload.put("user_id", userId);
            payload.put("restaurant_id", group.restaurantId);
            payload.put("delivery_type", deliveryType);
            payload.put("delivery_fee", deliveryFee);
            payload.put("subtotal", group.subtotal);
            payload.put("total_amount", total);
            payload.put("delivery_address", address);
            payload.put("status", Constants.STATUS_PENDING);
            payload.put("items", group.items);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to build order payload", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_ORDERS,
                payload,
                response -> {
                    collectOrderId(response);
                    successfulGroupCount++;
                    removeOrderedItemsFromGlobalCart(group.items);
                    placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee);
                },
                error -> placeOrderGroupLegacy(index, userId, token, address, deliveryType, deliveryFee, payload)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void placeOrderGroupLegacy(int index, int userId, String token, String address, String deliveryType, double deliveryFee, JSONObject payload) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_PLACE_ORDER_LEGACY,
                payload,
                response -> {
                    collectOrderId(response);
                    successfulGroupCount++;
                    removeOrderedItemsFromGlobalCart(orderGroups.get(index).items);
                    placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee);
                },
                error -> Toast.makeText(this, "Failed to place one of the restaurant orders", Toast.LENGTH_LONG).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                if (!TextUtils.isEmpty(token)) {
                    headers.put("Authorization", "Bearer " + token);
                }
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void collectOrderId(JSONObject response) {
        int orderId = response.optInt("order_id", response.optInt("id", -1));
        JSONObject order = response.optJSONObject("order");
        if (order != null) {
            orderId = order.optInt("id", order.optInt("order_id", orderId));
        }

        if (orderId > 0) {
            placedOrderIds.add(orderId);
        }
    }

    private void onAllGroupsPlaced() {
        if (successfulGroupCount <= 0) {
            Toast.makeText(this, "No order was placed", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Placed " + successfulGroupCount + " separate order(s)", Toast.LENGTH_LONG).show();

        int trackingOrderId = placedOrderIds.isEmpty() ? -1 : placedOrderIds.get(placedOrderIds.size() - 1);

        Intent intent = new Intent(this, OrderTrackingActivity.class);
        intent.putExtra("order_id", trackingOrderId);
        startActivity(intent);
        finish();
    }

    private void buildOrderGroups() {
        orderGroups.clear();

        Map<String, OrderGroup> grouped = new LinkedHashMap<>();
        try {
            JSONArray array = new JSONArray(cartItemsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }

                int itemRestaurantId = item.optInt("restaurant_id", restaurantId);
                String itemRestaurantName = item.optString("restaurant_name", "Restaurant " + itemRestaurantId);
                int quantity = item.optInt("quantity", 0);
                double price = item.optDouble("price", 0.0);
                if (quantity <= 0) {
                    continue;
                }

                String restaurantKey = buildRestaurantKey(itemRestaurantId, itemRestaurantName);
                OrderGroup group = grouped.get(restaurantKey);
                if (group == null) {
                    group = new OrderGroup(itemRestaurantId, itemRestaurantName);
                    grouped.put(restaurantKey, group);
                } else {
                    if (group.restaurantId <= 0 && itemRestaurantId > 0) {
                        group.restaurantId = itemRestaurantId;
                    }
                }

                group.items.put(item);
                group.subtotal += quantity * price;
            }
        } catch (Exception ignored) {
        }

        orderGroups.addAll(grouped.values());
    }

    private String buildRestaurantKey(int restaurantId, String restaurantName) {
        String normalizedName = restaurantName == null ? "" : restaurantName.trim().toLowerCase(Locale.ROOT);
        if (!normalizedName.isEmpty()) {
            return "name:" + normalizedName;
        }
        return "id:" + restaurantId;
    }

    private void removeOrderedItemsFromGlobalCart(JSONArray placedItems) {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String rawCart = prefs.getString("global_cart_json", "[]");

        try {
            JSONArray cart = new JSONArray(rawCart);
            JSONArray remaining = new JSONArray();

            for (int i = 0; i < cart.length(); i++) {
                JSONObject cartItem = cart.optJSONObject(i);
                if (cartItem == null) {
                    continue;
                }

                if (!containsItem(placedItems, cartItem)) {
                    remaining.put(cartItem);
                }
            }

            prefs.edit().putString("global_cart_json", remaining.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private boolean containsItem(JSONArray placedItems, JSONObject cartItem) {
        int cartRestaurantId = cartItem.optInt("restaurant_id", -1);
        int cartMenuItemId = cartItem.optInt("menu_item_id", -1);

        for (int i = 0; i < placedItems.length(); i++) {
            JSONObject placedItem = placedItems.optJSONObject(i);
            if (placedItem == null) {
                continue;
            }

            int placedRestaurantId = placedItem.optInt("restaurant_id", -1);
            int placedMenuItemId = placedItem.optInt("menu_item_id", -1);

            if (cartRestaurantId == placedRestaurantId && cartMenuItemId == placedMenuItemId) {
                return true;
            }
        }

        return false;
    }

    private static class OrderGroup {
        int restaurantId;
        String restaurantName;
        JSONArray items = new JSONArray();
        double subtotal = 0.0;

        OrderGroup(int restaurantId, String restaurantName) {
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
        }
    }
}
