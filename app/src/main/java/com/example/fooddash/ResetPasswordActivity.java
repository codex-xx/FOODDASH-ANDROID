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

public class ResetPasswordActivity extends AppCompatActivity {

    EditText passwordEdit, confirmPasswordEdit;
    Button btnResetPassword;
    String email;
    String resetToken;
    ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        passwordEdit = findViewById(R.id.passwordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        btnResetPassword = findViewById(R.id.btnResetPassword);

        apiService = RetrofitClient.getApiService();
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
        Map<String, String> fields = new HashMap<>();
        fields.put("email", email);
        fields.put("password", password);
        fields.put("reset_token", resetToken);

        Log.d("ResetPasswordActivity", "Sending reset password request for: " + email);

        apiService.resetPassword(fields).enqueue(new Callback<ResponseBody>() {
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
                        jsonResponse.put("message", responseBody.isEmpty() ? (response.isSuccessful() ? "Success" : "Failed") : responseBody);
                    }

                    String message = jsonResponse.optString("message", "Request completed.");
                    Toast.makeText(ResetPasswordActivity.this, message, Toast.LENGTH_LONG).show();

                    if (response.isSuccessful()) {
                        Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                } catch (Exception e) {
                    Log.e("ResetPasswordActivity", "Failed to parse response", e);
                    Toast.makeText(ResetPasswordActivity.this, "An error occurred.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ResetPasswordActivity", "Retrofit Error", t);
                Toast.makeText(ResetPasswordActivity.this, "Failed to reset password. Check network connection.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
