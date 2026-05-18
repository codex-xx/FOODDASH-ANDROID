package com.example.fooddash;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class RegisterActivity extends AppCompatActivity {

    private static final String NAME_REGEX = "^[A-Za-z ]+$";
    private static final String CONTACT_REGEX = "^\\d+$";
    private static final String LICENSE_REGEX = "^[A-Za-z0-9 -]+$";

    EditText nameEdit, contactEdit, addressEdit, emailEdit, otpEdit, passwordEdit, confirmPasswordEdit;
    EditText licenseNumberEdit;
    Spinner vehicleTypeSpinner;
    CheckBox termsAgreementCheckbox;
    TextView viewTermsText;
    LinearLayout driverFieldsContainer;
    RadioGroup roleGroup;
    Button btnRegister, btnLogin, btnSendOtp;
    String URL_REGISTER = Constants.URL_REGISTER;
    String URL_REGISTER_LEGACY = Constants.BASE_URL + "register.php";

    private boolean otpSent = false;
    private boolean otpVerified = false;
    private String otpTargetEmail = "";
    private String otpChallengeToken = "";
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String PREF_LAST_REGISTERED_EMAIL = "last_registered_email";
    private static final String PREF_LAST_REGISTERED_ROLE = "last_registered_role";

    private LinearLayout customerLocationContainer;
    private TextView customerLocationSummaryText;
    private TextView riderLocationSummaryText;
    private Button btnOpenLocationPicker;
    private ActivityResultLauncher<Intent> customerLocationPickerLauncher;

    private String selectedCustomerAddress = "";
    private Double selectedCustomerLatitude = null;
    private Double selectedCustomerLongitude = null;
    private String selectedDriverAddress = "";
    private Double selectedDriverLatitude = null;
    private Double selectedDriverLongitude = null;
    private boolean driverLocationLoading = false;
    private String pendingLocationRole = "customer";

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
        termsAgreementCheckbox = findViewById(R.id.termsAgreementCheckbox);
        viewTermsText = findViewById(R.id.viewTermsText);
        customerLocationContainer = findViewById(R.id.customerLocationContainer);
        customerLocationSummaryText = findViewById(R.id.customerLocationSummaryText);
        riderLocationSummaryText = findViewById(R.id.riderLocationSummaryText);
        btnOpenLocationPicker = findViewById(R.id.btnOpenLocationPicker);
        emailEdit = findViewById(R.id.emailEdit);
        otpEdit = findViewById(R.id.otpEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogin = findViewById(R.id.btnLogin);
        btnSendOtp = findViewById(R.id.btnSendOtp);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.vehicle_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleTypeSpinner.setAdapter(adapter);

        customerLocationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }

                    Intent data = result.getData();
                    String pickedAddress = data.getStringExtra(CustomerLocationPickerActivity.EXTRA_ADDRESS);
                    if (pickedAddress == null) {
                        pickedAddress = "";
                    }
                    Double pickedLatitude = getDoubleExtra(data, CustomerLocationPickerActivity.EXTRA_LATITUDE);
                    Double pickedLongitude = getDoubleExtra(data, CustomerLocationPickerActivity.EXTRA_LONGITUDE);

                    if ("driver".equals(pendingLocationRole)) {
                        selectedDriverAddress = pickedAddress;
                        selectedDriverLatitude = pickedLatitude;
                        selectedDriverLongitude = pickedLongitude;
                        refreshRiderLocationSummary();
                    } else {
                        selectedCustomerAddress = pickedAddress;
                        selectedCustomerLatitude = pickedLatitude;
                        selectedCustomerLongitude = pickedLongitude;
                        refreshCustomerLocationUi();
                    }
                }
        );

        viewTermsText.setOnClickListener(v -> showTermsDialog());

        btnOpenLocationPicker.setOnClickListener(v -> openCustomerLocationPicker());

        roleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isDriver = checkedId == R.id.rbDriver;
            driverFieldsContainer.setVisibility(isDriver ? View.VISIBLE : View.GONE);
            addressEdit.setVisibility(isDriver ? View.GONE : View.VISIBLE);
            customerLocationContainer.setVisibility(View.VISIBLE);
            riderLocationSummaryText.setVisibility(isDriver ? View.VISIBLE : View.GONE);
            btnOpenLocationPicker.setText(R.string.open_map_picker);

            if (!isDriver) {
                licenseNumberEdit.setText("");
                vehicleTypeSpinner.setSelection(0);
                termsAgreementCheckbox.setChecked(false);
                updateCustomerLocationSummary();
            } else {
                ensureDriverLocation();
                refreshRiderLocationSummary();
            }
        });

        btnRegister.setOnClickListener(v -> registerUser());

        btnSendOtp.setOnClickListener(v -> sendRegistrationOtp());

        btnLogin.setOnClickListener(v -> {
            Intent loginIntent = new Intent(this, LoginActivity.class);
            int selectedRoleId = roleGroup.getCheckedRadioButtonId();
            if (selectedRoleId == R.id.rbDriver) {
                loginIntent.putExtra("role", "driver");
            } else if (selectedRoleId == R.id.rbCustomer) {
                loginIntent.putExtra("role", "customer");
            }
            startActivity(loginIntent);
            finish();
        });

        emailEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearOtpState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
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
        String otpCode = otpEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();
        String confirmPassword = confirmPasswordEdit.getText().toString().trim();
        String licenseNumber = licenseNumberEdit.getText().toString().trim();
        
        String vehicleType = "";
        if (selectedRoleId == R.id.rbDriver && vehicleTypeSpinner.getSelectedItem() != null) {
            vehicleType = vehicleTypeSpinner.getSelectedItem().toString().trim();
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

        if (!isDriver && (selectedCustomerLatitude == null || selectedCustomerLongitude == null)) {
            Toast.makeText(this, "Please pick your delivery location on the map", Toast.LENGTH_SHORT).show();
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

        if (isDriver && !licenseNumber.matches(LICENSE_REGEX)) {
            Toast.makeText(this, "License number contains invalid special characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDriver && !hasAcceptedTerms) {
            Toast.makeText(this, "Please accept the Terms of Agreement", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDriver && (selectedDriverLatitude == null || selectedDriverLongitude == null)) {
            ensureDriverLocation();
            Toast.makeText(this, "Getting rider location, please try again in a moment.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isStrongPassword(password)) {
            Toast.makeText(this, getString(R.string.password_policy_error), Toast.LENGTH_LONG).show();
            return;
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this, getString(R.string.enter_valid_email), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!email.equalsIgnoreCase(otpTargetEmail)) {
            clearOtpState();
        }

        if (!otpSent) {
            Toast.makeText(this, getString(R.string.send_otp_first), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(otpCode)) {
            Toast.makeText(this, getString(R.string.enter_otp_code), Toast.LENGTH_SHORT).show();
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
            postData.put("address", isDriver ? "" : deliveryAddress);
            postData.put("latitude", getRegistrationLatitude(role));
            postData.put("longitude", getRegistrationLongitude(role));
            postData.put("customer_latitude", "customer".equals(role) ? getRegistrationLatitude(role) : JSONObject.NULL);
            postData.put("customer_longitude", "customer".equals(role) ? getRegistrationLongitude(role) : JSONObject.NULL);
            postData.put("driver_latitude", isDriver ? getRegistrationLatitude(role) : JSONObject.NULL);
            postData.put("driver_longitude", isDriver ? getRegistrationLongitude(role) : JSONObject.NULL);
            if ("customer".equals(role)) {
                postData.put("selected_location", buildLocationSummary(selectedCustomerAddress, selectedCustomerLatitude, selectedCustomerLongitude));
            }
            if (isDriver) {
                postData.put("license_number", licenseNumber);
                postData.put("vehicle_type", vehicleType);
                postData.put("delivery_address", selectedDriverAddress);
                postData.put("address", selectedDriverAddress);
            }
            postData.put("otp", otpCode);
            postData.put("code", otpCode);
            if (!TextUtils.isEmpty(otpChallengeToken)) {
                postData.put("otp_token", otpChallengeToken);
                postData.put("challenge_token", otpChallengeToken);
            }
        } catch (JSONException e) {
            Log.e("RegisterActivity", "Failed to create JSON object", e);
            return;
        }

        if (otpVerified) {
            submitRegistration(postData, role, email, name, true, URL_REGISTER);
            return;
        }

        verifyRegistrationOtpThenRegister(otpCode, postData, role, email, name);
    }

    private void sendRegistrationOtp() {
        int selectedRoleId = roleGroup.getCheckedRadioButtonId();
        String role = selectedRoleId == R.id.rbDriver ? "driver" : (selectedRoleId == R.id.rbCustomer ? "customer" : "");

        String name = nameEdit.getText().toString().trim();
        String contactNumber = contactEdit.getText().toString().trim();
        String deliveryAddress = addressEdit.getText().toString().trim();
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();
        String confirmPassword = confirmPasswordEdit.getText().toString().trim();

        if (selectedRoleId == R.id.rbCustomer && (selectedCustomerLatitude == null || selectedCustomerLongitude == null)) {
            Toast.makeText(this, "Please choose your delivery location first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (name.length() < 2) {
            Toast.makeText(this, "Name must be at least 2 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this, getString(R.string.enter_valid_email), Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("name", name);
            payload.put("full_name", name);
            payload.put("contact_number", contactNumber);
            payload.put("role", role);
            payload.put("delivery_address", deliveryAddress);
            payload.put("address", deliveryAddress);
            payload.put("latitude", getRegistrationLatitude(role));
            payload.put("longitude", getRegistrationLongitude(role));
            payload.put("customer_latitude", "customer".equals(role) ? getRegistrationLatitude(role) : JSONObject.NULL);
            payload.put("customer_longitude", "customer".equals(role) ? getRegistrationLongitude(role) : JSONObject.NULL);
            payload.put("driver_latitude", "driver".equals(role) ? getRegistrationLatitude(role) : JSONObject.NULL);
            payload.put("driver_longitude", "driver".equals(role) ? getRegistrationLongitude(role) : JSONObject.NULL);
            if ("driver".equals(role)) {
                payload.put("delivery_address", selectedDriverAddress);
                payload.put("address", selectedDriverAddress);
            }
            payload.put("email", email);
            payload.put("purpose", "register");
            if (!TextUtils.isEmpty(password)) {
                payload.put("password", password);
            }
            if (!TextUtils.isEmpty(confirmPassword)) {
                payload.put("password_confirmation", confirmPassword);
            }
        } catch (JSONException e) {
            Log.e("RegisterActivity", "Failed to build OTP payload", e);
            Toast.makeText(this, "Could not send OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        requestRegistrationOtp(Constants.URL_REGISTER_SEND_OTP, payload, email, true);
    }

    private void requestRegistrationOtp(String url, JSONObject payload, String email, boolean allowFallback) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, payload,
                response -> {
                    boolean success = response.optBoolean("success", false)
                            || "success".equalsIgnoreCase(response.optString("status"));
                    if (!success) {
                        String message = response.optString("message", "Failed to send OTP");
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        return;
                    }

                    otpSent = true;
                    otpVerified = false;
                    otpTargetEmail = email;
                    otpChallengeToken = extractOtpChallengeToken(response);
                    otpEdit.setText("");
                    Toast.makeText(this, getString(R.string.otp_sent_success), Toast.LENGTH_SHORT).show();
                },
                error -> {
                    if (allowFallback && Constants.URL_REGISTER_SEND_OTP.equals(url)) {
                        requestRegistrationOtp(Constants.URL_REGISTER_SEND_OTP_FALLBACK, payload, email, false);
                        return;
                    }

                    String message = "Failed to send OTP";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            if (errorJson.has("errors")) {
                                JSONObject errors = errorJson.getJSONObject("errors");
                                java.util.Iterator<String> keys = errors.keys();
                                if (keys.hasNext()) {
                                    String firstKey = keys.next();
                                    org.json.JSONArray messages = errors.optJSONArray(firstKey);
                                    if (messages != null && messages.length() > 0) {
                                        message = messages.optString(0, message);
                                    }
                                }
                            } else {
                                message = errorJson.optString("message", message);
                            }
                        } catch (JSONException parseError) {
                            Log.e("RegisterActivity", "Failed to parse send OTP error", parseError);
                        }
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }

    private void verifyRegistrationOtpThenRegister(
            String otpCode,
            JSONObject postData,
            String role,
            String email,
            String name
    ) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("email", email);
            payload.put("otp", otpCode);
            payload.put("code", otpCode);
            payload.put("purpose", "register");
            payload.put("role", role);
            if (!TextUtils.isEmpty(otpChallengeToken)) {
                payload.put("otp_token", otpChallengeToken);
                payload.put("challenge_token", otpChallengeToken);
            }
        } catch (JSONException e) {
            Log.e("RegisterActivity", "Failed to build OTP verify payload", e);
            Toast.makeText(this, "Could not verify OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        verifyRegistrationOtp(Constants.URL_REGISTER_VERIFY_OTP, payload, postData, role, email, name, true, true);
    }

    private void verifyRegistrationOtp(
            String url,
            JSONObject verifyPayload,
            JSONObject postData,
            String role,
            String email,
            String name,
            boolean allowEndpointFallback,
            boolean allowLegacyFallback
    ) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, verifyPayload,
                response -> {
                    boolean success = response.optBoolean("success", false)
                            || "success".equalsIgnoreCase(response.optString("status"));
                    if (!success) {
                        String message = response.optString("message", "Invalid OTP");
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        return;
                    }

                    otpVerified = true;
                    Toast.makeText(this, getString(R.string.otp_verified_success), Toast.LENGTH_SHORT).show();
                    submitRegistration(postData, role, email, name, true, URL_REGISTER);
                },
                error -> {
                    if (allowEndpointFallback && Constants.URL_REGISTER_VERIFY_OTP.equals(url)) {
                        verifyRegistrationOtp(
                                Constants.URL_VERIFY_CODE,
                                verifyPayload,
                                postData,
                                role,
                                email,
                                name,
                                false,
                                allowLegacyFallback
                        );
                        return;
                    }

                    if (allowLegacyFallback && Constants.URL_VERIFY_CODE.equals(url)) {
                        verifyRegistrationOtp(
                                Constants.BASE_URL + "verify-register-otp",
                                verifyPayload,
                                postData,
                                role,
                                email,
                                name,
                                false,
                                false
                        );
                        return;
                    }

                    String message = "OTP verification failed";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            message = errorJson.optString("message", message);
                        } catch (JSONException parseError) {
                            Log.e("RegisterActivity", "Failed to parse verify OTP error", parseError);
                        }
                    }

                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                10000,
                com.android.volley.DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        Volley.newRequestQueue(this).add(request);
    }

    private String extractOtpChallengeToken(JSONObject response) {
        if (response == null) {
            return "";
        }

        JSONObject data = response.optJSONObject("data");
        String token = firstNonEmpty(
                response.optString("otp_token", ""),
                response.optString("challenge_token", ""),
                response.optString("mfa_token", ""),
                data != null ? data.optString("otp_token", "") : "",
                data != null ? data.optString("challenge_token", "") : "",
                data != null ? data.optString("mfa_token", "") : ""
        );

        return token == null ? "" : token.trim();
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private boolean isValidEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return false;
        }
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void clearOtpState() {
        otpSent = false;
        otpVerified = false;
        otpTargetEmail = "";
        otpChallengeToken = "";
    }

    private void openCustomerLocationPicker() {
        int selectedRoleId = roleGroup.getCheckedRadioButtonId();
        pendingLocationRole = selectedRoleId == R.id.rbDriver ? "driver" : "customer";

        Intent intent = new Intent(this, CustomerLocationPickerActivity.class);
        if ("driver".equals(pendingLocationRole)) {
            if (selectedDriverLatitude != null && selectedDriverLongitude != null) {
                intent.putExtra(CustomerLocationPickerActivity.EXTRA_LATITUDE, selectedDriverLatitude);
                intent.putExtra(CustomerLocationPickerActivity.EXTRA_LONGITUDE, selectedDriverLongitude);
                intent.putExtra(CustomerLocationPickerActivity.EXTRA_ADDRESS, selectedDriverAddress);
            }
        } else if (selectedCustomerLatitude != null && selectedCustomerLongitude != null) {
            intent.putExtra(CustomerLocationPickerActivity.EXTRA_LATITUDE, selectedCustomerLatitude);
            intent.putExtra(CustomerLocationPickerActivity.EXTRA_LONGITUDE, selectedCustomerLongitude);
            intent.putExtra(CustomerLocationPickerActivity.EXTRA_ADDRESS, selectedCustomerAddress);
        }
        customerLocationPickerLauncher.launch(intent);
    }

    private void ensureDriverLocation() {
        if (driverLocationLoading || (selectedDriverLatitude != null && selectedDriverLongitude != null)) {
            refreshRiderLocationSummary();
            return;
        }

        if (!LocationHelper.hasLocationPermission(this)) {
            driverLocationLoading = false;
            refreshRiderLocationSummary();
            Toast.makeText(this, getString(R.string.location_permission_required), Toast.LENGTH_LONG).show();
            return;
        }

        driverLocationLoading = true;
        refreshRiderLocationSummary();
        LocationHelper.resolveCurrentLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onLocationReady(LocationHelper.LocationData locationData) {
                driverLocationLoading = false;
                if (locationData != null) {
                    selectedDriverLatitude = locationData.latitude;
                    selectedDriverLongitude = locationData.longitude;
                    selectedDriverAddress = locationData.address == null ? "" : locationData.address;
                }
                refreshRiderLocationSummary();
            }

            @Override
            public void onError(String message) {
                driverLocationLoading = false;
                refreshRiderLocationSummary();
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshCustomerLocationUi() {
        if (customerLocationSummaryText == null) {
            return;
        }

        if (selectedCustomerLatitude == null || selectedCustomerLongitude == null) {
            customerLocationSummaryText.setText(getString(R.string.location_unavailable));
            addressEdit.setText("");
            return;
        }

        customerLocationSummaryText.setText(buildLocationSummary(selectedCustomerAddress, selectedCustomerLatitude, selectedCustomerLongitude));
        addressEdit.setText(selectedCustomerAddress);
    }

    private void updateCustomerLocationSummary() {
        refreshCustomerLocationUi();
    }

    private void refreshRiderLocationSummary() {
        if (riderLocationSummaryText == null) {
            return;
        }

        if (driverLocationLoading) {
            riderLocationSummaryText.setText(getString(R.string.location_loading));
            return;
        }

        if (selectedDriverLatitude == null || selectedDriverLongitude == null) {
            riderLocationSummaryText.setText(getString(R.string.location_unavailable));
            return;
        }

        riderLocationSummaryText.setText(buildLocationSummary(selectedDriverAddress, selectedDriverLatitude, selectedDriverLongitude));
    }

    private String buildLocationSummary(String address, Double latitude, Double longitude) {
        String coords = buildCoordinateSummary(latitude, longitude);
        if (TextUtils.isEmpty(address)) {
            return coords;
        }
        return address + "\n" + coords;
    }

    private String buildCoordinateSummary(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return "";
        }
        return String.format(java.util.Locale.getDefault(), "Lat: %.6f, Lng: %.6f", latitude, longitude);
    }

    private double getRegistrationLatitude(String role) {
        if ("driver".equals(role) && selectedDriverLatitude != null) {
            return selectedDriverLatitude;
        }
        return selectedCustomerLatitude != null ? selectedCustomerLatitude : 0d;
    }

    private double getRegistrationLongitude(String role) {
        if ("driver".equals(role) && selectedDriverLongitude != null) {
            return selectedDriverLongitude;
        }
        return selectedCustomerLongitude != null ? selectedCustomerLongitude : 0d;
    }

    private Double getDoubleExtra(Intent data, String key) {
        if (data == null || !data.hasExtra(key)) {
            return null;
        }
        double value = data.getDoubleExtra(key, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    private void submitRegistration(JSONObject postData, String role, String email, String name, boolean allowFallback, String targetUrl) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, targetUrl, postData,
                response -> {
                    boolean isSuccess = response.optBoolean("success", false) || "success".equals(response.optString("status"));
                    if (isSuccess) {
                        getApplicationContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit()
                                .putString(PREF_LAST_REGISTERED_EMAIL, email)
                                .putString(PREF_LAST_REGISTERED_ROLE, role)
                                .putString("delivery_address", "customer".equals(role) ? selectedCustomerAddress : selectedDriverAddress)
                                .putString("latitude", String.valueOf(getRegistrationLatitude(role)))
                                .putString("longitude", String.valueOf(getRegistrationLongitude(role)))
                                .apply();

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
                        loginIntent.putExtra("role", role);
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

                    if (allowFallback && message.toLowerCase().contains("mfa_enabled")) {
                        submitRegistration(postData, role, email, name, false, URL_REGISTER_LEGACY);
                        return;
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

    private boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;

        for (char ch : password.toCharArray()) {
            if (Character.isUpperCase(ch)) {
                hasUpper = true;
            } else if (Character.isLowerCase(ch)) {
                hasLower = true;
            } else if (Character.isDigit(ch)) {
                hasDigit = true;
            }
        }

        return hasUpper && hasLower && hasDigit;
    }
}
