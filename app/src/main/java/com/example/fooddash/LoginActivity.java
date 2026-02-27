package com.example.fooddash;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
    String URL_LOGIN = Constants.BASE_URL + "login"; // Use the centralized URL

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
                    try {
                        String apiToken = "";

                        JSONObject data = response.optJSONObject("data");
                        if (data != null) {
                            apiToken = data.optString("token");
                            if (apiToken.isEmpty()) {
                                JSONObject user = data.optJSONObject("user");
                                if (user != null) {
                                    apiToken = user.optString("api_token");
                                }
                            }
                        }

                        if (apiToken.isEmpty()) {
                            Log.e("LoginActivity", "API token not found in response: " + response.toString());
                            Toast.makeText(this, "Login failed: Could not retrieve API token.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
                        prefs.edit().putString("api_token", apiToken).apply();

                        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, CustomerDashboard.class));
                        finish();

                    } catch (Exception e) {
                        Log.e("LoginActivity", "Failed to parse login success response", e);
                        Toast.makeText(this, "An error occurred after login.", Toast.LENGTH_LONG).show();
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
                            Log.e("LoginActivity", "Error parsing error response", e);
                            Toast.makeText(this, "Login Failed. Check logs for details.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Log.e("LoginActivity", "Login Volley Error", error);
                        Toast.makeText(this, "Login Failed. Check network connection.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}
