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

public class VerifyCodeActivity extends AppCompatActivity {

    EditText codeEdit;
    Button btnVerify;
    String email;
    String URL_VERIFY_CODE = Constants.BASE_URL + "verify-code";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_code);

        codeEdit = findViewById(R.id.codeEdit);
        btnVerify = findViewById(R.id.btnVerify);

        email = getIntent().getStringExtra("email");

        btnVerify.setOnClickListener(v -> {
            String code = codeEdit.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter the verification code", Toast.LENGTH_SHORT).show();
                return;
            }
            verifyCode(email, code);
        });
    }

    private void verifyCode(String email, String code) {
        JSONObject postData = new JSONObject();
        try {
            postData.put("email", email);
            postData.put("code", code);
        } catch (JSONException e) {
            Log.e("VerifyCodeActivity", "Failed to create JSON object", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_VERIFY_CODE, postData,
                response -> {
                    String fullResponseForDebugging;
                    try {
                        fullResponseForDebugging = response.toString(4);
                    } catch (JSONException e) {
                        fullResponseForDebugging = response.toString();
                    }
                    Log.d("VerifyCodeActivity", "Full response from server: " + fullResponseForDebugging);

                    try {
                        boolean success = response.optBoolean("success", false);
                        String message = response.getString("message");

                        if (success) {
                            // FIX: Get reset_token from inside "data" object
                            JSONObject data = response.optJSONObject("data");
                            String resetToken = "";

                            if (data != null) {
                                resetToken = data.optString("reset_token", "");
                                if (resetToken.isEmpty()) {
                                    resetToken = data.optString("resetToken", "");
                                }
                            }

                            if (resetToken.isEmpty()) {
                                Log.e("VerifyCodeActivity", "Reset token not found in data object");
                                new androidx.appcompat.app.AlertDialog.Builder(VerifyCodeActivity.this)
                                        .setTitle("Backend Response Issue")
                                        .setMessage("Code verified, but reset token not found.\n\nServer Response:\n" + fullResponseForDebugging)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();
                            } else {
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                Log.d("VerifyCodeActivity", "Proceeding to ResetPasswordActivity with token: " + resetToken);

                                Intent intent = new Intent(this, ResetPasswordActivity.class);
                                intent.putExtra("email", email);
                                intent.putExtra("reset_token", resetToken);
                                startActivity(intent);
                                finish();
                            }
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Log.e("VerifyCodeActivity", "Failed to parse response: " + fullResponseForDebugging, e);
                        Toast.makeText(this, "An error occurred parsing server response.", Toast.LENGTH_LONG).show();
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
                            Log.e("VerifyCodeActivity", "Error parsing error response", e);
                            Toast.makeText(this, "Failed to verify code. Check logs for details.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("VerifyCodeActivity", "Volley Error", error);
                        Toast.makeText(this, "Failed to verify code. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}