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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderHistoryActivity extends AppCompatActivity {

    private static final String TAG = "OrderHistoryActivity";
    private static final long POLL_MS = 10000L;
    private static final String KEY_ORDER_HISTORY_CACHE = "order_history_cache_json";
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(\\d+)");

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private ApiService apiService;

    private LinearLayout historyContainer;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private TextView tabNotificationsBadgeTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        if (!AccessControlManager.requireAccess(this,
            AccessControlManager.Resource.ORDER_TRACKING,
            AccessControlManager.Action.READ)) {
            return;
        }

        apiService = RetrofitClient.getApiService();

        historyContainer = findViewById(R.id.historyContainer);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);

        setupBottomNavigation();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();

        pollHistory();
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
                pollHistory();
                pollingHandler.postDelayed(this, POLL_MS);
            }
        }, POLL_MS);
    }

    private void pollHistory() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        if (userId <= 0) {
            Log.e(TAG, "No user ID found in prefs");
            return;
        }

        apiService.getOrders(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = mergeHistoryOrders(extractOrders(jsonResponse));
                    if (!orders.isEmpty()) {
                        renderHistory(orders);
                    } else if (historyContainer.getChildCount() == 0) {
                        List<JSONObject> cachedHistory = loadCachedOrderHistory();
                        if (!cachedHistory.isEmpty()) {
                            renderHistory(cachedHistory);
                        } else {
                            showEmptyMessage("No order history found.");
                        }
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (historyContainer.getChildCount() == 0) {
                    List<JSONObject> cachedHistory = loadCachedOrderHistory();
                    if (!cachedHistory.isEmpty()) {
                        renderHistory(cachedHistory);
                    } else {
                        showEmptyMessage("No order history found.");
                    }
                }
            }
        });

        apiService.getOrdersLegacy(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = mergeHistoryOrders(extractOrders(jsonResponse));
                    if (!orders.isEmpty()) {
                        renderHistory(orders);
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {}
        });
    }

    private List<JSONObject> extractOrders(JSONObject response) {
        List<JSONObject> list = new ArrayList<>();
        if (response == null) return list;

        JSONArray orders = response.optJSONArray("orders");
        if (orders == null) orders = response.optJSONArray("data");
        if (orders == null) orders = response.optJSONArray("history");
        if (orders == null) orders = response.optJSONArray("order_history");
        if (orders == null) orders = response.optJSONArray("results");
        if (orders == null) {
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                orders = data.optJSONArray("orders");
                if (orders == null) orders = data.optJSONArray("history");
                if (orders == null) orders = data.optJSONArray("order_history");
                if (orders == null) orders = data.optJSONArray("results");
            }
        }
        if (orders != null) {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject o = orders.optJSONObject(i);
                if (o != null) list.add(o);
            }
        } else {
            if (!TextUtils.isEmpty(extractOrderKey(response)) || response.has("items") || response.has("status")) {
                list.add(response);
            } else if (response.has("data")) {
                JSONObject data = response.optJSONObject("data");
                if (data != null && !TextUtils.isEmpty(extractOrderKey(data))) {
                    list.add(data);
                }
            }
        }
        return list;
    }

    private List<JSONObject> loadCachedOrderHistory() {
        List<JSONObject> history = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String cached = prefs.getString(KEY_ORDER_HISTORY_CACHE, "");
        if (TextUtils.isEmpty(cached)) {
            return history;
        }

        try {
            JSONArray array = new JSONArray(cached);
            for (int i = 0; i < array.length(); i++) {
                JSONObject order = array.optJSONObject(i);
                if (order != null) {
                    if (TextUtils.isEmpty(extractOrderKey(order))) {
                        continue;
                    }
                    String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
                    if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                        history.add(order);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load cached order history", e);
        }

        return history;
    }

    private List<JSONObject> mergeHistoryOrders(List<JSONObject> freshOrders) {
        Map<String, JSONObject> merged = new HashMap<>();

        for (JSONObject cachedOrder : loadCachedOrderHistory()) {
            String key = extractOrderKey(cachedOrder);
            if (!TextUtils.isEmpty(key)) {
                merged.put(key, cachedOrder);
            }
        }

        for (JSONObject freshOrder : freshOrders) {
            String key = extractOrderKey(freshOrder);
            if (TextUtils.isEmpty(key)) {
                continue;
            }

            String status = ActiveOrderActivity.normalizeStatus(freshOrder.optString("status", ""));
            if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                merged.put(key, freshOrder);
            }
        }

        List<JSONObject> list = new ArrayList<>(merged.values());
        Collections.sort(list, (a, b) -> Integer.compare(b.optInt("id", b.optInt("order_id", -1)), a.optInt("id", a.optInt("order_id", -1))));
        persistOrderHistoryCache(list);
        return list;
    }

    private void persistOrderHistoryCache(List<JSONObject> orders) {
        JSONArray updated = new JSONArray();
        for (JSONObject order : orders) {
            if (order != null) {
                updated.put(order);
            }
        }

        getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                .edit()
                .putString(KEY_ORDER_HISTORY_CACHE, updated.toString())
                .apply();
    }

    private void renderHistory(List<JSONObject> orders) {
        Map<String, JSONObject> unique = new HashMap<>();
        for (JSONObject o : orders) {
            String key = extractOrderKey(o);
            if (!TextUtils.isEmpty(key)) unique.put(key, o);
        }

        List<JSONObject> list = new ArrayList<>(unique.values());
        Collections.sort(list, (a, b) -> Integer.compare(extractOrderId(b), extractOrderId(a)));

        List<JSONObject> history = new ArrayList<>();
        for (JSONObject o : list) {
            String status = ActiveOrderActivity.normalizeStatus(o.optString("status", ""));
            if (Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_CANCELLED.equals(status)) {
                history.add(o);
            }
        }

        if (history.isEmpty()) {
            showEmptyMessage("No order history found.");
            return;
        }

        historyContainer.removeAllViews();
        for (JSONObject o : history) {
            historyContainer.addView(createOrderCard(o, getOrderDisplayRef(o)));
        }
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

    private int parseOrderId(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return -1;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
        }

        Matcher matcher = DIGITS_PATTERN.matcher(raw);
        String lastDigits = null;
        while (matcher.find()) {
            lastDigits = matcher.group(1);
        }
        if (!TextUtils.isEmpty(lastDigits)) {
            try {
                return Integer.parseInt(lastDigits);
            } catch (Exception ignored) {
            }
        }

        return -1;
    }

    private String getOrderDisplayRef(JSONObject order) {
        int id = extractOrderId(order);
        if (id > 0) {
            return "#" + id;
        }

        String ref = firstNonEmpty(
                order.optString("order_no", ""),
                order.optString("order_number", ""),
                order.optString("order_id", ""),
                order.optString("orderId", ""),
                order.optString("id", "")
        );
        if (TextUtils.isEmpty(ref)) {
            return "#Unknown";
        }
        return "#" + ref;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private View createOrderCard(JSONObject order, String orderRef) {
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
        header.setText(String.format(Locale.getDefault(), "Order %s - %s", orderRef, restaurant));
        header.setTextSize(18f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(getResources().getColor(android.R.color.black));
        box.addView(header);

        String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
        TextView statusText = new TextView(this);
        statusText.setPadding(0, 12, 0, 12);
        statusText.setText(status.replace("_", " ").toUpperCase());
        box.addView(statusText);

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomRow.setPadding(0, 12, 0, 0);

        TextView footer = new TextView(this);
        footer.setText(String.format(Locale.getDefault(), "Total: P%.2f", total));
        footer.setTypeface(null, android.graphics.Typeface.BOLD);
        footer.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bottomRow.addView(footer);

        Button viewBtn = new Button(this);
        viewBtn.setText("View");
        viewBtn.setLayoutParams(new LinearLayout.LayoutParams(160, LinearLayout.LayoutParams.WRAP_CONTENT));
        viewBtn.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, OrderDetailActivity.class);
                intent.putExtra("order_json", order.toString());
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open order details", Toast.LENGTH_SHORT).show();
            }
        });
        bottomRow.addView(viewBtn);

        box.addView(bottomRow);

        return box;
    }

    private void showEmptyMessage(String msg) {
        historyContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(msg);
        empty.setPadding(32, 64, 32, 32);
        empty.setGravity(android.view.Gravity.CENTER);
        historyContainer.addView(empty);
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
                Intent intent = new Intent(this, CartActivity.class);
                startActivity(intent);
                finish();
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

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!TextUtils.isEmpty(token)) headers.put("Authorization", "Bearer " + token);
        return headers;
    }

}
