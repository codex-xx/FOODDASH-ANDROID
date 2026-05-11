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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String DRIVER_APPROVAL_EMAIL_SENT_PREFIX = "driver_approval_email_sent_";
    private static final String PREF_LAST_REGISTERED_EMAIL = "last_registered_email";
    private static final String PREF_LAST_REGISTERED_ROLE = "last_registered_role";

    EditText emailEdit, passwordEdit;
    Button btnLogin, btnGoRegister;
    TextView forgotPassword;
    String URL_LOGIN = Constants.URL_LOGIN;

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
            if (registeredEmail != null) {
                emailEdit.setText(registeredEmail);
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
        performLogin(emailInput, passwordInput, URL_LOGIN, true, buildLoginRoleCandidates(emailInput), 0);
    }

    private void performLogin(String emailInput, String passwordInput, String url, boolean allowFallback, List<String> roleCandidates, int roleIndex) {
        final String email = emailInput == null ? "" : emailInput.trim();
        String password = passwordInput == null ? "" : passwordInput.trim();
        String roleHint = getRoleCandidate(roleCandidates, roleIndex);

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject postData = new JSONObject();
        try {
            postData.put("email", email);
            postData.put("password", password);
            if (!roleHint.isEmpty()) {
                postData.put("role", roleHint);
                postData.put("user_role", roleHint);
                postData.put("type", roleHint);
            }
        } catch (JSONException e) {
            Log.e("LoginActivity", "Failed to create JSON object", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, postData,
                response -> {
                    try {
                        if (!response.optBoolean("success", true)) {
                            String message = response.optString("message", "Login failed.");
                            if (isAuthFailureMessage(message) && tryNextLoginAttempt(emailInput, passwordInput, url, allowFallback, roleCandidates, roleIndex)) {
                                return;
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            return;
                        }

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
                            if (!"approved".equals(status) && !"active".equals(status)) {
                                Toast.makeText(this, "Driver account is not approved yet.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }

                        if (AuthSessionManager.isMfaRequired(response)) {
                            Intent mfaIntent = new Intent(this, MfaVerificationActivity.class);
                            mfaIntent.putExtra("email", email);
                            mfaIntent.putExtra("role", role);
                            mfaIntent.putExtra("mfa_challenge_token", AuthSessionManager.extractMfaChallengeToken(response));
                            startActivity(mfaIntent);
                            return;
                        }

                        String apiToken = AuthSessionManager.extractAccessToken(response);

                        if (apiToken.isEmpty()) {
                            Log.e("LoginActivity", "API token not found in response: " + response);
                            Toast.makeText(this, "Login failed: Could not retrieve API token.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        String refreshToken = AuthSessionManager.extractRefreshToken(response);
                        String tokenType = AuthSessionManager.extractTokenType(response);
                        Long expiresAt = AuthSessionManager.extractExpiresAtEpochSeconds(response, apiToken);
                        AuthSessionManager.saveSession(this, apiToken, refreshToken, expiresAt, tokenType);

                        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        int userId = extractUserId(response, data, user);
                        
                        String userName = firstNonEmpty(
                            findFirstStringForKeys(user, "name", "full_name", "username"),
                            findFirstStringForKeys(data, "name", "full_name", "username"),
                            findFirstStringForKeys(response, "name", "full_name", "username")
                        );

                        String vehicleType = normalizeValue(firstNonEmpty(
                            findFirstStringForKeys(user, "vehicle_type", "vehicleType", "vehicle"),
                            findFirstStringForKeys(data, "vehicle_type", "vehicleType", "vehicle"),
                            findFirstStringForKeys(response, "vehicle_type", "vehicleType", "vehicle")
                        ));
                        String deliveryAddress = firstNonEmpty(
                            findFirstStringForKeys(user, "delivery_address", "address"),
                            findFirstStringForKeys(data, "delivery_address", "address"),
                            findFirstStringForKeys(response, "delivery_address", "address")
                        );
                        String contactNumber = firstNonEmpty(
                            findFirstStringForKeys(user, "contact_number", "phone", "contact"),
                            findFirstStringForKeys(data, "contact_number", "phone", "contact"),
                            findFirstStringForKeys(response, "contact_number", "phone", "contact")
                        );

                        prefs.edit()
                            .putInt("user_id", userId)
                            .putString("user_name", userName)
                            .putString("user_role", role)
                            .putString("user_email", email)
                            .putString("delivery_address", deliveryAddress)
                            .putString("contact_number", contactNumber)
                            .putString("vehicle_type", vehicleType)
                            .apply();
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
                    if (error != null && error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                            JSONObject data = new JSONObject(responseBody);
                            String message = data.optString("message", "An unknown error occurred.");
                            if (isAuthFailureMessage(message) && tryNextLoginAttempt(emailInput, passwordInput, url, allowFallback, roleCandidates, roleIndex)) {
                                return;
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            return;
                        } catch (Exception e) {
                            Log.e("LoginActivity", "Error parsing error response", e);
                        }
                    }

                    if (tryNextLoginAttempt(emailInput, passwordInput, url, allowFallback, roleCandidates, roleIndex)) {
                        return;
                    }

                    if (error != null && error.networkResponse != null) {
                        Toast.makeText(this, "Login Failed (Code " + error.networkResponse.statusCode + ").", Toast.LENGTH_LONG).show();
                    } else {
                        Log.e("LoginActivity", "Login Volley Error", error);
                        Toast.makeText(this, "Login Failed. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    private List<String> buildLoginRoleCandidates(String email) {
        ArrayList<String> candidates = new ArrayList<>();

        addRoleCandidate(candidates, resolveLoginRoleHint(email));

        addRoleCandidate(candidates, "");
        addRoleCandidate(candidates, "driver");
        addRoleCandidate(candidates, "customer");

        return candidates;
    }

    private void addRoleCandidate(List<String> candidates, String role) {
        String normalized = normalizeRole(role);
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private String getRoleCandidate(List<String> candidates, int index) {
        if (candidates == null || index < 0 || index >= candidates.size()) {
            return "";
        }
        return candidates.get(index);
    }

    private boolean tryNextLoginAttempt(String emailInput, String passwordInput, String url, boolean allowFallback, List<String> roleCandidates, int roleIndex) {
        if (roleCandidates != null && roleIndex + 1 < roleCandidates.size()) {
            performLogin(emailInput, passwordInput, url, allowFallback, roleCandidates, roleIndex + 1);
            return true;
        }

        if (allowFallback && Constants.URL_LOGIN.equals(url)) {
            Log.w("LoginActivity", "Login failed on primary URL, trying legacy fallback...");
            performLogin(emailInput, passwordInput, Constants.URL_LOGIN_LEGACY, false, roleCandidates, 0);
            return true;
        }

        return false;
    }

    private boolean isAuthFailureMessage(String message) {
        String normalized = normalizeValue(message);
        return normalized.contains("invalid email or password")
                || normalized.contains("invalid credentials")
                || normalized.contains("incorrect password")
                || normalized.contains("unauthorized")
                || normalized.contains("authentication failed")
                || normalized.contains("login failed");
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
        return "approved".equals(status) || "active".equals(status) || "pending".equals(status) || "rejected".equals(status);
    }

    private String resolveLoginRoleHint(String email) {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String intentRole = normalizeRole(getIntent() != null ? getIntent().getStringExtra("role") : "");
        if (!intentRole.isEmpty()) {
            return intentRole;
        }

        String storedEmail = normalizeValue(prefs.getString(PREF_LAST_REGISTERED_EMAIL, ""));
        String storedRole = normalizeRole(prefs.getString(PREF_LAST_REGISTERED_ROLE, ""));
        if (!storedRole.isEmpty() && !storedEmail.isEmpty() && storedEmail.equalsIgnoreCase(normalizeValue(email))) {
            return storedRole;
        }

        return "";
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
            if (!direct.trim().isEmpty()) {
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

    private int extractUserId(JSONObject response, JSONObject data, JSONObject user) {
        int idFromUser = parseIntSafe(firstNonEmpty(
                findFirstStringForKeys(user, "id", "user_id"),
                findFirstStringForKeys(data, "id", "user_id"),
                findFirstStringForKeys(response, "id", "user_id")
        ));
        if (idFromUser > 0) {
            return idFromUser;
        }

        if (user != null) {
            int parsed = user.optInt("id", user.optInt("user_id", -1));
            if (parsed > 0) {
                return parsed;
            }
        }

        if (data != null) {
            int parsed = data.optInt("id", data.optInt("user_id", -1));
            if (parsed > 0) {
                return parsed;
            }
        }

        return response != null ? response.optInt("id", response.optInt("user_id", -1)) : -1;
    }

    private int parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
