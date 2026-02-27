package com.example.fooddash;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class RegisterActivity extends AppCompatActivity {

    EditText nameEdit, emailEdit, passwordEdit;
    Button btnRegister, btnLogin;
    String URL_REGISTER = Constants.BASE_URL + "register"; // Use the centralized URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameEdit = findViewById(R.id.nameEdit);
        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);

        btnRegister.setOnClickListener(v -> registerUser());

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String name = nameEdit.getText().toString().trim();
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject postData = new JSONObject();
        try {
            postData.put("name", name);
            postData.put("email", email);
            postData.put("password", password);
            postData.put("role", "customer");
        } catch (JSONException e) {
            Log.e("RegisterActivity", "Failed to create JSON object", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_REGISTER, postData,
                response -> {
                    boolean isSuccess = response.optBoolean("success", false) || "success".equals(response.optString("status"));
                    if (isSuccess) {
                        String apiToken = "";
                        JSONObject data = response.optJSONObject("data");
                        if (data != null) {
                            apiToken = data.optString("api_token");
                        }
                        if (apiToken.isEmpty()) {
                            apiToken = response.optString("api_token");
                        }

                        if (!apiToken.isEmpty()) {
                            Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();

                            SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
                            prefs.edit().putString("api_token", apiToken).apply();

                            Intent intent = new Intent(this, CustomerDashboard.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            Toast.makeText(this, "Registration successful. Please log in.", Toast.LENGTH_SHORT).show();
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("email", email);
                            resultIntent.putExtra("password", password);
                            setResult(Activity.RESULT_OK, resultIntent);
                            finish();
                        }
                    } else {
                        String message = response.optString("message", "Registration failed.");
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    String message = "Registration Failed. Please try again.";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("errors")) {
                                JSONObject errors = errorJson.getJSONObject("errors");
                                StringBuilder errorMessage = new StringBuilder();
                                java.util.Iterator<String> keys = errors.keys();
                                if (keys.hasNext()) {
                                    String key = keys.next();
                                    errorMessage.append(errors.getJSONArray(key).getString(0));
                                }
                                message = errorMessage.toString();
                            } else if (errorJson.has("message")) {
                                message = errorJson.getString("message");
                            }
                        } catch (JSONException e) {
                            Log.e("RegisterActivity", "Error parsing error JSON: " + responseBody, e);
                        }
                    }
                    Log.e("RegisterActivity", "Registration Volley Error", error);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        );

        Volley.newRequestQueue(this).add(request);
    }
}
