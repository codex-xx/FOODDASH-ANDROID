package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class RegisterActivity extends AppCompatActivity {

    EditText nameEdit, contactEdit, addressEdit, emailEdit, passwordEdit, confirmPasswordEdit;
    EditText licenseNumberEdit;
    Spinner vehicleTypeSpinner;
    LinearLayout driverFieldsContainer;
    RadioGroup roleGroup;
    Button btnRegister, btnLogin;
    String URL_REGISTER = Constants.BASE_URL + "register"; // Use the centralized URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        roleGroup = findViewById(R.id.roleGroup);
        nameEdit = findViewById(R.id.nameEdit);
        contactEdit = findViewById(R.id.contactEdit);
        addressEdit = findViewById(R.id.addressEdit);
        driverFieldsContainer = findViewById(R.id.driverFieldsContainer);
        licenseNumberEdit = findViewById(R.id.licenseNumberEdit);
        vehicleTypeSpinner = findViewById(R.id.vehicleTypeSpinner);
        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);

        roleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isDriver = checkedId == R.id.rbDriver;
            driverFieldsContainer.setVisibility(isDriver ? View.VISIBLE : View.GONE);
            addressEdit.setVisibility(isDriver ? View.GONE : View.VISIBLE);

            if (!isDriver) {
                licenseNumberEdit.setText("");
                vehicleTypeSpinner.setSelection(0);
            }
        });

        btnRegister.setOnClickListener(v -> registerUser());

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        int selectedRoleId = roleGroup.getCheckedRadioButtonId();
        String role = selectedRoleId == R.id.rbDriver ? "driver" : (selectedRoleId == R.id.rbCustomer ? "customer" : "");

        String name = nameEdit.getText().toString().trim();
        String contactNumber = contactEdit.getText().toString().trim();
        String deliveryAddress = addressEdit.getText().toString().trim();
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();
        String confirmPassword = confirmPasswordEdit.getText().toString().trim();
        String licenseNumber = licenseNumberEdit.getText().toString().trim();
        String vehicleType = vehicleTypeSpinner.getSelectedItem() != null
                ? vehicleTypeSpinner.getSelectedItem().toString().trim()
                : "";
        boolean isDriver = "driver".equals(role);

        if (role.isEmpty() || name.isEmpty() || contactNumber.isEmpty()
                || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isDriver && deliveryAddress.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDriver && (licenseNumber.isEmpty() || vehicleType.isEmpty() || "Select Vehicle Type".equalsIgnoreCase(vehicleType))) {
            Toast.makeText(this, "Please fill all driver fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject postData = new JSONObject();
        try {
            postData.put("name", name);
            postData.put("contact_number", contactNumber);
            postData.put("delivery_address", deliveryAddress);
            postData.put("email", email);
            postData.put("password", password);
            postData.put("password_confirmation", confirmPassword);
            postData.put("role", role);
            postData.put("status", "customer".equals(role) ? "active" : "pending");
            if (!isDriver) {
                postData.put("delivery_address", deliveryAddress);
            }
            if (isDriver) {
                postData.put("license_number", licenseNumber);
                postData.put("vehicle_type", vehicleType);
            }
        } catch (JSONException e) {
            Log.e("RegisterActivity", "Failed to create JSON object", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, URL_REGISTER, postData,
                response -> {
                    boolean isSuccess = response.optBoolean("success", false) || "success".equals(response.optString("status"));
                    if (isSuccess) {
                        String successMessage = "customer".equals(role)
                                ? "Registration successful. Please log in."
                                : "Driver account created and awaiting admin approval.";
                        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();

                        Intent loginIntent = new Intent(this, LoginActivity.class);
                        loginIntent.putExtra("email", email);
                        if ("customer".equals(role)) {
                            loginIntent.putExtra("password", password);
                        }
                        loginIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(loginIntent);
                        finish();
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
