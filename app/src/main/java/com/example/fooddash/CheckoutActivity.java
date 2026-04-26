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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
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
    private double grandSubtotal = 0.0;
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

        grandSubtotal = getIntent().getDoubleExtra("subtotal", 0.0);
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

        btnConfirmPlaceOrder.setOnClickListener(v -> placeOrders());
        btnBackToCart.setOnClickListener(v -> finish());
    }

    private void buildOrderGroups() {
        orderGroups.clear();
        Map<String, OrderGroup> groups = new LinkedHashMap<>();
        try {
            JSONArray array = new JSONArray(cartItemsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                int resId = item.optInt("restaurant_id", -1);
                String resName = item.optString("restaurant_name", "Store #" + resId);
                
                String key = "id:" + resId + ":" + resName.toLowerCase();
                OrderGroup group = groups.get(key);
                if (group == null) {
                    group = new OrderGroup(resId, resName);
                    groups.put(key, group);
                }
                group.items.put(item);
                group.subtotal += item.optInt("quantity", 1) * item.optDouble("price", 0.0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error grouping items", e);
        }
        orderGroups.addAll(groups.values());
    }

    private void updateSummary() {
        if (orderGroups.isEmpty()) {
            checkoutSummaryTextView.setText("No items selected.");
            return;
        }

        double fee = getSelectedFee();
        StringBuilder builder = new StringBuilder("Order Summary:\n\n");
        double grandTotal = 0;
        
        for (OrderGroup group : orderGroups) {
            double total = group.subtotal + fee;
            grandTotal += total;
            builder.append(String.format(Locale.getDefault(), 
                "%s\nSubtotal: P%.2f\nDelivery Fee: P%.2f\nTotal: P%.2f\n\n",
                group.restaurantName, group.subtotal, fee, total));
        }

        builder.append(String.format(Locale.getDefault(), "Grand Total: P%.2f", grandTotal));
        if (orderGroups.size() > 1) {
            builder.append("\n(Total of ").append(orderGroups.size()).append(" separate orders)");
        }
        checkoutSummaryTextView.setText(builder.toString());
    }

    private double getSelectedFee() {
        int checkedId = checkoutVehicleRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.checkoutRadioTricycle) return Constants.FEE_TRICYCLE;
        if (checkedId == R.id.checkoutRadioCab) return Constants.FEE_CAB;
        return Constants.FEE_MOTORCYCLE;
    }

    private String getSelectedDeliveryType() {
        int checkedId = checkoutVehicleRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.checkoutRadioTricycle) return Constants.DELIVERY_TRICYCLE;
        if (checkedId == R.id.checkoutRadioCab) return Constants.DELIVERY_CAB;
        return Constants.DELIVERY_MOTORCYCLE;
    }

    private void placeOrders() {
        String address = checkoutAddressEditText.getText().toString().trim();
        if (address.isEmpty()) {
            Toast.makeText(this, "Please enter delivery address", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        prefs.edit().putString("delivery_address", address).apply();

        int userId = prefs.getInt("user_id", -1);
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);

        if (userId <= 0 || TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        successfulGroupCount = 0;
        placedOrderIds.clear();
        processGroup(0, userId, token, address, getSelectedDeliveryType(), getSelectedFee());
    }

    private void processGroup(int index, int userId, String token, String address, String type, double fee) {
        if (index >= orderGroups.size()) {
            finalizeCheckout();
            return;
        }

        OrderGroup group = orderGroups.get(index);
        double total = group.subtotal + fee;

        JSONObject payload = new JSONObject();
        try {
            payload.put("user_id", userId);
            payload.put("restaurant_id", group.restaurantId);
            payload.put("address", address);
            payload.put("delivery_address", address);
            payload.put("delivery_type", type);
            payload.put("delivery_fee", fee);
            payload.put("subtotal", group.subtotal);
            payload.put("total_amount", total);
            payload.put("status", Constants.STATUS_PENDING);
            payload.put("payment_method", "cod");
            payload.put("items", group.items);
            payload.put("api_token", token);
        } catch (Exception e) {
            processGroup(index + 1, userId, token, address, type, fee);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, Constants.URL_ORDERS, payload,
                response -> {
                    successfulGroupCount++;
                    int id = response.optInt("order_id", response.optInt("id", -1));
                    if (id > 0) placedOrderIds.add(id);
                    removeItemsFromGlobalCart(group.items);
                    processGroup(index + 1, userId, token, address, type, fee);
                },
                error -> {
                    Log.e(TAG, "Failed order for " + group.restaurantName, error);
                    // Try legacy as fallback
                    placeLegacyOrder(index, userId, token, address, type, fee, payload);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> h = new HashMap<>();
                h.put("Authorization", "Bearer " + token);
                return h;
            }
        };
        requestQueue.add(request);
    }

    private void placeLegacyOrder(int index, int userId, String token, String address, String type, double fee, JSONObject payload) {
        OrderGroup group = orderGroups.get(index);
        StringRequest request = new StringRequest(Request.Method.POST, Constants.URL_PLACE_ORDER_LEGACY,
                response -> {
                    successfulGroupCount++;
                    removeItemsFromGlobalCart(group.items);
                    processGroup(index + 1, userId, token, address, type, fee);
                },
                error -> {
                    Log.e(TAG, "Legacy failed too", error);
                    processGroup(index + 1, userId, token, address, type, fee);
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> p = new HashMap<>();
                p.put("user_id", String.valueOf(userId));
                p.put("restaurant_id", String.valueOf(group.restaurantId));
                p.put("address", address);
                p.put("delivery_type", type);
                p.put("delivery_fee", String.valueOf(fee));
                p.put("total", String.valueOf(group.subtotal + fee));
                p.put("items", group.items.toString());
                p.put("api_token", token);
                return p;
            }
        };
        requestQueue.add(request);
    }

    private void removeItemsFromGlobalCart(JSONArray items) {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray cart = new JSONArray(prefs.getString("global_cart_json", "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < cart.length(); i++) {
                JSONObject cartItem = cart.optJSONObject(i);
                if (cartItem == null) continue;
                boolean found = false;
                for (int j = 0; j < items.length(); j++) {
                    JSONObject placed = items.optJSONObject(j);
                    if (placed != null && placed.optInt("menu_item_id") == cartItem.optInt("menu_item_id")) {
                        found = true;
                        break;
                    }
                }
                if (!found) updated.put(cartItem);
            }
            prefs.edit().putString("global_cart_json", updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void finalizeCheckout() {
        if (successfulGroupCount > 0) {
            Toast.makeText(this, "Order(s) placed successfully!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, OrderTrackingActivity.class);
            if (!placedOrderIds.isEmpty()) intent.putExtra("order_id", placedOrderIds.get(0));
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Failed to place orders. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private static class OrderGroup {
        int restaurantId;
        String restaurantName;
        JSONArray items = new JSONArray();
        double subtotal = 0.0;
        OrderGroup(int id, String name) { this.restaurantId = id; this.restaurantName = name; }
    }
}
