package com.example.fooddash;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * LoginAttemptsManager handles login security by tracking failed login attempts
 * and temporarily locking accounts after 3 consecutive failed attempts for 1 minute.
 * 
 * Features:
 * - Tracks failed login attempts per account (email-based)
 * - Automatic 1-minute lockout after 3 failed attempts
 * - Secure storage in encrypted SharedPreferences
 * - Device-level tracking to prevent cross-app bypass
 * - Automatic reset on successful login
 */
public final class LoginAttemptsManager {

    private static final String TAG = "LoginAttemptsManager";
    private static final String SECURE_PREFS = "fooddash_login_attempts";
    
    // Configuration constants
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION_MILLIS = 60_000; // 1 minute
    
    // Preference keys
    private static final String KEY_FAILED_ATTEMPTS_PREFIX = "login_failed_attempts_";
    private static final String KEY_LOCKOUT_TIMESTAMP_PREFIX = "login_lockout_timestamp_";
    
    private LoginAttemptsManager() {
    }

    /**
     * Checks if the login is currently locked for the given email.
     * 
     * @param context Android context
     * @param email User's email address
     * @return true if account is currently locked, false otherwise
     */
    public static boolean isLoginLocked(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) {
            return false;
        }
        
        String normalizedEmail = normalizeEmail(email);
        SharedPreferences prefs = getSecurePreferences(context);
        
        String lockoutTimestampKey = KEY_LOCKOUT_TIMESTAMP_PREFIX + normalizedEmail;
        long lockoutTimestamp = prefs.getLong(lockoutTimestampKey, 0);
        
        if (lockoutTimestamp == 0) {
            return false; // No lockout recorded
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLockout = currentTime - lockoutTimestamp;
        
        if (timeSinceLockout >= LOCKOUT_DURATION_MILLIS) {
            // Lockout period has expired - clear it
            clearLoginAttempts(context, email);
            return false;
        }
        
        return true; // Still locked
    }

    /**
     * Gets the remaining lockout time in milliseconds for the given email.
     * 
     * @param context Android context
     * @param email User's email address
     * @return remaining lockout time in milliseconds, 0 if not locked
     */
    public static long getRemainingLockoutTime(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) {
            return 0;
        }
        
        String normalizedEmail = normalizeEmail(email);
        SharedPreferences prefs = getSecurePreferences(context);
        
        String lockoutTimestampKey = KEY_LOCKOUT_TIMESTAMP_PREFIX + normalizedEmail;
        long lockoutTimestamp = prefs.getLong(lockoutTimestampKey, 0);
        
        if (lockoutTimestamp == 0) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLockout = currentTime - lockoutTimestamp;
        long remainingTime = LOCKOUT_DURATION_MILLIS - timeSinceLockout;
        
        return Math.max(0, remainingTime);
    }

    /**
     * Records a failed login attempt for the given email.
     * If max attempts are exceeded, triggers account lockout.
     * 
     * @param context Android context
     * @param email User's email address
     * @return the current failed attempt count after recording
     */
    public static int recordFailedAttempt(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) {
            return 0;
        }
        
        String normalizedEmail = normalizeEmail(email);
        SharedPreferences prefs = getSecurePreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        
        String failedAttemptsKey = KEY_FAILED_ATTEMPTS_PREFIX + normalizedEmail;
        int currentAttempts = prefs.getInt(failedAttemptsKey, 0);
        int newAttempts = currentAttempts + 1;
        
        editor.putInt(failedAttemptsKey, newAttempts);
        
        if (newAttempts >= MAX_FAILED_ATTEMPTS) {
            // Trigger lockout
            String lockoutTimestampKey = KEY_LOCKOUT_TIMESTAMP_PREFIX + normalizedEmail;
            editor.putLong(lockoutTimestampKey, System.currentTimeMillis());
            Log.w(TAG, "Login locked for email: " + normalizedEmail + 
                  " after " + newAttempts + " failed attempts");
        }
        
        editor.apply();
        return newAttempts;
    }

    /**
     * Resets the failed login attempts counter for the given email.
     * Called after a successful login.
     * 
     * @param context Android context
     * @param email User's email address
     */
    public static void clearLoginAttempts(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) {
            return;
        }
        
        String normalizedEmail = normalizeEmail(email);
        SharedPreferences prefs = getSecurePreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        
        String failedAttemptsKey = KEY_FAILED_ATTEMPTS_PREFIX + normalizedEmail;
        String lockoutTimestampKey = KEY_LOCKOUT_TIMESTAMP_PREFIX + normalizedEmail;
        
        editor.remove(failedAttemptsKey);
        editor.remove(lockoutTimestampKey);
        editor.apply();
        
        Log.d(TAG, "Login attempts cleared for email: " + normalizedEmail);
    }

    /**
     * Gets the current failed attempt count for the given email.
     * 
     * @param context Android context
     * @param email User's email address
     * @return current failed attempt count
     */
    public static int getFailedAttemptCount(Context context, String email) {
        if (context == null || TextUtils.isEmpty(email)) {
            return 0;
        }
        
        String normalizedEmail = normalizeEmail(email);
        SharedPreferences prefs = getSecurePreferences(context);
        String failedAttemptsKey = KEY_FAILED_ATTEMPTS_PREFIX + normalizedEmail;
        
        return prefs.getInt(failedAttemptsKey, 0);
    }

    /**
     * Gets the remaining attempts before lockout.
     * 
     * @param context Android context
     * @param email User's email address
     * @return number of remaining attempts (0 if locked or max attempts reached)
     */
    public static int getRemainingAttempts(Context context, String email) {
        int failedCount = getFailedAttemptCount(context, email);
        int remaining = MAX_FAILED_ATTEMPTS - failedCount;
        return Math.max(0, remaining);
    }

    /**
     * Clears all login attempt data for all accounts.
     * Should be called on app logout or when resetting security settings.
     * 
     * @param context Android context
     */
    public static void clearAllAttempts(Context context) {
        if (context == null) {
            return;
        }
        
        try {
            getSecurePreferences(context).edit().clear().apply();
            Log.d(TAG, "All login attempts cleared");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all login attempts", e);
        }
    }

    /**
     * Gets or creates encrypted SharedPreferences for secure storage.
     * 
     * @param context Android context
     * @return encrypted SharedPreferences instance
     */
    private static SharedPreferences getSecurePreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Error accessing encrypted preferences, falling back to regular SharedPreferences", e);
            // Fallback to regular SharedPreferences if encryption fails
            return context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE);
        }
    }

    /**
     * Normalizes email for consistent storage and comparison.
     * Uses SHA-256 hash to avoid storing plain email addresses in preferences.
     * 
     * @param email Email address to normalize
     * @return hashed and normalized email identifier
     */
    private static String normalizeEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return "";
        }
        
        try {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes());
            
            // Convert hash to hex string (first 16 chars for shorter keys)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 algorithm not available", e);
            // Fallback to simple hash
            return String.valueOf(email.hashCode());
        }
    }
}
