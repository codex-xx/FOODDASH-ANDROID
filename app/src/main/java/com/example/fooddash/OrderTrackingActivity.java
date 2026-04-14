package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderTrackingActivity extends AppCompatActivity {

    private static final long POLL_MS = 4000L;

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private RequestQueue requestQueue;

    private TextView trackingStatusTextView;
    private TextView trackingLocationTextView;
    private Button btnBackToDashboard;

    private int expectedOrderId = -1;
    private final List<String> timeline = Arrays.asList(
            Constants.STATUS_PENDING,
            Constants.STATUS_ACCEPTED,
            Constants.STATUS_PREPARING,
            Constants.STATUS_READY,
            Constants.STATUS_ASSIGNED,
            Constants.STATUS_ARRIVED_RESTAURANT,
            Constants.STATUS_PICKED_UP,
            Constants.STATUS_ON_THE_WAY,
            Constants.STATUS_DELIVERED
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.ORDER_TRACKING,
                AccessControlManager.Action.READ)) {
            return;
        }

        expectedOrderId = getIntent().getIntExtra("order_id", -1);
        requestQueue = Volley.newRequestQueue(this);

        trackingStatusTextView = findViewById(R.id.trackingStatusTextView);
        trackingLocationTextView = findViewById(R.id.trackingLocationTextView);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);

        btnBackToDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, CustomerDashboard.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        render("", "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        pollOrder();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void startPolling() {
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                pollOrder();
                pollingHandler.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    private void pollOrder() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        if (userId <= 0) {
            return;
        }

        String url = Constants.URL_ORDERS + "/" + userId;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> applyOrder(findOrder(response)),
                error -> pollLegacy(userId)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private void pollLegacy(int userId) {
        String url = Constants.URL_GET_ORDERS_LEGACY + "?user_id=" + userId;
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> applyOrder(findOrder(response)),
                error -> { }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private JSONObject findOrder(JSONObject response) {
        if (response == null) {
            return null;
        }

        JSONObject order = response.optJSONObject("order");
        if (order != null) {
            int id = order.optInt("id", order.optInt("order_id", -1));
            if (expectedOrderId <= 0 || id == expectedOrderId) {
                return order;
            }
        }

        JSONArray orders = response.optJSONArray("orders");
        if (orders == null) {
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                orders = data.optJSONArray("orders");
            }
        }
        if (orders == null) {
            orders = response.optJSONArray("data");
        }

        if (orders == null) {
            return null;
        }

        for (int i = 0; i < orders.length(); i++) {
            JSONObject item = orders.optJSONObject(i);
            if (item == null) {
                continue;
            }
            int id = item.optInt("id", item.optInt("order_id", -1));
            if (expectedOrderId > 0 && id == expectedOrderId) {
                return item;
            }
        }

        return orders.optJSONObject(0);
    }

    private void applyOrder(JSONObject order) {
        if (order == null) {
            render("", "");
            return;
        }

        String status = normalizeStatus(order.optString("status", Constants.STATUS_PENDING));
        String location = firstNonEmpty(
                order.optString("driver_location", ""),
                order.optString("current_location", ""),
                order.optString("driver_latlng", "")
        );

        JSONObject driver = order.optJSONObject("driver");
        if (driver != null && TextUtils.isEmpty(location)) {
            location = firstNonEmpty(
                    driver.optString("location", ""),
                    driver.optString("last_known_location", ""),
                    driver.optString("lat_lng", "")
            );
        }

        render(status, location);
    }

    private void render(String status, String location) {
        if (TextUtils.isEmpty(status)) {
            trackingStatusTextView.setText("No active order found yet.");
            trackingLocationTextView.setText("Driver location: waiting assignment");
            return;
        }

        StringBuilder builder = new StringBuilder();
        int current = timeline.indexOf(status);
        
        // If status is not in timeline, find the best match or default to start
        if (current == -1) {
            for (int i = 0; i < timeline.size(); i++) {
                if (status.contains(timeline.get(i))) {
                    current = i;
                    break;
                }
            }
        }

        for (int i = 0; i < timeline.size(); i++) {
            String step = timeline.get(i);
            String prefix = (current >= i) ? "[x] " : "[ ] ";
            builder.append(prefix).append(step.replace("_", " ").toUpperCase());
            if (i < timeline.size() - 1) {
                builder.append("\n");
            }
        }

        trackingStatusTextView.setText(builder.toString());
        if (TextUtils.isEmpty(location)) {
            trackingLocationTextView.setText("Driver location: updating from backend...");
        } else {
            trackingLocationTextView.setText("Driver location: " + location);
        }
    }

    private String normalizeStatus(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("accepted")) return Constants.STATUS_ACCEPTED;
        if (normalized.contains("prepar")) return Constants.STATUS_PREPARING;
        if (normalized.contains("ready")) return Constants.STATUS_READY;
        if (normalized.contains("assigned")) return Constants.STATUS_ASSIGNED;
        if (normalized.contains("arrived")) return Constants.STATUS_ARRIVED_RESTAURANT;
        if (normalized.contains("picked")) return Constants.STATUS_PICKED_UP;
        if (normalized.contains("way") || normalized.contains("transit")) return Constants.STATUS_ON_THE_WAY;
        if (normalized.contains("deliver")) return Constants.STATUS_DELIVERED;
        if (normalized.contains("pending")) return Constants.STATUS_PENDING;
        return raw;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
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
