package com.example.fooddash;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ActiveOrderActivity extends AppCompatActivity {

    private static final String TAG = "ActiveOrderActivity";

    private TextView activeOrderIdText, activeCustomerName, activeCustomerContact, activeDeliveryAddress;
    private TextView activeRestaurantName, activeOrderItems, activeOrderStatus;
    private Button btnArrived, btnPickedUp, btnOnTheWay, btnDelivered, btnBackToDashboard;

    private RequestQueue requestQueue;
    private JSONObject activeOrder;
    private int orderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_order);

        requestQueue = Volley.newRequestQueue(this);

        activeOrderIdText = findViewById(R.id.activeOrderIdText);
        activeCustomerName = findViewById(R.id.activeCustomerName);
        activeCustomerContact = findViewById(R.id.activeCustomerContact);
        activeDeliveryAddress = findViewById(R.id.activeDeliveryAddress);
        activeRestaurantName = findViewById(R.id.activeRestaurantName);
        activeOrderItems = findViewById(R.id.activeOrderItems);
        activeOrderStatus = findViewById(R.id.activeOrderStatus);

        btnArrived = findViewById(R.id.btnArrived);
        btnPickedUp = findViewById(R.id.btnPickedUp);
        btnOnTheWay = findViewById(R.id.btnOnTheWay);
        btnDelivered = findViewById(R.id.btnDelivered);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);

        String orderJson = getIntent().getStringExtra("order_json");
        if (orderJson != null) {
            try {
                activeOrder = new JSONObject(orderJson);
                renderOrderDetails();
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing order JSON", e);
                finish();
            }
        } else {
            finish();
        }

        btnArrived.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_ARRIVED_RESTAURANT));
        btnPickedUp.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_PICKED_UP));
        btnOnTheWay.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_ON_THE_WAY));
        btnDelivered.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_DELIVERED));
        btnBackToDashboard.setOnClickListener(v -> finish());
    }

    private void renderOrderDetails() {
        orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", -1));
        activeOrderIdText.setText("Order #" + orderId);
        
        activeCustomerName.setText(activeOrder.optString("customer_name", "Customer"));
        activeCustomerContact.setText("Contact: " + activeOrder.optString("customer_contact", "N/A"));
        activeDeliveryAddress.setText("Address: " + activeOrder.optString("delivery_address", "N/A"));
        
        activeRestaurantName.setText(activeOrder.optString("restaurant_name", "Restaurant"));
        activeOrderItems.setText("Items: " + activeOrder.optString("items_summary", "View in items list"));
        
        String status = activeOrder.optString("status", "pending");
        activeOrderStatus.setText("Status: " + status.toUpperCase());
        
        updateButtonVisibilities(status);
    }

    private void updateButtonVisibilities(String status) {
        btnArrived.setVisibility(View.GONE);
        btnPickedUp.setVisibility(View.GONE);
        btnOnTheWay.setVisibility(View.GONE);
        btnDelivered.setVisibility(View.GONE);

        if (Constants.STATUS_ASSIGNED.equals(status) || Constants.STATUS_ACCEPTED.equals(status)) {
            btnArrived.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_ARRIVED_RESTAURANT.equals(status)) {
            btnPickedUp.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_PICKED_UP.equals(status)) {
            btnOnTheWay.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_ON_THE_WAY.equals(status)) {
            btnDelivered.setVisibility(View.VISIBLE);
        }
    }

    private void updateOrderStatus(String status) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("order_id", orderId);
            payload.put("driver_id", getDriverId());
            payload.put("status", status);
        } catch (JSONException e) {
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_STATUS,
                payload,
                response -> {
                    Toast.makeText(this, "Status updated to " + status, Toast.LENGTH_SHORT).show();
                    activeOrderStatus.setText("Status: " + status.toUpperCase());
                    updateButtonVisibilities(status);
                    if (Constants.STATUS_DELIVERED.equals(status)) {
                        finish();
                    }
                },
                error -> updateStatusLegacy(payload, status)
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };

        requestQueue.add(request);
    }

    private void updateStatusLegacy(JSONObject payload, String status) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                Constants.URL_UPDATE_ORDER_STATUS_LEGACY,
                payload,
                response -> {
                    Toast.makeText(this, "Status updated to " + status, Toast.LENGTH_SHORT).show();
                    activeOrderStatus.setText("Status: " + status.toUpperCase());
                    updateButtonVisibilities(status);
                    if (Constants.STATUS_DELIVERED.equals(status)) {
                        finish();
                    }
                },
                error -> Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            public Map<String, String> getHeaders() {
                return buildAuthHeaders();
            }
        };
        requestQueue.add(request);
    }

    private int getDriverId() {
        return getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getInt("user_id", -1);
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getString("api_token", "");
        if (!TextUtils.isEmpty(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }
}
