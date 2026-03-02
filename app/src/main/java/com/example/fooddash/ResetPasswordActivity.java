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

public class ResetPasswordActivity extends AppCompatActivity {

    EditText passwordEdit, confirmPasswordEdit;
    Button btnResetPassword;
    String email;
    String resetToken;
    String URL_RESET_PASSWORD = Constants.BASE_URL + "reset-password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        passwordEdit = findViewById(R.id.passwordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        btnResetPassword = findViewById(R.id.btnResetPassword);

        email = getIntent().getStringExtra("email");
        resetToken = getIntent().getStringExtra("reset_token");
        Log.d("ResetPasswordActivity", "Received email: " + email + ", resetToken: " + resetToken);

        btnResetPassword.setOnClickListener(v -> {
            String password = passwordEdit.getText().toString().trim();
            String confirmPassword = confirmPasswordEdit.getText().toString().trim();

            if (password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please enter and confirm your new password", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            resetPassword(email, password, resetToken);
        });
    }

    private void resetPassword(String email, String password, String resetToken) {
        JSONObject postData = new JSONObject();
        try {
            postData.put("email", email);
            postData.put("password", password);
            postData.put("reset_token", resetToken);
        } catch (JSONException e) {
            Log.e("ResetPasswordActivity", "Failed to create JSON object", e);
        }

        Log.d("ResetPasswordActivity", "Sending reset password request with data: " + postData.toString());

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_RESET_PASSWORD, postData,
                response -> {
                    try {
                        String message = response.getString("message");
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    } catch (JSONException e) {
                        Log.e("ResetPasswordActivity", "Failed to parse success response", e);
                        Toast.makeText(this, "An error occurred.", Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);
                            String message = data.optString("message", "An unknown error occurred.");
                            Log.e("ResetPasswordActivity", "Reset password failed: " + message);
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e("ResetPasswordActivity", "Error parsing error response", e);
                            Toast.makeText(this, "Failed to reset password. Check logs for details.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("ResetPasswordActivity", "Volley Error", error);
                        Toast.makeText(this, "Failed to reset password. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}
