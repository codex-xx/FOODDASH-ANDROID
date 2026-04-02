package com.example.fooddash;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DriverDashboard extends AppCompatActivity {

    private static final String TAG = "DriverDashboard";
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String DRIVER_APPROVAL_EMAIL_SENT_PREFIX = "driver_approval_email_sent_";
    private static final long POLLING_MS = 4000L;

    private Switch onlineSwitch;
    private TextView driverVehicleTextView;
    private TextView incomingOrderTextView;
    private TextView activeOrderTextView;
    private Button btnRefreshRequests;
    private Button btnAcceptOrder;
    private Button btnRejectOrder;
    private Button btnArrived;
    private Button btnPickedUp;
    private Button btnOnTheWay;
    private Button btnDelivered;
    private Button btnLogout;

    private RequestQueue requestQueue;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private boolean isOnline = false;
    private JSONObject incomingOrder;
    private JSONObject activeOrder;

    private static final String URL_GET_PROFILE = Constants.BASE_URL + "get-profile";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_dashboard);

        requestQueue = Volley.newRequestQueue(this);

        onlineSwitch = findViewById(R.id.onlineSwitch);
        driverVehicleTextView = findViewById(R.id.driverVehicleTextView);
        incomingOrderTextView = findViewById(R.id.incomingOrderTextView);
        activeOrderTextView = findViewById(R.id.activeOrderTextView);
        btnRefreshRequests = findViewById(R.id.btnRefreshRequests);
        btnAcceptOrder = findViewById(R.id.btnAcceptOrder);
        btnRejectOrder = findViewById(R.id.btnRejectOrder);
        btnArrived = findViewById(R.id.btnArrived);
        btnPickedUp = findViewById(R.id.btnPickedUp);
        btnOnTheWay = findViewById(R.id.btnOnTheWay);
        btnDelivered = findViewById(R.id.btnDelivered);
        btnLogout = findViewById(R.id.btnLogout);

        String vehicleType = getVehicleType();
        driverVehicleTextView.setText("Vehicle: " + toTitleCase(vehicleType));

        btnRefreshRequests.setOnClickListener(v -> refreshDriverOrders());
        btnAcceptOrder.setOnClickListener(v -> acceptCurrentOrder());
        btnRejectOrder.setOnClickListener(v -> rejectCurrentOrder());
        btnArrived.setOnClickListener(v -> updateActiveOrderStatus(Constants.STATUS_ARRIVED_RESTAURANT));
        btnPickedUp.setOnClickListener(v -> updateActiveOrderStatus(Constants.STATUS_PICKED_UP));
        btnOnTheWay.setOnClickListener(v -> updateActiveOrderStatus(Constants.STATUS_ON_THE_WAY));
        btnDelivered.setOnClickListener(v -> updateActiveOrderStatus(Constants.STATUS_DELIVERED));

        onlineSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            isOnline = checked;
            onlineSwitch.setText(checked ? "Online" : "Go Online");
            if (checked) {
                refreshDriverOrders();
                startPolling();
            } else {
                stopPolling();
                incomingOrder = null;
                renderIncomingOrder();
            }
        });

        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Check if driver was approved and send confirmation email if needed
        checkDriverApprovalStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isOnline) {
            startPolling();
            refreshDriverOrders();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isOnline) {
                    refreshDriverOrders();
                    pollingHandler.postDelayed(this, POLLING_MS);
                }
            }
        }, POLLING_MS);
    }

    private void stopPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void refreshDriverOrders() {
        if (!isOnline) {
            return;
        }

        String url = Constants.URL_ORDERS + "?for_driver=1&vehicle_type=" + getVehicleType();
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    JSONArray orders = extractOrders(response);
                    selectIncomingAndActiveOrders(orders);
                },
                error -> fetchLegacyDriverOrders()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void fetchLegacyDriverOrders() {
        String url = Constants.URL_GET_DRIVER_ORDERS_LEGACY + "?vehicle_type=" + getVehicleType();
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    JSONArray orders = extractOrders(response);
                    selectIncomingAndActiveOrders(orders);
                },
                error -> Log.d(TAG, "No driver orders available")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private JSONArray extractOrders(JSONObject response) {
        if (response == null) {
            return new JSONArray();
        }

        JSONArray direct = response.optJSONArray("orders");
        if (direct != null) {
            return direct;
        }

        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            JSONArray nested = data.optJSONArray("orders");
            if (nested != null) {
                return nested;
            }
        }

        JSONArray fallback = response.optJSONArray("data");
        return fallback != null ? fallback : new JSONArray();
    }

    private void selectIncomingAndActiveOrders(JSONArray orders) {
        incomingOrder = null;
        activeOrder = null;
        int driverId = getDriverId();

        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null) {
                continue;
            }

            String status = normalizeStatus(order.optString("status", ""));
            int orderDriverId = order.optInt("driver_id", -1);

            if (driverId > 0 && orderDriverId == driverId && !Constants.STATUS_DELIVERED.equals(status)) {
                activeOrder = order;
                continue;
            }

            if ((Constants.STATUS_READY.equals(status) || Constants.STATUS_ASSIGNED.equals(status) || Constants.STATUS_PENDING.equals(status))
                    && vehicleMatches(order)
                    && incomingOrder == null) {
                incomingOrder = order;
            }
        }

        renderIncomingOrder();
        renderActiveOrder();
    }

    private boolean vehicleMatches(JSONObject order) {
        if (order == null) {
            return false;
        }

        String orderVehicle = normalizeValue(order.optString("delivery_type", order.optString("vehicle_type", "")));
        if (TextUtils.isEmpty(orderVehicle)) {
            return true;
        }
        return orderVehicle.equals(getVehicleType());
    }

    private void renderIncomingOrder() {
        if (incomingOrder == null) {
            incomingOrderTextView.setText("No request yet");
            return;
        }

        int orderId = incomingOrder.optInt("id", incomingOrder.optInt("order_id", -1));
        String customer = incomingOrder.optString("customer_name", incomingOrder.optString("customer", "Customer"));
        String restaurant = incomingOrder.optString("restaurant_name", incomingOrder.optString("restaurant", "Restaurant"));
        String status = normalizeStatus(incomingOrder.optString("status", Constants.STATUS_READY));
        String address = incomingOrder.optString("delivery_address", "No address");
        double total = incomingOrder.optDouble("total_amount", incomingOrder.optDouble("total", 0.0));
        String vehicle = toTitleCase(normalizeValue(incomingOrder.optString("delivery_type", incomingOrder.optString("vehicle_type", ""))));

        String text = String.format(
                Locale.getDefault(),
                "Order #%d\nCustomer: %s\nRestaurant: %s\nAddress: %s\nVehicle: %s\nTotal: ₱%.2f\nStatus: %s",
                orderId,
                customer,
                restaurant,
                address,
                TextUtils.isEmpty(vehicle) ? "Any" : vehicle,
                total,
                status
        );
        incomingOrderTextView.setText(text);
    }

    private void renderActiveOrder() {
        if (activeOrder == null) {
            activeOrderTextView.setText("No active order");
            return;
        }

        int orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", -1));
        String status = normalizeStatus(activeOrder.optString("status", Constants.STATUS_ASSIGNED));
        String customer = activeOrder.optString("customer_name", activeOrder.optString("customer", "Customer"));
        String restaurant = activeOrder.optString("restaurant_name", activeOrder.optString("restaurant", "Restaurant"));
        String text = String.format(
                Locale.getDefault(),
                "Order #%d\nCustomer: %s\nRestaurant: %s\nCurrent Status: %s",
                orderId,
                customer,
                restaurant,
                status
        );
        activeOrderTextView.setText(text);
    }

    private void acceptCurrentOrder() {
        if (incomingOrder == null) {
            Toast.makeText(this, "No incoming order to accept", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = new JSONObject();
        int orderId = incomingOrder.optInt("id", incomingOrder.optInt("order_id", -1));
        try {
            payload.put("order_id", orderId);
            payload.put("driver_id", getDriverId());
            payload.put("vehicle_type", getVehicleType());
            payload.put("status", Constants.STATUS_ASSIGNED);
        } catch (JSONException e) {
            Toast.makeText(this, "Unable to accept order", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_DRIVER_ACCEPT_ORDER,
                payload,
                response -> {
                    activeOrder = incomingOrder;
                    incomingOrder = null;
                    updateActiveOrderStatus(Constants.STATUS_ASSIGNED);
                    renderIncomingOrder();
                    renderActiveOrder();
                    Toast.makeText(this, "Order accepted", Toast.LENGTH_SHORT).show();
                },
                error -> acceptOrderLegacy(payload)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void acceptOrderLegacy(JSONObject payload) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_ORDER_STATUS_LEGACY,
                payload,
                response -> {
                    activeOrder = incomingOrder;
                    incomingOrder = null;
                    renderIncomingOrder();
                    renderActiveOrder();
                    Toast.makeText(this, "Order accepted", Toast.LENGTH_SHORT).show();
                },
                error -> {
                    Toast.makeText(this, "Failed to accept order", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "acceptOrderLegacy failed", error);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void rejectCurrentOrder() {
        if (incomingOrder == null) {
            Toast.makeText(this, "No incoming order to reject", Toast.LENGTH_SHORT).show();
            return;
        }

        incomingOrder = null;
        renderIncomingOrder();
        Toast.makeText(this, "Order rejected", Toast.LENGTH_SHORT).show();
        refreshDriverOrders();
    }

    private void updateActiveOrderStatus(String status) {
        if (activeOrder == null) {
            Toast.makeText(this, "No active order", Toast.LENGTH_SHORT).show();
            return;
        }

        int orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", -1));
        JSONObject payload = new JSONObject();
        try {
            payload.put("order_id", orderId);
            payload.put("driver_id", getDriverId());
            payload.put("status", status);
            payload.put("driver_location", "Driver en route");
        } catch (JSONException e) {
            Toast.makeText(this, "Failed to prepare status update", Toast.LENGTH_SHORT).show();
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_STATUS,
                payload,
                response -> applyActiveStatus(status),
                error -> updateStatusLegacy(payload, status)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void updateStatusLegacy(JSONObject payload, String status) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_ORDER_STATUS_LEGACY,
                payload,
                response -> applyActiveStatus(status),
                error -> {
                    Log.e(TAG, "Failed to update status", error);
                    Toast.makeText(this, "Status update failed", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void applyActiveStatus(String status) {
        if (activeOrder != null) {
            try {
                activeOrder.put("status", status);
            } catch (JSONException ignored) {
            }
            renderActiveOrder();
        }

        Toast.makeText(this, "Status updated: " + status, Toast.LENGTH_SHORT).show();

        if (Constants.STATUS_DELIVERED.equals(status)) {
            activeOrder = null;
            renderActiveOrder();
            refreshDriverOrders();
        }
    }

    private int getDriverId() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("user_id", -1);
    }

    private String getVehicleType() {
        String raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("vehicle_type", "");
        String normalized = normalizeValue(raw);
        if (normalized.contains("tri")) {
            return Constants.DELIVERY_TRICYCLE;
        }
        if (normalized.contains("cab") || normalized.contains("car")) {
            return Constants.DELIVERY_CAB;
        }
        return Constants.DELIVERY_MOTORCYCLE;
    }

    private String toTitleCase(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeValue(status);
        if (normalized.contains("accepted")) return Constants.STATUS_ACCEPTED;
        if (normalized.contains("prepar")) return Constants.STATUS_PREPARING;
        if (normalized.contains("ready")) return Constants.STATUS_READY;
        if (normalized.contains("assigned")) return Constants.STATUS_ASSIGNED;
        if (normalized.contains("arrived")) return Constants.STATUS_ARRIVED_RESTAURANT;
        if (normalized.contains("picked")) return Constants.STATUS_PICKED_UP;
        if (normalized.contains("way") || normalized.contains("transit")) return Constants.STATUS_ON_THE_WAY;
        if (normalized.contains("deliver")) return Constants.STATUS_DELIVERED;
        if (normalized.contains("pending")) return Constants.STATUS_PENDING;
        return status;
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_token", "");
        if (!TextUtils.isEmpty(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private void checkDriverApprovalStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String apiToken = prefs.getString("api_token", "");
        String email = prefs.getString("driver_email", "");

        if (apiToken.isEmpty() || email.isEmpty()) {
            Log.d(TAG, "Missing API token or email, skipping approval status check");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                URL_GET_PROFILE,
                null,
                response -> {
                    try {
                        String status = extractStatus(response);
                        String driverName = extractName(response);

                        if ("approved".equals(status) && !driverName.isEmpty()) {
                            maybeSendDriverApprovalEmail(email, driverName);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing approval status response", e);
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            Log.e(TAG, "Failed to check approval status: " + responseBody, error);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing approval status error response", e);
                        }
                    } else {
                        Log.d(TAG, "Could not check approval status (may be offline)");
                    }
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                String apiToken = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_token", "");
                if (!apiToken.isEmpty()) {
                    headers.put("Authorization", "Bearer " + apiToken);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private String extractStatus(JSONObject response) {
        if (response == null) return "";
        String status = response.optString("status", "");
        if (!status.isEmpty()) return status;
        
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            status = data.optString("status", "");
            if (!status.isEmpty()) return status;
            status = data.optString("account_status", "");
            if (!status.isEmpty()) return status;
            status = data.optString("driver_status", "");
        }
        return status;
    }

    private String extractName(JSONObject response) {
        if (response == null) return "";
        String name = response.optString("name", "");
        if (!name.isEmpty()) return name;
        name = response.optString("full_name", "");
        if (!name.isEmpty()) return name;
        
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            name = data.optString("name", "");
            if (!name.isEmpty()) return name;
            name = data.optString("full_name", "");
        }
        return name;
    }

    private void maybeSendDriverApprovalEmail(String email, String driverName) {
        if (!EmailNotificationService.isGmailAddress(email)) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String sentFlagKey = DRIVER_APPROVAL_EMAIL_SENT_PREFIX + normalizeValue(email);
        
        if (prefs.getBoolean(sentFlagKey, false)) {
            Log.d(TAG, "Approval email already sent for: " + email);
            return;
        }

        Log.i(TAG, "Sending driver approval confirmation email to: " + email);
        EmailNotificationService.sendDriverApplicationApproved(
                getApplicationContext(),
                email,
                driverName,
                () -> {
                    prefs.edit().putBoolean(sentFlagKey, true).apply();
                    Log.i(TAG, "Driver approval email sent successfully for: " + email);
                }
        );
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}