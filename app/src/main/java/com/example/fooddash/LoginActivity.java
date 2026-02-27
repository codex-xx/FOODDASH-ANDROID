package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    EditText emailEdit, passwordEdit;
    Button btnLogin, btnGoRegister;
    String URL_LOGIN = Constants.BASE_URL + "login"; // Use the centralized URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);

        Intent intent = getIntent();
        if (intent != null) {
            String registeredEmail = intent.getStringExtra("email");
            String registeredPassword = intent.getStringExtra("password");
            if (registeredEmail != null) {
                emailEdit.setText(registeredEmail);
            }
            if (registeredPassword != null) {
                passwordEdit.setText(registeredPassword);
            }
        }

        btnLogin.setOnClickListener(v -> loginUser(emailEdit.getText().toString(), passwordEdit.getText().toString()));
        btnGoRegister.setOnClickListener(v -> {
            Intent registerIntent = new Intent(this, RegisterActivity.class);
            startActivity(registerIntent);
        });
    }

    private void loginUser(String email, String password) {
        email = email.trim();
        password = password.trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

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
                        String role = normalizeValue(findFirstStringForKeys(response, "role", "user_role"));
                        String status = normalizeValue(findFirstStringForKeys(response, "status", "account_status"));

                        if ("driver".equals(role)) {
                            if ("pending".equals(status)) {
                                Toast.makeText(this, "Account awaiting approval", Toast.LENGTH_LONG).show();
                                return;
                            }
                            if ("rejected".equals(status)) {
                                String message = response.optString("message", "Your driver account was rejected.");
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (!"approved".equals(status)) {
                                Toast.makeText(this, "Driver account is not approved yet.", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }

                        JSONObject data = response.optJSONObject("data");
                        JSONObject user = data != null ? data.optJSONObject("user") : null;

                        String apiToken = "";
                        if (data != null) {
                            apiToken = data.optString("token");
                            if (apiToken.isEmpty()) {
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

                        Intent intent;
                        if ("driver".equals(role) && "approved".equals(status)) {
                            intent = new Intent(this, DriverDashboard.class);
                        } else {
                            intent = new Intent(this, CustomerDashboard.class);
                        }
                        startActivity(intent);
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

    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String findFirstStringForKeys(JSONObject source, String... keys) {
        if (source == null || keys == null || keys.length == 0) {
            return "";
        }

        for (String key : keys) {
            String direct = source.optString(key, "");
            if (direct != null && !direct.trim().isEmpty()) {
                return direct;
            }
        }

        JSONArray names = source.names();
        if (names == null) {
            return "";
        }

        for (int index = 0; index < names.length(); index++) {
            String childKey = names.optString(index, "");
            Object childValue = source.opt(childKey);

            if (childValue instanceof JSONObject) {
                String nestedValue = findFirstStringForKeys((JSONObject) childValue, keys);
                if (!nestedValue.isEmpty()) {
                    return nestedValue;
                }
            } else if (childValue instanceof JSONArray) {
                String arrayValue = findFirstStringInArray((JSONArray) childValue, keys);
                if (!arrayValue.isEmpty()) {
                    return arrayValue;
                }
            }
        }

        return "";
    }

    private String findFirstStringInArray(JSONArray array, String... keys) {
        if (array == null) {
            return "";
        }

        for (int index = 0; index < array.length(); index++) {
            Object item = array.opt(index);
            if (item instanceof JSONObject) {
                String nestedValue = findFirstStringForKeys((JSONObject) item, keys);
                if (!nestedValue.isEmpty()) {
                    return nestedValue;
                }
            } else if (item instanceof JSONArray) {
                String nestedArrayValue = findFirstStringInArray((JSONArray) item, keys);
                if (!nestedArrayValue.isEmpty()) {
                    return nestedArrayValue;
                }
            }
        }

        return "";
    }
}
