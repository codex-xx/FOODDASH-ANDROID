package com.example.fooddash;

import android.content.Intent;
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
    // IMPORTANT: If you change your Wi-Fi network, you need to update this IP address.
    // Find the IP address of the computer running your server and replace it here.
    String URL_REGISTER = "http://192.168.1.10/FoodDash/public/api/register"; // Replace with your IP

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
        JSONObject postData = new JSONObject();
        try {
            postData.put("name", nameEdit.getText().toString());
            postData.put("email", emailEdit.getText().toString());
            postData.put("password", passwordEdit.getText().toString());
            postData.put("role", "customer");
        } catch (JSONException e) {
            Log.e("RegisterActivity", "Failed to create JSON object", e);
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_REGISTER, postData,
                response -> {
                    try {
                        if (response.has("status") && response.getString("status").equals("success")) {
                            Toast.makeText(this, "Registration Successful. Please login.", Toast.LENGTH_LONG).show();
                            // Clear input fields
                            nameEdit.setText("");
                            emailEdit.setText("");
                            passwordEdit.setText("");
                        } else {
                            String message = "Registration failed.";
                            if (response.has("message")) {
                                message = response.getString("message");
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Log.e("RegisterActivity", "JSON Error", e);
                        Toast.makeText(this, "JSON Error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    String message = "Registration Failed. Check logs for details.";
                    if (error.networkResponse != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("errors")) {
                                JSONObject errors = errorJson.getJSONObject("errors");
                                StringBuilder errorMessage = new StringBuilder();
                                java.util.Iterator<String> keys = errors.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    Object errorValue = errors.get(key);
                                    if (errorValue instanceof org.json.JSONArray) {
                                        errorMessage.append(((org.json.JSONArray) errorValue).getString(0)).append("\n");
                                    } else {
                                        errorMessage.append(errorValue.toString()).append("\n");
                                    }
                                }
                                message = errorMessage.toString().trim();
                            } else if (errorJson.has("message")) {
                                message = errorJson.getString("message");
                            }
                        } catch (JSONException e) {
                            Log.e("RegisterActivity", "Error parsing error JSON from response: " + responseBody, e);
                        }
                    }
                    Log.e("RegisterActivity", "Registration Volley Error", error);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        );

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }
}
