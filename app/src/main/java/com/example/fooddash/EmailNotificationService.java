package com.example.fooddash;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class EmailNotificationService {

    private static final String TAG = "EmailNotificationService";
    private static final String EVENT_DRIVER_APPLICATION_RECEIVED = "driver_application_received";
    private static final String EVENT_DRIVER_APPLICATION_APPROVED = "driver_application_approved";
    private static final String EVENT_CUSTOMER_REGISTRATION_SUCCESS = "customer_registration_success";

    interface Callback {
        void onSuccess();
    }

    private EmailNotificationService() {
    }

    static boolean isGmailAddress(String email) {
        if (email == null) {
            return false;
        }
        return email.trim().toLowerCase(Locale.ROOT).endsWith("@gmail.com");
    }

    static void sendDriverApplicationReceived(Context context, String email, String name) {
        if (!isGmailAddress(email)) {
            Log.i(TAG, "Skipped driver email notification because email is not Gmail: " + email);
            return;
        }
        sendNotification(context, EVENT_DRIVER_APPLICATION_RECEIVED, email, name, "driver", null);
    }

    static void sendDriverApplicationApproved(Context context, String email, String name, Callback callback) {
        if (!isGmailAddress(email)) {
            Log.i(TAG, "Skipped driver approval email notification because email is not Gmail: " + email);
            return;
        }
        sendNotification(context, EVENT_DRIVER_APPLICATION_APPROVED, email, name, "driver", callback);
    }

    static void sendCustomerRegistrationSuccess(Context context, String email, String name) {
        sendNotification(context, EVENT_CUSTOMER_REGISTRATION_SUCCESS, email, name, "customer", null);
    }

    private static void sendNotification(
            Context context,
            String event,
            String email,
            String name,
            String role,
            Callback callback
    ) {
        String normalizedEmail = email == null ? "" : email.trim();
        if (normalizedEmail.isEmpty()) {
            Log.w(TAG, "Skipped notification because email is empty for event: " + event);
            return;
        }

        JSONObject postData = new JSONObject();
        try {
            postData.put("event", event);
            postData.put("email", normalizedEmail);
            postData.put("name", name == null ? "" : name.trim());
            postData.put("role", role);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build notification payload", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, Constants.URL_SEND_NOTIFICATION_EMAIL, postData,
                response -> {
                    if (isSuccess(response)) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } else {
                        Log.w(TAG, "Notification endpoint returned non-success response: " + response);
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        Log.e(TAG, "Failed to send email notification: " + responseBody, error);
                    } else {
                        Log.e(TAG, "Failed to send email notification", error);
                    }
                }
        );

        request.setRetryPolicy(new DefaultRetryPolicy(
                10000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(context.getApplicationContext()).add(request);
    }

    private static boolean isSuccess(JSONObject response) {
        if (response == null) {
            return false;
        }

        if (response.optBoolean("success", false)) {
            return true;
        }

        String status = response.optString("status", "").trim().toLowerCase(Locale.ROOT);
        return "success".equals(status) || "ok".equals(status);
    }
}