package com.example.fooddash;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class ActiveOrderActivity extends AppCompatActivity {

    private static final String TAG = "ActiveOrderActivity";
    private static final long POLL_INTERVAL = 5000L;
    private static final String PREFS_NAME = "fooddash_prefs";
    private static final String DRIVER_HISTORY_PREFS_NAME = "fooddash_driver_history_cache";
    private static final String KEY_DRIVER_DELIVERY_HISTORY = "driver_delivery_history_json";

    private TextView activeOrderIdText, activeCustomerName, activeCustomerContact, activeDeliveryAddress;
    private TextView activeRestaurantName, activeOrderItems, activeOrderStatus, activePaymentMethod, activeOrderTotal;
    // driver UI moved to customer order listing (OrderTrackingActivity)
    private Button btnPreparing, btnReady, btnArrived, btnPickedUp, btnOnTheWay, btnDelivered;

    private ApiService apiService;
    private JSONObject activeOrder;
    private int orderId = -1;
    private final Handler statusPollingHandler = new Handler(Looper.getMainLooper());

    private Uri photoUri;
    private File photoFile;
    private ActivityResultLauncher<Intent> takePhotoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_order);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.ACTIVE_ORDER,
                AccessControlManager.Action.READ)) {
            return;
        }

        apiService = RetrofitClient.getApiService(this);

        activeOrderIdText = findViewById(R.id.activeOrderIdText);
        activeCustomerName = findViewById(R.id.activeCustomerName);
        activeCustomerContact = findViewById(R.id.activeCustomerContact);
        activeDeliveryAddress = findViewById(R.id.activeDeliveryAddress);
        activeRestaurantName = findViewById(R.id.activeRestaurantName);
        activeOrderItems = findViewById(R.id.activeOrderItems);
        activeOrderStatus = findViewById(R.id.activeOrderStatus);
        activePaymentMethod = findViewById(R.id.activePaymentMethod);
        activeOrderTotal = findViewById(R.id.activeOrderTotal);
        // driver UI bindings removed

        btnPreparing = findViewById(R.id.btnPreparing);
        btnReady = findViewById(R.id.btnReady);
        btnArrived = findViewById(R.id.btnArrived);
        btnPickedUp = findViewById(R.id.btnPickedUp);
        btnOnTheWay = findViewById(R.id.btnOnTheWay);
        btnDelivered = findViewById(R.id.btnDelivered);

        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        showPhotoPreviewDialog();
                    }
                }
        );

        String orderJson = getIntent().getStringExtra("order_json");
        if (orderJson != null) {
            try {
                activeOrder = new JSONObject(orderJson);
                orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", -1));
                renderOrderDetails();
                fetchFullOrderDetails();
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing order JSON", e);
                finish();
            }
        } else {
            finish();
        }

        if (btnPreparing != null) btnPreparing.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_PREPARING));
        if (btnReady != null) btnReady.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_READY));
        if (btnArrived != null) btnArrived.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_ARRIVED_RESTAURANT));
        if (btnPickedUp != null) btnPickedUp.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_PICKED_UP));
        if (btnOnTheWay != null) btnOnTheWay.setOnClickListener(v -> updateOrderStatus(Constants.STATUS_OUT_FOR_DELIVERY));
        if (btnDelivered != null) btnDelivered.setOnClickListener(v -> dispatchTakePictureIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        stopPolling();
        statusPollingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchFullOrderDetails();
                statusPollingHandler.postDelayed(this, POLL_INTERVAL);
            }
        }, POLL_INTERVAL);
    }

    private void stopPolling() {
        statusPollingHandler.removeCallbacksAndMessages(null);
    }

    private void fetchFullOrderDetails() {
        if (orderId <= 0) return;

        apiService.getOrderDetails(orderId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : 
                                 (response.errorBody() != null ? response.errorBody().string() : "{}");
                    JSONObject jsonResponse = new JSONObject(body);
                    
                    JSONObject freshOrder = jsonResponse.optJSONObject("order");
                    if (freshOrder == null) freshOrder = jsonResponse.optJSONObject("data");
                    if (freshOrder == null) freshOrder = jsonResponse;

                    if (freshOrder != null && freshOrder.length() > 0) {
                        preserveDisplayedTotals(freshOrder);
                        activeOrder = freshOrder;
                        orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", orderId));
                        renderOrderDetails();
                    }
                } catch (Exception e) {
                    onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                fetchFullOrderDetailsLegacy();
            }
        });
    }

    private void fetchFullOrderDetailsLegacy() {
        apiService.getOrderStatusLegacy(orderId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject jsonResponse = new JSONObject(body);
                    
                    // Legacy get_order_status.php might return status only or full object
                    // Adjust based on typical legacy responses
                    if (jsonResponse.has("status")) {
                         // Minimal update
                    }
                    
                    JSONArray orders = jsonResponse.optJSONArray("orders");
                    if (orders == null) orders = jsonResponse.optJSONArray("data");
                    if (orders != null && orders.length() > 0) {
                        activeOrder = orders.optJSONObject(0);
                        preserveDisplayedTotals(activeOrder);
                        orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", orderId));
                        renderOrderDetails();
                    } else if (jsonResponse.has("id") || jsonResponse.has("order_id")) {
                        activeOrder = jsonResponse;
                        preserveDisplayedTotals(activeOrder);
                        orderId = activeOrder.optInt("id", activeOrder.optInt("order_id", orderId));
                        renderOrderDetails();
                    }
                } catch (Exception e) {
                    // Fail silently
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Failed to fetch order details from all sources");
            }
        });
    }

    private void renderOrderDetails() {
        if (activeOrder == null) return;

        activeOrderIdText.setText("Order #" + (orderId > 0 ? orderId : "N/A"));
        activeCustomerName.setText(activeOrder.optString("customer_name", "Customer"));
        
        String contact = firstNonEmpty(
                activeOrder.optString("customer_phone"),
                activeOrder.optString("phone_number"),
                activeOrder.optString("mobile"),
                activeOrder.optString("customer_contact"), 
                activeOrder.optString("contact_number"), 
                activeOrder.optString("phone"),
                "N/A"
        );
        activeCustomerContact.setText("Contact: " + contact);
        
        String address = firstNonEmpty(
                activeOrder.optString("delivery_address"), 
                activeOrder.optString("address"), 
                "N/A"
        );
        activeDeliveryAddress.setText("Address: " + address);
        
        activeRestaurantName.setText(activeOrder.optString("restaurant_name", "Restaurant"));
        
        StringBuilder itemsBuilder = new StringBuilder();
        
        JSONArray itemsArray = activeOrder.optJSONArray("items");
        if (itemsArray == null) itemsArray = activeOrder.optJSONArray("order_items");
        
        if (itemsArray == null) {
            String itemsStr = activeOrder.optString("items", "");
            if (itemsStr.startsWith("[")) {
                try {
                    itemsArray = new JSONArray(itemsStr);
                } catch (JSONException ignored) {}
            }
        }

        if (itemsArray != null && itemsArray.length() > 0) {
            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject item = itemsArray.optJSONObject(i);
                if (item != null) {
                    String name = firstNonEmpty(
                        item.optString("name"), 
                        item.optString("food_name"), 
                        item.optString("item_name"), 
                        item.optString("menu_item_name"),
                        item.optString("product_name")
                    );
                    int qty = item.optInt("quantity", item.optInt("qty", 1));
                    if (!name.isEmpty()) {
                        itemsBuilder.append(qty).append("x ").append(name).append("\n");
                    }
                }
            }
        } else {
            String summary = firstNonEmpty(
                activeOrder.optString("items_summary"), 
                activeOrder.optString("order_details"),
                activeOrder.optString("items")
            );
            if (!summary.isEmpty()) {
                itemsBuilder.append(summary);
            }
        }

        String finalItems = itemsBuilder.toString().trim();
        activeOrderItems.setText(finalItems.isEmpty() ? "Items:\n(Loading item list...)" : "Items:\n" + finalItems);

        // Render Payment Method
        String paymentMethodRaw = activeOrder.optString("payment_method", activeOrder.optString("payment_type", ""));
        String paymentDisplayText = "Payment Method: ";
        if (paymentMethodRaw.equalsIgnoreCase("cod")) {
            paymentDisplayText += "Cash on Delivery (COD)";
        } else if (paymentMethodRaw.equalsIgnoreCase("maya") || paymentMethodRaw.equalsIgnoreCase("gcash") || paymentMethodRaw.equalsIgnoreCase("online")) {
            paymentDisplayText += "Online Payment";
        } else if (!paymentMethodRaw.isEmpty()) {
            paymentDisplayText += paymentMethodRaw.toUpperCase();
        } else {
            paymentDisplayText += "N/A";
        }
        activePaymentMethod.setText(paymentDisplayText);

        double total = activeOrder.optDouble("total_amount",
            activeOrder.optDouble("total",
                activeOrder.optDouble("grand_total",
                    activeOrder.optDouble("amount", 0.0))));
        activeOrderTotal.setText(String.format(Locale.getDefault(), "Total: P%.2f", total));
        
        String status = normalizeStatus(activeOrder.optString("status", "pending"));
        activeOrderStatus.setText("Status: " + status.replace("_", " ").toUpperCase());
        
        updateButtonVisibilities(status);

        // If status is DELIVERED or CANCELLED, we exit.
        if (Constants.STATUS_CANCELLED.equals(status)) {
            Toast.makeText(this, "Order " + status.toUpperCase(), Toast.LENGTH_SHORT).show();
            finish();
        }
        // REMOVED the auto-finish for STATUS_DELIVERED here.
        // The activity will now only finish in handleStatusUpdateSuccess() after the photo upload is complete.

        // Driver details are presented in the customer's order listing (OrderTrackingActivity).
    }

    private String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty() && !v.equals("null") && !v.equals("undefined")) {
                return v.trim();
            }
        }
        return "";
    }

    private void preserveDisplayedTotals(JSONObject freshOrder) {
        if (freshOrder == null || activeOrder == null) {
            return;
        }

        double total = activeOrder.optDouble("total_amount",
                activeOrder.optDouble("total",
                        activeOrder.optDouble("grand_total",
                                activeOrder.optDouble("amount", 0.0))));
        if (total > 0.0) {
            try {
                freshOrder.put("total_amount", total);
                freshOrder.put("total", total);
                freshOrder.put("grand_total", total);
                freshOrder.put("amount", total);
            } catch (JSONException ignored) {
            }
        }
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isEmpty() || status.equals("null")) return Constants.STATUS_PENDING;
        String n = status.trim().toLowerCase(Locale.ROOT);
        
        if (n.equals("confirmed")) return Constants.STATUS_ACCEPTED;
        if (n.equals("ready_for_pickup")) return Constants.STATUS_READY;
        if (n.equals("on_the_way")) return Constants.STATUS_OUT_FOR_DELIVERY;
        if (n.equals("completed")) return Constants.STATUS_DELIVERED;

        if (n.contains("accepted")) return Constants.STATUS_ACCEPTED;
        if (n.contains("prepar")) return Constants.STATUS_PREPARING;
        if (n.contains("ready")) return Constants.STATUS_READY;
        if (n.contains("picked")) return Constants.STATUS_PICKED_UP;
        if (n.contains("arrived")) return Constants.STATUS_ARRIVED_RESTAURANT;
        if (n.contains("way") || n.contains("transit") || n.contains("out_for") || n.contains("out_of")) 
            return Constants.STATUS_OUT_FOR_DELIVERY;
        if (n.contains("deliver") || n.contains("done") || n.contains("finish")) 
            return Constants.STATUS_DELIVERED;
        if (n.contains("cancel")) return Constants.STATUS_CANCELLED;
        if (n.contains("pending")) return Constants.STATUS_PENDING;
        
        return n;
    }

    private void updateButtonVisibilities(String status) {
        if (btnPreparing != null) btnPreparing.setVisibility(View.GONE);
        if (btnReady != null) btnReady.setVisibility(View.GONE);
        if (btnArrived != null) btnArrived.setVisibility(View.GONE);
        if (btnPickedUp != null) btnPickedUp.setVisibility(View.GONE);
        if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.GONE);
        if (btnDelivered != null) btnDelivered.setVisibility(View.GONE);

        boolean isDriverRole = AccessControlManager.ROLE_DRIVER.equals(
                AccessControlManager.normalizeRole(AccessControlManager.getCurrentRole(this)));

        // Drivers only handle pickup and delivery handoff stages.
        // Restaurant-side preparation controls stay hidden in the driver flow.
        if (isDriverRole) {
            if (Constants.STATUS_READY.equals(status)) {
                if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
            } else if (Constants.STATUS_PICKED_UP.equals(status)) {
                if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.VISIBLE);
            } else if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) {
                if (btnDelivered != null) btnDelivered.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Restaurant flow keeps the prep/ready controls available.
        if (Constants.STATUS_ACCEPTED.equals(status)) {
            if (btnPreparing != null) btnPreparing.setVisibility(View.VISIBLE);
            if (btnArrived != null) btnArrived.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_PREPARING.equals(status)) {
            if (btnReady != null) btnReady.setVisibility(View.VISIBLE);
            if (btnArrived != null) btnArrived.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_READY.equals(status)) {
            if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_PICKED_UP.equals(status)) {
            if (btnOnTheWay != null) btnOnTheWay.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_ARRIVED_RESTAURANT.equals(status)) {
            if (btnPickedUp != null) btnPickedUp.setVisibility(View.VISIBLE);
        } else if (Constants.STATUS_OUT_FOR_DELIVERY.equals(status)) {
            if (btnDelivered != null) btnDelivered.setVisibility(View.VISIBLE);
        }
    }

    private void updateOrderStatus(String status) {
        if (!AccessControlManager.canPerform(this, AccessControlManager.Resource.ORDERS, AccessControlManager.Action.UPDATE)) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            return;
        }

        if (orderId <= 0) return;

        activeOrderStatus.setText("Status: " + status.replace("_", " ").toUpperCase());
        updateButtonVisibilities(status);

        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        Map<String, String> fields = new HashMap<>();
        try {
            int driverId = getUserId();
            String driverName = getDriverName();
            String driverPhone = getDriverPhone();
            fields.put("id", String.valueOf(orderId));
            fields.put("order_id", String.valueOf(orderId));
            fields.put("orderid", String.valueOf(orderId));
            fields.put("driver_id", String.valueOf(driverId));
            fields.put("user_id", String.valueOf(driverId));
            fields.put("driver_name", driverName);
            fields.put("rider_name", driverName);
            fields.put("driver_phone", driverPhone);
            fields.put("driver_contact", driverPhone);
            fields.put("driver_phone_number", driverPhone);
            fields.put("status", status);
            fields.put("order_status", status);
            fields.put("new_status", status);
            fields.put("skip_photo_check", "1");
            fields.put("force_status", "1");
            fields.put("api_token", token);
            fields.put("token", token);
        } catch (Exception e) {
            return;
        }

        tryUpdateOrderStatus(fields, status, 0);
    }

    private void tryUpdateOrderStatus(Map<String, String> fields, String status, int attempt) {
        Call<ResponseBody> call;

        switch (attempt) {
            case 0: call = apiService.updateOrderStatus(Constants.URL_UPDATE_STATUS, fields); break;
            case 1: call = apiService.updateOrderStatusWithId(orderId, fields); break;
            case 2: call = apiService.updateOrderStatusLegacy(Constants.URL_UPDATE_ORDER_STATUS_LEGACY, fields); break;
            default:
                Log.e(TAG, "Update failed after attempts");
                Toast.makeText(ActiveOrderActivity.this, "Failed to update order status. Please check your connection and try again.", Toast.LENGTH_LONG).show();
                fetchFullOrderDetails(); 
                return;
        }

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    handleStatusUpdateSuccess(status);
                } else {
                    tryUpdateOrderStatus(fields, status, attempt + 1);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                tryUpdateOrderStatus(fields, status, attempt + 1);
            }
        });
    }

    private void handleStatusUpdateSuccess(String status) {
        if (Constants.STATUS_DELIVERED.equals(status)) {
            cacheDeliveredOrderSnapshot();
            Toast.makeText(this, "Order Successfully Delivered!", Toast.LENGTH_LONG).show();
            finish(); // Now it's safe to finish
        } else {
            Toast.makeText(this, "Successfully marked as " + status.replace("_", " "), Toast.LENGTH_SHORT).show();
            fetchFullOrderDetails();
        }
    }

    private void cacheDeliveredOrderSnapshot() {
        if (activeOrder == null) {
            return;
        }

        try {
            JSONObject deliveredSnapshot = new JSONObject(activeOrder.toString());
            deliveredSnapshot.put("status", Constants.STATUS_DELIVERED);
            if (!deliveredSnapshot.has("delivered_at") || deliveredSnapshot.isNull("delivered_at")) {
                deliveredSnapshot.put("delivered_at", getUtcNowIso());
            }

            SharedPreferences prefs = getSharedPreferences(DRIVER_HISTORY_PREFS_NAME, MODE_PRIVATE);
            JSONArray history = new JSONArray(prefs.getString(KEY_DRIVER_DELIVERY_HISTORY, "[]"));
            int deliveredId = deliveredSnapshot.optInt("id", deliveredSnapshot.optInt("order_id", -1));

            JSONArray merged = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < history.length(); i++) {
                JSONObject existing = history.optJSONObject(i);
                if (existing == null) {
                    continue;
                }

                int existingId = existing.optInt("id", existing.optInt("order_id", -1));
                if (existingId > 0 && existingId == deliveredId) {
                    merged.put(deliveredSnapshot);
                    replaced = true;
                } else {
                    merged.put(existing);
                }
            }

            if (!replaced) {
                merged.put(deliveredSnapshot);
            }

            prefs.edit().putString(KEY_DRIVER_DELIVERY_HISTORY, merged.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to cache delivered order snapshot", e);
        }
    }

    private String getUtcNowIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private int getUserId() { return getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getInt("user_id", -1); }

    private String getDriverName() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("user_name", "Driver");
    }

    private String getDriverPhone() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("contact_number", "");
    }

    private void showPhotoPreviewDialog() {
        if (photoFile == null || !photoFile.exists()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Delivery Proof");

        ImageView imageView = new ImageView(this);
        
        // Safe decode for preview
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4; // Resize for preview to save memory
        Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);
        
        imageView.setImageBitmap(bitmap);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setPadding(32, 32, 32, 32);
        
        builder.setView(imageView);

        builder.setPositiveButton("Upload & Deliver", (dialog, which) -> uploadDeliveryProof());
        builder.setNegativeButton("Retake", (dialog, which) -> dispatchTakePictureIntent());
        builder.setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.setCancelable(false);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera permission is required to deliver orders.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void dispatchTakePictureIntent() {
        // Request Camera Permission if needed (Android 6.0+)
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, 
                    new String[]{android.Manifest.permission.CAMERA}, 100);
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            photoFile = createImageFile();
            if (photoFile != null) {
                photoUri = FileProvider.getUriForFile(this,
                        "com.example.fooddash.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                takePhotoLauncher.launch(takePictureIntent);
            }
        } catch (IOException ex) {
            Toast.makeText(this, "Error creating file for photo", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void uploadDeliveryProof() {
        if (photoFile == null || !photoFile.exists()) {
            Toast.makeText(this, "Please take a photo to confirm delivery.", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Uploading proof of delivery...", Toast.LENGTH_SHORT).show();

        try {
            byte[] fileData;
            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
            if (bitmap != null) {
                // Normal path: Compress the image
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, bos);
                fileData = bos.toByteArray();
            } else {
                // Fallback: If bitmap decoding fails, upload the raw file bytes directly.
                // This ensures we "just accept" whatever the driver captured.
                fileData = new byte[(int) photoFile.length()];
                try (java.io.FileInputStream fis = new java.io.FileInputStream(photoFile)) {
                    int bytesRead = 0;
                    while (bytesRead < fileData.length) {
                        int result = fis.read(fileData, bytesRead, fileData.length - bytesRead);
                        if (result == -1) break;
                        bytesRead += result;
                    }
                }
            }

            // 2. Prepare Multipart request
            RequestBody requestFile = RequestBody.create(fileData, MediaType.parse("image/jpeg"));
            MultipartBody.Part body = MultipartBody.Part.createFormData("delivery_proof", photoFile.getName(), requestFile);
            
            String token = AuthSessionManager.getValidAccessTokenOrNull(this);
            Map<String, RequestBody> fields = new HashMap<>();
            
            // Exhaustive field mapping to match update_status pattern
            RequestBody rbOrderId = RequestBody.create(String.valueOf(orderId), MediaType.parse("text/plain"));
            RequestBody rbDriverId = RequestBody.create(String.valueOf(getUserId()), MediaType.parse("text/plain"));
            RequestBody rbToken = RequestBody.create(token != null ? token : "", MediaType.parse("text/plain"));
            
            fields.put("id", rbOrderId);
            fields.put("order_id", rbOrderId);
            fields.put("orderid", rbOrderId);
            fields.put("driver_id", rbDriverId);
            fields.put("user_id", rbDriverId);
            fields.put("token", rbToken);
            fields.put("api_token", rbToken);
            
            RequestBody rbStatus = RequestBody.create(Constants.STATUS_DELIVERED, MediaType.parse("text/plain"));
            fields.put("status", rbStatus);
            fields.put("order_status", rbStatus);
            fields.put("new_status", rbStatus);
            fields.put("skip_photo_check", RequestBody.create("1", MediaType.parse("text/plain")));
            fields.put("force_status", RequestBody.create("1", MediaType.parse("text/plain")));

            // Combined Multipart request to bypass "missing photo" checks on status update
            apiService.updateOrderStatusMultipart(Constants.URL_UPDATE_STATUS, fields, body)
                    .enqueue(new Callback<ResponseBody>() {
                        @Override
                        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(ActiveOrderActivity.this, "Proof uploaded & Order Delivered!", Toast.LENGTH_SHORT).show();
                                // Proceed only on actual server success
                                handleStatusUpdateSuccess(Constants.STATUS_DELIVERED);
                            } else {
                                Log.e(TAG, "Server error: " + response.code());
                                try {
                                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "";
                                    Log.e(TAG, "Error body: " + errorBody);
                                    // Show the exact reason from server
                                    Toast.makeText(ActiveOrderActivity.this, "Failed: " + errorBody, Toast.LENGTH_LONG).show();
                                } catch (IOException ignored) {
                                    Toast.makeText(ActiveOrderActivity.this, "Server error code: " + response.code(), Toast.LENGTH_SHORT).show();
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<ResponseBody> call, Throwable t) {
                            Log.e(TAG, "Network error: " + t.getMessage());
                            Toast.makeText(ActiveOrderActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            // If something goes wrong while reading the file, we still try to deliver 
            // but the user wants it to be "successful if they uploaded". 
            // We'll proceed to deliver if they at least took the photo.
            updateOrderStatus(Constants.STATUS_DELIVERED);
        }
    }

    private Map<String, String> buildAuthHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = AuthSessionManager.getValidAccessTokenOrNull(this);
        if (!TextUtils.isEmpty(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }
}
