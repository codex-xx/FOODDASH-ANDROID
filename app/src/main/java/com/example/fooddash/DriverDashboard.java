package com.example.fooddash;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class DriverDashboard extends AppCompatActivity {

    private static final String TAG = "DriverDashboard";
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String DRIVER_APPROVAL_EMAIL_SENT_PREFIX = "driver_approval_email_sent_";

    Button btnViewOrders, btnLogout;
    String URL_VIEW_ORDERS = Constants.BASE_URL + "orders";
    String URL_GET_PROFILE = Constants.BASE_URL + "get-profile";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_dashboard);

        btnViewOrders = findViewById(R.id.btnViewOrders);
        btnLogout = findViewById(R.id.btnLogout);

        btnViewOrders.setOnClickListener(v -> viewOrders());

        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        // Check if driver was approved and send confirmation email if needed
        checkDriverApprovalStatus();
    }

    private void viewOrders() {
        StringRequest request = new StringRequest(Request.Method.GET, URL_VIEW_ORDERS,
                response -> {
                    // In a real app, you would parse this JSON and display it in a list
                    Toast.makeText(this, response, Toast.LENGTH_LONG).show();
                },
                error -> Toast.makeText(this, "Failed to fetch orders", Toast.LENGTH_SHORT).show()
        );

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }

    private void checkDriverApprovalStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String apiToken = prefs.getString("api_token", "");
        String email = prefs.getString("driver_email", "");

        if (apiToken.isEmpty() || email.isEmpty()) {
            Log.d(TAG, "Missing API token or email, skipping approval status check");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                URL_GET_PROFILE,
                null,
                response -> {
                    try {
                        String status = extractStatus(response);
                        String driverName = extractName(response);

                        if ("approved".equals(status) && !driverName.isEmpty()) {
                            maybeSendDriverApprovalEmail(email, driverName);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing approval status response", e);
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            Log.e(TAG, "Failed to check approval status: " + responseBody, error);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing approval status error response", e);
                        }
                    } else {
                        Log.d(TAG, "Could not check approval status (may be offline)");
                    }
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                String apiToken = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("api_token", "");
                if (!apiToken.isEmpty()) {
                    headers.put("Authorization", "Bearer " + apiToken);
                }
                return headers;
            }
        };

        Volley.newRequestQueue(this).add(request);
    }

    private String extractStatus(JSONObject response) {
        if (response == null) return "";
        String status = response.optString("status", "");
        if (!status.isEmpty()) return status;
        
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            status = data.optString("status", "");
            if (!status.isEmpty()) return status;
            status = data.optString("account_status", "");
            if (!status.isEmpty()) return status;
            status = data.optString("driver_status", "");
        }
        return status;
    }

    private String extractName(JSONObject response) {
        if (response == null) return "";
        String name = response.optString("name", "");
        if (!name.isEmpty()) return name;
        name = response.optString("full_name", "");
        if (!name.isEmpty()) return name;
        
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            name = data.optString("name", "");
            if (!name.isEmpty()) return name;
            name = data.optString("full_name", "");
        }
        return name;
    }

    private void maybeSendDriverApprovalEmail(String email, String driverName) {
        if (!EmailNotificationService.isGmailAddress(email)) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String sentFlagKey = DRIVER_APPROVAL_EMAIL_SENT_PREFIX + normalizeValue(email);
        
        if (prefs.getBoolean(sentFlagKey, false)) {
            Log.d(TAG, "Approval email already sent for: " + email);
            return;
        }

        Log.i(TAG, "Sending driver approval confirmation email to: " + email);
        EmailNotificationService.sendDriverApplicationApproved(
                getApplicationContext(),
                email,
                driverName,
                () -> {
                    prefs.edit().putBoolean(sentFlagKey, true).apply();
                    Log.i(TAG, "Driver approval email sent successfully for: " + email);
                }
        );
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }
}