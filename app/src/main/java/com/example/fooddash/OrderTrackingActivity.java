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

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderTrackingActivity extends AppCompatActivity {

    private static final String TAG = "OrderTrackingActivity";
    private static final long POLL_MS = 5000L;
    private static final String KEY_ACTIVE_ORDERS_CACHE = "active_orders_cache_json";
    private static final String KEY_ORDER_HISTORY_CACHE = "order_history_cache_json";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(\\d+)");

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private ApiService apiService;

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
    private JSONObject initialOrder;
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
        String orderJson = getIntent().getStringExtra("order_json");
        if (!TextUtils.isEmpty(orderJson)) {
            try {
                initialOrder = new JSONObject(orderJson);
                int initialOrderId = initialOrder.optInt("id", initialOrder.optInt("order_id", -1));
                if (initialOrderId > 0) {
                    expectedOrderId = initialOrderId;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse initial order payload", e);
            }
        }
        apiService = RetrofitClient.getApiService();

        ordersContainer = findViewById(R.id.ordersContainer);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);

        btnBackToDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderHistoryActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        setupBottomNavigation();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();

        loadCachedActiveOrder();
        if (initialOrder != null) {
            renderOrders(mergeActiveOrders(Collections.singletonList(initialOrder)));
        }
        
        pollOrders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();
        pollOrders();
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

        List<JSONObject> trackedOrders = loadCachedActiveOrders();
        if (!trackedOrders.isEmpty()) {
            refreshTrackedActiveOrders(userId, trackedOrders);
            return;
        }

        apiService.getOrders(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = extractOrders(jsonResponse);
                    if (!orders.isEmpty()) {
                        NotificationStore.mergeFetchedOrders(OrderTrackingActivity.this, orders);
                        updateNotificationsTabCount();
                        renderOrders(mergeActiveOrders(orders));
                    } else if (ordersContainer.getChildCount() == 0) {
                        fetchLatestCustomerOrderLegacy(userId);
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                fetchLatestCustomerOrderLegacy(userId);
            }
        });
    }

    private void refreshTrackedActiveOrders(int userId, List<JSONObject> trackedOrders) {
        List<Integer> trackedOrderIds = new ArrayList<>();
        for (JSONObject order : trackedOrders) {
            if (order == null) continue;
            int orderId = order.optInt("id", order.optInt("order_id", -1));
            if (orderId > 0 && !trackedOrderIds.contains(orderId)) {
                trackedOrderIds.add(orderId);
            }
        }

        if (trackedOrderIds.isEmpty()) {
            fetchLatestCustomerOrderLegacy(userId);
            return;
        }

        final List<JSONObject> refreshedOrders = new ArrayList<>();
        final int[] remaining = {trackedOrderIds.size()};
        final boolean[] hadSuccess = {false};

        for (Integer orderId : trackedOrderIds) {
            apiService.getOrderDetails(orderId).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    try {
                        String body = response.body() != null ? response.body().string() : "{}";
                        JSONObject jsonResponse = new JSONObject(body);
                        List<JSONObject> orders = extractOrders(jsonResponse);
                        if (!orders.isEmpty()) {
                            refreshedOrders.addAll(orders);
                            NotificationStore.mergeFetchedOrders(OrderTrackingActivity.this, orders);
                            hadSuccess[0] = true;
                        } else if (jsonResponse.has("id") || jsonResponse.has("order_id")) {
                            refreshedOrders.add(jsonResponse);
                            NotificationStore.mergeFetchedOrders(OrderTrackingActivity.this, Collections.singletonList(jsonResponse));
                            hadSuccess[0] = true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to refresh tracked order #" + orderId, e);
                    } finally {
                        finishTrackedOrdersRefresh(userId, trackedOrders, refreshedOrders, hadSuccess, remaining);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "Failed to refresh tracked order #" + orderId, t);
                    finishTrackedOrdersRefresh(userId, trackedOrders, refreshedOrders, hadSuccess, remaining);
                }
            });
        }
    }

    private void finishTrackedOrdersRefresh(int userId,
                                            List<JSONObject> trackedOrders,
                                            List<JSONObject> refreshedOrders,
                                            boolean[] hadSuccess,
                                            int[] remaining) {
        remaining[0]--;
        if (remaining[0] > 0) {
            return;
        }

        if (!refreshedOrders.isEmpty()) {
            updateNotificationsTabCount();
            renderOrders(mergeActiveOrders(refreshedOrders));
            return;
        }

        if (hadSuccess[0]) {
            renderOrders(mergeActiveOrders(trackedOrders));
            return;
        }

        reconcileTrackedOrdersFromListApi(userId);
    }

    private void reconcileTrackedOrdersFromListApi(int userId) {
        apiService.getOrders(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = extractOrders(jsonResponse);
                    if (!orders.isEmpty()) {
                        NotificationStore.mergeFetchedOrders(OrderTrackingActivity.this, orders);
                        updateNotificationsTabCount();
                    }
                    List<JSONObject> merged = mergeActiveOrders(orders);
                    if (!merged.isEmpty()) {
                        renderOrders(merged);
                    } else {
                        clearActiveOrdersCache();
                        showEmptyMessage("No active orders found.");
                    }
                } catch (Exception e) {
                    fetchLatestCustomerOrderLegacy(userId);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                fetchLatestCustomerOrderLegacy(userId);
            }
        });
    }

    private void fetchLatestCustomerOrderLegacy(int userId) {
        apiService.getOrdersLegacy(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = extractOrders(jsonResponse);
                    if (!orders.isEmpty()) {
                        NotificationStore.mergeFetchedOrders(OrderTrackingActivity.this, orders);
                        updateNotificationsTabCount();
                        renderOrders(mergeActiveOrders(orders));
                    } else {
                        clearActiveOrdersCache();
                        showEmptyMessage("No active orders found.");
                    }
                } catch (Exception ignored) {
                    clearActiveOrdersCache();
                    showEmptyMessage("No active orders found.");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (ordersContainer.getChildCount() == 0) {
                    List<JSONObject> cachedOrders = loadCachedActiveOrders();
                    if (!cachedOrders.isEmpty()) {
                        renderOrders(cachedOrders);
                    } else {
                        showEmptyMessage("No active orders found.");
                    }
                }
            }
        });
    }

    private void loadCachedActiveOrder() {
        List<JSONObject> cachedOrders = loadCachedActiveOrders();
        if (!cachedOrders.isEmpty()) {
            renderOrders(cachedOrders);
        }
    }

    private List<JSONObject> loadCachedActiveOrders() {
        List<JSONObject> orders = new ArrayList<>();
        List<JSONObject> filtered = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);

        String cachedList = prefs.getString(KEY_ACTIVE_ORDERS_CACHE, "");
        if (!TextUtils.isEmpty(cachedList)) {
            try {
                JSONArray array = new JSONArray(cachedList);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject order = array.optJSONObject(i);
                    if (order != null) {
                        orders.add(order);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load cached active orders list", e);
            }
        }

        for (JSONObject order : orders) {
            if (order == null) continue;
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
            if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                retireCompletedOrder(order);
                continue;
            }
            filtered.add(order);
        }

        orders = filtered;

        if (orders.isEmpty()) {
            String cachedOrder = prefs.getString("last_active_order_json", "");
            if (!TextUtils.isEmpty(cachedOrder)) {
                try {
                    JSONObject order = new JSONObject(cachedOrder);
                    String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
                    if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                        retireCompletedOrder(order);
                    } else {
                        orders.add(order);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load cached active order", e);
                }
            }
        }

        return orders;
    }

    private List<JSONObject> mergeActiveOrders(List<JSONObject> freshOrders) {
        Map<String, JSONObject> merged = new HashMap<>();
        for (JSONObject cachedOrder : loadCachedActiveOrders()) {
            String key = extractOrderKey(cachedOrder);
            if (TextUtils.isEmpty(key)) continue;
            String status = ActiveOrderActivity.normalizeStatus(cachedOrder.optString("status", ""));
            if (!Constants.STATUS_DELIVERED.equals(status) && !Constants.STATUS_CANCELLED.equals(status)) {
                merged.put(key, cachedOrder);
            }
        }

        for (JSONObject freshOrder : freshOrders) {
            String key = extractOrderKey(freshOrder);
            if (TextUtils.isEmpty(key)) continue;
            String status = ActiveOrderActivity.normalizeStatus(freshOrder.optString("status", ""));
            if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                persistOrderHistoryCache(Collections.singletonList(freshOrder));
                merged.remove(key);
            } else {
                merged.put(key, freshOrder);
            }
        }

        List<JSONObject> list = new ArrayList<>(merged.values());
        persistActiveOrdersCache(list);
        return list;
    }

    private void retireCompletedOrder(JSONObject order) {
        if (order == null) {
            return;
        }

        persistOrderHistoryCache(Collections.singletonList(order));
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String completedKey = extractOrderKey(order);

        JSONArray activeOrders = new JSONArray();
        try {
            String existing = prefs.getString(KEY_ACTIVE_ORDERS_CACHE, "[]");
            if (!TextUtils.isEmpty(existing)) {
                activeOrders = new JSONArray(existing);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load active orders while retiring completed order", e);
        }

        JSONArray updated = new JSONArray();
        for (int i = 0; i < activeOrders.length(); i++) {
            JSONObject existing = activeOrders.optJSONObject(i);
            if (existing == null) {
                continue;
            }

            String existingKey = extractOrderKey(existing);
            if (!TextUtils.isEmpty(completedKey) && completedKey.equals(existingKey)) {
                continue;
            }

            String status = ActiveOrderActivity.normalizeStatus(existing.optString("status", ""));
            if (!Constants.STATUS_DELIVERED.equals(status) && !Constants.STATUS_CANCELLED.equals(status)) {
                updated.put(existing);
            }
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ACTIVE_ORDERS_CACHE, updated.toString());
        if (prefs.getInt("last_active_order_id", -1) == extractOrderId(order)) {
            editor.remove("last_active_order_id");
        }
        if (order.toString().equals(prefs.getString("last_active_order_json", ""))) {
            editor.remove("last_active_order_json");
        }
        editor.apply();
    }

    private void persistOrderHistoryCache(List<JSONObject> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        JSONArray existingHistory = new JSONArray();
        try {
            String cached = prefs.getString(KEY_ORDER_HISTORY_CACHE, "[]");
            if (!TextUtils.isEmpty(cached)) {
                existingHistory = new JSONArray(cached);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load cached order history", e);
        }

        Map<String, JSONObject> merged = new HashMap<>();
        for (int i = 0; i < existingHistory.length(); i++) {
            JSONObject item = existingHistory.optJSONObject(i);
            if (item == null) continue;
            String key = extractOrderKey(item);
            if (!TextUtils.isEmpty(key)) {
                merged.put(key, item);
            }
        }

        for (JSONObject order : orders) {
            if (order == null) continue;
            String key = extractOrderKey(order);
            if (TextUtils.isEmpty(key)) continue;

            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
            if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                merged.put(key, order);
            }
        }

        JSONArray updated = new JSONArray();
        for (JSONObject order : merged.values()) {
            updated.put(order);
        }

        prefs.edit().putString(KEY_ORDER_HISTORY_CACHE, updated.toString()).apply();
    }

    private int extractOrderId(JSONObject order) {
        if (order == null) {
            return -1;
        }

        int id = order.optInt("id", -1);
        if (id > 0) return id;

        id = order.optInt("order_id", -1);
        if (id > 0) return id;

        id = order.optInt("orderId", -1);
        if (id > 0) return id;

        String[] idCandidates = new String[] {
                order.optString("id", ""),
                order.optString("order_id", ""),
                order.optString("orderId", ""),
                order.optString("order_no", ""),
                order.optString("order_number", "")
        };

        for (String candidate : idCandidates) {
            int parsed = parseOrderId(candidate);
            if (parsed > 0) {
                return parsed;
            }
        }

        return -1;
    }

    private String extractOrderKey(JSONObject order) {
        if (order == null) {
            return "";
        }

        int numericId = order.optInt("id", -1);
        if (numericId > 0) {
            return "id:" + numericId;
        }

        numericId = order.optInt("order_id", -1);
        if (numericId > 0) {
            return "order_id:" + numericId;
        }

        numericId = order.optInt("orderId", -1);
        if (numericId > 0) {
            return "orderId:" + numericId;
        }

        String rawOrderId = order.optString("order_id", "").trim();
        if (!TextUtils.isEmpty(rawOrderId)) {
            return "order_id_raw:" + rawOrderId;
        }

        String rawOrderNo = order.optString("order_no", "").trim();
        if (!TextUtils.isEmpty(rawOrderNo)) {
            return "order_no:" + rawOrderNo;
        }

        String rawOrderNumber = order.optString("order_number", "").trim();
        if (!TextUtils.isEmpty(rawOrderNumber)) {
            return "order_number:" + rawOrderNumber;
        }

        String rawOrderIdCamel = order.optString("orderId", "").trim();
        if (!TextUtils.isEmpty(rawOrderIdCamel)) {
            return "orderId_raw:" + rawOrderIdCamel;
        }

        String rawId = order.optString("id", "").trim();
        if (!TextUtils.isEmpty(rawId)) {
            return "id_raw:" + rawId;
        }

        return "";
    }

    private int parseOrderId(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return -1;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
        }

        Matcher matcher = DIGITS_PATTERN.matcher(raw);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (Exception ignored) {
            }
        }

        return -1;
    }

    private void persistActiveOrdersCache(List<JSONObject> orders) {
        JSONArray array = new JSONArray();
        for (JSONObject order : orders) {
            if (order != null) {
                array.put(order);
            }
        }
        getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_ORDERS_CACHE, array.toString())
                .apply();
    }

    private void clearActiveOrdersCache() {
        getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_ORDERS_CACHE, "[]")
                .remove("last_active_order_id")
                .remove("last_active_order_json")
                .apply();
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
            showEmptyMessage("No active orders found.");
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
            showEmptyMessage("No active orders found.");
            return;
        }

        ordersContainer.removeAllViews();
        for (JSONObject order : visibleOrders) {
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
            int orderId = order.optInt("id", order.optInt("order_id", -1));
            ordersContainer.addView(createOrderCard(order, orderId, status));
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
