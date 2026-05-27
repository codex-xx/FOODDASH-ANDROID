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

import com.bumptech.glide.Glide;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

    private ApiService apiService;
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

        apiService = RetrofitClient.getApiService();

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
            String notificationHistory = prefs.getString("notification_history_json", "[]");
            String dismissedNotificationKeys = prefs.getString("dismissed_notification_keys_json", "[]");
            prefs.edit().clear().apply();
            prefs.edit()
                    .putString("notification_history_json", notificationHistory)
                    .putString("dismissed_notification_keys_json", dismissedNotificationKeys)
                    .apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        checkDriverApprovalStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isOnline) {
            refreshDriverOrders();
            startPolling();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        stopPolling(); 
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

        apiService.getDriverOrders().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    
                    if (response.code() == 404) {
                        fetchLegacyDriverOrders();
                        return;
                    }

                    JSONObject jsonResponse = new JSONObject(body);
                    JSONArray orders = extractOrders(jsonResponse);
                    selectIncomingAndActiveOrders(orders);
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                fetchLegacyDriverOrders();
            }
        });
    }

    private void fetchLegacyDriverOrders() {
        apiService.getDriverOrdersLegacy().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    
                    if (response.code() == 404) {
                        fetchDriverOrdersById();
                        return;
                    }

                    JSONObject jsonResponse = new JSONObject(body);
                    JSONArray orders = extractOrders(jsonResponse);
                    selectIncomingAndActiveOrders(orders);
                } catch (Exception e) {
                    fetchDriverOrdersById();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                fetchDriverOrdersById();
            }
        });
    }

    private void fetchDriverOrdersById() {
        int driverId = getDriverId();
        if (driverId <= 0) return;

        apiService.getOrdersByDriver(driverId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    
                    JSONObject jsonResponse = new JSONObject(body);
                    JSONArray orders = extractOrders(jsonResponse);
                    selectIncomingAndActiveOrders(orders);
                } catch (Exception e) {
                    // Fail silently
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d(TAG, "All driver order fetch attempts failed");
            }
        });
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

        Log.d(TAG, "Processing " + orders.length() + " orders for driverId: " + driverId);

        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null) {
                continue;
            }

            int orderId = order.optInt("id", order.optInt("order_id", -1));
            String status = normalizeStatus(order.optString("status", ""));
            
            int orderDriverId = -1;
            if (!order.isNull("driver_id")) {
                orderDriverId = order.optInt("driver_id", -1);
            } else if (order.has("driver_id") && order.optString("driver_id").equals("null")) {
                orderDriverId = -1;
            }

            // Flow: pending -> accepted -> preparing -> ready -> picked_up -> arrived_at_restaurant -> out_for_delivery -> delivered
            boolean isAcceptedByDriver = Constants.STATUS_ACCEPTED.equals(status) || 
                                        Constants.STATUS_PREPARING.equals(status) || 
                                        Constants.STATUS_READY.equals(status) ||
                                        Constants.STATUS_PICKED_UP.equals(status) || 
                                        Constants.STATUS_ARRIVED_RESTAURANT.equals(status) || 
                                        Constants.STATUS_OUT_FOR_DELIVERY.equals(status);

            if (driverId > 0 && orderDriverId == driverId && isAcceptedByDriver) {
                activeOrder = order;
                Log.d(TAG, "Found active order: " + orderId);
                continue;
            }

            // Drivers should see orders that are confirmed/accepted by restaurant but not yet assigned to a driver
            // Or orders that are ready for pickup
            boolean isAwaitingDriver = Constants.STATUS_ACCEPTED.equals(status) || 
                                       Constants.STATUS_PREPARING.equals(status) || 
                                       Constants.STATUS_READY.equals(status) ||
                                       Constants.STATUS_PENDING.equals(status);

            if (isAwaitingDriver) {
                if (rejectedOrderIds.contains(orderId)) {
                    continue;
                }

                if (!isOrderWithinAllowedRadius(order)) {
                    continue;
                }
                
                if (orderDriverId <= 0) {
                    if (vehicleMatches(order)) {
                        if (incomingOrder == null) {
                            incomingOrder = order;
                            Log.d(TAG, "Selected incoming order: " + orderId);
                        }
                    }
                } else {
                    Log.d(TAG, "Order " + orderId + " already assigned to driver: " + orderDriverId);
                }
            } else {
                Log.d(TAG, "Order " + orderId + " ignored due to status: " + status);
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
        String driverVehicle = getVehicleType();
        
        // If order doesn't specify a vehicle, any driver can take it
        if (TextUtils.isEmpty(orderVehicle)) {
            return true;
        }
        
        boolean matches = orderVehicle.equals(driverVehicle);
        if (!matches) {
            Log.d(TAG, "Vehicle mismatch: order=" + orderVehicle + ", driver=" + driverVehicle);
        }
        return matches;
    }

    private boolean isOrderWithinAllowedRadius(JSONObject order) {
        if (order == null) {
            return true;
        }

        Double riderLat = getStoredDouble("latitude");
        Double riderLng = getStoredDouble("longitude");
        
        // If driver location is not available, don't filter out by radius
        if (riderLat == null || riderLng == null) {
            Log.d(TAG, "Driver location missing, skipping radius filter");
            return true;
        }

        Double restaurantLat = getOrderDouble(order, "restaurant_latitude", "restaurant_lat", "merchant_latitude", "lat");
        Double restaurantLng = getOrderDouble(order, "restaurant_longitude", "restaurant_lng", "merchant_longitude", "lng");
        Double radiusKm = getOrderDouble(order, "delivery_radius", "delivery_zone_radius_km", "delivery_radius_km", "radius_km");

        // If order/restaurant data or radius limit is missing, don't filter out
        if (restaurantLat == null || restaurantLng == null) {
            Log.d(TAG, "Restaurant location missing for order, skipping radius filter");
            return true;
        }
        
        if (radiusKm == null || radiusKm <= 0d) {
            // Default to a large radius or skip filtering if not specified
            Log.d(TAG, "Radius limit missing for order, skipping radius filter");
            return true;
        }

        double distanceKm = haversineKm(riderLat, riderLng, restaurantLat, restaurantLng);
        boolean withinRadius = distanceKm <= radiusKm;
        
        if (!withinRadius) {
            Log.d(TAG, String.format(Locale.US, "Order filtered out: distance=%.2fkm, radius=%.2fkm", distanceKm, radiusKm));
        }
        
        return withinRadius;
    }

    private Double getStoredDouble(String key) {
        String value = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(key, "");
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double getOrderDouble(JSONObject order, String... keys) {
        if (order == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            if (TextUtils.isEmpty(key) || !order.has(key) || order.isNull(key)) {
                continue;
            }

            try {
                Object value = order.get(key);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                if (value != null) {
                    String text = value.toString().trim();
                    if (!TextUtils.isEmpty(text)) {
                        return Double.parseDouble(text);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
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
        String status = normalizeStatus(activeOrder.optString("status", Constants.STATUS_ACCEPTED));
        
        String text = String.format(
                Locale.getDefault(),
                "ACTIVE DELIVERY: Order #%d\nRestaurant: %s\nStatus: %s",
                orderId,
                restaurant,
                status.replace("_", " ").toUpperCase()
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

        int orderId = incomingOrder.optInt("id", incomingOrder.optInt("order_id", -1));
        String currentStatus = normalizeStatus(incomingOrder.optString("status", Constants.STATUS_PENDING));
        // Force status to "accepted" if it's currently pending
        String newStatus = Constants.STATUS_PENDING.equals(currentStatus) ? Constants.STATUS_ACCEPTED : currentStatus;

        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        Map<String, String> fields = new HashMap<>();
        String idStr = String.valueOf(orderId);
        String driverIdStr = String.valueOf(getDriverId());
        
        fields.put("id", idStr);
        fields.put("order_id", idStr);
        fields.put("orderid", idStr);
        fields.put("driver_id", driverIdStr);
        fields.put("user_id", driverIdStr);
        fields.put("status", newStatus);
        fields.put("order_status", newStatus);
        fields.put("new_status", newStatus);
        fields.put("api_token", token);
        fields.put("token", token);

        tryAcceptOrder(fields, 0);
    }

    private void tryAcceptOrder(Map<String, String> fields, int attempt) {
        Call<ResponseBody> call;
        String url;
        int orderId = Integer.parseInt(fields.get("id"));

        switch (attempt) {
            case 0: 
                url = Constants.URL_DRIVER_ACCEPT_ORDER;
                call = apiService.acceptOrder(url, fields); 
                break;
            case 1: 
                url = Constants.URL_DRIVER_ACCEPT_ORDER_LEGACY;
                call = apiService.acceptOrderLegacy(url, fields); 
                break;
            case 2: 
                url = Constants.BASE_URL + "driver_accept_order.php";
                call = apiService.acceptOrder(url, fields); 
                break;
            case 3: 
                url = Constants.BASE_URL + "orders/" + orderId + "/accept";
                call = apiService.acceptOrder(url, fields); 
                break;
            case 4: 
                url = Constants.BASE_URL + "orders/" + orderId + "/assign";
                call = apiService.acceptOrder(url, fields); 
                break;
            case 5: 
                url = Constants.URL_UPDATE_STATUS;
                call = apiService.updateOrderStatus(url, fields); 
                break;
            case 6: 
                url = Constants.URL_UPDATE_ORDER_STATUS_LEGACY;
                call = apiService.updateOrderStatusLegacy(url, fields); 
                break;
            case 7:
                url = Constants.BASE_URL + "orders/" + orderId;
                call = apiService.updateOrderStatus(url, fields);
                break;
            default:
                Log.e(TAG, "All accept order attempts failed");
                Toast.makeText(DriverDashboard.this, "Failed to accept order. Check backend logs.", Toast.LENGTH_SHORT).show();
                return;
        }

        Log.d(TAG, "Attempting to accept order #" + orderId + " via: " + url);

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "");
                    
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Accept success via " + url + ": " + body);
                        handleAcceptSuccess();
                    } else {
                        Log.w(TAG, "Accept attempt " + attempt + " failed (" + url + ") code: " + response.code() + " body: " + body);
                        tryAcceptOrder(fields, attempt + 1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error reading accept response", e);
                    tryAcceptOrder(fields, attempt + 1);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Accept attempt " + attempt + " network error (" + url + ")", t);
                tryAcceptOrder(fields, attempt + 1);
            }
        });
    }

    private void handleAcceptSuccess() {
        if (incomingOrder == null) return;
        
        activeOrder = incomingOrder;
        incomingOrder = null;
        renderIncomingOrder();
        renderActiveOrder();
        
        Toast.makeText(this, "Order Accepted!", Toast.LENGTH_SHORT).show();
        openActiveOrderPage();
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
        // Delegate to ActiveOrderActivity for consistency
        return ActiveOrderActivity.normalizeStatus(status);
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

        apiService.getProfile().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                // Handle profile if needed
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                // Handle failure
            }
        });
    }

    private String normalizeValue(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
