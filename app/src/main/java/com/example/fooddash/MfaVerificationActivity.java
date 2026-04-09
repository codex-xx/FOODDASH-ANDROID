package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class MfaVerificationActivity extends AppCompatActivity {

    private static final String TAG = "MfaVerificationActivity";
    private static final String PREFS_NAME = "fooddash_prefs";

    private EditText mfaCodeEdit;
    private Button verifyMfaButton;

    private String email = "";
    private String role = "customer";
    private String challengeToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mfa_verification);

        mfaCodeEdit = findViewById(R.id.mfaCodeEdit);
        verifyMfaButton = findViewById(R.id.btnVerifyMfa);

        Intent intent = getIntent();
        if (intent != null) {
            email = safeValue(intent.getStringExtra("email"));
            role = safeValue(intent.getStringExtra("role"));
            challengeToken = safeValue(intent.getStringExtra("mfa_challenge_token"));
        }

        verifyMfaButton.setOnClickListener(v -> verifyMfaCode());
    }

    private void verifyMfaCode() {
        String code = safeValue(mfaCodeEdit.getText().toString());
        if (code.length() < 4) {
            Toast.makeText(this, "Enter a valid MFA code", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("email", email);
            payload.put("code", code);
            payload.put("otp", code);
            if (!TextUtils.isEmpty(challengeToken)) {
                payload.put("mfa_token", challengeToken);
                payload.put("challenge_token", challengeToken);
                payload.put("challenge_id", challengeToken);
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Failed to build MFA payload", exception);
            Toast.makeText(this, "Could not build MFA request", Toast.LENGTH_SHORT).show();
            return;
        }

        sendMfaRequest(Constants.URL_MFA_VERIFY, payload, true);
    }

    private void sendMfaRequest(String url, JSONObject payload, boolean allowFallback) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                payload,
                this::onMfaSuccess,
                error -> {
                    if (allowFallback && Constants.URL_MFA_VERIFY.equals(url)) {
                        sendMfaRequest(Constants.URL_MFA_VERIFY_FALLBACK, payload, false);
                        return;
                    }

                    String message = "MFA verification failed";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                            JSONObject json = new JSONObject(responseBody);
                            message = json.optString("message", message);
                        } catch (Exception parseException) {
                            Log.e(TAG, "Failed to parse MFA error", parseException);
                        }
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        );

        request.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        Volley.newRequestQueue(this).add(request);
    }

    private void onMfaSuccess(JSONObject response) {
        String accessToken = AuthSessionManager.extractAccessToken(response);
        if (TextUtils.isEmpty(accessToken)) {
            Toast.makeText(this, "MFA verified but session token is missing", Toast.LENGTH_LONG).show();
            return;
        }

        String refreshToken = AuthSessionManager.extractRefreshToken(response);
        String tokenType = AuthSessionManager.extractTokenType(response);
        Long expiresAt = AuthSessionManager.extractExpiresAtEpochSeconds(response, accessToken);

        AuthSessionManager.saveSession(this, accessToken, refreshToken, expiresAt, tokenType);

        JSONObject data = response.optJSONObject("data");
        JSONObject user = data != null ? data.optJSONObject("user") : null;

        String resolvedRole = firstNonEmpty(
                safeValue(role),
                extractValue(user, "role"),
                extractValue(data, "role"),
                extractValue(response, "role")
        );

        int userId = extractUserId(response, data, user);
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putInt("user_id", userId)
                .putString("user_role", resolvedRole)
                .putString("user_email", email)
                .apply();

        Toast.makeText(this, "MFA verification successful", Toast.LENGTH_SHORT).show();

        Intent destination = "driver".equalsIgnoreCase(resolvedRole)
                ? new Intent(this, DriverDashboard.class)
                : new Intent(this, CustomerDashboard.class);

        destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(destination);
        finish();
    }

    private int extractUserId(JSONObject response, JSONObject data, JSONObject user) {
        int id = parseInt(firstNonEmpty(
                extractValue(user, "id"),
                extractValue(user, "user_id"),
                extractValue(data, "id"),
                extractValue(data, "user_id"),
                extractValue(response, "id"),
                extractValue(response, "user_id")
        ));

        if (id > 0) {
            return id;
        }

        if (user != null) {
            id = user.optInt("id", user.optInt("user_id", -1));
        }
        if (id > 0) {
            return id;
        }

        if (data != null) {
            id = data.optInt("id", data.optInt("user_id", -1));
        }
        if (id > 0) {
            return id;
        }

        return response != null ? response.optInt("id", response.optInt("user_id", -1)) : -1;
    }

    private int parseInt(String value) {
        if (TextUtils.isEmpty(value)) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String extractValue(JSONObject source, String key) {
        if (source == null || TextUtils.isEmpty(key)) {
            return "";
        }
        return safeValue(source.optString(key, ""));
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
