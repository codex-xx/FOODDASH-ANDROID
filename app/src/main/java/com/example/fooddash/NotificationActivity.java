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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationActivity extends AppCompatActivity {

    private static final long POLL_INTERVAL = 5000L;
    private static final String PREFS_NAME = "fooddash_prefs";

    private ApiService apiService;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            loadOrderUpdates();
            pollingHandler.postDelayed(this, POLL_INTERVAL);
        }
    };

    private RecyclerView notificationsRecyclerView;
    private View notificationsEmptyState;
    private TextView notificationsEmptyTitle;
    private TextView notificationsEmptySubtitle;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private Button btnRefreshNotifications;
    private MaterialButton btnClearAllNotifications;
    private MaterialButton btnMarkAllRead;
    private ChipGroup filterChipGroup;
    private Chip chipAllNotifications;
    private Chip chipUnreadNotifications;
    private Chip chipOrderNotifications;
    private Chip chipPromotionNotifications;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private TextView tabNotificationsBadgeTextView;

    private NotificationGroupAdapter adapter;
    private NotificationStore.NotificationFilter currentFilter = NotificationStore.NotificationFilter.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.ORDER_TRACKING,
                AccessControlManager.Action.READ)) {
            return;
        }

        apiService = RetrofitClient.getApiService();
        bindViews();
        setupRecyclerView();
        setupFilters();
        setupActions();
        setupBottomNavigation();

        updateCartBadgeFromPrefs();
        updateNotificationsBadge();
        loadOrderUpdates();
        startPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadgeFromPrefs();
        updateNotificationsBadge();
        renderCurrentNotifications();
        loadOrderUpdates();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void bindViews() {
        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);
        notificationsEmptyState = findViewById(R.id.notificationsEmptyState);
        notificationsEmptyTitle = findViewById(R.id.notificationsEmptyTitle);
        notificationsEmptySubtitle = findViewById(R.id.notificationsEmptySubtitle);
        swipeRefreshLayout = findViewById(R.id.notificationsSwipeRefreshLayout);
        btnRefreshNotifications = findViewById(R.id.btnRefreshNotifications);
        btnClearAllNotifications = findViewById(R.id.btnClearAllNotifications);
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        filterChipGroup = findViewById(R.id.notificationFilterChipGroup);
        chipAllNotifications = findViewById(R.id.chipAllNotifications);
        chipUnreadNotifications = findViewById(R.id.chipUnreadNotifications);
        chipOrderNotifications = findViewById(R.id.chipOrderNotifications);
        chipPromotionNotifications = findViewById(R.id.chipPromotionNotifications);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);
    }

    private void setupRecyclerView() {
        adapter = new NotificationGroupAdapter(group -> {
            NotificationStore.markGroupRead(this, group.groupKey);
            renderCurrentNotifications();
            updateNotificationsBadge();
        });
        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationsRecyclerView.setItemAnimator(new DefaultItemAnimator());
        notificationsRecyclerView.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                List<NotificationStore.NotificationGroup> groups = adapter.getCurrentList();
                if (position >= groups.size()) {
                    return;
                }
                NotificationStore.NotificationGroup group = groups.get(position);
                NotificationStore.dismissGroupKeys(NotificationActivity.this, Collections.singleton(group.groupKey));
                Toast.makeText(NotificationActivity.this, getString(R.string.notification_removed), Toast.LENGTH_SHORT).show();
                renderCurrentNotifications();
                updateNotificationsBadge();
            }
        });
        itemTouchHelper.attachToRecyclerView(notificationsRecyclerView);

        notificationsRecyclerView.setItemViewCacheSize(12);
        notificationsRecyclerView.setHasFixedSize(false);
    }

    private void setupFilters() {
        if (filterChipGroup != null) {
            filterChipGroup.check(R.id.chipAllNotifications);
        }

        if (chipAllNotifications != null) {
            chipAllNotifications.setOnClickListener(v -> applyFilter(NotificationStore.NotificationFilter.ALL));
        }
        if (chipUnreadNotifications != null) {
            chipUnreadNotifications.setOnClickListener(v -> applyFilter(NotificationStore.NotificationFilter.UNREAD));
        }
        if (chipOrderNotifications != null) {
            chipOrderNotifications.setOnClickListener(v -> applyFilter(NotificationStore.NotificationFilter.ORDERS));
        }
        if (chipPromotionNotifications != null) {
            chipPromotionNotifications.setOnClickListener(v -> applyFilter(NotificationStore.NotificationFilter.PROMOTIONS));
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::loadOrderUpdates);
        }
    }

    private void setupActions() {
        if (btnRefreshNotifications != null) {
            btnRefreshNotifications.setOnClickListener(v -> loadOrderUpdates());
        }
        if (btnClearAllNotifications != null) {
            btnClearAllNotifications.setOnClickListener(v -> clearVisibleNotifications());
        }
        if (btnMarkAllRead != null) {
            btnMarkAllRead.setOnClickListener(v -> markAllNotificationsRead());
        }
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

    private void startPolling() {
        stopPolling();
        pollingHandler.postDelayed(pollingRunnable, POLL_INTERVAL);
    }

    private void stopPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void applyFilter(NotificationStore.NotificationFilter filter) {
        currentFilter = filter;
        if (filterChipGroup != null) {
            switch (filter) {
                case UNREAD:
                    filterChipGroup.check(R.id.chipUnreadNotifications);
                    break;
                case ORDERS:
                    filterChipGroup.check(R.id.chipOrderNotifications);
                    break;
                case PROMOTIONS:
                    filterChipGroup.check(R.id.chipPromotionNotifications);
                    break;
                case ALL:
                default:
                    filterChipGroup.check(R.id.chipAllNotifications);
                    break;
            }
        }
        renderCurrentNotifications();
    }

    private void loadOrderUpdates() {
        int userId = NotificationStore.getCurrentUserId(this);
        if (userId <= 0) {
            renderCurrentNotifications();
            return;
        }

        if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(true);
        }

        loadOrderUpdatesFromModernList(userId);
    }

    private void loadOrderUpdatesFromModernList(int userId) {
        apiService.getOrders(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = extractOrders(jsonResponse);
                    if (orders.isEmpty()) {
                        loadOrderUpdatesFromModernPath(userId);
                    } else {
                        NotificationStore.mergeFetchedOrders(NotificationActivity.this, orders);
                        renderCurrentNotifications();
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                loadOrderUpdatesFromModernPath(userId);
            }
        });
    }

    private void loadOrderUpdatesFromModernPath(int userId) {
        apiService.getOrderDetails(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = extractOrders(jsonResponse);
                    if (orders.isEmpty()) {
                        loadOrderUpdatesLegacy(userId);
                    } else {
                        NotificationStore.mergeFetchedOrders(NotificationActivity.this, orders);
                        renderCurrentNotifications();
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                loadOrderUpdatesLegacy(userId);
            }
        });
    }

    private void loadOrderUpdatesLegacy(int userId) {
        apiService.getOrdersLegacy(userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    List<JSONObject> orders = extractOrders(jsonResponse);
                    if (!orders.isEmpty()) {
                        NotificationStore.mergeFetchedOrders(NotificationActivity.this, orders);
                    }
                    renderCurrentNotifications();
                } catch (Exception e) {
                    renderCurrentNotifications();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                renderCurrentNotifications();
            }
        });
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
            if (response.has("id") || response.has("order_id") || response.has("status") || response.has("items")) {
                list.add(response);
            } else if (response.has("data")) {
                JSONObject data = response.optJSONObject("data");
                if (data != null && (data.has("id") || data.has("order_id"))) {
                    list.add(data);
                }
            }
        }

        Collections.sort(list, (left, right) -> Integer.compare(
                right.optInt("id", right.optInt("order_id", 0)),
                left.optInt("id", left.optInt("order_id", 0))));
        return list;
    }

    private void renderCurrentNotifications() {
        List<NotificationStore.NotificationGroup> groups = NotificationStore.buildGroups(
                NotificationStore.loadHistory(this),
                currentFilter);

        if (adapter != null) {
            adapter.submitList(new ArrayList<>(groups));
        }

        boolean hasItems = !groups.isEmpty();
        if (notificationsRecyclerView != null) {
            notificationsRecyclerView.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        }
        if (notificationsEmptyState != null) {
            notificationsEmptyState.setVisibility(hasItems ? View.GONE : View.VISIBLE);
        }
        if (notificationsEmptyTitle != null) {
            notificationsEmptyTitle.setText(getEmptyStateTitle());
        }
        if (notificationsEmptySubtitle != null) {
            notificationsEmptySubtitle.setText(getEmptyStateMessage());
        }
        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
            swipeRefreshLayout.setRefreshing(false);
        }
        updateActionState(groups);
        updateNotificationsBadge();
    }

    private void updateActionState(List<NotificationStore.NotificationGroup> groups) {
        boolean hasVisibleItems = groups != null && !groups.isEmpty();
        boolean hasUnread = NotificationStore.getUnreadGroupCount(this) > 0;

        if (btnClearAllNotifications != null) {
            btnClearAllNotifications.setEnabled(hasVisibleItems);
            btnClearAllNotifications.setAlpha(hasVisibleItems ? 1f : 0.5f);
        }
        if (btnMarkAllRead != null) {
            btnMarkAllRead.setEnabled(hasUnread);
            btnMarkAllRead.setAlpha(hasUnread ? 1f : 0.5f);
        }
    }

    private String getEmptyStateTitle() {
        switch (currentFilter) {
            case UNREAD:
                return getString(R.string.no_unread_notifications);
            case ORDERS:
                return getString(R.string.no_order_notifications);
            case PROMOTIONS:
                return getString(R.string.no_promotion_notifications);
            case ALL:
            default:
                return getString(R.string.no_notifications_yet);
        }
    }

    private String getEmptyStateMessage() {
        switch (currentFilter) {
            case UNREAD:
                return getString(R.string.no_unread_notifications);
            case ORDERS:
                return getString(R.string.no_order_notifications);
            case PROMOTIONS:
                return getString(R.string.no_promotion_notifications);
            case ALL:
            default:
                return getString(R.string.no_notifications_body);
        }
    }

    private void clearVisibleNotifications() {
        List<NotificationStore.NotificationGroup> groups = adapter == null ? new ArrayList<>() : adapter.getCurrentList();
        if (groups.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_notifications_yet), Toast.LENGTH_SHORT).show();
            return;
        }

        NotificationStore.dismissGroups(this, groups);
        Toast.makeText(this, getString(R.string.notifications_cleared), Toast.LENGTH_SHORT).show();
        renderCurrentNotifications();
    }

    private void markAllNotificationsRead() {
        NotificationStore.markAllRead(this);
        Toast.makeText(this, getString(R.string.notifications_marked_read), Toast.LENGTH_SHORT).show();
        renderCurrentNotifications();
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

    private int getCartItemCountFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int count = 0;
        try {
            JSONArray array = new JSONArray(prefs.getString("global_cart_json", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) count += obj.optInt("quantity", 0);
            }
        } catch (Exception ignored) {
        }
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

    private void updateNotificationsBadge() {
        int unreadCount = NotificationStore.getUnreadGroupCount(this);
        if (tabNotificationsBadgeTextView != null) {
            if (unreadCount <= 0) {
                tabNotificationsBadgeTextView.setVisibility(View.GONE);
            } else {
                tabNotificationsBadgeTextView.setVisibility(View.VISIBLE);
                tabNotificationsBadgeTextView.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
            }
        }
    }
}