package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotificationActivity extends AppCompatActivity {

    private RequestQueue requestQueue;
    private LinearLayout notificationsContainer;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;

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
        btnRefreshNotifications.setOnClickListener(v -> loadOrderUpdates());
        loadOrderUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadgeFromPrefs();
        loadOrderUpdates();
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

    private void loadOrderUpdates() {
        int userId = getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getInt("user_id", -1);
        if (userId <= 0) {
            renderEmpty("No account session found.");
            return;
        }

        String url = Constants.URL_ORDERS + "/" + userId;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> renderNotifications(extractOrders(response)),
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
                response -> renderNotifications(extractOrders(response)),
                error -> renderEmpty("No order updates yet.")
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

        if (orders != null) {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order != null) list.add(order);
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
        notificationsContainer.removeAllViews();
        if (orders.isEmpty()) {
            renderEmpty("No order updates yet.");
            return;
        }

        int rendered = 0;
        for (JSONObject order : orders) {
            if (rendered >= 12) break;

            int orderId = order.optInt("id", order.optInt("order_id", -1));
            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", "pending"));
            String restaurant = order.optString("restaurant_name", "Restaurant");

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
            title.setText("Order #" + orderId + " Update");
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextSize(16f);

            TextView detail = new TextView(this);
            detail.setText("Your order from " + restaurant + " is now " + toFriendlyStatus(status) + ".");
            detail.setPadding(0, 6, 0, 6);

            String location = order.optString("driver_location", "");
            if (!TextUtils.isEmpty(location) && !"null".equalsIgnoreCase(location.trim())) {
                TextView locationView = new TextView(this);
                locationView.setText("Driver: " + location);
                locationView.setTextSize(12f);
                card.addView(locationView);
            }

            Button trackButton = new Button(this);
            trackButton.setText("Track Order");
            trackButton.setOnClickListener(v -> {
                Intent intent = new Intent(NotificationActivity.this, OrderTrackingActivity.class);
                intent.putExtra("order_id", orderId);
                startActivity(intent);
                finish();
            });

            card.addView(title);
            card.addView(detail);
            card.addView(trackButton);
            notificationsContainer.addView(card);
            rendered++;
        }
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
