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
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerDashboard extends AppCompatActivity {

    private static final int PREVIEW_RESTAURANT_LIMIT = 3;
    private static final int PREVIEW_ITEMS_PER_RESTAURANT = 2;
    private static final int PREVIEW_TOTAL_ITEMS_LIMIT = 8;
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
    private Button btnPlaceOrder, btnLogout;
    private RadioGroup vehicleRadioGroup;
    private TextView totalPriceTextView;
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
    private String lastSearchQuery = "";
    private Runnable searchRunnable;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private static final long POLLING_INTERVAL_MS = 10000L;
    private SearchRestaurantAdapter searchRestaurantAdapter;
    private SearchMenuItemAdapter searchMenuItemAdapter;

    // Use the centralized URL from Constants
    private static final String API_URL = Constants.BASE_URL + "orders";
    private static final String ALL_RESTAURANTS_ENDPOINT = Constants.BASE_URL + "get_all_restaurants.php";
    private static final String MENU_BY_RESTAURANT_ENDPOINT = Constants.BASE_URL + "get_menus_by_restaurant.php";
    private static final String LEGACY_MENU_ENDPOINT = Constants.BASE_URL + "get_menus.php";
    private static final String SEARCH_ENDPOINT = Constants.BASE_URL + "search.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_dashboard);

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
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
        btnLogout = findViewById(R.id.btnLogout);
        vehicleRadioGroup = findViewById(R.id.vehicleRadioGroup);
        totalPriceTextView = findViewById(R.id.totalPriceTextView);

        restaurantsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        searchRestaurantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchMenuItemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        searchRestaurantsRecyclerView.setNestedScrollingEnabled(false);
        searchMenuItemsRecyclerView.setNestedScrollingEnabled(false);
        requestQueue = Volley.newRequestQueue(this);
        restaurantId = getIntent().getIntExtra("restaurant_id", -1);

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

        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        btnLogout.setOnClickListener(v -> {
            // Clear session/token using Application Context
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
            prefs.edit().clear().apply();

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchRestaurants(true);
        startMenuPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMenuPolling();
        searchHandler.removeCallbacksAndMessages(null);
        requestQueue.cancelAll(SEARCH_REQUEST_TAG);
    }

    private void startMenuPolling() {
        pollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isSearchMode && restaurantId > 0) {
                    fetchMenu(false);
                }
                pollingHandler.postDelayed(this, POLLING_INTERVAL_MS);
            }
        }, POLLING_INTERVAL_MS);
    }

    private void stopMenuPolling() {
        pollingHandler.removeCallbacksAndMessages(null);
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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
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

        searchRunnable = () -> executeSearch(query);
        searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
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
                    if (!query.equals(lastSearchQuery)) {
                        return;
                    }

                    JSONArray restaurantsArray = findArray(response,
                            "restaurants",
                            "restaurant_results",
                            "restaurant",
                            "data.restaurants",
                            "data.restaurant_results");
                    JSONArray menuItemsArray = findArray(response,
                            "menu_items",
                            "menuItems",
                            "menus",
                            "items",
                            "products",
                            "data.menu_items",
                            "data.items",
                            "data.products");

                    applySearchResults(restaurantsArray, menuItemsArray);
                },
                error -> {
                    if (!query.equals(lastSearchQuery)) {
                        return;
                    }

                    loadingProgressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    searchRestaurants.clear();
                    searchMenuItems.clear();
                    searchRestaurantAdapter.notifyDataSetChanged();
                    searchMenuItemAdapter.notifyDataSetChanged();
                    updateSearchSectionsVisibility();
                    searchNoResultsTextView.setVisibility(View.VISIBLE);
                    Toast.makeText(CustomerDashboard.this, "Search failed", Toast.LENGTH_SHORT).show();
                    Log.e("CustomerDashboard", "Search request failed", error);
                }
        );

        searchRequest.setTag(SEARCH_REQUEST_TAG);
        requestQueue.add(searchRequest);
    }

    private JSONArray findArray(JSONObject source, String... keyPaths) {
        if (source == null || keyPaths == null) {
            return new JSONArray();
        }

        for (String keyPath : keyPaths) {
            if (TextUtils.isEmpty(keyPath)) {
                continue;
            }

            String[] keys = keyPath.split("\\.");
            JSONObject current = source;
            for (int i = 0; i < keys.length - 1; i++) {
                current = current.optJSONObject(keys[i]);
                if (current == null) {
                    break;
                }
            }

            if (current == null) {
                continue;
            }

            JSONArray array = current.optJSONArray(keys[keys.length - 1]);
            if (array != null) {
                return array;
            }
        }

        return new JSONArray();
    }

    private void applySearchResults(JSONArray restaurantsArray, JSONArray menuItemsArray) {
        searchRestaurants.clear();
        searchMenuItems.clear();

        for (int i = 0; i < restaurantsArray.length(); i++) {
            JSONObject item = restaurantsArray.optJSONObject(i);
            if (item == null) {
                continue;
            }

            int id = item.optInt("id", item.optInt("restaurant_id", -1));
            if (id <= 0) {
                continue;
            }

            String name = item.optString("name", item.optString("restaurant_name", "Restaurant " + id));
            String imageUrl = item.optString("image_url",
                    item.optString("image",
                            item.optString("image_path",
                                    item.optString("logo", item.optString("restaurant_image", "")))));
            searchRestaurants.add(new SearchRestaurant(id, name, normalizeImageUrl(imageUrl)));
        }

        for (int i = 0; i < menuItemsArray.length(); i++) {
            JSONObject item = menuItemsArray.optJSONObject(i);
            if (item == null) {
                continue;
            }

            int menuId = item.optInt("id", item.optInt("menu_id", -1));
            int itemRestaurantId = item.optInt("restaurant_id", item.optInt("restaurantId", -1));
            String itemName = item.optString("name", item.optString("food_name", item.optString("item_name", "Menu Item")));

            JSONObject nestedRestaurant = item.optJSONObject("restaurant");
            String itemRestaurantName = item.optString("restaurant_name", item.optString("restaurant", ""));
            if (TextUtils.isEmpty(itemRestaurantName) && nestedRestaurant != null) {
                itemRestaurantName = nestedRestaurant.optString("name", nestedRestaurant.optString("restaurant_name", ""));
                if (itemRestaurantId <= 0) {
                    itemRestaurantId = nestedRestaurant.optInt("id", nestedRestaurant.optInt("restaurant_id", -1));
                }
            }

            double price = item.optDouble("price", item.optDouble("menu_price", 0.0));
            String imageUrl = item.optString("image_url", item.optString("image", item.optString("image_path", "")));

            searchMenuItems.add(new SearchMenuItem(
                    menuId,
                    itemName,
                    itemRestaurantId,
                    itemRestaurantName,
                    price,
                    normalizeImageUrl(imageUrl)
            ));
        }

        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        searchRestaurantAdapter.notifyDataSetChanged();
        searchMenuItemAdapter.notifyDataSetChanged();
        updateSearchSectionsVisibility();
    }

    private void updateSearchSectionsVisibility() {
        boolean hasRestaurants = !searchRestaurants.isEmpty();
        boolean hasMenuItems = !searchMenuItems.isEmpty();

        searchRestaurantsSectionTitle.setVisibility(hasRestaurants ? View.VISIBLE : View.GONE);
        searchRestaurantsRecyclerView.setVisibility(hasRestaurants ? View.VISIBLE : View.GONE);
        searchMenuSectionTitle.setVisibility(hasMenuItems ? View.VISIBLE : View.GONE);
        searchMenuItemsRecyclerView.setVisibility(hasMenuItems ? View.VISIBLE : View.GONE);
        searchNoResultsTextView.setVisibility((hasRestaurants || hasMenuItems) ? View.GONE : View.VISIBLE);
    }

    private void enterSearchMode() {
        if (isSearchMode) {
            return;
        }

        isSearchMode = true;
        productsRecyclerView.setVisibility(View.GONE);
        searchResultsScrollView.setVisibility(View.VISIBLE);
        emptyMessageTextView.setVisibility(View.GONE);
    }

    private void exitSearchMode() {
        isSearchMode = false;
        requestQueue.cancelAll(SEARCH_REQUEST_TAG);
        loadingProgressBar.setVisibility(View.GONE);
        searchResultsScrollView.setVisibility(View.GONE);
        productsRecyclerView.setVisibility(View.VISIBLE);
        searchRestaurants.clear();
        searchMenuItems.clear();
        searchRestaurantAdapter.notifyDataSetChanged();
        searchMenuItemAdapter.notifyDataSetChanged();
        searchRestaurantsSectionTitle.setVisibility(View.GONE);
        searchMenuSectionTitle.setVisibility(View.GONE);
        searchNoResultsTextView.setVisibility(View.GONE);

        if (restaurantId > 0) {
            fetchMenu(false);
        } else {
            fetchMixedMenuPreview(false);
        }
    }

    private void navigateToRestaurantFromSearch(int targetRestaurantId) {
        if (targetRestaurantId <= 0) {
            Toast.makeText(this, "Restaurant is unavailable", Toast.LENGTH_SHORT).show();
            return;
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
            restaurantsRecyclerView.smoothScrollToPosition(targetPosition);
            return;
        }

        Intent intent = new Intent(this, CustomerDashboard.class);
        intent.putExtra("restaurant_id", targetRestaurantId);
        startActivity(intent);
    }

    private void navigateToMenuItemFromSearch(SearchMenuItem item) {
        if (item.restaurantId <= 0) {
            Toast.makeText(this, "Restaurant for this item is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingHighlightItemName = item.name;
        navigateToRestaurantFromSearch(item.restaurantId);
    }

    private void highlightPendingMenuItemIfNeeded() {
        if (TextUtils.isEmpty(pendingHighlightItemName)) {
            return;
        }

        int matchPosition = -1;
        for (int i = 0; i < productList.size(); i++) {
            if (pendingHighlightItemName.equalsIgnoreCase(productList.get(i).getName())) {
                matchPosition = i;
                break;
            }
        }

        if (matchPosition < 0) {
            pendingHighlightItemName = null;
            return;
        }

        highlightedProductPosition = matchPosition;
        adapter.notifyItemChanged(matchPosition);
        productsRecyclerView.smoothScrollToPosition(matchPosition);
        final int highlightedPosition = matchPosition;
        pendingHighlightItemName = null;

        productsRecyclerView.postDelayed(() -> {
            if (highlightedProductPosition == highlightedPosition) {
                highlightedProductPosition = -1;
                adapter.notifyItemChanged(highlightedPosition);
            }
        }, 1800L);
    }

    private void fetchRestaurants(boolean showBlockingLoader) {
        if (showBlockingLoader) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            emptyMessageTextView.setVisibility(View.GONE);
        }

        JsonArrayRequest arrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                ALL_RESTAURANTS_ENDPOINT,
                null,
                this::applyRestaurantData,
                error -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    if (restaurantList.isEmpty()) {
                        showEmpty("No partner restaurants available.");
                    }
                    Toast.makeText(CustomerDashboard.this, "Failed to fetch restaurants", Toast.LENGTH_SHORT).show();
                    Log.e("CustomerDashboard", "Restaurants fetch failed", error);
                }
        );

        JsonObjectRequest objectRequest = new JsonObjectRequest(
                Request.Method.GET,
                ALL_RESTAURANTS_ENDPOINT,
                null,
                response -> {
                    Log.d("CustomerDashboard", "Restaurants response: " + response);
                    JSONArray restaurants = response.optJSONArray("restaurants");
                    if (restaurants == null) {
                        restaurants = response.optJSONArray("data");
                    }
                    if (restaurants == null) {
                        restaurants = response.optJSONArray("items");
                    }
                    if (restaurants == null) {
                        restaurants = new JSONArray();
                    }
                    applyRestaurantData(restaurants);
                },
                error -> requestQueue.add(arrayRequest)
        );

        requestQueue.add(objectRequest);
    }

    private void applyRestaurantData(JSONArray restaurants) {
        int previousRestaurantId = restaurantId;
        restaurantList.clear();

        for (int i = 0; i < restaurants.length(); i++) {
            JSONObject restaurant = restaurants.optJSONObject(i);
            if (restaurant == null) {
                continue;
            }

            int id = restaurant.optInt("id", restaurant.optInt("restaurant_id", -1));
            if (id <= 0) {
                continue;
            }

            String name = restaurant.optString("name", restaurant.optString("restaurant_name", "Restaurant " + id));
            restaurantList.add(new Restaurant(id, name));
        }

        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        restaurantAdapter.notifyDataSetChanged();

        if (restaurantList.isEmpty()) {
            restaurantId = -1;
            selectedRestaurantPosition = -1;
            productList.clear();
            adapter.notifyDataSetChanged();
            calculateTotalPrice();
            selectedRestaurantTextView.setText("Mixed picks from partner restaurants");
            showEmpty("No partner restaurants available.");
            return;
        }

        int selectedPosition = -1;
        for (int i = 0; i < restaurantList.size(); i++) {
            if (restaurantList.get(i).getId() == previousRestaurantId) {
                selectedPosition = i;
                break;
            }
        }

        if (selectedPosition >= 0) {
            selectRestaurant(selectedPosition, true);
        } else {
            restaurantId = -1;
            selectedRestaurantPosition = -1;
            restaurantAdapter.notifyDataSetChanged();
            fetchMixedMenuPreview(true);
        }
    }

    private void selectRestaurant(int position, boolean fetchMenuNow) {
        if (position < 0 || position >= restaurantList.size()) {
            return;
        }

        selectedRestaurantPosition = position;
        Restaurant selectedRestaurant = restaurantList.get(position);
        restaurantId = selectedRestaurant.getId();
        selectedRestaurantTextView.setText("Menu: " + selectedRestaurant.getName());
        restaurantAdapter.notifyDataSetChanged();

        if (fetchMenuNow) {
            fetchMenu(true);
        }
    }

    private void clearRestaurantSelection(boolean fetchMenuNow) {
        restaurantId = -1;
        selectedRestaurantPosition = -1;
        selectedRestaurantTextView.setText("Mixed picks from partner restaurants");
        restaurantAdapter.notifyDataSetChanged();

        if (fetchMenuNow) {
            fetchMixedMenuPreview(true);
        }
    }

    private void fetchMixedMenuPreview(boolean showBlockingLoader) {
        if (restaurantList.isEmpty()) {
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            showEmpty("No partner restaurants available.");
            return;
        }

        if (showBlockingLoader) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            emptyMessageTextView.setVisibility(View.GONE);
        }

        selectedRestaurantTextView.setText("Mixed picks from partner restaurants");

        int restaurantCount = Math.min(PREVIEW_RESTAURANT_LIMIT, restaurantList.size());
        List<Product> mixedProducts = new ArrayList<>();
        final int[] pendingRequests = {restaurantCount};

        for (int i = 0; i < restaurantCount; i++) {
            Restaurant restaurant = restaurantList.get(i);
            requestMenuArrayForRestaurant(
                    restaurant.getId(),
                    menus -> {
                        addPreviewProducts(mixedProducts, menus, restaurant.getName());
                        pendingRequests[0]--;
                        if (pendingRequests[0] == 0) {
                            applyMixedPreviewData(mixedProducts);
                        }
                    },
                    error -> {
                        Log.e("CustomerDashboard", "Preview fetch failed for restaurant " + restaurant.getId(), error);
                        pendingRequests[0]--;
                        if (pendingRequests[0] == 0) {
                            applyMixedPreviewData(mixedProducts);
                        }
                    }
            );
        }
    }

    private void applyMixedPreviewData(List<Product> mixedProducts) {
        productList.clear();
        productList.addAll(mixedProducts);

        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        adapter.notifyDataSetChanged();
        calculateTotalPrice();

        if (productList.isEmpty()) {
            showEmpty("No menu available right now.");
        } else {
            emptyMessageTextView.setVisibility(View.GONE);
        }
    }

    private void addPreviewProducts(List<Product> destination, JSONArray menus, String restaurantName) {
        int addedForRestaurant = 0;
        for (int i = 0; i < menus.length(); i++) {
            if (destination.size() >= PREVIEW_TOTAL_ITEMS_LIMIT || addedForRestaurant >= PREVIEW_ITEMS_PER_RESTAURANT) {
                break;
            }

            JSONObject item = menus.optJSONObject(i);
            if (item == null) {
                continue;
            }

            destination.add(parseProductFromJson(item, restaurantName, true));
            addedForRestaurant++;
        }
    }

    private void fetchMenu(boolean showBlockingLoader) {
        if (restaurantId <= 0) {
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            showEmpty("Tap a restaurant to view the full menu.");
            return;
        }

        if (showBlockingLoader) {
            loadingProgressBar.setVisibility(View.VISIBLE);
            emptyMessageTextView.setVisibility(View.GONE);
        }

        Response.ErrorListener errorListener = error -> {
            loadingProgressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
            if (productList.isEmpty()) {
                showEmpty("No menu available right now.");
            }
            String errorMessage = "Failed to fetch menu";
            if (error.networkResponse != null) {
                errorMessage = "Failed to fetch menu (" + error.networkResponse.statusCode + ")";
            }
            Toast.makeText(CustomerDashboard.this, errorMessage, Toast.LENGTH_SHORT).show();
            Log.e("CustomerDashboard", "Menu fetch failed", error);
        };

        requestMenuArrayForRestaurant(restaurantId, this::applyMenuData, errorListener);
    }

    private void requestMenuArrayForRestaurant(int targetRestaurantId, Response.Listener<JSONArray> successListener, Response.ErrorListener errorListener) {
        String menuUrl = MENU_BY_RESTAURANT_ENDPOINT + "?restaurant_id=" + targetRestaurantId;
        String legacyMenuUrl = LEGACY_MENU_ENDPOINT + "?restaurant_id=" + targetRestaurantId;

        JsonArrayRequest legacyArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                legacyMenuUrl,
                null,
                successListener,
                errorListener
        );

        JsonArrayRequest arrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                menuUrl,
                null,
                successListener,
                error -> requestQueue.add(legacyArrayRequest)
        );

        JsonObjectRequest objectRequest = new JsonObjectRequest(
                Request.Method.GET,
                menuUrl,
                null,
                response -> {
                    Log.d("CustomerDashboard", "Menu response: " + response);
                    JSONArray menus = response.optJSONArray("menus");
                    if (menus == null) {
                        menus = response.optJSONArray("data");
                    }
                    if (menus == null) {
                        menus = response.optJSONArray("items");
                    }
                    if (menus == null) {
                        JSONObject restaurant = response.optJSONObject("restaurant");
                        if (restaurant != null) {
                            menus = restaurant.optJSONArray("menus");
                        }
                    }
                    if (menus == null) {
                        menus = new JSONArray();
                    }
                    successListener.onResponse(menus);
                },
                error -> requestQueue.add(arrayRequest)
        );

        requestQueue.add(objectRequest);
    }

    private void applyMenuData(JSONArray menus) {
        productList.clear();
        for (int i = 0; i < menus.length(); i++) {
            JSONObject item = menus.optJSONObject(i);
            if (item == null) {
                continue;
            }

            productList.add(parseProductFromJson(item, null, false));
        }

        loadingProgressBar.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);
        adapter.notifyDataSetChanged();
        calculateTotalPrice();
        highlightPendingMenuItemIfNeeded();

        if (productList.isEmpty()) {
            showEmpty("No menu available for this restaurant.");
        } else {
            emptyMessageTextView.setVisibility(View.GONE);
        }
    }

    private Product parseProductFromJson(JSONObject item, String restaurantName, boolean includeRestaurantLabel) {
        String name = item.optString("name", "Unnamed Item");
        String description = item.optString("description", "");
        double price = item.optDouble("price", 0.0);
        String imageUrl = item.optString("image_url", "");

        if (TextUtils.isEmpty(imageUrl)) {
            imageUrl = item.optString("image", "");
        }
        if (TextUtils.isEmpty(imageUrl)) {
            imageUrl = item.optString("image_path", "");
        }

        imageUrl = normalizeImageUrl(imageUrl);
        boolean isAvailable = parseItemAvailability(item);

        if (includeRestaurantLabel && !TextUtils.isEmpty(restaurantName)) {
            if (TextUtils.isEmpty(description)) {
                description = "From " + restaurantName;
            } else {
                description = "From " + restaurantName + " - " + description;
            }
        }

        return new Product(name, description, price, imageUrl, isAvailable);
    }

    private void showEmpty(String message) {
        emptyMessageTextView.setText(message);
        emptyMessageTextView.setVisibility(View.VISIBLE);
    }

    private String normalizeImageUrl(String rawImageUrl) {
        if (TextUtils.isEmpty(rawImageUrl)) {
            return "";
        }

        String imageUrl = rawImageUrl.trim();
        String serverRoot = "http://" + Constants.IP_ADDRESS;

        if (imageUrl.startsWith("http://localhost") || imageUrl.startsWith("https://localhost")) {
            int slashIndex = imageUrl.indexOf('/', imageUrl.indexOf("//") + 2);
            if (slashIndex != -1) {
                return serverRoot + imageUrl.substring(slashIndex);
            }
            return serverRoot;
        }

        if (imageUrl.startsWith("/")) {
            return serverRoot + imageUrl;
        }

        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
            return serverRoot + "/" + imageUrl;
        }

        return imageUrl;
    }

    private boolean parseItemAvailability(JSONObject item) {
        if (item == null) {
            return true;
        }

        if (item.has("is_available")) {
            return parseFlexibleBoolean(item.opt("is_available"), true);
        }

        if (item.has("available")) {
            return parseFlexibleBoolean(item.opt("available"), true);
        }

        String status = item.optString("status", "").trim().toLowerCase(Locale.ROOT);
        if (!status.isEmpty()) {
            if ("unavailable".equals(status) || "out_of_stock".equals(status) || "inactive".equals(status)) {
                return false;
            }
            if ("available".equals(status) || "active".equals(status)) {
                return true;
            }
        }

        if (item.has("stock")) {
            return item.optInt("stock", 1) > 0;
        }

        return true;
    }

    private boolean parseFlexibleBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }

        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return defaultValue;
        }

        if ("1".equals(text) || "true".equals(text) || "yes".equals(text) || "available".equals(text) || "active".equals(text)) {
            return true;
        }

        if ("0".equals(text) || "false".equals(text) || "no".equals(text) || "unavailable".equals(text) || "inactive".equals(text) || "out_of_stock".equals(text)) {
            return false;
        }

        return defaultValue;
    }

    private double calculateTotal() {
        double total = 0;
        for (Product product : productList) {
            total += product.getPrice() * product.getQuantity();
        }
        return total;
    }

    private void calculateTotalPrice() {
        totalPriceTextView.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", calculateTotal()));
    }

    private void placeOrder() {
        if (restaurantId <= 0) {
            Toast.makeText(this, "Please select a restaurant first", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Product> selectedProducts = adapter.getSelectedProducts();
        if (selectedProducts.isEmpty()) {
            Toast.makeText(this, "Please select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get token from SharedPreferences using Application Context
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String token = prefs.getString("api_token", null);
        Log.d("CustomerDashboard", "Retrieved token for order: " + token);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "You are not logged in. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                conn.setDoInput(true);

                JSONObject jsonPayload = new JSONObject();
                jsonPayload.put("restaurant_id", restaurantId);
                jsonPayload.put("total_amount", calculateTotal());
                jsonPayload.put("delivery_address", "123 Food Street, App City");

                JSONArray itemsArray = new JSONArray();
                for (Product product : selectedProducts) {
                    JSONObject item = new JSONObject();
                    item.put("name", product.getName());
                    item.put("quantity", product.getQuantity());
                    item.put("price", product.getPrice());
                    itemsArray.put(item);
                }
                jsonPayload.put("items", itemsArray);


                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonPayload.toString());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    runOnUiThread(() -> {
                        Toast.makeText(CustomerDashboard.this, "Order placed successfully!", Toast.LENGTH_LONG).show();
                        // Reset quantities
                        for(Product p : productList) p.setQuantity(0);
                        adapter.notifyDataSetChanged();
                        calculateTotalPrice();
                    });
                } else {
                    final String errorResponse = new java.util.Scanner(conn.getErrorStream()).useDelimiter("\\A").next();
                    Log.e("CustomerDashboard", "Error response from server (" + responseCode + "): " + errorResponse);
                    runOnUiThread(() -> Toast.makeText(CustomerDashboard.this, "Failed to place order. Server code: " + responseCode, Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(CustomerDashboard.this, "Error placing order: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }


    // Product data model
    private static class Restaurant {
        int id;
        String name;

        public Restaurant(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    // Product data model
    private static class Product {
        String name;
        String description;
        double price;
        String imageUrl;
        boolean isAvailable;
        int quantity = 0;

        public Product(String name, String description, double price, String imageUrl, boolean isAvailable) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.imageUrl = imageUrl;
            this.isAvailable = isAvailable;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public double getPrice() {
            return price;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    // RecyclerView Adapter
    private class RestaurantAdapter extends RecyclerView.Adapter<RestaurantAdapter.RestaurantViewHolder> {

        private final List<Restaurant> restaurants;

        RestaurantAdapter(List<Restaurant> restaurants) {
            this.restaurants = restaurants;
        }

        @NonNull
        @Override
        public RestaurantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restaurant, parent, false);
            return new RestaurantViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RestaurantViewHolder holder, int position) {
            Restaurant restaurant = restaurants.get(position);
            boolean isSelected = position == selectedRestaurantPosition;

            holder.restaurantNameTextView.setText(restaurant.getName());
            holder.itemView.setAlpha(isSelected ? 1.0f : 0.8f);
            holder.itemView.setBackgroundResource(isSelected ? R.drawable.button_border : R.drawable.view_border);

            holder.itemView.setOnClickListener(v -> {
                if (selectedRestaurantPosition == position) {
                    clearRestaurantSelection(true);
                    return;
                }
                selectRestaurant(position, true);
            });
        }

        @Override
        public int getItemCount() {
            return restaurants.size();
        }

        class RestaurantViewHolder extends RecyclerView.ViewHolder {
            TextView restaurantNameTextView;

            RestaurantViewHolder(@NonNull View itemView) {
                super(itemView);
                restaurantNameTextView = itemView.findViewById(R.id.restaurantNameTextView);
            }
        }
    }

    private static class SearchRestaurant {
        int id;
        String name;
        String imageUrl;

        SearchRestaurant(int id, String name, String imageUrl) {
            this.id = id;
            this.name = name;
            this.imageUrl = imageUrl;
        }
    }

    private static class SearchMenuItem {
        int id;
        String name;
        int restaurantId;
        String restaurantName;
        double price;
        String imageUrl;

        SearchMenuItem(int id, String name, int restaurantId, String restaurantName, double price, String imageUrl) {
            this.id = id;
            this.name = name;
            this.restaurantId = restaurantId;
            this.restaurantName = restaurantName;
            this.price = price;
            this.imageUrl = imageUrl;
        }
    }

    private class SearchRestaurantAdapter extends RecyclerView.Adapter<SearchRestaurantAdapter.ViewHolder> {

        private final List<SearchRestaurant> restaurants;

        SearchRestaurantAdapter(List<SearchRestaurant> restaurants) {
            this.restaurants = restaurants;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_restaurant, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchRestaurant restaurant = restaurants.get(position);
            holder.nameTextView.setText(restaurant.name);

            Glide.with(holder.itemView.getContext())
                    .load(restaurant.imageUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
                searchEditText.setText("");
                searchEditText.clearFocus();
                navigateToRestaurantFromSearch(restaurant.id);
            });
        }

        @Override
        public int getItemCount() {
            return restaurants.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView nameTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.searchRestaurantImageView);
                nameTextView = itemView.findViewById(R.id.searchRestaurantNameTextView);
            }
        }
    }

    private class SearchMenuItemAdapter extends RecyclerView.Adapter<SearchMenuItemAdapter.ViewHolder> {

        private final List<SearchMenuItem> menuItems;

        SearchMenuItemAdapter(List<SearchMenuItem> menuItems) {
            this.menuItems = menuItems;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_menu, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SearchMenuItem item = menuItems.get(position);
            holder.nameTextView.setText(item.name);

            String sourceRestaurant = TextUtils.isEmpty(item.restaurantName)
                    ? "Partner restaurant"
                    : item.restaurantName;
            holder.restaurantTextView.setText(sourceRestaurant);
            holder.priceTextView.setText(String.format(Locale.getDefault(), "₱%.2f", item.price));

            Glide.with(holder.itemView.getContext())
                    .load(item.imageUrl)
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .into(holder.imageView);

            holder.itemView.setOnClickListener(v -> {
                searchEditText.setText("");
                searchEditText.clearFocus();
                navigateToMenuItemFromSearch(item);
            });
        }

        @Override
        public int getItemCount() {
            return menuItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            TextView nameTextView;
            TextView restaurantTextView;
            TextView priceTextView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.searchMenuImageView);
                nameTextView = itemView.findViewById(R.id.searchMenuNameTextView);
                restaurantTextView = itemView.findViewById(R.id.searchMenuRestaurantTextView);
                priceTextView = itemView.findViewById(R.id.searchMenuPriceTextView);
            }
        }
    }

    // RecyclerView Adapter
    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

        private List<Product> products;

        public ProductAdapter(List<Product> products) {
            this.products = products;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.product_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Product product = products.get(position);
            boolean isHighlighted = position == highlightedProductPosition;
            holder.productNameTextView.setText(product.getName());
            holder.productDescriptionTextView.setText(product.getDescription());
            holder.productPriceTextView.setText(String.format(Locale.getDefault(), "₱%.2f", product.getPrice()));
            holder.quantityTextView.setText(String.valueOf(product.getQuantity()));
            boolean unavailable = !product.isAvailable();

            holder.productCardView.setStrokeWidth(isHighlighted ? 2 : 0);
            holder.productCardView.setStrokeColor(isHighlighted ? 0xFFEB5E28 : 0x00000000);

            if (unavailable && product.getQuantity() != 0) {
                product.setQuantity(0);
                holder.quantityTextView.setText("0");
                calculateTotalPrice();
            }

            holder.unavailableTextView.setVisibility(unavailable ? View.VISIBLE : View.GONE);
            holder.plusButton.setEnabled(!unavailable);
            holder.minusButton.setEnabled(!unavailable);
            holder.plusButton.setClickable(!unavailable);
            holder.minusButton.setClickable(!unavailable);
            holder.itemView.setEnabled(!unavailable);
            holder.itemView.setClickable(false);
            holder.itemView.setAlpha(unavailable ? 0.65f : 1.0f);
            holder.plusButton.setAlpha(unavailable ? 0.35f : 1.0f);
            holder.minusButton.setAlpha(unavailable ? 0.35f : 1.0f);

            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .into(holder.productImageView);

            holder.plusButton.setOnClickListener(v -> {
                if (!product.isAvailable()) {
                    return;
                }
                product.setQuantity(product.getQuantity() + 1);
                notifyItemChanged(position);
                calculateTotalPrice();
            });

            holder.minusButton.setOnClickListener(v -> {
                if (!product.isAvailable()) {
                    return;
                }
                if (product.getQuantity() > 0) {
                    product.setQuantity(product.getQuantity() - 1);
                    notifyItemChanged(position);
                    calculateTotalPrice();
                }
            });
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        public List<Product> getSelectedProducts() {
            List<Product> selectedProducts = new ArrayList<>();
            for (Product product : products) {
                if (product.isAvailable() && product.getQuantity() > 0) {
                    selectedProducts.add(product);
                }
            }
            return selectedProducts;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView productCardView;
            ImageView productImageView;
            TextView productNameTextView, productDescriptionTextView, productPriceTextView, quantityTextView, unavailableTextView;
            ImageButton plusButton, minusButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                productCardView = (MaterialCardView) itemView;
                productImageView = itemView.findViewById(R.id.productImageView);
                productNameTextView = itemView.findViewById(R.id.productNameTextView);
                productDescriptionTextView = itemView.findViewById(R.id.productDescriptionTextView);
                productPriceTextView = itemView.findViewById(R.id.productPriceTextView);
                quantityTextView = itemView.findViewById(R.id.quantityTextView);
                unavailableTextView = itemView.findViewById(R.id.unavailableTextView);
                plusButton = itemView.findViewById(R.id.plusButton);
                minusButton = itemView.findViewById(R.id.minusButton);
            }
        }
    }
}