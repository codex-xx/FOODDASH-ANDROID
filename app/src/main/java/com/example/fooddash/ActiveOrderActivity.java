package com.example.fooddash;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ActiveOrderActivity extends AppCompatActivity {

    private static final String TAG = "ActiveOrderActivity";

    private TextView activeOrderIdText, activeCustomerName, activeCustomerContact, activeDeliveryAddress;
    private TextView activeRestaurantName, activeOrderItems, activeOrderStatus;
    private Button btnArrived, btnPickedUp, btnOnTheWay, btnDelivered, btnBackToDashboard;

    private RequestQueue requestQueue;
    private JSONObject activeOrder;
    private int orderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_order);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.ACTIVE_ORDER,
                AccessControlManager.Action.READ)) {
            return;
        }

        requestQueue = Volley.newRequestQueue(this);

        activeOrderIdText = findViewById(R.id.activeOrderIdText);
        activeCustomerName = findViewById(R.id.activeCustomerName);
        activeCustomerContact = findViewById(R.id.activeCustomerContact);
        activeDeliveryAddress = findViewById(R.id.activeDeliveryAddress);
        activeRestaurantName = findViewById(R.id.activeRestaurantName);
        activeOrderItems = findViewById(R.id.activeOrderItems);
        activeOrderStatus = findViewById(R.id.activeOrderStatus);

        btnArrived = findViewById(R.id.btnArrived);
        btnPickedUp = findViewById(R.id.btnPickedUp);
        btnOnTheWay = findViewById(R.id.btnOnTheWay);
        btnDelivered = findViewById(R.id.btnDelivered);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);

        String orderJson = getIntent().getStringExtra("order_json");
        if (orderJson != null) {
            try {
                activeOrder = new JSONObject(orderJson);
                orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", -1));
                renderOrderDetails();
                fetchFullOrderDetails();
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing order JSON", e);
                finish();
            }
        } else {
            finish();
        }

        if (btnArrived != null) {
            btnArrived.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_ARRIVED_RESTAURANT));
        }
        btnPickedUp.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_PICKED_UP));
        btnOnTheWay.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_ON_THE_WAY));
        btnDelivered.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_DELIVERED));
        
        btnBackToDashboard.setOnClickListener(v -> finish());
    }

    private void fetchFullOrderDetails() {
        if (orderId <= 0) return;

        String url = Constants.URL_ORDERS + "/" + orderId;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    JSONObject freshOrder = response.optJSONObject("order");
                    if (freshOrder == null) freshOrder = response.optJSONObject("data");
                    if (freshOrder == null) freshOrder = response;

                    if (freshOrder != null && freshOrder.length() > 0) {
                        activeOrder = freshOrder;
                        orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", orderId));
                        renderOrderDetails();
                    }
                },
                error -> fetchFullOrderDetailsLegacy()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void fetchFullOrderDetailsLegacy() {
        String url = Constants.URL_GET_ORDERS_LEGACY + "?order_id=" + orderId;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    JSONArray orders = response.optJSONArray("orders");
                    if (orders == null) orders = response.optJSONArray("data");
                    if (orders != null && orders.length() > 0) {
                        activeOrder = orders.optJSONObject(0);
                        orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", orderId));
                        renderOrderDetails();
                    }
                },
                error -> Log.e(TAG, "Failed to fetch order details from all sources")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void renderOrderDetails() {
        if (activeOrder == null) return;

        activeOrderIdText.setText("Order #" + orderId);
        activeCustomerName.setText(activeOrder.optString("customer_name", "Customer"));
        
        String contact = firstNonEmpty(
                activeOrder.optString("customer_phone"),
                activeOrder.optString("phone_number"),
                activeOrder.optString("mobile"),
                activeOrder.optString("customer_contact"), 
                activeOrder.optString("contact_number"), 
                activeOrder.optString("phone"),
                "N/A"
        );
        activeCustomerContact.setText("Contact: " + contact);
        
        String address = firstNonEmpty(
                activeOrder.optString("delivery_address"), 
                activeOrder.optString("address"), 
                "N/A"
        );
        activeDeliveryAddress.setText("Address: " + address);
        
        activeRestaurantName.setText(activeOrder.optString("restaurant_name", "Restaurant"));
        
        StringBuilder itemsBuilder = new StringBuilder();
        
        JSONArray itemsArray = activeOrder.optJSONArray("items");
        if (itemsArray == null) itemsArray = activeOrder.optJSONArray("order_items");
        
        if (itemsArray == null) {
            String itemsStr = activeOrder.optString("items", "");
            if (itemsStr.startsWith("[")) {
                try {
                    itemsArray = new JSONArray(itemsStr);
                } catch (JSONException ignored) {}
            }
        }

        if (itemsArray != null && itemsArray.length() > 0) {
            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject item = itemsArray.optJSONObject(i);
                if (item != null) {
                    String name = firstNonEmpty(
                        item.optString("name"), 
                        item.optString("food_name"), 
                        item.optString("item_name"), 
                        item.optString("menu_item_name"),
                        item.optString("product_name")
                    );
                    int qty = item.optInt("quantity", item.optInt("qty", 1));
                    if (!name.isEmpty()) {
                        itemsBuilder.append(qty).append("x ").append(name).append("\n");
                    }
                }
            }
        } else {
            String summary = firstNonEmpty(
                activeOrder.optString("items_summary"), 
                activeOrder.optString("order_details"),
                activeOrder.optString("items")
            );
            if (!summary.isEmpty()) {
                itemsBuilder.append(summary);
            }
        }

        String finalItems = itemsBuilder.toString().trim();
        activeOrderItems.setText(finalItems.isEmpty() ? "Items:\n(Loading item list...)" : "Items:\n" + finalItems);
        
        String status = normalizeStatus(activeOrder.optString("status", "pending"));
        activeOrderStatus.setText("Status: " + status.toUpperCase());
        
        updateButtonVisibilities(status);
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty() && !v.equals("null") && !v.equals("undefined")) {
                return v.trim();
            }
        }
        return "";
    }

    private String normalizeStatus(String status) {
        if (status == null) return "pending";
        String n = status.toLowerCase();
        if (n.contains("assigned")) return Constants.STATUS_ASSIGNED;
        if (n.contains("accepted")) return Constants.STATUS_ACCEPTED;
        if (n.contains("arrived")) return Constants.STATUS_ARRIVED_RESTAURANT;
        if (n.contains("picked")) return Constants.STATUS_PICKED_UP;
        if (n.contains("way") || n.contains("transit")) return Constants.STATUS_ON_THE_WAY;
        if (n.contains("deliver")) return Constants.STATUS_DELIVERED;
        if (n.contains("ready")) return Constants.STATUS_READY;
        if (n.contains("prepar")) return Constants.STATUS_PREPARING;
        return n;
    }

    private void updateButtonVisibilities(String status) {
        if (btnArrived != null) btnArrived.setVisibility(View.GONE);
        btnPickedUp.setVisibility(View.GONE);
        btnOnTheWay.setVisibility(View.GONE);
        btnDelivered.setVisibility(View.GONE);

        if (Constants.STATUS_ASSIGNED.equals(status) || Constants.STATUS_ACCEPTED.equals(status)) {
            if (btnArrived != null) btnArrived.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_ARRIVED_RESTAURANT.equals(status) || 
                   Constants.STATUS_READY.equals(status) || 
                   Constants.STATUS_PREPARING.equals(status)) {
            btnPickedUp.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_PICKED_UP.equals(status)) {
            btnOnTheWay.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_ON_THE_WAY.equals(status)) {
            btnDelivered.setVisibility(View.VISIBLE);
        }
    }

    private void updateOrderStatus(String status) {
        if (!AccessControlManager.canPerform(this,
                AccessControlManager.Resource.ORDERS,
                AccessControlManager.Action.UPDATE)) {
            Toast.makeText(this, "Access denied for this action", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            int driverId = getDriverId();
            payload.put("order_id", orderId);
            payload.put("orderid", orderId);
            payload.put("driver_id", driverId);
            payload.put("user_id", driverId);
            payload.put("status", status);
        } catch (JSONException e) {
            return;
        }

        // Try driver/accept_order first if status is assigned, as it's the more robust endpoint on some backends
        String primaryUrl = (Constants.STATUS_ASSIGNED.equals(status)) ? Constants.URL_DRIVER_ACCEPT_ORDER : Constants.URL_UPDATE_STATUS;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                primaryUrl,
                payload,
                response -> handleStatusUpdateSuccess(status),
                error -> {
                    Log.e(TAG, "Update status failed, trying fallbacks", error);
                    if (primaryUrl.equals(Constants.URL_DRIVER_ACCEPT_ORDER)) {
                        updateStatusViaGeneralEndpoint(payload, status);
                    } else {
                        updateStatusFallbackPHP(payload, status);
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void updateStatusViaGeneralEndpoint(JSONObject payload, String status) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_STATUS,
                payload,
                response -> handleStatusUpdateSuccess(status),
                error -> updateStatusFallbackPHP(payload, status)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void updateStatusFallbackPHP(JSONObject payload, String status) {
        // Many PHP backends serve at update_status.php without /api/ prefix if routing isn't used
        String fallbackUrl = Constants.URL_UPDATE_ORDER_STATUS_LEGACY;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                fallbackUrl,
                payload,
                response -> handleStatusUpdateSuccess(status),
                error -> {
                    Log.e(TAG, "All status update attempts failed", error);
                    Toast.makeText(this, "Failed to update status. Check backend.", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void handleStatusUpdateSuccess(String status) {
        Toast.makeText(this, "Order marked as " + status.replace("_", " "), Toast.LENGTH_SHORT).show();
        activeOrderStatus.setText("Status: " + status.toUpperCase());
        updateButtonVisibilities(status);
        if (Constants.STATUS_DELIVERED.equals(status)) {
            finish();
        }
    }

    private int getDriverId() {
        return getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getInt("user_id", -1);
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!TextUtils.isEmpty(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }
}
