package com.example.fooddash;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONException;
import org.json.JSONObject;

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

        Map<String, String> fields = new HashMap<>();
        fields.put("event", event);
        fields.put("email", normalizedEmail);
        fields.put("name", name == null ? "" : name.trim());
        fields.put("role", role);

        ApiService apiService = RetrofitClient.getApiService();
        apiService.sendNotificationEmail(fields).enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    JSONObject jsonResponse = new JSONObject(body);
                    if (isSuccess(jsonResponse)) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } else {
                        Log.w(TAG, "Notification endpoint returned non-success response: " + body);
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed to send email notification", t);
            }
        });
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