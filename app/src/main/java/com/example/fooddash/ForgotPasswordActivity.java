package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class ForgotPasswordActivity extends AppCompatActivity {

    EditText emailEdit;
    Button btnResetPassword, btnTestConnection;
    String URL_FORGOT_PASSWORD = Constants.BASE_URL + "forgot-password";
    String URL_TEST_CONNECTION = Constants.BASE_URL + "test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        emailEdit = findViewById(R.id.emailEdit);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        btnTestConnection = findViewById(R.id.btnTestConnection);

        btnResetPassword.setOnClickListener(v -> {
            String email = emailEdit.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }
            sendPasswordResetLink(email);
        });

        btnTestConnection.setOnClickListener(v -> testConnection());
    }

    private void testConnection() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, URL_TEST_CONNECTION, null,
                response -> {
                    Log.d("ForgotPasswordActivity", "Test connection success: " + response.toString());
                    new androidx.appcompat.app.AlertDialog.Builder(ForgotPasswordActivity.this)
                        .setTitle("Connection Successful")
                        .setMessage("Successfully connected to the backend.\n\nServer Response:\n" + response.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                },
                error -> {
                    Log.e("ForgotPasswordActivity", "Test connection failed", error);
                    new androidx.appcompat.app.AlertDialog.Builder(ForgotPasswordActivity.this)
                        .setTitle("Connection Failed")
                        .setMessage("Could not connect to the backend. Please check your network and IP address.")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
        );

        Volley.newRequestQueue(this).add(request);
    }

    private void sendPasswordResetLink(String email) {
        JSONObject postData = new JSONObject();
        try {
            postData.put("email", email);
        } catch (JSONException e) {
            Log.e("ForgotPasswordActivity", "Failed to create JSON object", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_FORGOT_PASSWORD, postData,
                response -> {
                    try {
                        String message = response.getString("message");
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(this, VerifyCodeActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                        finish();
                    } catch (JSONException e) {
                        Log.e("ForgotPasswordActivity", "Failed to parse success response", e);
                        Toast.makeText(this, "An error occurred.", Toast.LENGTH_LONG).show();
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
                            Log.e("ForgotPasswordActivity", "Error parsing error response", e);
                            Toast.makeText(this, "Failed to send reset link. Check logs for details.", Toast.LENGTH_LONG).show();
                        }                    } else {
                        Log.e("ForgotPasswordActivity", "Volley Error", error);
                        Toast.makeText(this, "Failed to send reset link. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}
