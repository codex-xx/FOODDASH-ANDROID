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

public class VerifyCodeActivity extends AppCompatActivity {

    EditText codeEdit;
    Button btnVerify;
    String email;
    ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_code);

        codeEdit = findViewById(R.id.codeEdit);
        btnVerify = findViewById(R.id.btnVerify);

        apiService = RetrofitClient.getApiService();
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
        Map<String, String> fields = new HashMap<>();
        fields.put("email", email);
        fields.put("code", code);

        apiService.verifyCode(fields).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String responseBody = response.body() != null ? response.body().string() : 
                                         (response.errorBody() != null ? response.errorBody().string() : "");
                    responseBody = responseBody.trim();
                    Log.d("VerifyCodeActivity", "Full response from server: " + responseBody);

                    JSONObject jsonResponse;
                    if (responseBody.startsWith("{")) {
                        jsonResponse = new JSONObject(responseBody);
                    } else {
                        jsonResponse = new JSONObject();
                        jsonResponse.put("success", response.isSuccessful());
                        jsonResponse.put("message", responseBody.isEmpty() ? (response.isSuccessful() ? "Success" : "Failed") : responseBody);
                    }

                    boolean success = response.isSuccessful() && jsonResponse.optBoolean("success", false);
                    String message = jsonResponse.optString("message", "Request completed.");

                    if (success) {
                        JSONObject data = jsonResponse.optJSONObject("data");
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
                                    .setMessage("Code verified, but reset token not found.\n\nServer Response:\n" + responseBody)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        } else {
                            Toast.makeText(VerifyCodeActivity.this, message, Toast.LENGTH_LONG).show();
                            Log.d("VerifyCodeActivity", "Proceeding to ResetPasswordActivity with token: " + resetToken);

                            Intent intent = new Intent(VerifyCodeActivity.this, ResetPasswordActivity.class);
                            intent.putExtra("email", email);
                            intent.putExtra("reset_token", resetToken);
                            startActivity(intent);
                            finish();
                        }
                    } else {
                        Toast.makeText(VerifyCodeActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Log.e("VerifyCodeActivity", "Failed to parse response", e);
                    Toast.makeText(VerifyCodeActivity.this, "An error occurred parsing server response.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("VerifyCodeActivity", "Retrofit Error", t);
                Toast.makeText(VerifyCodeActivity.this, "Failed to verify code. Check network connection.", Toast.LENGTH_LONG).show();
            }
        });
    }
}