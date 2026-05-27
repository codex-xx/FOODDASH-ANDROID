package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckoutActivity extends AppCompatActivity {

    private static final String TAG = "CheckoutActivity";
    private ApiService apiService;
    private double grandSubtotal = 0.0;
    private String cartItemsJson = "[]";
    private final List<OrderGroup> orderGroups = new ArrayList<>();
    private final List<Integer> placedOrderIds = new ArrayList<>();
    private int successfulGroupCount = 0;
    private boolean isCheckoutInProgress = false;

    private EditText checkoutAddressEditText;
    private RadioGroup checkoutVehicleRadioGroup;
    private RadioGroup checkoutPaymentRadioGroup;
    private TextView checkoutSummaryTextView;
    private TextView checkoutProcessingMessageTextView;
    private ProgressBar checkoutPaymentProgress;
    private Button btnConfirmPlaceOrder;

    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;
    private TextView tabNotificationsBadgeTextView;

    private String pendingPaymentMethod = "cod";
    private String pendingAddress = "";
    private String pendingDeliveryType = Constants.DELIVERY_MOTORCYCLE;
    private double pendingDeliveryFee = 0.0;
    private int pendingUserId = -1;
    private String pendingAuthToken = "";

    private final ActivityResultLauncher<Intent> paymentResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    onPaymentSuccess();
                    return;
                }

                setCheckoutUiState(false, "");
                if (!placedOrderIds.isEmpty() && !"gcash".equalsIgnoreCase(pendingPaymentMethod)) {
                    showPaymentRetryDialog();
                } else if (!placedOrderIds.isEmpty() && "gcash".equalsIgnoreCase(pendingPaymentMethod)) {
                    // Just show a simple toast for GCash cancellation instead of a persistent dialog
                    Toast.makeText(this, "GCash payment was cancelled. You can try again from the tracking screen later.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Payment failed. Please try again.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.CHECKOUT,
                AccessControlManager.Action.READ)) {
            return;
        }

        apiService = RetrofitClient.getApiService();

        checkoutAddressEditText = findViewById(R.id.checkoutAddressEditText);
        checkoutVehicleRadioGroup = findViewById(R.id.checkoutVehicleRadioGroup);
        checkoutPaymentRadioGroup = findViewById(R.id.checkoutPaymentRadioGroup);
        checkoutSummaryTextView = findViewById(R.id.checkoutSummaryTextView);
        checkoutProcessingMessageTextView = findViewById(R.id.checkoutProcessingMessageTextView);
        checkoutPaymentProgress = findViewById(R.id.checkoutPaymentProgress);
        btnConfirmPlaceOrder = findViewById(R.id.btnConfirmPlaceOrder);

        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);
        tabNotificationsBadgeTextView = findViewById(R.id.tabNotificationsBadgeTextView);

        setupBottomNavigation();
        updateCartTabBadge();
        updateNotificationsTabCount();

        grandSubtotal = getIntent().getDoubleExtra("subtotal", 0.0);
        cartItemsJson = getIntent().getStringExtra("cart_items_json");
        if (cartItemsJson == null || cartItemsJson.trim().isEmpty()) {
            cartItemsJson = "[]";
        }

        buildOrderGroups();

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String savedAddress = prefs.getString("delivery_address", "");
        if (!TextUtils.isEmpty(savedAddress)) {
            checkoutAddressEditText.setText(savedAddress);
        }

        updateSummary();
        checkoutVehicleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> updateSummary());

        btnConfirmPlaceOrder.setOnClickListener(v -> {
            if (isCheckoutInProgress) return;
            placeOrders();
        });
    }

    private void buildOrderGroups() {
        orderGroups.clear();
        Map<String, OrderGroup> groups = new LinkedHashMap<>();
        try {
            JSONArray array = new JSONArray(cartItemsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                int resId = item.optInt("restaurant_id", -1);
                String resName = item.optString("restaurant_name", "Store #" + resId);
                
                String key = "id:" + resId + ":" + resName.toLowerCase();
                OrderGroup group = groups.get(key);
                if (group == null) {
                    group = new OrderGroup(resId, resName);
                    groups.put(key, group);
                }
                group.items.put(item);
                group.subtotal += item.optInt("quantity", 1) * item.optDouble("price", 0.0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error grouping items", e);
        }
        orderGroups.addAll(groups.values());
    }

    private void updateSummary() {
        if (orderGroups.isEmpty()) {
            checkoutSummaryTextView.setText("No items selected.");
            return;
        }

        double fee = getSelectedFee();
        StringBuilder builder = new StringBuilder("Order Summary:\n\n");
        double grandTotal = 0;
        
        for (OrderGroup group : orderGroups) {
            double total = group.subtotal + fee;
            grandTotal += total;
            builder.append(String.format(Locale.getDefault(), 
                "%s\nSubtotal: P%.2f\nDelivery Fee: P%.2f\nTotal: P%.2f\n\n",
                group.restaurantName, group.subtotal, fee, total));
        }

        builder.append(String.format(Locale.getDefault(), "Grand Total: P%.2f", grandTotal));
        if (orderGroups.size() > 1) {
            builder.append("\n(Total of ").append(orderGroups.size()).append(" separate orders)");
        }
        checkoutSummaryTextView.setText(builder.toString());
    }

    private double getSelectedFee() {
        int checkedId = checkoutVehicleRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.checkoutRadioTricycle) return Constants.FEE_TRICYCLE;
        if (checkedId == R.id.checkoutRadioCab) return Constants.FEE_CAB;
        return Constants.FEE_MOTORCYCLE;
    }

    private String getSelectedDeliveryType() {
        int checkedId = checkoutVehicleRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.checkoutRadioTricycle) return Constants.DELIVERY_TRICYCLE;
        if (checkedId == R.id.checkoutRadioCab) return Constants.DELIVERY_CAB;
        return Constants.DELIVERY_MOTORCYCLE;
    }

    private String getSelectedPaymentMethod() {
        int checkedId = checkoutPaymentRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.checkoutRadioCod) return "cod";
        return "gcash";
    }

    private void placeOrders() {
        String address = checkoutAddressEditText.getText().toString().trim();
        if (address.isEmpty()) {
            Toast.makeText(this, "Please enter delivery address", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        prefs.edit().putString("delivery_address", address).apply();

        int userId = prefs.getInt("user_id", -1);
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);

        if (userId <= 0 || TextUtils.isEmpty(token)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingPaymentMethod = getSelectedPaymentMethod();
        pendingAddress = address;
        pendingDeliveryType = getSelectedDeliveryType();
        pendingDeliveryFee = getSelectedFee();
        pendingUserId = userId;
        pendingAuthToken = token;

        prefs.edit().putString("last_payment_method", pendingPaymentMethod).apply();

        successfulGroupCount = 0;
        placedOrderIds.clear();
        setCheckoutUiState(true, "Processing payment...");
        processGroup(0, pendingUserId, pendingAuthToken, pendingAddress, pendingDeliveryType, pendingDeliveryFee, pendingPaymentMethod);
    }

    private void processGroup(int index, int userId, String token, String address, String type, double fee, String paymentMethod) {
        if (index >= orderGroups.size()) {
            finalizeCheckout();
            return;
        }

        OrderGroup group = orderGroups.get(index);
        double total = group.subtotal + fee;

        JSONObject payload = new JSONObject();
        try {
            payload.put("user_id", userId);
            payload.put("restaurant_id", group.restaurantId);
            payload.put("address", address);
            payload.put("delivery_address", address);
            payload.put("delivery_type", type);
            payload.put("delivery_fee", fee);
            payload.put("subtotal", group.subtotal);
            payload.put("total_amount", total);
            payload.put("status", Constants.STATUS_PENDING);
            payload.put("payment_method", paymentMethod);
            payload.put("items", group.items);
            payload.put("api_token", token);
        } catch (Exception e) {
            processGroup(index + 1, userId, token, address, type, fee, paymentMethod);
            return;
        }

        // Modern orders API
        Map<String, Object> payloadMap = new HashMap<>();
        try {
            payloadMap.put("user_id", userId);
            payloadMap.put("restaurant_id", group.restaurantId);
            payloadMap.put("address", address);
            payloadMap.put("delivery_address", address);
            payloadMap.put("delivery_type", type);
            payloadMap.put("delivery_fee", fee);
            payloadMap.put("subtotal", group.subtotal);
            payloadMap.put("total_amount", total);
            payloadMap.put("total", total);
            payloadMap.put("status", Constants.STATUS_PENDING);
            payloadMap.put("payment_method", paymentMethod);
            
            // Convert JSONArray to List<Map> for Retrofit/Gson if needed, 
            // but ApiService.placeOrder(Map<String, Object>) should handle this if configured properly.
            // For safety with a simple Map<String, Object>, we'll pass the string or list.
            payloadMap.put("items", group.items.toString()); 
            payloadMap.put("api_token", token);
        } catch (Exception e) {
            processGroup(index + 1, userId, token, address, type, fee, paymentMethod);
            return;
        }

        apiService.placeOrder(payloadMap).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    JSONObject jsonResponse = new JSONObject(body);
                    
                    if (response.isSuccessful() || jsonResponse.optBoolean("success", false)) {
                        successfulGroupCount++;
                        int orderId = jsonResponse.optInt("order_id", jsonResponse.optInt("id", -1));
                        if (orderId <= 0) {
                            JSONObject data = jsonResponse.optJSONObject("data");
                            if (data != null) orderId = data.optInt("order_id", data.optInt("id", -1));
                        }
                        addPlacedOrderId(orderId);
                        processGroup(index + 1, userId, token, address, type, fee, paymentMethod);
                    } else {
                        // Fallback to legacy if modern fails
                        placeLegacyOrder(index, userId, token, address, type, fee, paymentMethod, payload);
                    }
                } catch (Exception e) {
                    placeLegacyOrder(index, userId, token, address, type, fee, paymentMethod, payload);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                placeLegacyOrder(index, userId, token, address, type, fee, paymentMethod, payload);
            }
        });
    }

    private void placeLegacyOrder(int index, int userId, String token, String address, String type, double fee, String paymentMethod, JSONObject payload) {
        OrderGroup group = orderGroups.get(index);
        Map<String, String> fields = new HashMap<>();
        fields.put("user_id", String.valueOf(userId));
        fields.put("restaurant_id", String.valueOf(group.restaurantId));
        fields.put("address", address);
        fields.put("delivery_type", type);
        fields.put("delivery_fee", String.valueOf(fee));
        fields.put("total", String.valueOf(group.subtotal + fee));
        fields.put("payment_method", paymentMethod);
        fields.put("items", group.items.toString());
        fields.put("api_token", token);

        apiService.placeOrderLegacy(fields).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    successfulGroupCount++;
                    int legacyId = extractOrderIdFromLegacyResponse(body);
                    addPlacedOrderId(legacyId);
                    processGroup(index + 1, userId, token, address, type, fee, paymentMethod);
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Legacy order failed", t);
                processGroup(index + 1, userId, token, address, type, fee, paymentMethod);
            }
        });
    }

    private void removeItemsFromGlobalCart(JSONArray items) {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray cart = new JSONArray(prefs.getString("global_cart_json", "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < cart.length(); i++) {
                JSONObject cartItem = cart.optJSONObject(i);
                if (cartItem == null) continue;
                boolean found = false;
                for (int j = 0; j < items.length(); j++) {
                    JSONObject placed = items.optJSONObject(j);
                    if (placed != null && placed.optInt("menu_item_id") == cartItem.optInt("menu_item_id")) {
                        found = true;
                        break;
                    }
                }
                if (!found) updated.put(cartItem);
            }
            prefs.edit().putString("global_cart_json", updated.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void finalizeCheckout() {
        if (successfulGroupCount > 0) {
            if ("cod".equalsIgnoreCase(pendingPaymentMethod)) {
                clearCartAfterSuccessfulCheckout();
                Toast.makeText(this, "Order(s) placed successfully!", Toast.LENGTH_LONG).show();
                openPostCheckoutOrderScreen();
            } else if ("gcash".equalsIgnoreCase(pendingPaymentMethod)) {
                launchGcashPayment();
            } else {
                requestSimulatedPaymentCheckoutUrl();
            }
        } else {
            setCheckoutUiState(false, "");
            Toast.makeText(this, "Failed to place orders. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearCartAfterSuccessfulCheckout() {
        for (OrderGroup group : orderGroups) {
            removeItemsFromGlobalCart(group.items);
        }
    }

    private void launchGcashPayment() {
        Intent intent = new Intent(this, GcashPaymentActivity.class);
        intent.putExtra(GcashPaymentActivity.EXTRA_AMOUNT, getGrandTotal(pendingDeliveryFee));
        if (!placedOrderIds.isEmpty()) {
            intent.putExtra("order_id", placedOrderIds.get(0));
        }
        paymentResultLauncher.launch(intent);
    }

    private void requestSimulatedPaymentCheckoutUrl() {
        setCheckoutUiState(true, "Processing payment...");

        Map<String, Object> fields = new HashMap<>();
        fields.put("payment_method", pendingPaymentMethod);
        fields.put("user_id", pendingUserId);
        fields.put("amount", getGrandTotal(pendingDeliveryFee));
        fields.put("order_ids", new JSONArray(placedOrderIds));

        apiService.simulatePayment(fields).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject json = new JSONObject(body);
                    launchPaymentWebView(json.optString("checkout_url", ""));
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "simulate-payment request failed", t);
                launchPaymentWebView("");
            }
        });
    }

    private void launchPaymentWebView(String checkoutUrl) {
        Intent intent = new Intent(this, PaymentSimulationActivity.class);
        intent.putExtra(PaymentSimulationActivity.EXTRA_CHECKOUT_URL, checkoutUrl);
        intent.putExtra(PaymentSimulationActivity.EXTRA_PAYMENT_METHOD, pendingPaymentMethod);
        intent.putExtra(PaymentSimulationActivity.EXTRA_AMOUNT, getGrandTotal(pendingDeliveryFee));
        paymentResultLauncher.launch(intent);
    }

    private void retrySimulatedPayment() {
        requestSimulatedPaymentCheckoutUrl();
    }

    private void showPaymentRetryDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Payment Failed")
                .setMessage("Your payment did not go through. Would you like to retry?")
                .setPositiveButton("Retry", (dialog, which) -> retrySimulatedPayment())
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    setCheckoutUiState(false, "");
                })
                .setCancelable(true)
                .show();
    }

    private void onPaymentSuccess() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        prefs.edit()
            .putString("last_payment_status", "paid")
            .putString("last_payment_method", pendingPaymentMethod)
            .apply();

        clearCartAfterSuccessfulCheckout();
        Toast.makeText(this, "Payment successful. Order marked as paid.", Toast.LENGTH_LONG).show();
        openPostCheckoutOrderScreen();
    }

    private void openPostCheckoutOrderScreen() {
        int primaryOrderId = placedOrderIds.isEmpty() ? -1 : placedOrderIds.get(0);
        if (primaryOrderId > 0) {
            getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                    .edit()
                    .putInt("last_active_order_id", primaryOrderId)
                    .apply();
            try {
                JSONObject activeOrderPayload = new JSONObject();
                activeOrderPayload.put("id", primaryOrderId);
                activeOrderPayload.put("order_id", primaryOrderId);
                activeOrderPayload.put("status", Constants.STATUS_PENDING);
                activeOrderPayload.put("delivery_address", pendingAddress);
                activeOrderPayload.put("payment_method", pendingPaymentMethod);
                if (!orderGroups.isEmpty()) {
                    activeOrderPayload.put("restaurant_name", orderGroups.get(0).restaurantName);
                    activeOrderPayload.put("items", orderGroups.get(0).items);
                }

                getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("last_active_order_json", activeOrderPayload.toString())
                        .apply();

                Intent activeIntent = new Intent(this, ActiveOrderActivity.class);
                activeIntent.putExtra("order_json", activeOrderPayload.toString());
                activeIntent.putExtra("order_id", primaryOrderId);
                startActivity(activeIntent);
                finish();
                return;
            } catch (Exception e) {
                Log.e(TAG, "Failed to build active-order payload", e);
            }
        }

        Intent trackingIntent = new Intent(this, OrderTrackingActivity.class);
        if (primaryOrderId > 0) {
            trackingIntent.putExtra("order_id", primaryOrderId);
        }
        trackingIntent.putExtra("payment_method", pendingPaymentMethod);
        trackingIntent.putExtra("is_paid", true);
        startActivity(trackingIntent);
        finish();
    }

    private void addPlacedOrderId(int id) {
        if (id > 0 && !placedOrderIds.contains(id)) {
            placedOrderIds.add(id);
        }
    }

    private int extractOrderIdFromLegacyResponse(String response) {
        if (TextUtils.isEmpty(response)) {
            return -1;
        }
        try {
            JSONObject json = new JSONObject(response);
            int id = json.optInt("order_id", json.optInt("id", -1));
            if (id > 0) {
                return id;
            }
            JSONObject data = json.optJSONObject("data");
            if (data != null) {
                return data.optInt("order_id", data.optInt("id", -1));
            }
        } catch (Exception ignored) {
            // Ignore malformed legacy responses and fall back gracefully.
        }
        return -1;
    }

    private double getGrandTotal(double deliveryFee) {
        double total = 0.0;
        for (OrderGroup group : orderGroups) {
            total += group.subtotal + deliveryFee;
        }
        return total;
    }

    private void setCheckoutUiState(boolean processing, String message) {
        isCheckoutInProgress = processing;
        btnConfirmPlaceOrder.setEnabled(!processing);
        checkoutAddressEditText.setEnabled(!processing);
        checkoutVehicleRadioGroup.setEnabled(!processing);
        checkoutPaymentRadioGroup.setEnabled(!processing);
        checkoutPaymentProgress.setVisibility(processing ? View.VISIBLE : View.GONE);
        findViewById(R.id.checkoutProcessingContainer).setVisibility(processing ? View.VISIBLE : View.GONE);
        if (!TextUtils.isEmpty(message)) {
            checkoutProcessingMessageTextView.setText(message);
        }
    }

    private void setupBottomNavigation() {
        if (tabHomeButton != null) {
            tabHomeButton.setOnClickListener(v -> {
                startActivity(new Intent(this, CustomerDashboard.class));
                finish();
            });
        }

        if (tabOrdersButton != null) {
            tabOrdersButton.setOnClickListener(v -> {
                startActivity(new Intent(this, OrderTrackingActivity.class));
                finish();
            });
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> {
                // Already in checkout flow, clicking cart could go back to CartActivity
                finish();
            });
        }

        if (tabNotificationsButton != null) {
            tabNotificationsButton.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationActivity.class));
            });
        }

        if (tabProfileButton != null) {
            tabProfileButton.setOnClickListener(v -> {
                startActivity(new Intent(this, ProfileActivity.class));
            });
        }
    }

    private void updateCartTabBadge() {
        if (tabCartBadgeTextView == null) return;
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray cart = new JSONArray(prefs.getString("global_cart_json", "[]"));
            int count = 0;
            for (int i = 0; i < cart.length(); i++) {
                JSONObject obj = cart.optJSONObject(i);
                if (obj != null) count += obj.optInt("quantity", 0);
            }
            if (count <= 0) {
                tabCartBadgeTextView.setVisibility(View.GONE);
            } else {
                tabCartBadgeTextView.setVisibility(View.VISIBLE);
                tabCartBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
            }
        } catch (Exception e) {
            tabCartBadgeTextView.setVisibility(View.GONE);
        }
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

    private static class OrderGroup {
        int restaurantId;
        String restaurantName;
        JSONArray items = new JSONArray();
        double subtotal = 0.0;
        OrderGroup(int id, String name) { this.restaurantId = id; this.restaurantName = name; }
    }
}
