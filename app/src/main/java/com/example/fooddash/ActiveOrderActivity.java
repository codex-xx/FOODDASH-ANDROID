package com.example.fooddash;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
// removed unused Intent/Uri imports (no driver UI in this activity)
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
// image view for driver removed from this activity
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
// Glide not required here after moving driver UI to OrderTrackingActivity

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ActiveOrderActivity extends AppCompatActivity {

    private static final String TAG = "ActiveOrderActivity";
    private static final long POLL_INTERVAL = 5000L;
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String DRIVER_HISTORY_PREFS_NAME = "fooddash_driver_history_cache";
    private static final String KEY_DRIVER_DELIVERY_HISTORY = "driver_delivery_history_json";

    private TextView activeOrderIdText, activeCustomerName, activeCustomerContact, activeDeliveryAddress;
    private TextView activeRestaurantName, activeOrderItems, activeOrderStatus, activePaymentMethod;
    // driver UI moved to customer order listing (OrderTrackingActivity)
    private Button btnPreparing, btnReady, btnArrived, btnPickedUp, btnOnTheWay, btnDelivered;

    private RequestQueue requestQueue;
    private JSONObject activeOrder;
    private int orderId = -1;
    private final Handler statusPollingHandler = new Handler(Looper.getMainLooper());

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
        activePaymentMethod = findViewById(R.id.activePaymentMethod);
        // driver UI bindings removed

        btnPreparing = findViewById(R.id.btnPreparing);
        btnReady = findViewById(R.id.btnReady);
        btnArrived = findViewById(R.id.btnArrived);
        btnPickedUp = findViewById(R.id.btnPickedUp);
        btnOnTheWay = findViewById(R.id.btnOnTheWay);
        btnDelivered = findViewById(R.id.btnDelivered);

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

        if (btnPreparing != null) btnPreparing.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_PREPARING));
        if (btnReady != null) btnReady.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_READY));
        if (btnArrived != null) btnArrived.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_ARRIVED_RESTAURANT));
        if (btnPickedUp != null) btnPickedUp.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_PICKED_UP));
        if (btnOnTheWay != null) btnOnTheWay.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_OUT_FOR_DELIVERY));
        if (btnDelivered != null) btnDelivered.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_DELIVERED));
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        stopPolling();
        statusPollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchFullOrderDetails();
                statusPollingHandler.postDelayed(this, POLL_INTERVAL);
            }
        }, POLL_INTERVAL);
    }

    private void stopPolling() {
        statusPollingHandler.removeCallbacksAndMessages(null);
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

        activeOrderIdText.setText("Order #" + (orderId > 0 ? orderId : "N/A"));
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

        // Render Payment Method
        String paymentMethodRaw = activeOrder.optString("payment_method", activeOrder.optString("payment_type", ""));
        String paymentDisplayText = "Payment Method: ";
        if (paymentMethodRaw.equalsIgnoreCase("cod")) {
            paymentDisplayText += "Cash on Delivery (COD)";
        } else if (paymentMethodRaw.equalsIgnoreCase("maya") || paymentMethodRaw.equalsIgnoreCase("gcash") || paymentMethodRaw.equalsIgnoreCase("online")) {
            paymentDisplayText += "Online Payment";
        } else if (!paymentMethodRaw.isEmpty()) {
            paymentDisplayText += paymentMethodRaw.toUpperCase();
        } else {
            paymentDisplayText += "N/A";
        }
        activePaymentMethod.setText(paymentDisplayText);
        
        String status = normalizeStatus(activeOrder.optString("status", "pending"));
        activeOrderStatus.setText("Status: " + status.replace("_", " ").toUpperCase());
        
        updateButtonVisibilities(status);

        if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
            Toast.makeText(this, "Order " + status.toUpperCase(), Toast.LENGTH_SHORT).show();
            finish();
        }

        // Driver details are presented in the customer's order listing (OrderTrackingActivity).
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty() && !v.equals("null") && !v.equals("undefined")) {
                return v.trim();
            }
        }
        return "";
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isEmpty() || status.equals("null")) return Constants.STATUS_PENDING;
        String n = status.trim().toLowerCase(Locale.ROOT);
        
        if (n.equals("confirmed")) return Constants.STATUS_ACCEPTED;
        if (n.equals("ready_for_pickup")) return Constants.STATUS_READY;
        if (n.equals("on_the_way")) return Constants.STATUS_OUT_FOR_DELIVERY;
        if (n.equals("completed")) return Constants.STATUS_DELIVERED;

        if (n.contains("accepted")) return Constants.STATUS_ACCEPTED;
        if (n.contains("prepar")) return Constants.STATUS_PREPARING;
        if (n.contains("ready")) return Constants.STATUS_READY;
        if (n.contains("picked")) return Constants.STATUS_PICKED_UP;
        if (n.contains("arrived")) return Constants.STATUS_ARRIVED_RESTAURANT;
        if (n.contains("way") || n.contains("transit") || n.contains("out_for") || n.contains("out_of")) 
            return Constants.STATUS_OUT_FOR_DELIVERY;
        if (n.contains("deliver") || n.contains("done") || n.contains("finish")) 
            return Constants.STATUS_DELIVERED;
        if (n.contains("cancel")) return Constants.STATUS_CANCELLED;
        if (n.contains("pending")) return Constants.STATUS_PENDING;
        
        return n;
    }

    private void updateButtonVisibilities(String status) {
        if (btnPreparing != null) btnPreparing.setVisibility(View.GONE);
        if (btnReady != null) btnReady.setVisibility(View.GONE);
        if (btnArrived != null) btnArrived.setVisibility(View.GONE);
        if (btnPickedUp != null) btnPickedUp.setVisibility(View.GONE);
        if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.GONE);
        if (btnDelivered != null) btnDelivered.setVisibility(View.GONE);

        boolean isDriverRole = AccessControlManager.ROLE_DRIVER.equals(
                AccessControlManager.normalizeRole(AccessControlManager.getCurrentRole(this)));

        // Drivers only handle pickup and delivery handoff stages.
        // Restaurant-side preparation controls stay hidden in the driver flow.
        if (isDriverRole) {
            if (Constants.STATUS_READY.equals(status)) {
                if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
            } else if (Constants.STATUS_PICKED_UP.equals(status)) {
                if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.VISIBLE);
            } else if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) {
                if (btnDelivered != null) btnDelivered.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Restaurant flow keeps the prep/ready controls available.
        if (Constants.STATUS_ACCEPTED.equals(status)) {
            if (btnPreparing != null) btnPreparing.setVisibility(View.VISIBLE);
            if (btnArrived != null) btnArrived.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_PREPARING.equals(status)) {
            if (btnReady != null) btnReady.setVisibility(View.VISIBLE);
            if (btnArrived != null) btnArrived.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_READY.equals(status)) {
            if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_PICKED_UP.equals(status)) {
            if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_ARRIVED_RESTAURANT.equals(status)) {
            if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) {
            if (btnDelivered != null) btnDelivered.setVisibility(View.VISIBLE);
        }
    }

    private void updateOrderStatus(String status) {
        if (!AccessControlManager.canPerform(this, AccessControlManager.Resource.ORDERS, AccessControlManager.Action.UPDATE)) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            return;
        }

        if (orderId <= 0) return;

        activeOrderStatus.setText("Status: " + status.replace("_", " ").toUpperCase());
        updateButtonVisibilities(status);

        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        JSONObject payload = new JSONObject();
        try {
            int driverId = getUserId();
            payload.put("id", orderId);
            payload.put("order_id", orderId);
            payload.put("orderid", orderId);
            payload.put("driver_id", driverId);
            payload.put("user_id", driverId);
            payload.put("status", status);
            payload.put("order_status", status);
            payload.put("new_status", status);
            payload.put("api_token", token);
            payload.put("token", token);
        } catch (JSONException e) {
            return;
        }

        tryUpdateOrderStatus(payload, status, 0);
    }

    private void tryUpdateOrderStatus(JSONObject payload, String status, int attempt) {
        String url;
        int method = Request.Method.POST;

        switch (attempt) {
            case 0: url = Constants.URL_UPDATE_STATUS; break;
            case 1: url = Constants.URL_ORDERS + "/" + orderId + "/status"; break;
            case 2: url = Constants.URL_UPDATE_ORDER_STATUS_LEGACY; break;
            default:
                Log.e(TAG, "Update failed after attempts");
                fetchFullOrderDetails(); 
                return;
        }

        JsonObjectRequest request = new JsonObjectRequest(method, url, payload,
                response -> handleStatusUpdateSuccess(status),
                error -> tryUpdateOrderStatus(payload, status, attempt + 1)
        ) {
            @Override
            public Map<String, String> getHeaders() { return buildAuthHeaders(); }
        };
        requestQueue.add(request);
    }

    private void handleStatusUpdateSuccess(String status) {
        if (Constants.STATUS_DELIVERED.equals(status)) {
            cacheDeliveredOrderSnapshot();
        }
        Toast.makeText(this, "Successfully marked as " + status.replace("_", " "), Toast.LENGTH_SHORT).show();
        fetchFullOrderDetails(); 
    }

    private void cacheDeliveredOrderSnapshot() {
        if (activeOrder == null) {
            return;
        }

        try {
            JSONObject deliveredSnapshot = new JSONObject(activeOrder.toString());
            deliveredSnapshot.put("status", Constants.STATUS_DELIVERED);
            if (!deliveredSnapshot.has("delivered_at") || deliveredSnapshot.isNull("delivered_at")) {
                deliveredSnapshot.put("delivered_at", getUtcNowIso());
            }

            SharedPreferences prefs = getSharedPreferences(DRIVER_HISTORY_PREFS_NAME, MODE_PRIVATE);
            JSONArray history = new JSONArray(prefs.getString(KEY_DRIVER_DELIVERY_HISTORY, "[]"));
            int deliveredId = deliveredSnapshot.optInt("id", deliveredSnapshot.optInt("order_id", -1));

            JSONArray merged = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < history.length(); i++) {
                JSONObject existing = history.optJSONObject(i);
                if (existing == null) {
                    continue;
                }

                int existingId = existing.optInt("id", existing.optInt("order_id", -1));
                if (existingId > 0 && existingId == deliveredId) {
                    merged.put(deliveredSnapshot);
                    replaced = true;
                } else {
                    merged.put(existing);
                }
            }

            if (!replaced) {
                merged.put(deliveredSnapshot);
            }

            prefs.edit().putString(KEY_DRIVER_DELIVERY_HISTORY, merged.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache delivered order snapshot", e);
        }
    }

    private String getUtcNowIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private int getUserId() { return getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getInt("user_id", -1); }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!TextUtils.isEmpty(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }
}
