package com.example.fooddash;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DriverDashboard extends AppCompatActivity {

    private static final String TAG = "DriverDashboard";
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final long POLLING_MS = 3000L;

    private Switch onlineSwitch;
    private TextView driverVehicleTextView;
    private TextView incomingOrderTextView;
    private TextView activeOrderTextView;
    private Button btnRefreshRequests;
    private Button btnAcceptOrder;
    private Button btnRejectOrder;
    private Button btnViewActiveOrder; 
    private Button btnViewHistory;
    private Button btnLogout;

    private RequestQueue requestQueue;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private boolean isOnline = false;
    private JSONObject incomingOrder;
    private JSONObject activeOrder;
    
    private final Set<Integer> rejectedOrderIds = new HashSet<>();

    private static final String URL_GET_PROFILE = Constants.BASE_URL + "get-profile";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_dashboard);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.DRIVER_DASHBOARD,
                AccessControlManager.Action.READ)) {
            return;
        }

        requestQueue = Volley.newRequestQueue(this);

        onlineSwitch = findViewById(R.id.onlineSwitch);
        driverVehicleTextView = findViewById(R.id.driverVehicleTextView);
        incomingOrderTextView = findViewById(R.id.incomingOrderTextView);
        activeOrderTextView = findViewById(R.id.activeOrderTextView);
        btnRefreshRequests = findViewById(R.id.btnRefreshRequests);
        btnAcceptOrder = findViewById(R.id.btnAcceptOrder);
        btnRejectOrder = findViewById(R.id.btnRejectOrder);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        
        btnViewActiveOrder = findViewById(R.id.btnArrived); 
        if (btnViewActiveOrder != null) {
            btnViewActiveOrder.setText("View Active Order Details");
            btnViewActiveOrder.setOnClickListener(v -> openActiveOrderPage());
            btnViewActiveOrder.setVisibility(View.GONE);
        }

        View btnPickedUp = findViewById(R.id.btnPickedUp);
        View btnOnTheWay = findViewById(R.id.btnOnTheWay);
        View btnDelivered = findViewById(R.id.btnDelivered);
        if (btnPickedUp != null) btnPickedUp.setVisibility(View.GONE);
        if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.GONE);
        if (btnDelivered != null) btnDelivered.setVisibility(View.GONE);

        btnLogout = findViewById(R.id.btnLogout);

        String vehicleType = getVehicleType();
        driverVehicleTextView.setText("Vehicle: " + toTitleCase(vehicleType));

        btnRefreshRequests.setOnClickListener(v -> refreshDriverOrders());
        btnAcceptOrder.setOnClickListener(v -> acceptCurrentOrder());
        btnRejectOrder.setOnClickListener(v -> rejectCurrentOrder());
        
        if (btnViewHistory != null) {
            btnViewHistory.setOnClickListener(v -> {
                Intent historyIntent = new Intent(this, DriverHistoryActivity.class);
                startActivity(historyIntent);
            });
        }

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
            AuthSessionManager.clearSession(this);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

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
        pollingHandler.removeCallbacksAndMessages(null);
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

        // Try to fetch orders. We include vehicle_type to let the server filter if possible.
        String url = Constants.URL_ORDERS + "?vehicle_type=" + getVehicleType();
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

            int orderId = order.optInt("id", order.optInt("order_id", -1));
            String status = normalizeStatus(order.optString("status", ""));
            
            // Check driver_id robustly
            int orderDriverId = -1;
            if (!order.isNull("driver_id")) {
                orderDriverId = order.optInt("driver_id", -1);
            }

            // 1. Check for Active Order (already assigned to this driver)
            if (driverId > 0 && orderDriverId == driverId) {
                if (!Constants.STATUS_DELIVERED.equals(status)) {
                    activeOrder = order;
                }
                continue;
            }

            // 2. Check for Incoming Request
            // We show it if it's unassigned AND the status indicates it's ready for a driver.
            // We include PENDING as a fallback because some systems use it for "needs driver".
            if (orderDriverId <= 0) {
                boolean isAvailable = Constants.STATUS_ACCEPTED.equals(status) || 
                                     Constants.STATUS_PREPARING.equals(status) || 
                                     Constants.STATUS_READY.equals(status) ||
                                     Constants.STATUS_PENDING.equals(status);
                
                if (isAvailable) {
                    if (rejectedOrderIds.contains(orderId)) {
                        continue;
                    }
                    
                    if (vehicleMatches(order) && incomingOrder == null) {
                        incomingOrder = order;
                    }
                }
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
            btnAcceptOrder.setEnabled(false);
            btnRejectOrder.setEnabled(false);
            return;
        }

        btnAcceptOrder.setEnabled(true);
        btnRejectOrder.setEnabled(true);

        int orderId = incomingOrder.optInt("id", incomingOrder.optInt("order_id", -1));
        String restaurant = incomingOrder.optString("restaurant_name", "Restaurant");
        String status = normalizeStatus(incomingOrder.optString("status", Constants.STATUS_READY));
        
        String text = String.format(
                Locale.getDefault(),
                "NEW REQUEST: Order #%d\nFrom: %s\nStatus: %s\n\nClick Accept to start delivery.",
                orderId,
                restaurant,
                status
        );
        incomingOrderTextView.setText(text);
    }

    private void renderActiveOrder() {
        if (activeOrder == null) {
            activeOrderTextView.setText("No active order");
            if (btnViewActiveOrder != null) btnViewActiveOrder.setVisibility(View.GONE);
            return;
        }

        if (btnViewActiveOrder != null) btnViewActiveOrder.setVisibility(View.VISIBLE);

        int orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", -1));
        String restaurant = activeOrder.optString("restaurant_name", "Restaurant");
        String status = normalizeStatus(activeOrder.optString("status", Constants.STATUS_ASSIGNED));
        
        String text = String.format(
                Locale.getDefault(),
                "ACTIVE DELIVERY: Order #%d\nRestaurant: %s\nStatus: %s",
                orderId,
                restaurant,
                status
        );
        activeOrderTextView.setText(text);
    }

    private void acceptCurrentOrder() {
        if (incomingOrder == null) return;

        if (!AccessControlManager.canPerform(this,
                AccessControlManager.Resource.ORDERS,
                AccessControlManager.Action.UPDATE)) {
            Toast.makeText(this, "Access denied for this action", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = new JSONObject();
        int orderId = incomingOrder.optInt("id", incomingOrder.optInt("order_id", -1));
        try {
            payload.put("order_id", orderId);
            payload.put("driver_id", getDriverId());
            payload.put("driver_name", getDriverName());
            payload.put("driver_phone", getDriverPhone());
            payload.put("driver_email", getDriverEmail());
            payload.put("status", Constants.STATUS_ASSIGNED); 
        } catch (JSONException e) {
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_STATUS,
                payload,
                response -> handleAcceptSuccess(),
                error -> acceptOrderLegacy(payload)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void handleAcceptSuccess() {
        if (incomingOrder == null) return;
        
        activeOrder = incomingOrder;
        incomingOrder = null;
        renderIncomingOrder();
        renderActiveOrder();
        
        Toast.makeText(this, "Order Accepted! Redirecting to details...", Toast.LENGTH_SHORT).show();
        openActiveOrderPage();
    }

    private void acceptOrderLegacy(JSONObject payload) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_ORDER_STATUS_LEGACY,
                payload,
                response -> handleAcceptSuccess(),
                error -> Toast.makeText(this, "Failed to accept order", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void openActiveOrderPage() {
        if (activeOrder == null) return;
        Intent intent = new Intent(this, ActiveOrderActivity.class);
        intent.putExtra("order_json", activeOrder.toString());
        startActivity(intent);
    }

    private void rejectCurrentOrder() {
        if (incomingOrder != null) {
            int orderId = incomingOrder.optInt("id", incomingOrder.optInt("order_id", -1));
            rejectedOrderIds.add(orderId);
        }
        
        incomingOrder = null;
        renderIncomingOrder();
        Toast.makeText(this, "Order rejected", Toast.LENGTH_SHORT).show();
    }

    private int getDriverId() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("user_id", -1);
    }

    private String getDriverName() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("user_name", "Driver");
    }

    private String getDriverPhone() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("contact_number", "");
    }
    
    private String getDriverEmail() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("user_email", "");
    }

    private String getVehicleType() {
        String raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("vehicle_type", "");
        String normalized = normalizeValue(raw);
        if (normalized.contains("tri")) return Constants.DELIVERY_TRICYCLE;
        if (normalized.contains("cab") || normalized.contains("car")) return Constants.DELIVERY_CAB;
        return Constants.DELIVERY_MOTORCYCLE;
    }

    private String toTitleCase(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeValue(status);
        if (normalized.contains("accepted") || normalized.contains("confirm")) return Constants.STATUS_ACCEPTED;
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
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!TextUtils.isEmpty(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    private void checkDriverApprovalStatus() {
        String apiToken = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (apiToken.isEmpty()) return;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                URL_GET_PROFILE,
                null,
                response -> {
                },
                error -> { }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Authorization", "Bearer " + apiToken);
                return headers;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private String normalizeValue(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
