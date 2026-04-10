package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = "CheckoutActivity";
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

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.CHECKOUT,
                AccessControlManager.Action.READ)) {
            return;
        }

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
        if (!AccessControlManager.canPerform(this,
                AccessControlManager.Resource.ORDERS,
                AccessControlManager.Action.WRITE)) {
            Toast.makeText(this, "Access denied for this action", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);

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

        placeOrderGroup(0, userId, token, address, getSelectedDeliveryType(), getSelectedFee(), prefs);
    }

    private void placeOrderGroup(int index, int userId, String token, String address, String deliveryType, double deliveryFee, SharedPreferences prefs) {
        if (index >= orderGroups.size()) {
            onAllGroupsPlaced();
            return;
        }

        OrderGroup group = orderGroups.get(index);
        
        // Final safety check: if restaurantId is still invalid, we cannot proceed with this group.
        if (group.restaurantId <= 0) {
            Log.e(TAG, "Skipping group due to invalid restaurant_id: " + group.restaurantName);
            placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee, prefs);
            return;
        }

        double total = group.subtotal + deliveryFee;

        JSONObject payload = new JSONObject();
        try {
            payload.put("user_id", userId);
            payload.put("customer_id", userId);
            payload.put("restaurant_id", group.restaurantId);
            payload.put("delivery_address", address);
            payload.put("address", address);
            payload.put("delivery_type", deliveryType);
            payload.put("vehicle_type", deliveryType);
            payload.put("delivery_method", deliveryType);
            payload.put("delivery_fee", deliveryFee);
            payload.put("fee", deliveryFee);
            payload.put("subtotal", group.subtotal);
            payload.put("total_amount", total);
            payload.put("total", total);
            payload.put("grand_total", total);
            payload.put("total_price", total);
            payload.put("status", "pending");
            payload.put("payment_method", "cod");
            payload.put("payment_type", "cod");
            payload.put("order_type", "delivery");
            payload.put("token", token);
            payload.put("api_token", token);

            JSONArray mappedItems = new JSONArray();
            for (int i = 0; i < group.items.length(); i++) {
                JSONObject original = group.items.optJSONObject(i);
                if (original == null) continue;
                
                JSONObject mapped = new JSONObject();
                int mid = original.optInt("menu_item_id", original.optInt("id", -1));
                mapped.put("menu_item_id", mid);
                mapped.put("id", mid);
                mapped.put("item_id", mid);
                mapped.put("food_id", mid);
                mapped.put("product_id", mid);
                mapped.put("name", original.optString("name"));
                mapped.put("quantity", original.optInt("quantity", 1));
                mapped.put("qty", original.optInt("quantity", 1));
                mapped.put("price", original.optDouble("price", 0.0));
                mapped.put("unit_price", original.optDouble("price", 0.0));
                mappedItems.put(mapped);
            }
            payload.put("items", mappedItems);
            payload.put("order_items", mappedItems);
            payload.put("cart", mappedItems);
            
            if (mappedItems.length() == 0) {
                 Log.e(TAG, "Skipping group due to empty items: " + group.restaurantName);
                 placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee, prefs);
                 return;
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "Error building request", Toast.LENGTH_SHORT).show();
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
                    placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee, prefs);
                },
                error -> {
                    logVolleyError("Primary API Error", error);
                    // Fallback to legacy
                    placeOrderGroupLegacy(index, userId, token, address, deliveryType, deliveryFee, payload, prefs);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                headers.put("Accept", "application/json");
                headers.put("Content-Type", "application/json");
                headers.put("X-Requested-With", "XMLHttpRequest");
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void placeOrderGroupLegacy(int index, int userId, String token, String address, String deliveryType, double deliveryFee, JSONObject jsonPayload, SharedPreferences prefs) {
        String legacyUrl = Constants.URL_PLACE_ORDER_LEGACY;
        if (!legacyUrl.contains("api_token=")) {
            legacyUrl += (legacyUrl.contains("?") ? "&" : "?") + "api_token=" + android.net.Uri.encode(token);
        }

        OrderGroup group = orderGroups.get(index);
        final double total = group.subtotal + deliveryFee;

        StringRequest request = new StringRequest(
                Request.Method.POST,
                legacyUrl,
                response -> {
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        collectOrderId(jsonResponse);
                    } catch (Exception e) {
                        Log.d(TAG, "Legacy response not JSON: " + response);
                    }
                    successfulGroupCount++;
                    removeOrderedItemsFromGlobalCart(group.items);
                    placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee, prefs);
                },
                error -> {
                    logVolleyError("Legacy API Error", error);
                    // If everything fails, still try next group
                    placeOrderGroup(index + 1, userId, token, address, deliveryType, deliveryFee, prefs);
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("user_id", String.valueOf(userId));
                params.put("customer_id", String.valueOf(userId));
                params.put("restaurant_id", String.valueOf(group.restaurantId));
                params.put("address", address);
                params.put("delivery_address", address);
                params.put("delivery_type", deliveryType);
                params.put("delivery_fee", String.format(Locale.US, "%.2f", deliveryFee));
                params.put("subtotal", String.format(Locale.US, "%.2f", group.subtotal));
                params.put("total", String.format(Locale.US, "%.2f", total));
                params.put("total_amount", String.format(Locale.US, "%.2f", total));
                params.put("payment_method", "cod");
                params.put("status", "pending");
                
                JSONArray items = jsonPayload.optJSONArray("items");
                if (items != null) {
                    params.put("items", items.toString());
                    params.put("order_items", items.toString());
                }
                return params;
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void logVolleyError(String prefix, VolleyError error) {
        String message = prefix + ": " + error.toString();
        if (error.networkResponse != null) {
            try {
                String body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                message += " | Code: " + error.networkResponse.statusCode + " | Body: " + body;
            } catch (Exception ignored) {}
        }
        Log.e(TAG, message);
    }

    private void collectOrderId(JSONObject response) {
        int orderId = response.optInt("order_id", response.optInt("id", -1));
        if (orderId <= 0) {
            JSONObject data = response.optJSONObject("data");
            if (data != null) orderId = data.optInt("id", data.optInt("order_id", -1));
        }
        if (orderId > 0) placedOrderIds.add(orderId);
    }

    private void onAllGroupsPlaced() {
        if (successfulGroupCount <= 0) {
            Toast.makeText(this, "Checkout failed. Please check the network.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Order placed successfully!", Toast.LENGTH_SHORT).show();
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
                if (item == null) continue;

                int itemResId = item.optInt("restaurant_id", -1);
                String itemResName = item.optString("restaurant_name", "Restaurant " + itemResId);
                int qty = item.optInt("quantity", 0);
                double price = item.optDouble("price", 0.0);
                if (qty <= 0) continue;

                String key = buildRestaurantKey(itemResId, itemResName);
                OrderGroup group = grouped.get(key);
                if (group == null) {
                    group = new OrderGroup(itemResId, itemResName);
                    grouped.put(key, group);
                }
                
                // If the group had an invalid ID (-1) but this item has a valid one, update the group.
                if (group.restaurantId <= 0 && itemResId > 0) {
                    group.restaurantId = itemResId;
                }
                
                group.items.put(item);
                group.subtotal += qty * price;
            }
            
            // Final fallback: if any group still has no valid ID, try using the top-level restaurantId
            for (OrderGroup g : grouped.values()) {
                if (g.restaurantId <= 0 && restaurantId > 0) {
                    g.restaurantId = restaurantId;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error building order groups", e);
        }
        orderGroups.addAll(grouped.values());
    }

    private String buildRestaurantKey(int resId, String resName) {
        String n = resName == null ? "" : resName.trim().toLowerCase(Locale.ROOT);
        return n.isEmpty() ? "id:" + resId : "name:" + n;
    }

    private void removeOrderedItemsFromGlobalCart(JSONArray placedItems) {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray cart = new JSONArray(prefs.getString("global_cart_json", "[]"));
            JSONArray remaining = new JSONArray();
            for (int i = 0; i < cart.length(); i++) {
                JSONObject cartItem = cart.optJSONObject(i);
                if (cartItem != null && !containsItem(placedItems, cartItem)) remaining.put(cartItem);
            }
            prefs.edit().putString("global_cart_json", remaining.toString()).apply();
        } catch (Exception ignored) {}
    }

    private boolean containsItem(JSONArray placedItems, JSONObject cartItem) {
        int cResId = cartItem.optInt("restaurant_id", -1);
        int cMenuId = cartItem.optInt("menu_item_id", cartItem.optInt("id", -1));
        for (int i = 0; i < placedItems.length(); i++) {
            JSONObject pItem = placedItems.optJSONObject(i);
            if (pItem == null) continue;
            int pResId = pItem.optInt("restaurant_id", -1);
            int pMenuId = pItem.optInt("menu_item_id", pItem.optInt("id", -1));
            if (cResId == pResId && cMenuId == pMenuId) return true;
        }
        return false;
    }

    private static class OrderGroup {
        int restaurantId;
        String restaurantName;
        JSONArray items = new JSONArray();
        double subtotal = 0.0;
        OrderGroup(int id, String name) { this.restaurantId = id; this.restaurantName = name; }
    }
}
