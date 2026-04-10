package com.example.fooddash;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AuthSessionManager {

    private static final String TAG = "AuthSessionManager";
    private static final String LEGACY_PREFS = "fooddash_prefs";
    private static final String SECURE_PREFS = "fooddash_secure_prefs";

    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_TOKEN_TYPE = "token_type";
    private static final String KEY_EXPIRES_AT = "expires_at_epoch_seconds";

    // Clock skew buffer in seconds (e.g., 60 seconds) to avoid using tokens that are about to expire.
    private static final int EXPIRATION_BUFFER_SECONDS = 60;

    private AuthSessionManager() {
    }

    public static void saveSession(Context context, String accessToken, String refreshToken, Long expiresAtEpochSeconds, String tokenType) {
        SharedPreferences securePrefs = getSecurePreferences(context);
        SharedPreferences.Editor editor = securePrefs.edit();
        editor.putString(KEY_ACCESS_TOKEN, safeValue(accessToken));
        editor.putString(KEY_REFRESH_TOKEN, safeValue(refreshToken));
        editor.putString(KEY_TOKEN_TYPE, normalizeTokenType(tokenType));

        if (expiresAtEpochSeconds != null && expiresAtEpochSeconds > 0) {
            editor.putLong(KEY_EXPIRES_AT, expiresAtEpochSeconds);
        } else {
            editor.remove(KEY_EXPIRES_AT);
        }
        editor.apply();

        // Keep legacy token key for existing app flows while migration is in progress.
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("api_token", safeValue(accessToken))
                .apply();
    }

    public static void clearSession(Context context) {
        getSecurePreferences(context).edit().clear().apply();
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("api_token")
                .apply();
    }

    public static String getValidAccessTokenOrNull(Context context) {
        SharedPreferences securePrefs = getSecurePreferences(context);

        String token = safeValue(securePrefs.getString(KEY_ACCESS_TOKEN, ""));
        if (TextUtils.isEmpty(token)) {
            token = safeValue(context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString("api_token", ""));
        }

        if (TextUtils.isEmpty(token)) {
            return "";
        }

        if (isTokenExpired(securePrefs, token)) {
            Log.w(TAG, "Access token is expired (or will expire within buffer).");
            return "";
        }

        return token;
    }

    public static boolean isMfaRequired(JSONObject response) {
        JSONObject data = response != null ? response.optJSONObject("data") : null;
        return boolForKeys(data, "mfa_required", "requires_mfa", "requires_2fa", "two_factor_required")
                || boolForKeys(response, "mfa_required", "requires_mfa", "requires_2fa", "two_factor_required");
    }

    public static String extractMfaChallengeToken(JSONObject response) {
        JSONObject data = response != null ? response.optJSONObject("data") : null;
        String token = firstStringForKeys(data,
                "mfa_token",
                "mfa_challenge_token",
                "challenge_token",
                "challenge_id",
                "temp_token",
                "temporary_token");

        if (!TextUtils.isEmpty(token)) {
            return token;
        }

        return firstStringForKeys(response,
                "mfa_token",
                "mfa_challenge_token",
                "challenge_token",
                "challenge_id",
                "temp_token",
                "temporary_token");
    }

    public static String extractAccessToken(JSONObject response) {
        JSONObject data = response != null ? response.optJSONObject("data") : null;
        JSONObject user = data != null ? data.optJSONObject("user") : null;

        String token = firstNonEmpty(
                firstStringForKeys(data, "token", "access_token", "jwt", "api_token"),
                firstStringForKeys(user, "token", "access_token", "jwt", "api_token"),
                firstStringForKeys(response, "token", "access_token", "jwt", "api_token")
        );

        return safeValue(token);
    }

    public static String extractRefreshToken(JSONObject response) {
        JSONObject data = response != null ? response.optJSONObject("data") : null;
        return safeValue(firstNonEmpty(
                firstStringForKeys(data, "refresh_token", "refreshToken"),
                firstStringForKeys(response, "refresh_token", "refreshToken")
        ));
    }

    public static String extractTokenType(JSONObject response) {
        JSONObject data = response != null ? response.optJSONObject("data") : null;
        return normalizeTokenType(firstNonEmpty(
                firstStringForKeys(data, "token_type", "tokenType"),
                firstStringForKeys(response, "token_type", "tokenType"),
                "Bearer"
        ));
    }

    public static Long extractExpiresAtEpochSeconds(JSONObject response, String accessToken) {
        JSONObject data = response != null ? response.optJSONObject("data") : null;

        long now = System.currentTimeMillis() / 1000L;
        long expiresAt = longForKeys(data, "expires_at", "expiresAt", "access_expires_at", "accessExpiresAt");
        if (expiresAt <= 0) {
            expiresAt = longForKeys(response, "expires_at", "expiresAt", "access_expires_at", "accessExpiresAt");
        }

        if (expiresAt > 0) {
            return expiresAt;
        }

        long expiresIn = longForKeys(data, "expires_in", "expiresIn", "access_expires_in", "accessExpiresIn");
        if (expiresIn <= 0) {
            expiresIn = longForKeys(response, "expires_in", "expiresIn", "access_expires_in", "accessExpiresIn");
        }
        if (expiresIn > 0) {
            return now + expiresIn;
        }

        Long jwtExp = parseJwtExp(accessToken);
        if (jwtExp != null && jwtExp > 0) {
            return jwtExp;
        }

        return null;
    }

    private static boolean isTokenExpired(SharedPreferences securePrefs, String token) {
        // Add a buffer to 'now' to handle clock skew and ensure token is valid for the duration of the request.
        long nowWithBuffer = (System.currentTimeMillis() / 1000L) + EXPIRATION_BUFFER_SECONDS;
        long expiresAt = securePrefs.getLong(KEY_EXPIRES_AT, -1L);

        if (expiresAt > 0) {
            return nowWithBuffer >= expiresAt;
        }

        Long expFromJwt = parseJwtExp(token);
        return expFromJwt != null && nowWithBuffer >= expFromJwt;
    }

    private static Long parseJwtExp(String token) {
        if (TextUtils.isEmpty(token)) {
            return null;
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            JSONObject jsonPayload = new JSONObject(payload);
            if (jsonPayload.has("exp")) {
                return jsonPayload.optLong("exp", -1L);
            }
        } catch (IllegalArgumentException | JSONException exception) {
            Log.w(TAG, "JWT payload parsing failed", exception);
        }

        return null;
    }

    private static SharedPreferences getSecurePreferences(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception exception) {
            Log.w(TAG, "Encrypted storage unavailable, fallback to private prefs", exception);
            return context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE);
        }
    }

    private static boolean boolForKeys(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return false;
        }

        for (String key : keys) {
            if (source.has(key) && source.optBoolean(key, false)) {
                return true;
            }

            String value = source.optString(key, "").trim().toLowerCase(Locale.ROOT);
            if ("true".equals(value) || "1".equals(value) || "yes".equals(value)) {
                return true;
            }
        }

        return false;
    }

    private static long longForKeys(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return -1L;
        }

        for (String key : keys) {
            if (source.has(key)) {
                Object value = source.opt(key);
                if (value instanceof Number) {
                    return ((Number) value).longValue();
                }

                String text = source.optString(key, "").trim();
                if (!TextUtils.isEmpty(text)) {
                    try {
                        return Long.parseLong(text);
                    } catch (NumberFormatException ignored) {
                        // Try next key.
                    }
                }
            }
        }

        return -1L;
    }

    private static String firstStringForKeys(JSONObject source, String... keys) {
        if (source == null || keys == null) {
            return "";
        }

        for (String key : keys) {
            String value = source.optString(key, "");
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }

        return "";
    }

    private static String normalizeTokenType(String tokenType) {
        String normalized = safeValue(tokenType);
        return normalized.isEmpty() ? "Bearer" : normalized;
    }

    private static String safeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
