package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONException;
import org.json.JSONObject;

public class ForgotPasswordActivity extends AppCompatActivity {

    EditText emailEdit;
    Button btnResetPassword, btnTestConnection;
    ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        emailEdit = findViewById(R.id.emailEdit);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        btnTestConnection = findViewById(R.id.btnTestConnection);
        apiService = RetrofitClient.getApiService();

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
        apiService.testConnection().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    Log.d("ForgotPasswordActivity", "Test connection success: " + body);
                    new androidx.appcompat.app.AlertDialog.Builder(ForgotPasswordActivity.this)
                        .setTitle("Connection Successful")
                        .setMessage("Successfully connected to the backend.\n\nServer Response:\n" + body)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ForgotPasswordActivity", "Test connection failed", t);
                new androidx.appcompat.app.AlertDialog.Builder(ForgotPasswordActivity.this)
                    .setTitle("Connection Failed")
                    .setMessage("Could not connect to the backend. Please check your network and IP address.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            }
        });
    }

    private void sendPasswordResetLink(String email) {
        Map<String, String> fields = new HashMap<>();
        fields.put("email", email);

        apiService.forgotPassword(fields).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String responseBody = response.body() != null ? response.body().string() : 
                                         (response.errorBody() != null ? response.errorBody().string() : "");
                    responseBody = responseBody.trim();

                    JSONObject jsonResponse;
                    if (responseBody.startsWith("{")) {
                        jsonResponse = new JSONObject(responseBody);
                    } else {
                        jsonResponse = new JSONObject();
                        jsonResponse.put("success", response.isSuccessful());
                        jsonResponse.put("message", responseBody.isEmpty() ? (response.isSuccessful() ? "Request sent." : "Request failed.") : responseBody);
                    }

                    String message = jsonResponse.optString("message", "Request sent.");
                    Toast.makeText(ForgotPasswordActivity.this, message, Toast.LENGTH_LONG).show();
                    
                    if (response.isSuccessful()) {
                        Intent intent = new Intent(ForgotPasswordActivity.this, VerifyCodeActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                        finish();
                    }
                } catch (Exception e) {
                    Log.e("ForgotPasswordActivity", "Failed to parse response", e);
                    Toast.makeText(ForgotPasswordActivity.this, "An error occurred.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ForgotPasswordActivity", "Retrofit Error", t);
                Toast.makeText(ForgotPasswordActivity.this, "Failed to send reset link. Check network connection.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
