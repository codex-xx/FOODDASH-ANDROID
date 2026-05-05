package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationActivity extends AppCompatActivity {

    private static final long POLL_INTERVAL = 5000L; // Poll every 5 seconds
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String KEY_NOTIFICATION_HISTORY_JSON = "notification_history_json";

    private RequestQueue requestQueue;
    private LinearLayout notificationsContainer;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.ORDER_TRACKING,
                AccessControlManager.Action.READ)) {
            return;
        }
        requestQueue = Volley.newRequestQueue(this);
        notificationsContainer = findViewById(R.id.notificationsContainer);
        Button btnRefreshNotifications = findViewById(R.id.btnRefreshNotifications);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);

        setupBottomNavigation();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();
        btnRefreshNotifications.setOnClickListener(v -> loadOrderUpdates());
        loadOrderUpdates();
        startPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();
        loadOrderUpdates();
        startPolling();
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
                loadOrderUpdates();
                pollingHandler.postDelayed(this, POLL_INTERVAL);
            }
        }, POLL_INTERVAL);
    }

    private void stopPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void setupBottomNavigation() {
        highlightBottomTab(tabNotificationsButton);

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
            tabOrdersButton.setOnClickListener(v -> {
                highlightBottomTab(tabOrdersButton);
                startActivity(new Intent(this, OrderTrackingActivity.class));
                finish();
            });
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> {
                highlightBottomTab(tabCartButton);
                openCartFromPrefs();
            });
        }

        if (tabNotificationsButton != null) {
            tabNotificationsButton.setOnClickListener(v -> highlightBottomTab(tabNotificationsButton));
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
            tabCartBadgeTextView.setVisibility(android.view.View.GONE);
            return;
        }
        tabCartBadgeTextView.setVisibility(android.view.View.VISIBLE);
        tabCartBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private int getCartItemCountFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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

    private void loadOrderUpdates() {
        int userId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("user_id", -1);
        if (userId <= 0) {
            renderStoredNotificationsOrEmpty("No account session found.");
            return;
        }

        // Try the same endpoint variants used in tracking so status updates are not missed.
        loadOrderUpdatesFromModernList(userId);
    }

    private void loadOrderUpdatesFromModernList(int userId) {
        String url = Constants.URL_ORDERS + "?user_id=" + userId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    List<JSONObject> orders = extractOrders(response);
                    if (orders.isEmpty()) {
                        loadOrderUpdatesFromModernPath(userId);
                    } else {
                        renderNotifications(orders);
                    }
                },
                error -> loadOrderUpdatesFromModernPath(userId)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = AuthSessionManager.getValidAccessTokenOrNull(NotificationActivity.this);
                if (!TextUtils.isEmpty(token)) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        requestQueue.add(request);
    }

    private void loadOrderUpdatesFromModernPath(int userId) {
        String url = Constants.URL_ORDERS + "/" + userId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    List<JSONObject> orders = extractOrders(response);
                    if (orders.isEmpty()) {
                        loadOrderUpdatesLegacy(userId);
                    } else {
                        renderNotifications(orders);
                    }
                },
                error -> loadOrderUpdatesLegacy(userId)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = AuthSessionManager.getValidAccessTokenOrNull(NotificationActivity.this);
                if (!TextUtils.isEmpty(token)) headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        requestQueue.add(request);
    }

    private void loadOrderUpdatesLegacy(int userId) {
        String url = Constants.URL_GET_ORDERS_LEGACY + "?user_id=" + userId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    List<JSONObject> orders = extractOrders(response);
                    if (orders.isEmpty()) {
                        renderStoredNotificationsOrEmpty("No order updates yet.");
                    } else {
                        renderNotifications(orders);
                    }
                },
                error -> renderStoredNotificationsOrEmpty("No order updates yet.")
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                String token = AuthSessionManager.getValidAccessTokenOrNull(NotificationActivity.this);
                if (!TextUtils.isEmpty(token)) headers.put("Authorization", "Bearer " + token);
                return headers;
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
                JSONObject order = orders.optJSONObject(i);
                if (order != null) list.add(order);
            }
        } else {
            // Single-order response fallback
            if (response.has("id") || response.has("order_id") || response.has("status") || response.has("items")) {
                list.add(response);
            } else if (response.has("data")) {
                JSONObject data = response.optJSONObject("data");
                if (data != null && (data.has("id") || data.has("order_id"))) {
                    list.add(data);
                }
            }
        }

        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Integer.compare(b.optInt("id", b.optInt("order_id", 0)), a.optInt("id", a.optInt("order_id", 0)));
            }
        });
        return list;
    }

    private void renderNotifications(List<JSONObject> orders) {
        JSONArray history = loadNotificationHistory();
        for (JSONObject order : orders) {
            int orderId = order.optInt("id", order.optInt("order_id", -1));
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", "pending"));
            String restaurant = order.optString("restaurant_name", "Restaurant");

            if (!isNotifiableStatus(status)) continue;

            // Keep previous notification stages visible even when order status progresses.
            if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) {
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_ACCEPTED, restaurant, order));
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_PICKED_UP, restaurant, order));
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_OUT_FOR_DELIVERY, restaurant, order));
            } else if (Constants.STATUS_DELIVERED.equals(status)) {
                // When delivered, keep all previous notification stages
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_ACCEPTED, restaurant, order));
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_PICKED_UP, restaurant, order));
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_OUT_FOR_DELIVERY, restaurant, order));
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_DELIVERED, restaurant, order));
            } else if (Constants.STATUS_PICKED_UP.equals(status)) {
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_ACCEPTED, restaurant, order));
                addEventIfAbsent(history, createNotificationEvent(orderId, Constants.STATUS_PICKED_UP, restaurant, order));
            } else {
                addEventIfAbsent(history, createNotificationEvent(orderId, status, restaurant, order));
            }
        }

        saveNotificationHistory(history);
        renderNotificationHistory(history, "No accepted, driver-accepted, or delivery-ready updates yet.");
    }

    private void addEventIfAbsent(JSONArray history, JSONObject event) {
        String eventKey = event.optString("event_key", "");
        if (!containsEventKey(history, eventKey)) {
            history.put(event);
        }
    }

    private void renderStoredNotificationsOrEmpty(String emptyMessage) {
        JSONArray history = loadNotificationHistory();
        renderNotificationHistory(history, emptyMessage);
    }

    private void renderNotificationHistory(JSONArray history, String emptyMessage) {
        notificationsContainer.removeAllViews();
        if (history == null || history.length() == 0) {
            renderEmpty(emptyMessage);
            updateNotificationsTabCount();
            return;
        }

        int rendered = 0;
        for (int i = history.length() - 1; i >= 0; i--) {
            if (rendered >= 24) break;
            JSONObject event = history.optJSONObject(i);
            if (event == null) continue;

            int orderId = event.optInt("order_id", -1);
            String status = event.optString("status", "");
            String titleText = event.optString("title", "Order Update");
            String messageText = event.optString("message", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.view_border);
            card.setPadding(24, 20, 24, 20);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 16);
            card.setLayoutParams(cardParams);

            TextView title = new TextView(this);
            title.setText(orderId > 0 ? "Order #" + orderId + " " + titleText : titleText);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextSize(16f);

            TextView detail = new TextView(this);
            detail.setText(messageText);
            detail.setPadding(0, 6, 0, 6);

            card.addView(title);
            card.addView(detail);

            if (Constants.STATUS_PICKED_UP.equals(status) || Constants.STATUS_OUT_FOR_DELIVERY.equals(status) || Constants.STATUS_DELIVERED.equals(status)) {
                addDriverDetailsView(card, event);
            }

            notificationsContainer.addView(card);
            rendered++;
        }

        if (rendered == 0) {
            renderEmpty(emptyMessage);
        }
        updateNotificationsTabCount();
    }

    private void addDriverDetailsView(LinearLayout card, JSONObject source) {
        String driverName = firstNonEmpty(source.optString("driver_name"), source.optString("rider_name"));
        String driverPhone = firstNonEmpty(source.optString("driver_phone"), source.optString("driver_contact"));
        String driverAvatar = firstNonEmpty(source.optString("driver_avatar"), source.optString("driver_image"));

        if (driverName.isEmpty() && driverPhone.isEmpty() && driverAvatar.isEmpty()) return;

        LinearLayout driverBox = new LinearLayout(this);
        driverBox.setOrientation(LinearLayout.HORIZONTAL);
        driverBox.setPadding(0, 12, 0, 12);

        ImageView img = new ImageView(this);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(80, 80);
        imgLp.setMargins(0, 0, 12, 0);
        img.setLayoutParams(imgLp);
        if (!driverAvatar.isEmpty()) {
            Glide.with(this).load(driverAvatar).placeholder(R.drawable.ic_launcher_foreground).into(img);
        } else {
            img.setImageResource(R.drawable.ic_launcher_foreground);
        }
        driverBox.addView(img);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView dn = new TextView(this);
        dn.setText(!driverName.isEmpty() ? driverName : "Driver");
        dn.setTypeface(null, android.graphics.Typeface.BOLD);
        dn.setTextSize(14f);
        info.addView(dn);

        TextView dp = new TextView(this);
        dp.setText(!driverPhone.isEmpty() ? "Phone: " + driverPhone : "Phone: N/A");
        dp.setTextColor(getResources().getColor(R.color.primary_blue));
        dp.setPadding(0, 4, 0, 0);
        if (!driverPhone.isEmpty()) {
            final String driverPhoneFinal = driverPhone;
            dp.setOnClickListener(v -> {
                try {
                    Intent i = new Intent(Intent.ACTION_DIAL);
                    i.setData(Uri.parse("tel:" + driverPhoneFinal));
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }
        info.addView(dp);

        driverBox.addView(info);
        card.addView(driverBox);
    }

    private boolean isNotifiableStatus(String status) {
        return Constants.STATUS_ACCEPTED.equals(status)
                || Constants.STATUS_PICKED_UP.equals(status)
                || Constants.STATUS_OUT_FOR_DELIVERY.equals(status)
                || Constants.STATUS_DELIVERED.equals(status);
    }

    private JSONObject createNotificationEvent(int orderId, String status, String restaurant, JSONObject order) {
        JSONObject event = new JSONObject();
        try {
            event.put("event_key", orderId + "_" + status);
            event.put("order_id", orderId);
            event.put("status", status);
            event.put("title", getStatusTitle(status));
            event.put("message", getStatusMessage(status, restaurant));
            event.put("created_at", System.currentTimeMillis());

            // Keep driver details on driver-stage notifications.
            JSONObject driverObj = order.optJSONObject("driver");
            if (driverObj != null) {
                event.put("driver_name", firstNonEmpty(driverObj.optString("name"), driverObj.optString("driver_name"), driverObj.optString("full_name")));
                event.put("driver_phone", firstNonEmpty(driverObj.optString("phone"), driverObj.optString("contact"), driverObj.optString("mobile")));
                event.put("driver_avatar", firstNonEmpty(driverObj.optString("avatar"), driverObj.optString("image"), driverObj.optString("photo")));
            } else {
                event.put("driver_name", firstNonEmpty(order.optString("driver_name"), order.optString("rider_name")));
                event.put("driver_phone", firstNonEmpty(order.optString("driver_phone"), order.optString("driver_contact"), order.optString("phone")));
                event.put("driver_avatar", firstNonEmpty(order.optString("driver_avatar"), order.optString("driver_image")));
            }
        } catch (Exception ignored) {}
        return event;
    }

    private boolean containsEventKey(JSONArray history, String eventKey) {
        if (TextUtils.isEmpty(eventKey)) return true;
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            if (eventKey.equals(item.optString("event_key", ""))) return true;
        }
        return false;
    }

    private JSONArray loadNotificationHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String raw = prefs.getString(KEY_NOTIFICATION_HISTORY_JSON, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private void saveNotificationHistory(JSONArray history) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_NOTIFICATION_HISTORY_JSON, history.toString())
                .apply();
    }

    private void updateNotificationsTabCount() {
        if (tabNotificationsButton == null) return;
        int count = loadNotificationHistory().length();
        if (count <= 0) {
            tabNotificationsButton.setText("Notifications");
            return;
        }
        String badge = count > 99 ? "99+" : String.valueOf(count);
        tabNotificationsButton.setText("Notif (" + badge + ")");
    }

    private String getStatusTitle(String status) {
        if (Constants.STATUS_ACCEPTED.equals(status)) return "Accepted";
        if (Constants.STATUS_PICKED_UP.equals(status)) return "Driver Accepted";
        if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) return "Ready to Deliver";
        if (Constants.STATUS_DELIVERED.equals(status)) return "Delivered";
        return "Update";
    }

    private String getStatusMessage(String status, String restaurant) {
        switch (status) {
            case Constants.STATUS_ACCEPTED:
                return "Restaurant accepted your order from " + restaurant + ".";
            case Constants.STATUS_PICKED_UP:
                return "Driver accepted and picked up your order from " + restaurant + ".";
            case Constants.STATUS_OUT_FOR_DELIVERY:
                return "Driver is ready to deliver your order from " + restaurant + ".";
            case Constants.STATUS_DELIVERED:
                return "Your order from " + restaurant + " has been delivered. Enjoy your meal!";
            default:
                return "";
        }
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v) && !"null".equalsIgnoreCase(v.trim())) return v.trim();
        }
        return "";
    }

    private void renderEmpty(String message) {
        notificationsContainer.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setPadding(24, 40, 24, 24);
        empty.setGravity(android.view.Gravity.CENTER);
        notificationsContainer.addView(empty);
    }

    private String toFriendlyStatus(String status) {
        switch (status) {
            case Constants.STATUS_PENDING:
                return "Order Placed";
            case Constants.STATUS_ACCEPTED:
                return "Accepted by Restaurant";
            case Constants.STATUS_PREPARING:
                return "Preparing Food";
            case Constants.STATUS_READY:
                return "Food Ready";
            case Constants.STATUS_PICKED_UP:
                return "Picked Up";
            case Constants.STATUS_ARRIVED_RESTAURANT:
                return "Driver at Restaurant";
            case Constants.STATUS_OUT_FOR_DELIVERY:
                return "Out for Delivery";
            case Constants.STATUS_DELIVERED:
                return "Delivered";
            case Constants.STATUS_CANCELLED:
                return "Cancelled";
            default:
                return status.replace("_", " ").toUpperCase(Locale.ROOT);
        }
    }
}
