package com.example.fooddash;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    EditText emailEdit, passwordEdit;
    Button btnLogin, btnGoRegister;
    String URL_LOGIN = "http://192.168.1.10/FoodDash/public/api/login"; // Replace with your IP

    private ActivityResultLauncher<Intent> registerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        registerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            String email = data.getStringExtra("email");
                            String password = data.getStringExtra("password");
                            emailEdit.setText(email);
                            passwordEdit.setText(password);
                            loginUser(email, password);
                        }
                    }
                }
        );

        btnLogin.setOnClickListener(v -> loginUser(emailEdit.getText().toString(), passwordEdit.getText().toString()));
        btnGoRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, RegisterActivity.class);
            registerLauncher.launch(intent);
        });
    }

    private void loginUser(String email, String password) {
        JSONObject postData = new JSONObject();
        try {
            postData.put("email", email);
            postData.put("password", password);
        } catch (JSONException e) {
            Log.e("LoginActivity", "Failed to create JSON object", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_LOGIN, postData,
                response -> {
                    // The request was successful (HTTP 2xx), so we are in the success block.
                    try {
                        // Check if the role is explicitly defined and is NOT 'customer'.
                        if (response.has("role") && !response.getString("role").equals("customer")) {
                            Log.e("LoginActivity", "Login attempt by non-customer role: " + response.getString("role"));
                            Toast.makeText(this, "This application is for customers only.", Toast.LENGTH_SHORT).show();
                        } else {
                            // Otherwise, assume a successful customer login.
                            // This handles cases where the role is 'customer' or is not specified in the success response.
                            startActivity(new Intent(this, CustomerDashboard.class));
                            finish();
                        }
                    } catch (JSONException e) {
                        // If we are here, the success response was not a valid JSON or 'role' key was not a string.
                        // It's safest to let the user in as a customer.
                        Log.e("LoginActivity", "Error processing JSON on success", e);
                        startActivity(new Intent(this, CustomerDashboard.class));
                        finish();
                    }
                },
                error -> {
                    // The request failed (HTTP 4xx or 5xx)
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject data = new JSONObject(responseBody);
                            // Display the specific error message from the server, e.g., "Invalid credentials"
                            String message = data.optString("message", "An unknown error occurred.");
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e("LoginActivity", "Error parsing error response", e);
                            Toast.makeText(this, "Login Failed. Check logs for details.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // This handles cases like no network connection.
                        Log.e("LoginActivity", "Login Volley Error", error);
                        Toast.makeText(this, "Login Failed. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }
}
