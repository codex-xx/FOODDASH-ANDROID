package com.example.fooddash;

import android.app.Activity;
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

public class RegisterActivity extends AppCompatActivity {

    EditText nameEdit, emailEdit, passwordEdit;
    Button btnRegister;
    String URL_REGISTER = "http://192.168.1.10/FoodDash/public/api/register"; // Replace with your IP

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameEdit = findViewById(R.id.nameEdit);
        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> registerUser());
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
                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("email", emailEdit.getText().toString());
                            resultIntent.putExtra("password", passwordEdit.getText().toString());
                            setResult(Activity.RESULT_OK, resultIntent);
                            finish();
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
                    if (error.networkResponse != null) {
                        Log.e("RegisterActivity", "Registration Error Response: " + new String(error.networkResponse.data));
                    }
                    Log.e("RegisterActivity", "Registration Volley Error", error);
                    Toast.makeText(this, "Registration Failed. Check logs for details.", Toast.LENGTH_LONG).show();
                }
        );

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }
}
