package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String DRIVER_APPROVAL_EMAIL_SENT_PREFIX = "driver_approval_email_sent_";

    EditText emailEdit, passwordEdit;
    Button btnLogin, btnGoRegister;
    TextView forgotPassword;
    String URL_LOGIN = Constants.BASE_URL + "login"; // Use the centralized URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);
        forgotPassword = findViewById(R.id.forgotPassword);

        Intent intent = getIntent();
        if (intent != null) {
            String registeredEmail = intent.getStringExtra("email");
            String registeredPassword = intent.getStringExtra("password");
            if (registeredEmail != null) {
                emailEdit.setText(registeredEmail);
            }
            if (registeredPassword != null) {
                passwordEdit.setText(registeredPassword);
            }
        }

        btnLogin.setOnClickListener(v -> loginUser(emailEdit.getText().toString(), passwordEdit.getText().toString()));
        btnGoRegister.setOnClickListener(v -> {
            Intent registerIntent = new Intent(this, RegisterActivity.class);
            startActivity(registerIntent);
        });

        forgotPassword.setOnClickListener(v -> {
            Intent forgotPasswordIntent = new Intent(this, ForgotPasswordActivity.class);
            startActivity(forgotPasswordIntent);
        });
    }

    private void loginUser(String emailInput, String passwordInput) {
        final String email = emailInput == null ? "" : emailInput.trim();
        String password = passwordInput == null ? "" : passwordInput.trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject postData = new JSONObject();
        try {
            postData.put("email", email);
            postData.put("password", password);
        } catch (JSONException e) {
            Log.e("LoginActivity", "Failed to create JSON object", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_LOGIN, postData,
                response -> {
                    try {
                        JSONObject data = response.optJSONObject("data");
                        JSONObject user = data != null ? data.optJSONObject("user") : null;

                        String role = extractRole(response, data, user);
                        String status = extractDriverStatus(response, data, user);
                        boolean isDriver = isDriverRole(role);

                        if (isDriver) {
                            if ("pending".equals(status)) {
                                Toast.makeText(this, "Account awaiting approval", Toast.LENGTH_LONG).show();
                                return;
                            }
                            if ("rejected".equals(status)) {
                                String message = response.optString("message", "Your driver account was rejected.");
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (!"approved".equals(status)) {
                                Toast.makeText(this, "Driver account is not approved yet.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }

                        String apiToken = "";
                        if (data != null) {
                            apiToken = data.optString("token");
                            if (apiToken.isEmpty()) {
                                if (user != null) {
                                    apiToken = user.optString("api_token");
                                }
                            }
                        }

                        if (apiToken.isEmpty()) {
                            Log.e("LoginActivity", "API token not found in response: " + response.toString());
                            Toast.makeText(this, "Login failed: Could not retrieve API token.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit().putString("api_token", apiToken).apply();
                        if (isDriver) {
                            prefs.edit().putString("driver_email", email).apply();
                        }

                        if (isDriver && "approved".equals(status)) {
                            maybeSendDriverApprovalEmail(email, response, data, user);
                        }

                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();

                        Intent intent;
                        if (isDriver) {
                            intent = new Intent(this, DriverDashboard.class);
                        } else {
                            intent = new Intent(this, CustomerDashboard.class);
                        }
                        startActivity(intent);
                        finish();

                    } catch (Exception e) {
                        Log.e("LoginActivity", "Failed to parse login success response", e);
                        Toast.makeText(this, "An error occurred after login.", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);
                            String message = data.optString("message", "An unknown error occurred.");
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e("LoginActivity", "Error parsing error response", e);
                            Toast.makeText(this, "Login Failed. Check logs for details.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("LoginActivity", "Login Volley Error", error);
                        Toast.makeText(this, "Login Failed. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    private void maybeSendDriverApprovalEmail(String email, JSONObject response, JSONObject data, JSONObject user) {
        if (!EmailNotificationService.isGmailAddress(email)) {
            return;
        }

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String sentFlagKey = DRIVER_APPROVAL_EMAIL_SENT_PREFIX + normalizeValue(email);
        if (prefs.getBoolean(sentFlagKey, false)) {
            return;
        }

        String driverName = firstNonEmpty(
                findFirstStringForKeys(user, "name", "full_name"),
                findFirstStringForKeys(data, "name", "full_name"),
                findFirstStringForKeys(response, "name", "full_name")
        );

        EmailNotificationService.sendDriverApplicationApproved(
                getApplicationContext(),
                email,
                driverName,
                () -> prefs.edit().putBoolean(sentFlagKey, true).apply()
        );
    }

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private String normalizeRole(String rawRole) {
        String normalized = normalizeValue(rawRole);
        if (normalized.contains("driver")) {
            return "driver";
        }
        if (normalized.contains("customer")) {
            return "customer";
        }
        return normalized;
    }

    private boolean isDriverRole(String role) {
        return "driver".equals(role);
    }

    private boolean isKnownDriverStatus(String status) {
        return "approved".equals(status) || "pending".equals(status) || "rejected".equals(status);
    }

    private String extractRole(JSONObject response, JSONObject data, JSONObject user) {
        String roleValue = firstNonEmpty(
                findFirstStringForKeys(user, "role", "user_role", "role_name", "user_type", "type"),
                findFirstStringForKeys(data, "role", "user_role", "role_name", "user_type", "type"),
                findFirstStringForKeys(response, "role", "user_role", "role_name", "user_type", "type")
        );
        return normalizeRole(roleValue);
    }

    private String extractDriverStatus(JSONObject response, JSONObject data, JSONObject user) {
        String status = normalizeValue(firstNonEmpty(
                findFirstStringForKeys(user, "account_status", "driver_status", "status"),
                findFirstStringForKeys(data, "account_status", "driver_status", "status"),
                findFirstStringForKeys(response, "account_status", "driver_status")
        ));

        if (isKnownDriverStatus(status)) {
            return status;
        }

        String fallbackStatus = normalizeValue(findFirstStringForKeys(response, "status", "account_status"));
        if (isKnownDriverStatus(fallbackStatus)) {
            return fallbackStatus;
        }

        return status;
    }

    private String findFirstStringForKeys(JSONObject source, String... keys) {
        if (source == null || keys == null || keys.length == 0) {
            return "";
        }

        for (String key : keys) {
            String direct = source.optString(key, "");
            if (direct != null && !direct.trim().isEmpty()) {
                return direct;
            }
        }

        JSONArray names = source.names();
        if (names == null) {
            return "";
        }

        for (int index = 0; index < names.length(); index++) {
            String childKey = names.optString(index, "");
            Object childValue = source.opt(childKey);

            if (childValue instanceof JSONObject) {
                String nestedValue = findFirstStringForKeys((JSONObject) childValue, keys);
                if (!nestedValue.isEmpty()) {
                    return nestedValue;
                }
            } else if (childValue instanceof JSONArray) {
                String arrayValue = findFirstStringInArray((JSONArray) childValue, keys);
                if (!arrayValue.isEmpty()) {
                    return arrayValue;
                }
            }
        }

        return "";
    }

    private String findFirstStringInArray(JSONArray array, String... keys) {
        if (array == null) {
            return "";
        }

        for (int index = 0; index < array.length(); index++) {
            Object item = array.opt(index);
            if (item instanceof JSONObject) {
                String nestedValue = findFirstStringForKeys((JSONObject) item, keys);
                if (!nestedValue.isEmpty()) {
                    return nestedValue;
                }
            } else if (item instanceof JSONArray) {
                String nestedArrayValue = findFirstStringInArray((JSONArray) item, keys);
                if (!nestedArrayValue.isEmpty()) {
                    return nestedArrayValue;
                }
            }
        }

        return "";
    }
}
