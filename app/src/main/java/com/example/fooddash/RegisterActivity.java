package com.example.fooddash;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class RegisterActivity extends AppCompatActivity {

    private static final String NAME_REGEX = "^[A-Za-z ]+$";
    private static final String CONTACT_REGEX = "^\\d+$";
    private static final String ADDRESS_REGEX = "^[A-Za-z0-9 .,#/\\-]+$";
    private static final String LICENSE_REGEX = "^[A-Za-z0-9 -]+$";

    EditText nameEdit, contactEdit, addressEdit, emailEdit, passwordEdit, confirmPasswordEdit;
    EditText licenseNumberEdit;
    RadioGroup vehicleTypeGroup;
    CheckBox termsAgreementCheckbox;
    TextView viewTermsText;
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
        vehicleTypeGroup = findViewById(R.id.vehicleTypeGroup);
        termsAgreementCheckbox = findViewById(R.id.termsAgreementCheckbox);
        viewTermsText = findViewById(R.id.viewTermsText);
        emailEdit = findViewById(R.id.emailEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);

        viewTermsText.setOnClickListener(v -> showTermsDialog());

        roleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isDriver = checkedId == R.id.rbDriver;
            driverFieldsContainer.setVisibility(isDriver ? View.VISIBLE : View.GONE);
            addressEdit.setVisibility(isDriver ? View.GONE : View.VISIBLE);

            if (!isDriver) {
                licenseNumberEdit.setText("");
                vehicleTypeGroup.clearCheck();
                termsAgreementCheckbox.setChecked(false);
            }
        });

        btnRegister.setOnClickListener(v -> registerUser());

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void showTermsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.terms_dialog_title)
                .setMessage(R.string.terms_dialog_content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
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
        int selectedVehicleId = vehicleTypeGroup.getCheckedRadioButtonId();
        String vehicleType = "";
        if (selectedVehicleId != -1) {
            RadioButton selectedVehicleButton = findViewById(selectedVehicleId);
            if (selectedVehicleButton != null) {
                vehicleType = selectedVehicleButton.getText().toString().trim();
            }
        }
        boolean hasAcceptedTerms = termsAgreementCheckbox.isChecked();
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

        if (isDriver && (licenseNumber.isEmpty() || vehicleType.isEmpty())) {
            Toast.makeText(this, "Please fill all driver fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!name.matches(NAME_REGEX)) {
            Toast.makeText(this, "Name must contain letters and spaces only", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!contactNumber.matches(CONTACT_REGEX)) {
            Toast.makeText(this, "Contact number must contain digits only", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isDriver && !deliveryAddress.matches(ADDRESS_REGEX)) {
            Toast.makeText(this, "Delivery address contains invalid special characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDriver && !licenseNumber.matches(LICENSE_REGEX)) {
            Toast.makeText(this, "License number contains invalid special characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDriver && !hasAcceptedTerms) {
            Toast.makeText(this, "Please accept the Terms of Agreement", Toast.LENGTH_SHORT).show();
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
            postData.put("email", email);
            postData.put("password", password);
            postData.put("password_confirmation", confirmPassword);
            postData.put("role", role);
            postData.put("status", "customer".equals(role) ? "active" : "pending");
            postData.put("delivery_address", isDriver ? "" : deliveryAddress);
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
                        if ("customer".equals(role)) {
                            EmailNotificationService.sendCustomerRegistrationSuccess(getApplicationContext(), email, name);
                        } else {
                            EmailNotificationService.sendDriverApplicationReceived(getApplicationContext(), email, name);
                        }

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
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    org.json.JSONArray errorArray = errors.getJSONArray(key);
                                    for (int i = 0; i < errorArray.length(); i++) {
                                        errorMessage.append(errorArray.getString(i)).append("\n");
                                    }
                                }
                                if (errorMessage.length() > 0) {
                                    message = errorMessage.substring(0, errorMessage.length() - 1);
                                } else {
                                    message = "An unknown error occurred.";
                                }
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

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }
}
