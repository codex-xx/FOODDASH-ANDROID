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

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONException;
import org.json.JSONObject;

public class MfaVerificationActivity extends AppCompatActivity {

    private static final String TAG = "MfaVerificationActivity";
    private static final String PREFS_NAME = "fooddash_prefs";

    private EditText mfaCodeEdit;
    private Button verifyMfaButton;
    private ApiService apiService;

    private String email = "";
    private String role = "customer";
    private String challengeToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mfa_verification);

        mfaCodeEdit = findViewById(R.id.mfaCodeEdit);
        verifyMfaButton = findViewById(R.id.btnVerifyMfa);
        apiService = RetrofitClient.getApiService();

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

        Map<String, String> fields = new HashMap<>();
        fields.put("email", email);
        fields.put("code", code);
        fields.put("otp", code);
        if (!TextUtils.isEmpty(challengeToken)) {
            fields.put("mfa_token", challengeToken);
            fields.put("challenge_token", challengeToken);
            fields.put("challenge_id", challengeToken);
        }

        sendMfaRequest(Constants.URL_MFA_VERIFY, fields, true);
    }

    private void sendMfaRequest(String url, Map<String, String> fields, boolean allowFallback) {
        Call<ResponseBody> call = Constants.URL_MFA_VERIFY.equals(url) ? 
                                 apiService.verifyMfa(fields) : apiService.verifyMfaFallback(fields);
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String responseBody = response.body() != null ? response.body().string() : 
                                         (response.errorBody() != null ? response.errorBody().string() : "");
                    responseBody = responseBody.trim();

                    JSONObject jsonResponse;
                    if (responseBody.startsWith("{")) {
                        jsonResponse = new JSONObject(responseBody);
                    } else {
                        jsonResponse = new JSONObject();
                        jsonResponse.put("success", response.isSuccessful());
                        jsonResponse.put("message", responseBody.isEmpty() ? (response.isSuccessful() ? "MFA verified" : "MFA verification failed") : responseBody);
                    }
                    
                    if (response.isSuccessful()) {
                        onMfaSuccess(jsonResponse);
                    } else if (allowFallback && Constants.URL_MFA_VERIFY.equals(url)) {
                        sendMfaRequest(Constants.URL_MFA_VERIFY_FALLBACK, fields, false);
                    } else {
                        String message = jsonResponse.optString("message", "MFA verification failed");
                        Toast.makeText(MfaVerificationActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                if (allowFallback && Constants.URL_MFA_VERIFY.equals(url)) {
                    sendMfaRequest(Constants.URL_MFA_VERIFY_FALLBACK, fields, false);
                } else {
                    Toast.makeText(MfaVerificationActivity.this, "MFA verification failed", Toast.LENGTH_LONG).show();
                }
            }
        });
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
