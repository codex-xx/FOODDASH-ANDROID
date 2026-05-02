package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CustomerDashboard extends AppCompatActivity {

    private static final String TAG = "CustomerDashboard";
    private static final long SEARCH_DEBOUNCE_MS = 400L;
    private static final String SEARCH_REQUEST_TAG = "search_requests";

    private RecyclerView restaurantsRecyclerView;
    private RecyclerView productsRecyclerView;
    private RecyclerView searchRestaurantsRecyclerView;
    private RecyclerView searchMenuItemsRecyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView searchResultsScrollView;
    private ProgressBar loadingProgressBar;
    private TextView selectedRestaurantTextView;
    private TextView emptyMessageTextView;
    private TextView searchRestaurantsSectionTitle;
    private TextView searchMenuSectionTitle;
    private TextView searchNoResultsTextView;
    private EditText searchEditText;
    private EditText deliveryAddressEditText;
    private Button btnPlaceOrder;
    private ImageButton btnViewActiveOrders;
    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private RadioGroup vehicleRadioGroup;
    private TextView totalPriceTextView;
    private LinearLayout orderTrackingLayout;
    private TextView orderStatusTimelineTextView;
    private TextView driverLocationTextView;
    private RestaurantAdapter restaurantAdapter;
    private ProductAdapter adapter;
    private List<Product> productList;
    private final List<Restaurant> restaurantList = new ArrayList<>();
    private final List<SearchRestaurant> searchRestaurants = new ArrayList<>();
    private final List<SearchMenuItem> searchMenuItems = new ArrayList<>();
    private RequestQueue requestQueue;
    private int restaurantId = -1;
    private int selectedRestaurantPosition = -1;
    private int highlightedProductPosition = -1;
    private String pendingHighlightItemName;
    private boolean isSearchMode = false;
    private boolean suppressSearchWatcher = false;
    private String lastSearchQuery = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private static final long POLLING_INTERVAL_MS = 10000L;
    private static final long ORDER_POLLING_INTERVAL_MS = 4000L;
    private SearchRestaurantAdapter searchRestaurantAdapter;
    private SearchMenuItemAdapter searchMenuItemAdapter;
    private final Map<String, CartEntry> globalCart = new LinkedHashMap<>();
    private int activeOrderId = -1;
    private String activeOrderStatus = "";
    private JSONObject activeOrderSnapshot;
    
    private final List<String> canonicalStatusFlow = Arrays.asList(
            Constants.STATUS_PENDING,
            Constants.STATUS_ACCEPTED,
            Constants.STATUS_PREPARING,
            Constants.STATUS_READY,
            Constants.STATUS_PICKED_UP,
            Constants.STATUS_ARRIVED_RESTAURANT,
            Constants.STATUS_OUT_FOR_DELIVERY,
            Constants.STATUS_DELIVERED
    );

    private static final String ORDERS_ENDPOINT = Constants.URL_ORDERS;
    private static final String ALL_RESTAURANTS_ENDPOINT = Constants.URL_GET_ALL_RESTAURANTS;
    private static final String MENU_BY_RESTAURANT_ENDPOINT = Constants.URL_GET_MENU_BY_RESTAURANT;
    private static final String LEGACY_MENU_ENDPOINT = Constants.URL_GET_MENU_LEGACY;
    private static final String SEARCH_ENDPOINT = Constants.BASE_URL + "search.php";

    private final Runnable menuPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isSearchMode) {
                if (restaurantId > 0) {
                    fetchMenu(false);
                } else {
                    fetchMixedMenuPreview(false);
                }
            }
            pollingHandler.postDelayed(this, POLLING_INTERVAL_MS);
        }
    };

    private final Runnable orderPollingRunnable = new Runnable() {
        @Override
        public void run() {
            fetchLatestCustomerOrder();
            pollingHandler.postDelayed(this, ORDER_POLLING_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_dashboard);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.CUSTOMER_DASHBOARD,
                AccessControlManager.Action.READ)) {
            return;
        }

        restaurantsRecyclerView = findViewById(R.id.restaurantsRecyclerView);
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchRestaurantsRecyclerView = findViewById(R.id.searchRestaurantsRecyclerView);
        searchMenuItemsRecyclerView = findViewById(R.id.searchMenuItemsRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        searchResultsScrollView = findViewById(R.id.searchResultsScrollView);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        selectedRestaurantTextView = findViewById(R.id.selectedRestaurantTextView);
        emptyMessageTextView = findViewById(R.id.emptyMessageTextView);
        searchRestaurantsSectionTitle = findViewById(R.id.searchRestaurantsSectionTitle);
        searchMenuSectionTitle = findViewById(R.id.searchMenuSectionTitle);
        searchNoResultsTextView = findViewById(R.id.searchNoResultsTextView);
        searchEditText = findViewById(R.id.searchEditText);
        deliveryAddressEditText = findViewById(R.id.deliveryAddressEditText);
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        btnViewActiveOrders = findViewById(R.id.btnViewActiveOrders);
        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        vehicleRadioGroup = findViewById(R.id.vehicleRadioGroup);
        totalPriceTextView = findViewById(R.id.totalPriceTextView);
        orderTrackingLayout = findViewById(R.id.orderTrackingLayout);
        orderStatusTimelineTextView = findViewById(R.id.orderStatusTimelineTextView);
        driverLocationTextView = findViewById(R.id.driverLocationTextView);

        restaurantsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        searchRestaurantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchMenuItemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchRestaurantsRecyclerView.setNestedScrollingEnabled(false);
        searchMenuItemsRecyclerView.setNestedScrollingEnabled(false);
        requestQueue = Volley.newRequestQueue(this);
        restaurantId = getIntent().getIntExtra("restaurant_id", -1);
        loadGlobalCart();

        productList = new ArrayList<>();

        restaurantAdapter = new RestaurantAdapter(restaurantList);
        restaurantsRecyclerView.setAdapter(restaurantAdapter);

        adapter = new ProductAdapter(productList);
        productsRecyclerView.setAdapter(adapter);
        searchRestaurantAdapter = new SearchRestaurantAdapter(searchRestaurants);
        searchRestaurantsRecyclerView.setAdapter(searchRestaurantAdapter);

        searchMenuItemAdapter = new SearchMenuItemAdapter(searchMenuItems);
        searchMenuItemsRecyclerView.setAdapter(searchMenuItemAdapter);

        setupSearchInput();
        setupBottomNavigation();
        setupDeliveryDefaults();
        updateCartButtonState();
        updateCartTabBadge();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            String query = searchEditText.getText().toString().trim();
            if (query.isEmpty()) {
                fetchRestaurants(false);
            } else {
                executeSearch(query);
            }
        });
        showEmpty("Loading restaurants...");
        selectedRestaurantTextView.setText("Mixed picks from partner restaurants");
        updateOrderControlsState();

        if (btnPlaceOrder != null) {
            btnPlaceOrder.setOnClickListener(v -> openCartPage());
        }

        vehicleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> calculateTotalPrice());

        if (btnViewActiveOrders != null) {
            btnViewActiveOrders.setOnClickListener(v -> openOrderTrackingPage());
        }
    }

    private void setupBottomNavigation() {
        highlightBottomTab(tabHomeButton);

        if (tabHomeButton != null) {
            tabHomeButton.setOnClickListener(v -> {
                highlightBottomTab(tabHomeButton);
                if (isSearchMode) {
                    searchEditText.setText("");
                    exitSearchMode();
                }
                productsRecyclerView.smoothScrollToPosition(0);
            });
        }

        if (tabOrdersButton != null) {
            tabOrdersButton.setOnClickListener(v -> {
                highlightBottomTab(tabOrdersButton);
                openOrderTrackingPage();
            });
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> {
                highlightBottomTab(tabCartButton);
                openCartPage();
            });
        }

        if (tabNotificationsButton != null) {
            tabNotificationsButton.setOnClickListener(v -> {
                highlightBottomTab(tabNotificationsButton);
                startActivity(new Intent(this, NotificationActivity.class));
            });
        }

        if (tabProfileButton != null) {
            tabProfileButton.setOnClickListener(v -> {
                highlightBottomTab(tabProfileButton);
                startActivity(new Intent(this, ProfileActivity.class));
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

    private void openOrderTrackingPage() {
        if (activeOrderId > 0
                && !Constants.STATUS_DELIVERED.equals(activeOrderStatus)
                && !Constants.STATUS_CANCELLED.equals(activeOrderStatus)) {
            Intent intent = new Intent(this, ActiveOrderActivity.class);
            JSONObject payload = activeOrderSnapshot;
            if (payload == null) {
                payload = new JSONObject();
                try {
                    payload.put("id", activeOrderId);
                    payload.put("order_id", activeOrderId);
                    payload.put("status", activeOrderStatus);
                    payload.put("delivery_address", deliveryAddressEditText.getText().toString().trim());
                } catch (Exception ignored) {
                    // Keep fallback behavior even if snapshot building fails.
                }
            }
            intent.putExtra("order_json", payload.toString());
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(this, OrderTrackingActivity.class);
        if (activeOrderId > 0) {
            intent.putExtra("order_id", activeOrderId);
        }
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGlobalCart();
        updateCartButtonState();
        updateCartTabBadge();
        if (restaurantId > 0) {
            fetchMenu(false);
        } else {
            fetchMixedMenuPreview(false);
        }
        fetchRestaurants(true);
        startMenuPolling();
        startOrderPolling();
        fetchLatestCustomerOrder();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
        searchHandler.removeCallbacksAndMessages(null);
        requestQueue.cancelAll(SEARCH_REQUEST_TAG);
    }

    private void startMenuPolling() {
        pollingHandler.removeCallbacks(menuPollingRunnable);
        pollingHandler.postDelayed(menuPollingRunnable, POLLING_INTERVAL_MS);
    }

    private void startOrderPolling() {
        pollingHandler.removeCallbacks(orderPollingRunnable);
        pollingHandler.postDelayed(orderPollingRunnable, ORDER_POLLING_INTERVAL_MS);
    }

    private void stopPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
    }

    private void showEmpty(String message) {
        if (emptyMessageTextView != null) {
            emptyMessageTextView.setText(message);
            emptyMessageTextView.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmpty() {
        if (emptyMessageTextView != null) {
            emptyMessageTextView.setVisibility(View.GONE);
        }
    }

    private void setupDeliveryDefaults() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String savedAddress = prefs.getString("delivery_address", "");
        if (!TextUtils.isEmpty(savedAddress)) {
            deliveryAddressEditText.setText(savedAddress);
        }
        orderTrackingLayout.setVisibility(View.GONE);
        deliveryAddressEditText.setVisibility(View.GONE);
        vehicleRadioGroup.setVisibility(View.GONE);
        totalPriceTextView.setVisibility(View.GONE);
    }

    private void setupSearchInput() {
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String query = searchEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    executeSearch(query);
                }
                return true;
            }
            return false;
        });

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearchWatcher) return;
                scheduleSearch(s.toString().trim());
            }
        });
    }

    private void scheduleSearch(String query) {
        searchHandler.removeCallbacksAndMessages(null);
        if (query.isEmpty()) {
            lastSearchQuery = "";
            exitSearchMode();
            return;
        }
        searchHandler.postDelayed(() -> executeSearch(query), SEARCH_DEBOUNCE_MS);
    }

    private void executeSearch(String query) {
        if (query.isEmpty()) {
            exitSearchMode();
            return;
        }

        lastSearchQuery = query;
        enterSearchMode();
        loadingProgressBar.setVisibility(View.VISIBLE);
        searchNoResultsTextView.setVisibility(View.GONE);
        requestQueue.cancelAll(SEARCH_REQUEST_TAG);

        String searchUrl = SEARCH_ENDPOINT + "?query=" + Uri.encode(query);
        JsonObjectRequest searchRequest = new JsonObjectRequest(
                Request.Method.GET,
                searchUrl,
                null,
                response -> {
                    if (!query.equals(lastSearchQuery)) return;
                    applySearchResults(query,
                            findArray(response, "restaurants", "data.restaurants"),
                            findArray(response, "menu_items", "data.menu_items"));
                },
                error -> {
                    if (!query.equals(lastSearchQuery)) return;
                    loadingProgressBar.setVisibility(View.GONE);
                    searchNoResultsTextView.setVisibility(View.VISIBLE);
                }
        );
        searchRequest.setTag(SEARCH_REQUEST_TAG);
        requestQueue.add(searchRequest);
    }

    private JSONArray findArray(JSONObject source, String... keyPaths) {
        for (String keyPath : keyPaths) {
            JSONObject current = source;
            String[] keys = keyPath.split("\\.");
            for (int i = 0; i < keys.length - 1; i++) {
                current = current.optJSONObject(keys[i]);
                if (current == null) break;
            }
            if (current != null) {
                JSONArray array = current.optJSONArray(keys[keys.length - 1]);
                if (array != null) return array;
            }
        }
        return new JSONArray();
    }

    private void applySearchResults(String query, JSONArray restaurantsArray, JSONArray menuItemsArray) {
        searchRestaurants.clear();
        searchMenuItems.clear();

        for (int i = 0; i < restaurantsArray.length(); i++) {
            JSONObject item = restaurantsArray.optJSONObject(i);
            if (item != null) {
                int id = item.optInt("id", item.optInt("restaurant_id", -1));
                String name = item.optString("name", "Restaurant");
                String imageUrl = normalizeImageUrl(item.optString("image_url", ""));
                searchRestaurants.add(new SearchRestaurant(id, name, imageUrl));
            }
        }

        loadingProgressBar.setVisibility(View.GONE);
        searchRestaurantAdapter.notifyDataSetChanged();
        searchMenuItemAdapter.notifyDataSetChanged();
        updateSearchSectionsVisibility();
    }

    private void fetchLocalMenuMatches(String query) {
        if (TextUtils.isEmpty(query) || restaurantList.isEmpty()) {
            return;
        }

        final String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        final List<SearchMenuItem> localMatches = new ArrayList<>();
        final Set<String> dedupe = new HashSet<>();
        final int[] completed = {0};
        final int total = restaurantList.size();

        for (Restaurant restaurant : restaurantList) {
            requestMenuArrayForRestaurant(restaurant.id,
                    response -> {
                        collectLocalMatches(response, restaurant, normalizedQuery, localMatches, dedupe);
                        completed[0]++;
                        if (completed[0] >= total) {
                            applyLocalSearchMatches(query, localMatches);
                        }
                    },
                    error -> {
                        completed[0]++;
                        if (completed[0] >= total) {
                            applyLocalSearchMatches(query, localMatches);
                        }
                    });
        }
    }

    private void collectLocalMatches(JSONArray menuItems,
                                     Restaurant restaurant,
                                     String normalizedQuery,
                                     List<SearchMenuItem> sink,
                                     Set<String> dedupe) {
        for (int i = 0; i < menuItems.length(); i++) {
            JSONObject item = menuItems.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("name", "");
            String description = item.optString("description", "");
            String haystack = (name + " " + description + " " + restaurant.name).toLowerCase(Locale.ROOT);
            if (!haystack.contains(normalizedQuery)) continue;

            int menuId = item.optInt("id", item.optInt("menu_item_id", -1));
            if (menuId <= 0) continue;

            String uniqueKey = restaurant.id + ":" + menuId;
            if (!dedupe.add(uniqueKey)) continue;

            String imageUrl = normalizeImageUrl(item.optString("image_url", item.optString("image", "")));
            double price = item.optDouble("price", 0.0);
            boolean available = isItemAvailable(item);

            sink.add(new SearchMenuItem(menuId, name, restaurant.id, restaurant.name, price, imageUrl, available));
        }
    }

    private void applyLocalSearchMatches(String query, List<SearchMenuItem> localMatches) {
        if (!isSearchMode || !query.equals(lastSearchQuery)) {
            return;
        }
        if (localMatches.isEmpty()) {
            return;
        }

        searchMenuItems.clear();
        searchMenuItems.addAll(localMatches);
        searchMenuItemAdapter.notifyDataSetChanged();
        updateSearchSectionsVisibility();
    }

    private void updateSearchSectionsVisibility() {
        boolean hasRestaurants = !searchRestaurants.isEmpty();
        boolean hasMenuItems = false;
        searchRestaurantsSectionTitle.setVisibility(hasRestaurants ? View.VISIBLE : View.GONE);
        searchRestaurantsRecyclerView.setVisibility(hasRestaurants ? View.VISIBLE : View.GONE);
        searchMenuSectionTitle.setVisibility(hasMenuItems ? View.VISIBLE : View.GONE);
        searchMenuItemsRecyclerView.setVisibility(hasMenuItems ? View.VISIBLE : View.GONE);
        searchNoResultsTextView.setVisibility(hasRestaurants ? View.GONE : View.VISIBLE);
    }

    private void enterSearchMode() {
        isSearchMode = true;
        productsRecyclerView.setVisibility(View.GONE);
        searchResultsScrollView.setVisibility(View.VISIBLE);
        emptyMessageTextView.setVisibility(View.GONE);
    }

    private void exitSearchMode() {
        exitSearchMode(true);
    }

    private void exitSearchMode(boolean reloadContent) {
        isSearchMode = false;
        searchResultsScrollView.setVisibility(View.GONE);
        productsRecyclerView.setVisibility(View.VISIBLE);
        if (!reloadContent) return;
        if (restaurantId > 0) fetchMenu(false);
        else fetchMixedMenuPreview(false);
    }

    private void navigateToRestaurantFromSearch(int targetRestaurantId) {
        if (isSearchMode) {
            requestQueue.cancelAll(SEARCH_REQUEST_TAG);
            lastSearchQuery = "";
            suppressSearchWatcher = true;
            searchEditText.setText("");
            suppressSearchWatcher = false;
            exitSearchMode(false);
        }

        int targetPosition = -1;
        for (int i = 0; i < restaurantList.size(); i++) {
            if (restaurantList.get(i).getId() == targetRestaurantId) {
                targetPosition = i;
                break;
            }
        }
        if (targetPosition >= 0) {
            selectRestaurant(targetPosition, true);
        } else {
            restaurantId = targetRestaurantId;
            fetchMenu(true);
        }
    }

    private void navigateToMenuItemFromSearch(SearchMenuItem item) {
        pendingHighlightItemName = item.name;
        navigateToRestaurantFromSearch(item.restaurantId);
    }

    private void highlightPendingMenuItemIfNeeded() {
        if (TextUtils.isEmpty(pendingHighlightItemName)) return;
        int matchPosition = -1;
        for (int i = 0; i < productList.size(); i++) {
            if (pendingHighlightItemName.equalsIgnoreCase(productList.get(i).getName())) {
                matchPosition = i;
                break;
            }
        }
        if (matchPosition >= 0) {
            highlightedProductPosition = matchPosition;
            adapter.notifyItemChanged(matchPosition);
            productsRecyclerView.smoothScrollToPosition(matchPosition);
            int pos = matchPosition;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                highlightedProductPosition = -1;
                adapter.notifyItemChanged(pos);
            }, 2000);
        }
        pendingHighlightItemName = null;
    }

    private void fetchRestaurants(boolean showBlockingLoader) {
        if (showBlockingLoader) loadingProgressBar.setVisibility(View.VISIBLE);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, ALL_RESTAURANTS_ENDPOINT, null,
                response -> {
                    JSONArray restaurants = response.optJSONArray("restaurants");
                    if (restaurants == null) restaurants = response.optJSONArray("data");
                    applyRestaurantData(restaurants != null ? restaurants : new JSONArray());
                },
                error -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    showEmpty("Failed to load restaurants");
                }
        );
        requestQueue.add(request);
    }

    private void applyRestaurantData(JSONArray restaurants) {
        restaurantList.clear();
        for (int i = 0; i < restaurants.length(); i++) {
            JSONObject res = restaurants.optJSONObject(i);
            if (res != null) {
                int id = res.optInt("id", res.optInt("restaurant_id", -1));
                String name = res.optString("name", "Restaurant");
                restaurantList.add(new Restaurant(id, name));
            }
        }
        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        restaurantAdapter.notifyDataSetChanged();
        
        if (restaurantList.isEmpty()) {
            showEmpty("No restaurants available");
        } else {
            hideEmpty();
        }

        if (restaurantId > 0) {
            for (int i = 0; i < restaurantList.size(); i++) {
                if (restaurantList.get(i).id == restaurantId) {
                    selectedRestaurantPosition = i;
                    break;
                }
            }
        }
        if (restaurantId <= 0) fetchMixedMenuPreview(true);
        else fetchMenu(true);
    }

    private void selectRestaurant(int position, boolean fetchMenuNow) {
        selectedRestaurantPosition = position;
        restaurantId = restaurantList.get(position).getId();
        selectedRestaurantTextView.setText("Menu: " + restaurantList.get(position).getName());
        restaurantAdapter.notifyDataSetChanged();
        if (fetchMenuNow) fetchMenu(true);
    }

    private void clearRestaurantSelection(boolean fetchMenuNow) {
        restaurantId = -1;
        selectedRestaurantPosition = -1;
        selectedRestaurantTextView.setText("Mixed picks from partner restaurants");
        restaurantAdapter.notifyDataSetChanged();
        if (fetchMenuNow) fetchMixedMenuPreview(true);
    }

    private boolean isHomepageMode() {
        return restaurantId <= 0;
    }

    private void updateOrderControlsState() {
        if (btnPlaceOrder != null) {
            btnPlaceOrder.setVisibility(View.GONE);
        }
        updateCartButtonState();
    }

    private void openCartPage() {
        List<CartEntry> items = getGlobalCartItems();
        if (items.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray array = new JSONArray();
        for (CartEntry e : items) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("menu_item_id", e.id);
                obj.put("name", e.name);
                obj.put("quantity", e.quantity);
                obj.put("price", e.price);
                obj.put("restaurant_id", e.restaurantId);
                obj.put("restaurant_name", e.restaurantName);
                array.put(obj);
            } catch (Exception ignored) {}
        }
        Intent intent = new Intent(this, CartActivity.class);
        intent.putExtra("cart_items_json", array.toString());
        startActivity(intent);
    }

    private List<CartEntry> getGlobalCartItems() {
        return new ArrayList<>(globalCart.values());
    }

    private void updateCartButtonState() {
        if (btnPlaceOrder != null) {
            btnPlaceOrder.setText("Cart (" + getGlobalCartItemCount() + ")");
        }
        updateCartTabBadge();
    }

    private void updateCartTabBadge() {
        if (tabCartBadgeTextView == null) return;
        int count = getGlobalCartItemCount();
        if (count <= 0) {
            tabCartBadgeTextView.setVisibility(View.GONE);
            return;
        }
        tabCartBadgeTextView.setVisibility(View.VISIBLE);
        tabCartBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private int getGlobalCartItemCount() {
        int c = 0;
        for (CartEntry e : globalCart.values()) c += e.quantity;
        return c;
    }

    private void syncProductWithGlobalCart(Product product) {
        String key = "res:" + product.restaurantId + ":id:" + product.getId();
        if (product.quantity <= 0) globalCart.remove(key);
        else {
            globalCart.put(key, new CartEntry(product.restaurantId, product.restaurantName, product.id, product.name, product.price, product.quantity));
        }
        saveGlobalCart();
        updateCartButtonState();
    }

    private void loadGlobalCart() {
        globalCart.clear();
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString("global_cart_json", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) {
                    CartEntry e = new CartEntry(obj.optInt("restaurant_id"), obj.optString("restaurant_name"), 
                                              obj.optInt("menu_item_id"), obj.optString("name"), 
                                              obj.optDouble("price"), obj.optInt("quantity"));
                    globalCart.put("res:" + e.restaurantId + ":id:" + e.id, e);
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveGlobalCart() {
        JSONArray array = new JSONArray();
        for (CartEntry e : globalCart.values()) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("restaurant_id", e.restaurantId);
                obj.put("restaurant_name", e.restaurantName);
                obj.put("menu_item_id", e.id);
                obj.put("name", e.name);
                obj.put("price", e.price);
                obj.put("quantity", e.quantity);
                array.put(obj);
            } catch (Exception ignored) {}
        }
        getSharedPreferences("fooddash_prefs", MODE_PRIVATE).edit().putString("global_cart_json", array.toString()).apply();
    }

    private void fetchMixedMenuPreview(boolean showBlockingLoader) {
        if (showBlockingLoader) loadingProgressBar.setVisibility(View.VISIBLE);
        
        if (!restaurantList.isEmpty()) {
            fetchMenuForPreview(restaurantList.get(0).id, restaurantList.get(0).name);
        } else {
            loadingProgressBar.setVisibility(View.GONE);
        }
    }

    private void fetchMenuForPreview(int resId, String resName) {
        requestMenuArrayForRestaurant(resId, response -> {
            productList.clear();
            for (int i = 0; i < Math.min(response.length(), 6); i++) {
                JSONObject obj = response.optJSONObject(i);
                if (obj != null) {
                    Product p = parseProductFromJson(obj, resId, resName, true);
                    CartEntry ce = globalCart.get("res:" + resId + ":id:" + p.id);
                    if (ce != null) p.quantity = ce.quantity;
                    productList.add(p);
                }
            }
            loadingProgressBar.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            if (productList.isEmpty()) showEmpty("No featured items available");
            else hideEmpty();
        }, error -> {
            loadingProgressBar.setVisibility(View.GONE);
            showEmpty("Failed to load featured items");
        });
    }

    private void fetchMenu(boolean showBlockingLoader) {
        if (showBlockingLoader) loadingProgressBar.setVisibility(View.VISIBLE);
        final int currentResId = restaurantId;
        final String currentResName = selectedRestaurantPosition >= 0 ? restaurantList.get(selectedRestaurantPosition).name : "";
        
        requestMenuArrayForRestaurant(currentResId, response -> {
            productList.clear();
            for (int i = 0; i < response.length(); i++) {
                JSONObject obj = response.optJSONObject(i);
                if (obj != null) {
                    Product p = parseProductFromJson(obj, currentResId, currentResName, false);
                    CartEntry ce = globalCart.get("res:" + currentResId + ":id:" + p.id);
                    if (ce != null) p.quantity = ce.quantity;
                    productList.add(p);
                }
            }
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            adapter.notifyDataSetChanged();
            highlightPendingMenuItemIfNeeded();

            if (productList.isEmpty()) showEmpty("No menu items available for this restaurant");
            else hideEmpty();
        }, error -> {
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            showEmpty("Failed to load menu");
        });
    }

    private void requestMenuArrayForRestaurant(int resId, Response.Listener<JSONArray> success, Response.ErrorListener error) {
        String url = MENU_BY_RESTAURANT_ENDPOINT + "?restaurant_id=" + resId;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    JSONArray menus = response.optJSONArray("menus");
                    if (menus == null) menus = response.optJSONArray("data");
                    success.onResponse(menus != null ? menus : new JSONArray());
                },
                e -> {
                    JsonArrayRequest legacy = new JsonArrayRequest(Request.Method.GET, LEGACY_MENU_ENDPOINT + "?restaurant_id=" + resId, null, success, error);
                    requestQueue.add(legacy);
                }
        );
        requestQueue.add(req);
    }

    private void fetchLatestCustomerOrder() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        if (userId <= 0) return;

        String url = Constants.URL_ORDERS + "/" + userId;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    JSONArray orders = extractOrdersFromResponse(response);
                    applyOrderTracking(findActiveOrderInList(orders));
                },
                error -> fetchLatestCustomerOrderLegacy(userId)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(req);
    }

    private void fetchLatestCustomerOrderLegacy(int userId) {
        String url = Constants.URL_GET_ORDERS_LEGACY + "?user_id=" + userId;
        JsonObjectRequest legacy = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    JSONArray orders = extractOrdersFromResponse(response);
                    applyOrderTracking(findActiveOrderInList(orders));
                }, e -> {}) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(legacy);
    }

    private JSONArray extractOrdersFromResponse(JSONObject response) {
        if (response == null) return new JSONArray();
        JSONArray orders = response.optJSONArray("orders");
        if (orders == null) orders = response.optJSONArray("data");
        if (orders == null) {
             JSONObject data = response.optJSONObject("data");
             if (data != null) orders = data.optJSONArray("orders");
        }
        if (orders == null && response.has("items")) {
            orders = new JSONArray();
            orders.put(response);
        }
        return orders != null ? orders : new JSONArray();
    }

    private JSONObject findActiveOrderInList(JSONArray orders) {
        if (orders == null || orders.length() == 0) return null;
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null) {
                String status = normalizeStatus(order.optString("status", ""));
                if (!status.equals(Constants.STATUS_DELIVERED) && !status.equals(Constants.STATUS_CANCELLED)) {
                    return order;
                }
            }
        }
        return orders.optJSONObject(0);
    }

    private void applyOrderTracking(JSONObject order) {
        if (order == null) {
            activeOrderId = -1;
            activeOrderStatus = "";
            activeOrderSnapshot = null;
            orderTrackingLayout.setVisibility(View.GONE);
            if (btnViewActiveOrders != null) btnViewActiveOrders.setVisibility(View.GONE);
            return;
        }
        activeOrderId = order.optInt("id", order.optInt("order_id", -1));
        activeOrderStatus = normalizeStatus(order.optString("status", "pending"));
        activeOrderSnapshot = order;
        if (activeOrderId > 0) {
            getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("last_active_order_id", activeOrderId)
                    .putString("last_active_order_json", order.toString())
                    .apply();
        }
        
        if (btnViewActiveOrders != null) btnViewActiveOrders.setVisibility(View.VISIBLE);
        renderOrderTimeline(activeOrderStatus, order.optString("driver_location", ""));
    }

    private void renderOrderTimeline(String status, String location) {
        orderTrackingLayout.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        int currentIdx = canonicalStatusFlow.indexOf(status);
        
        for (int i = 0; i < canonicalStatusFlow.size(); i++) {
            String stepStatus = canonicalStatusFlow.get(i);
            String prefix;
            if (currentIdx > i) {
                prefix = "✓ "; 
            } else if (currentIdx == i) {
                prefix = "▶ "; 
            } else {
                prefix = "○ "; 
            }
            sb.append(prefix).append(getFriendlyStatus(stepStatus));
            if (i < canonicalStatusFlow.size() - 1) sb.append("\n");
        }
        orderStatusTimelineTextView.setText(sb.toString());
        
        String friendlyStatus = getFriendlyStatus(status);
        String subText = "Order #" + activeOrderId + ": " + friendlyStatus;
        
        if (!location.isEmpty() && !location.equals("null") && !location.equals("Waiting")) {
            subText += "\nDriver Update: " + location;
        } else {
            if (status.equals(Constants.STATUS_PICKED_UP)) {
                subText += "\nDriver is picking up your order";
            } else if (status.equals(Constants.STATUS_ARRIVED_RESTAURANT)) {
                subText += "\nDriver has arrived at the restaurant";
            } else if (status.equals(Constants.STATUS_OUT_FOR_DELIVERY)) {
                subText += "\nDriver is on the way to you";
            } else if (status.equals(Constants.STATUS_DELIVERED)) {
                subText = "Order #" + activeOrderId + " Delivered! Enjoy your meal.";
            } else if (status.equals(Constants.STATUS_CANCELLED)) {
                subText = "Order #" + activeOrderId + " was Cancelled.";
            } else {
                subText += "\nWaiting for update...";
            }
        }
        
        driverLocationTextView.setText(subText);
    }

    private String getFriendlyStatus(String status) {
        switch (status) {
            case Constants.STATUS_PENDING: return "Order Placed";
            case Constants.STATUS_ACCEPTED: return "Confirmed";
            case Constants.STATUS_PREPARING: return "Preparing Food";
            case Constants.STATUS_READY: return "Food Ready";
            case Constants.STATUS_PICKED_UP: return "Order Picked Up";
            case Constants.STATUS_ARRIVED_RESTAURANT: return "Driver at Restaurant";
            case Constants.STATUS_OUT_FOR_DELIVERY: return "Out for Delivery";
            case Constants.STATUS_DELIVERED: return "Delivered";
            case Constants.STATUS_CANCELLED: return "Cancelled";
            default: return status.substring(0, 1).toUpperCase() + status.substring(1).replace("_", " ");
        }
    }

    private String normalizeStatus(String raw) {
        if (raw == null) return Constants.STATUS_PENDING;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        
        if (n.equals("confirmed")) return Constants.STATUS_ACCEPTED;
        if (n.equals("ready_for_pickup")) return Constants.STATUS_READY;
        if (n.equals("completed")) return Constants.STATUS_DELIVERED;
        if (n.equals("on_the_way")) return Constants.STATUS_OUT_FOR_DELIVERY;

        if (n.contains("accepted")) return Constants.STATUS_ACCEPTED;
        if (n.contains("prepar")) return Constants.STATUS_PREPARING;
        if (n.contains("ready")) return Constants.STATUS_READY;
        if (n.contains("arrived")) return Constants.STATUS_ARRIVED_RESTAURANT;
        if (n.contains("picked")) return Constants.STATUS_PICKED_UP;
        if (n.contains("way") || n.contains("transit") || n.contains("delivery")) return Constants.STATUS_OUT_FOR_DELIVERY;
        if (n.contains("deliver")) return Constants.STATUS_DELIVERED;
        if (n.contains("cancel")) return Constants.STATUS_CANCELLED;

        return Constants.STATUS_PENDING;
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!token.isEmpty()) headers.put("Authorization", "Bearer " + token);
        return headers;
    }

    private boolean isItemAvailable(JSONObject item) {
        boolean available = true;
        
        // Handle all common availability keys and data types (String, Int, Boolean)
        if (item.has("is_available")) {
            String val = item.optString("is_available", "1");
            available = val.equals("1") || val.equalsIgnoreCase("true");
        } else if (item.has("available")) {
            String val = item.optString("available", "1");
            available = val.equals("1") || val.equalsIgnoreCase("true");
        } else if (item.has("status")) {
            String val = item.optString("status", "").toLowerCase();
            available = val.equals("available") || val.equals("active") || val.equals("1");
        } else if (item.has("stock")) {
            available = item.optInt("stock", 1) > 0;
        }
        
        return available;
    }

    private Product parseProductFromJson(JSONObject item, int resId, String resName, boolean includeRes) {
        int id = item.optInt("id", -1);
        String name = item.optString("name", "Item");
        String desc = item.optString("description", "");
        double price = item.optDouble("price", 0.0);
        String img = normalizeImageUrl(item.optString("image_url", item.optString("image", "")));
        
        boolean available = isItemAvailable(item);
        
        if (includeRes && !TextUtils.isEmpty(resName)) desc = "From " + resName + " - " + desc;
        return new Product(id, name, desc, price, img, available, resId, resName);
    }

    private String normalizeImageUrl(String url) {
        if (url.isEmpty()) return "";
        if (url.startsWith("http")) return url;
        return "http://" + Constants.IP_ADDRESS + "/" + url;
    }

    private double calculateTotal() {
        double t = 0;
        for (Product p : productList) t += p.price * p.quantity;
        return t;
    }

    private void calculateTotalPrice() {
        totalPriceTextView.setText("Total: ₱" + String.format("%.2f", calculateTotal()));
    }

    private double getSelectedDeliveryFee() {
        int id = vehicleRadioGroup.getCheckedRadioButtonId();
        if (id == R.id.radioTricycle) return Constants.FEE_TRICYCLE;
        if (id == R.id.radioCab) return Constants.FEE_CAB;
        return Constants.FEE_MOTORCYCLE;
    }

    private static class Restaurant {
        int id;
        String name;
        Restaurant(int id, String name) { this.id = id; this.name = name; }
        int getId() { return id; }
        String getName() { return name; }
    }

    private static class Product {
        int id;
        String name;
        String description;
        double price;
        String imageUrl;
        boolean isAvailable;
        int quantity = 0;
        int restaurantId;
        String restaurantName;
        Product(int id, String name, String description, double price, String imageUrl, boolean available, int resId, String resName) {
            this.id = id; this.name = name; this.description = description; this.price = price; this.imageUrl = imageUrl; this.isAvailable = available;
            this.restaurantId = resId; this.restaurantName = resName;
        }
        int getId() { return id; }
        String getName() { return name; }
        String getDescription() { return description; }
        double getPrice() { return price; }
        String getImageUrl() { return imageUrl; }
        boolean isAvailable() { return isAvailable; }
    }

    private static class CartEntry {
        int restaurantId, id, quantity;
        String restaurantName, name;
        double price;
        CartEntry(int resId, String resName, int id, String name, double price, int qty) {
            this.restaurantId = resId; this.restaurantName = resName; this.id = id; this.name = name; this.price = price; this.quantity = qty;
        }
    }

    private class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.ViewHolder> {
        List<Restaurant> list;
        RestaurantAdapter(List<Restaurant> list) { this.list = list; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_restaurant, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            Restaurant r = list.get(p);
            h.name.setText(r.name);
            boolean sel = p == selectedRestaurantPosition;
            h.itemView.setAlpha(sel ? 1.0f : 0.7f);
            h.itemView.setOnClickListener(v -> selectRestaurant(p, true));
        }
        @Override public int getItemCount() { return list.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            ViewHolder(View v) { super(v); name = v.findViewById(R.id.restaurantNameTextView); }
        }
    }

    private static class SearchRestaurant {
        int id; String name, imageUrl;
        SearchRestaurant(int id, String name, String img) { this.id = id; this.name = name; this.imageUrl = img; }
    }

    private static class SearchMenuItem {
        int id, restaurantId; String name, restaurantName, imageUrl; double price; boolean isAvailable;
        SearchMenuItem(int id, String name, int resId, String resName, double price, String img, boolean available) {
            this.id = id; this.name = name; this.restaurantId = resId; this.restaurantName = resName; this.price = price; this.imageUrl = img; this.isAvailable = available;
        }
    }

    private class SearchRestaurantAdapter extends RecyclerView.Adapter<SearchRestaurantAdapter.ViewHolder> {
        List<SearchRestaurant> list;
        SearchRestaurantAdapter(List<SearchRestaurant> list) { this.list = list; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_search_restaurant, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            SearchRestaurant r = list.get(p);
            h.name.setText(r.name);
            Glide.with(h.itemView).load(r.imageUrl).into(h.img);
            h.itemView.setOnClickListener(v -> navigateToRestaurantFromSearch(r.id));
        }
        @Override public int getItemCount() { return list.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img; TextView name;
            ViewHolder(View v) { super(v); img = v.findViewById(R.id.searchRestaurantImageView); name = v.findViewById(R.id.searchRestaurantNameTextView); }
        }
    }

    private class SearchMenuItemAdapter extends RecyclerView.Adapter<SearchMenuItemAdapter.ViewHolder> {
        List<SearchMenuItem> list;
        SearchMenuItemAdapter(List<SearchMenuItem> list) { this.list = list; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_search_menu, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            SearchMenuItem i = list.get(p);
            h.name.setText(i.name); h.res.setText(i.restaurantName); h.price.setText("₱" + String.format("%.2f", i.price));
            Glide.with(h.itemView).load(i.imageUrl).into(h.img);
            
            if (i.isAvailable) {
                h.itemView.setAlpha(1.0f);
                h.unavailableText.setVisibility(View.GONE);
                h.itemView.setOnClickListener(v -> navigateToMenuItemFromSearch(i));
            } else {
                h.itemView.setAlpha(0.6f);
                h.unavailableText.setVisibility(View.VISIBLE);
                h.itemView.setOnClickListener(null);
            }
        }
        @Override public int getItemCount() { return list.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img; TextView name, res, price, unavailableText;
            ViewHolder(View v) { 
                super(v); 
                img = v.findViewById(R.id.searchMenuImageView); 
                name = v.findViewById(R.id.searchMenuNameTextView); 
                res = v.findViewById(R.id.searchMenuRestaurantTextView); 
                price = v.findViewById(R.id.searchMenuPriceTextView); 
                unavailableText = v.findViewById(R.id.searchMenuUnavailableTextView);
            }
        }
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
        List<Product> list;
        ProductAdapter(List<Product> list) { this.list = list; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.product_item, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int p) {
            Product pr = list.get(p);
            h.name.setText(pr.name); h.desc.setText(pr.description); h.price.setText("₱" + String.format("%.2f", pr.price)); h.qty.setText(String.valueOf(pr.quantity));
            Glide.with(h.itemView).load(pr.imageUrl).into(h.img);
            
            if (pr.isAvailable) {
                h.itemView.setAlpha(1.0f);
                h.unavailableText.setVisibility(View.GONE);
                h.controlsLayout.setVisibility(View.VISIBLE);
                
                h.plus.setOnClickListener(v -> { 
                    pr.quantity++; 
                    h.qty.setText(String.valueOf(pr.quantity)); 
                    syncProductWithGlobalCart(pr); 
                });
                h.minus.setOnClickListener(v -> { 
                    if (pr.quantity > 0) { 
                        pr.quantity--; 
                        h.qty.setText(String.valueOf(pr.quantity)); 
                        syncProductWithGlobalCart(pr); 
                    } 
                });
            } else {
                h.itemView.setAlpha(0.6f);
                h.unavailableText.setVisibility(View.VISIBLE);
                h.controlsLayout.setVisibility(View.GONE);
                h.plus.setOnClickListener(null);
                h.minus.setOnClickListener(null);
                
                if (pr.quantity > 0) {
                    pr.quantity = 0;
                    h.qty.setText("0");
                    syncProductWithGlobalCart(pr);
                }
            }
        }
        @Override public int getItemCount() { return list.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView img; TextView name, desc, price, qty, unavailableText; ImageButton plus, minus; View controlsLayout;
            ViewHolder(View v) { 
                super(v); 
                img = v.findViewById(R.id.productImageView); 
                name = v.findViewById(R.id.productNameTextView); 
                desc = v.findViewById(R.id.productDescriptionTextView); 
                price = v.findViewById(R.id.productPriceTextView); 
                qty = v.findViewById(R.id.quantityTextView); 
                plus = v.findViewById(R.id.plusButton); 
                minus = v.findViewById(R.id.minusButton);
                unavailableText = v.findViewById(R.id.unavailableTextView);
                controlsLayout = v.findViewById(R.id.quantityControlsLayout);
            }
        }
    }
}
