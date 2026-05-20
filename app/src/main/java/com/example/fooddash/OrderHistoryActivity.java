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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderHistoryActivity extends AppCompatActivity {

    private static final String TAG = "OrderHistoryActivity";
    private static final long POLL_MS = 10000L;

    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private RequestQueue requestQueue;

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

        requestQueue = Volley.newRequestQueue(this);

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

        fetchOrdersFromEndpoint(Constants.URL_ORDERS + "?user_id=" + userId, false);
        fetchOrdersFromEndpoint(Constants.URL_GET_ORDERS_LEGACY + "?user_id=" + userId, true);
    }

    private void fetchOrdersFromEndpoint(String url, boolean isLegacy) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    List<JSONObject> orders = extractOrders(response);
                    if (!orders.isEmpty()) {
                        renderHistory(orders);
                    } else if (historyContainer.getChildCount() == 0) {
                        showEmptyMessage("No order history found.");
                    }
                },
                error -> {
                    if (isLegacy) Log.e(TAG, "Failed to poll legacy orders: " + error.toString());
                    if (historyContainer.getChildCount() == 0) {
                        showEmptyMessage("No order history found.");
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() { return buildAuthHeaders(); }
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
        if (orders != null) {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject o = orders.optJSONObject(i);
                if (o != null) list.add(o);
            }
        } else {
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

    private void renderHistory(List<JSONObject> orders) {
        Map<Integer, JSONObject> unique = new HashMap<>();
        for (JSONObject o : orders) {
            int id = o.optInt("id", o.optInt("order_id", -1));
            if (id > 0) unique.put(id, o);
        }

        List<JSONObject> list = new ArrayList<>(unique.values());
        Collections.sort(list, (a, b) -> Integer.compare(b.optInt("id"), a.optInt("id")));

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
            int id = o.optInt("id", o.optInt("order_id", -1));
            historyContainer.addView(createOrderCard(o, id));
        }
    }

    private View createOrderCard(JSONObject order, int orderId) {
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
