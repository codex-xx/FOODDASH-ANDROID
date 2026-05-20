package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderTrackingActivity extends AppCompatActivity {

    private static final String TAG = "OrderTrackingActivity";
    private static final long POLL_MS = 5000L;

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private RequestQueue requestQueue;

    private LinearLayout ordersContainer;
    private Button btnBackToDashboard;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private TextView tabNotificationsBadgeTextView;

    private int expectedOrderId = -1;
    private final List<String> timeline = Arrays.asList(
            Constants.STATUS_PENDING,
            Constants.STATUS_ACCEPTED,
            Constants.STATUS_PREPARING,
            Constants.STATUS_READY,
            Constants.STATUS_PICKED_UP,
            Constants.STATUS_ARRIVED_RESTAURANT,
            Constants.STATUS_OUT_FOR_DELIVERY,
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
        if (expectedOrderId <= 0) {
            SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
            expectedOrderId = prefs.getInt("last_active_order_id", -1);
        }
        requestQueue = Volley.newRequestQueue(this);

        ordersContainer = findViewById(R.id.ordersContainer);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);

        loadCachedActiveOrder();

        btnBackToDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderHistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        setupBottomNavigation();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();
        
        pollOrders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void startPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                pollOrders();
                pollingHandler.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    private void pollOrders() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        if (userId <= 0) {
            Log.e(TAG, "No user ID found in prefs");
            return;
        }

        // Try both modern and legacy endpoints to ensure we find the order
        fetchOrdersFromEndpoint(Constants.URL_ORDERS + "?user_id=" + userId, false);
        fetchOrdersFromEndpoint(Constants.URL_ORDERS + "/" + userId, false);
        fetchOrdersFromEndpoint(Constants.URL_GET_ORDERS_LEGACY + "?user_id=" + userId, true);
        if (expectedOrderId > 0) {
            fetchOrdersFromEndpoint(Constants.URL_ORDERS + "/" + expectedOrderId, false);
            fetchOrdersFromEndpoint(Constants.URL_GET_ORDERS_LEGACY + "?order_id=" + expectedOrderId, true);
        }
    }

    private void loadCachedActiveOrder() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String cachedOrder = prefs.getString("last_active_order_json", "");
        if (TextUtils.isEmpty(cachedOrder)) {
            if (expectedOrderId > 0) {
                showFallbackActiveOrder(expectedOrderId, Constants.STATUS_PENDING);
            }
            return;
        }

        try {
            JSONObject order = new JSONObject(cachedOrder);
            renderOrders(Collections.singletonList(order));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load cached active order", e);
            if (expectedOrderId > 0) {
                showFallbackActiveOrder(expectedOrderId, Constants.STATUS_PENDING);
            }
        }
    }

    private void setupBottomNavigation() {
        highlightBottomTab(tabOrdersButton);

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
            tabOrdersButton.setOnClickListener(v -> highlightBottomTab(tabOrdersButton));
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> {
                highlightBottomTab(tabCartButton);
                openCartFromPrefs();
            });
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

    private void updateCartBadgeFromPrefs() {
        if (tabCartBadgeTextView == null) return;
        int count = getCartItemCountFromPrefs();
        if (count <= 0) {
            tabCartBadgeTextView.setVisibility(View.GONE);
            return;
        }
        tabCartBadgeTextView.setVisibility(View.VISIBLE);
        tabCartBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
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

    private int getCartItemCountFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int count = 0;
        try {
            JSONArray array = new JSONArray(prefs.getString("global_cart_json", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) count += obj.optInt("quantity", 0);
            }
        } catch (Exception ignored) {}
        return count;
    }

    private void openCartFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String cartJson = prefs.getString("global_cart_json", "[]");
        try {
            JSONArray cart = new JSONArray(cartJson);
            if (cart.length() == 0) {
                Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CartActivity.class);
        intent.putExtra("cart_items_json", cartJson);
        startActivity(intent);
        finish();
    }

    private void fetchOrdersFromEndpoint(String url, boolean isLegacy) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    List<JSONObject> orders = extractOrders(response);
                    if (!orders.isEmpty()) {
                        renderOrders(orders);
                    } else if (expectedOrderId > 0 && ordersContainer.getChildCount() == 0) {
                        showFallbackActiveOrder(expectedOrderId, Constants.STATUS_PENDING);
                    }
                },
                error -> {
                    if (isLegacy) Log.e(TAG, "Failed to poll legacy orders: " + error.toString());
                    if (expectedOrderId > 0 && ordersContainer.getChildCount() == 0) {
                        showFallbackActiveOrder(expectedOrderId, Constants.STATUS_PENDING);
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

    private List<JSONObject> extractOrders(JSONObject response) {
        List<JSONObject> list = new ArrayList<>();
        if (response == null) return list;

        JSONArray orders = response.optJSONArray("orders");
        if (orders == null) orders = response.optJSONArray("data");
        if (orders == null) {
            JSONObject data = response.optJSONObject("data");
            if (data != null) orders = data.optJSONArray("orders");
        }
        if (orders == null) {
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                JSONObject nestedOrder = data.optJSONObject("order");
                if (nestedOrder != null) {
                    list.add(nestedOrder);
                }
            }
        }

        if (orders == null) {
            JSONObject nestedOrder = response.optJSONObject("order");
            if (nestedOrder != null) {
                list.add(nestedOrder);
            }
        }

        if (orders != null) {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject o = orders.optJSONObject(i);
                if (o != null) list.add(o);
            }
        } else {
            // Check if it's a single order object
            if (response.has("id") || response.has("order_id") || response.has("items") || response.has("status")) {
                list.add(response);
            } else if (response.has("data")) {
                JSONObject data = response.optJSONObject("data");
                if (data != null && (data.has("id") || data.has("order_id"))) {
                    list.add(data);
                }
            }
        }
        return list;
    }

    private void showFallbackActiveOrder(int orderId, String status) {
        JSONObject fallback = new JSONObject();
        try {
            fallback.put("id", orderId);
            fallback.put("order_id", orderId);
            fallback.put("status", status);
            fallback.put("restaurant_name", "Your Restaurant Order");
            fallback.put("total_amount", 0.0);
        } catch (Exception ignored) {
        }

        ordersContainer.removeAllViews();
        ordersContainer.addView(createOrderCard(fallback, orderId, ActiveOrderActivity.normalizeStatus(status)));
    }

    private void renderOrders(List<JSONObject> orders) {
        // Simple deduplication by ID
        Map<Integer, JSONObject> uniqueOrders = new HashMap<>();
        for (JSONObject o : orders) {
            int id = o.optInt("id", o.optInt("order_id", -1));
            if (id > 0) uniqueOrders.put(id, o);
        }

        if (uniqueOrders.isEmpty()) {
            if (expectedOrderId > 0) {
                showFallbackActiveOrder(expectedOrderId, getCachedActiveOrderStatus());
            } else if (ordersContainer.getChildCount() == 0) {
                showEmptyMessage("No active orders found.");
            }
            return;
        }

        List<JSONObject> sortedList = new ArrayList<>(uniqueOrders.values());
        // Sort by ID descending (newest first)
        Collections.sort(sortedList, (a, b) -> Integer.compare(b.optInt("id"), a.optInt("id")));

        List<JSONObject> visibleOrders = new ArrayList<>();

        for (JSONObject order : sortedList) {
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
            int orderId = order.optInt("id", order.optInt("order_id", -1));

            // Only show active orders unless it's specifically requested
            if (expectedOrderId != orderId) {
                if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                    continue;
                }
            }

            visibleOrders.add(order);
        }

        if (visibleOrders.isEmpty()) {
            if (expectedOrderId > 0) {
                showFallbackActiveOrder(expectedOrderId, getCachedActiveOrderStatus());
            } else if (ordersContainer.getChildCount() == 0) {
                showEmptyMessage("No active orders found.");
            }
            return;
        }

        ordersContainer.removeAllViews();
        for (JSONObject order : visibleOrders) {
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
            int orderId = order.optInt("id", order.optInt("order_id", -1));
            ordersContainer.addView(createOrderCard(order, orderId, status));
        }
    }

    private String getCachedActiveOrderStatus() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String cachedOrder = prefs.getString("last_active_order_json", "");
        if (TextUtils.isEmpty(cachedOrder)) {
            return Constants.STATUS_PENDING;
        }

        try {
            JSONObject order = new JSONObject(cachedOrder);
            return ActiveOrderActivity.normalizeStatus(order.optString("status", Constants.STATUS_PENDING));
        } catch (Exception e) {
            return Constants.STATUS_PENDING;
        }
    }

    private View createOrderCard(JSONObject order, int orderId, String status) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.view_border);
        box.setPadding(32, 32, 32, 32);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 32);
        box.setLayoutParams(lp);

        String restaurant = order.optString("restaurant_name", "Restaurant");
        double total = order.optDouble("total_amount", order.optDouble("total", 0.0));

        TextView header = new TextView(this);
        header.setText(String.format(Locale.getDefault(), "Order #%d - %s", orderId, restaurant));
        header.setTextSize(18f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(getResources().getColor(android.R.color.black));
        box.addView(header);

        TextView statusText = new TextView(this);
        statusText.setPadding(0, 12, 0, 12);
        statusText.setText(renderTimeline(status));
        statusText.setLineSpacing(1.1f, 1.1f);
        box.addView(statusText);

        TextView footer = new TextView(this);
        footer.setText(String.format(Locale.getDefault(), "Total: P%.2f", total));
        footer.setGravity(android.view.Gravity.END);
        footer.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(footer);

        return box;
    }

    private void showEmptyMessage(String msg) {
        ordersContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(msg);
        empty.setPadding(32, 64, 32, 32);
        empty.setGravity(android.view.Gravity.CENTER);
        ordersContainer.addView(empty);
    }

    private String renderTimeline(String status) {
        StringBuilder builder = new StringBuilder();
        int current = timeline.indexOf(status);
        
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
            String prefix;
            if (current > i) prefix = "✓ ";
            else if (current == i) prefix = "▶ ";
            else prefix = "○ ";
            
            builder.append(prefix).append(getFriendlyStatus(step));
            if (i < timeline.size() - 1) builder.append("\n");
        }
        return builder.toString();
    }

    private String getFriendlyStatus(String status) {
        switch (status) {
            case Constants.STATUS_PENDING: return "Order Placed";
            case Constants.STATUS_ACCEPTED: return "Accepted by Restaurant";
            case Constants.STATUS_PREPARING: return "Preparing Food";
            case Constants.STATUS_READY: return "Food is Ready";
            case Constants.STATUS_PICKED_UP: return "Picked Up";
            case Constants.STATUS_ARRIVED_RESTAURANT: return "Driver at Store";
            case Constants.STATUS_OUT_FOR_DELIVERY: return "Out for Delivery";
            case Constants.STATUS_DELIVERED: return "Delivered";
            default: return status.replace("_", " ").toUpperCase();
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v) && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        }
        return "";
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!TextUtils.isEmpty(token)) headers.put("Authorization", "Bearer " + token);
        return headers;
    }
}
