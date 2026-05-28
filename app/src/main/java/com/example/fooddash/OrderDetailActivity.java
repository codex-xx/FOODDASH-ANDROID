package com.example.fooddash;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class OrderDetailActivity extends AppCompatActivity {

    private TextView tvOrderId, tvDate, tvStatus, tvCustomerName, tvCustomerContact, tvCustomerAddress;
    private TextView tvRestaurant, tvItems, tvTotal, tvPayment;
    private TextView tvDriverName, tvDriverContact, tvDriverVehicle;
    private View driverSeparator, driverLabel, proofSeparator;
    private TextView tvProofLabel, tvDeliveredAt;
    private ImageView ivDeliveryProof;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        tvOrderId = findViewById(R.id.tvOrderId);
        tvDate = findViewById(R.id.tvDate);
        tvStatus = findViewById(R.id.tvStatus);
        tvCustomerName = findViewById(R.id.tvCustomerName);
        tvCustomerContact = findViewById(R.id.tvCustomerContact);
        tvCustomerAddress = findViewById(R.id.tvCustomerAddress);
        tvRestaurant = findViewById(R.id.tvRestaurant);
        tvItems = findViewById(R.id.tvItems);
        tvTotal = findViewById(R.id.tvTotal);
        tvPayment = findViewById(R.id.tvPayment);

        tvDriverName = findViewById(R.id.tvDriverName);
        tvDriverContact = findViewById(R.id.tvDriverContact);
        tvDriverVehicle = findViewById(R.id.tvDriverVehicle);
        driverLabel = findViewById(R.id.driverLabel);
        driverSeparator = findViewById(R.id.tvDriverName).getParent() instanceof View ? (View) findViewById(R.id.tvDriverName).getParent() : null;

        proofSeparator = findViewById(R.id.proofSeparator);
        tvProofLabel = findViewById(R.id.tvProofLabel);
        ivDeliveryProof = findViewById(R.id.ivDeliveryProof);
        tvDeliveredAt = findViewById(R.id.tvDeliveredAt);

        Button btnClose = findViewById(R.id.btnCloseDetail);
        btnClose.setOnClickListener(v -> finish());

        boolean showDriverDetails = getIntent().getBooleanExtra("show_driver_details", true);
        if (!showDriverDetails) {
            if (driverLabel != null) driverLabel.setVisibility(View.GONE);
            if (tvDriverName != null) tvDriverName.setVisibility(View.GONE);
            if (tvDriverContact != null) tvDriverContact.setVisibility(View.GONE);
            if (tvDriverVehicle != null) tvDriverVehicle.setVisibility(View.GONE);
        }

        String orderJson = getIntent().getStringExtra("order_json");
        if (orderJson == null) {
            Toast.makeText(this, "No order data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            JSONObject order = new JSONObject(orderJson);
            renderOrder(order);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid order data", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void renderOrder(JSONObject order) {
        int id = order.optInt("id", order.optInt("order_id", -1));
        tvOrderId.setText("Order #" + (id > 0 ? id : "N/A"));

        String date = firstNonEmpty(
            order.optString("delivered_at"),
            order.optString("updated_at"),
            order.optString("completed_at"),
            order.optString("created_at"),
            "N/A"
        );
        tvDate.setText("Date: " + date);

        String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
        tvStatus.setText("Status: " + status.replace("_", " ").toUpperCase());

        tvCustomerName.setText("Customer: " + firstNonEmpty(order.optString("customer_name"), order.optString("full_name"), order.optString("name"), "N/A"));
        tvCustomerContact.setText("Contact: " + firstNonEmpty(
            order.optString("customer_phone"),
            order.optString("phone_number"),
            order.optString("mobile"),
            order.optString("customer_contact"),
            order.optString("contact_number"),
            order.optString("phone"),
            "N/A"
        ));
        tvCustomerAddress.setText("Address: " + firstNonEmpty(order.optString("delivery_address"), order.optString("address"), "N/A"));

        String restaurant = "";
        
        // Try to get restaurant as string first
        String restaurantStr = order.optString("restaurant_name", "");
        if (!restaurantStr.isEmpty() && !restaurantStr.startsWith("{")) {
            restaurant = restaurantStr;
        } else {
            // Try to parse as JSON object
            try {
                JSONObject restaurantObj = order.optJSONObject("restaurant_name");
                if (restaurantObj == null) restaurantObj = order.optJSONObject("restaurant");
                if (restaurantObj != null) {
                    restaurant = firstNonEmpty(restaurantObj.optString("name"), restaurantObj.optString("business_name"), "");
                }
            } catch (Exception ignored) {}
        }
        
        // Fallback to other fields
        if (restaurant.isEmpty()) {
            restaurant = firstNonEmpty(
                    order.optString("restaurant"),
                    order.optString("store_name"),
                    order.optString("merchant_name"),
                    order.optString("vendor_name"),
                    ""
            );
        }
        
        if (restaurant.isEmpty()) restaurant = "Unknown Restaurant";
        tvRestaurant.setText("Restaurant: " + restaurant);

        // Items
        StringBuilder itemsBuilder = new StringBuilder();
        JSONArray items = order.optJSONArray("items");
        if (items == null) {
            String itemsStr = order.optString("items", "");
            if (!TextUtils.isEmpty(itemsStr) && itemsStr.startsWith("[")) {
                try { items = new JSONArray(itemsStr); } catch (Exception ignored) {}
            }
        }
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it != null) {
                    String name = it.optString("name", it.optString("food_name", it.optString("item_name", "Item")));
                    int qty = it.optInt("quantity", it.optInt("qty", 1));
                    itemsBuilder.append(qty).append("x ").append(name).append("\n");
                }
            }
        } else {
            itemsBuilder.append(order.optString("items_summary", order.optString("order_details", "(no items)")));
        }
        tvItems.setText("Items:\n" + itemsBuilder.toString().trim());

        double total = order.optDouble("total_amount", order.optDouble("total", 0.0));
        tvTotal.setText(String.format("Total: P%.2f", total));

        String payment = firstNonEmpty(
                order.optString("payment_method"),
                order.optString("payment_type"),
                order.optString("payment"),
                "Cash on Delivery"
        );
        if (payment.equalsIgnoreCase("cod")) {
            tvPayment.setText("Payment: Cash on Delivery");
        } else if (payment.equalsIgnoreCase("gcash") || payment.equalsIgnoreCase("maya")) {
            tvPayment.setText("Payment: Online Payment " + payment.toUpperCase());
        } else if (!payment.isEmpty()) {
            tvPayment.setText("Payment: " + payment);
        } else {
            tvPayment.setText("Payment: Cash on Delivery");
        }

        // Driver details - check nested driver object first
        String driverName = "";
        String driverContact = "";
        String driverVehicle = "";

        JSONObject driver = order.optJSONObject("driver");
        if (driver != null) {
            driverName = firstNonEmpty(driver.optString("name"), driver.optString("fullname"), driver.optString("first_name"), "");
            driverContact = firstNonEmpty(driver.optString("phone"), driver.optString("contact"), driver.optString("phone_number"), "");
            
            // Try to get vehicle from driver's vehicle field (as string)
            String vehicleStr = driver.optString("vehicle", "");
            if (!vehicleStr.isEmpty() && !vehicleStr.startsWith("{")) {
                driverVehicle = vehicleStr;
            } else {
                // Try to parse vehicle as JSON object
                try {
                    JSONObject vehicleObj = driver.optJSONObject("vehicle");
                    if (vehicleObj != null) {
                        driverVehicle = firstNonEmpty(
                                vehicleObj.optString("name"),
                                vehicleObj.optString("type"),
                                vehicleObj.optString("model"),
                                vehicleObj.optString("plate"),
                                vehicleObj.optString("plate_number"),
                                vehicleObj.optString("license_plate"),
                                ""
                        );
                    }
                } catch (Exception ignored) {}
            }
            
            // Try other vehicle field names if still empty
            if (driverVehicle.isEmpty()) {
                driverVehicle = firstNonEmpty(
                        driver.optString("vehicle_type"),
                        driver.optString("vehicle_name"),
                        driver.optString("motorcycle"),
                        driver.optString("plate_number"),
                        driver.optString("license_plate"),
                        ""
                );
            }
        } else {
            driverName = firstNonEmpty(order.optString("driver_name"), order.optString("driver_fullname"), "");
            driverContact = firstNonEmpty(order.optString("driver_phone"), order.optString("driver_contact"), order.optString("driver_phone_number"), "");
            driverVehicle = firstNonEmpty(
                    order.optString("driver_vehicle"),
                    order.optString("vehicle"),
                    order.optString("vehicle_type"),
                    order.optString("plate_number"),
                    ""
            );
        }

        tvDriverName.setText("Name: " + (driverName.isEmpty() ? "Not Assigned" : driverName));
        tvDriverContact.setText("Phone: " + (driverContact.isEmpty() ? "N/A" : driverContact));
        tvDriverVehicle.setText("Vehicle: " + (driverVehicle.isEmpty() ? "Not Assigned" : driverVehicle));

        // Delivery Proof Logic
        String proofPhoto = order.optString("delivery_proof_photo", "");
        if (!proofPhoto.isEmpty() && !proofPhoto.equalsIgnoreCase("null")) {
            proofSeparator.setVisibility(View.VISIBLE);
            tvProofLabel.setVisibility(View.VISIBLE);
            ivDeliveryProof.setVisibility(View.VISIBLE);
            tvDeliveredAt.setVisibility(View.VISIBLE);

            String fullPhotoUrl = proofPhoto;
            if (!proofPhoto.startsWith("http")) {
                fullPhotoUrl = Constants.RESOURCE_URL + proofPhoto;
            }

            Glide.with(this)
                    .load(fullPhotoUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(ivDeliveryProof);

            String deliveredAt = order.optString("delivered_at", "");
            if (!deliveredAt.isEmpty()) {
                tvDeliveredAt.setText("Delivered at: " + deliveredAt);
            } else {
                tvDeliveredAt.setVisibility(View.GONE);
            }

            // Click to view full size
            String finalFullPhotoUrl = fullPhotoUrl;
            ivDeliveryProof.setOnClickListener(v -> {
                Toast.makeText(OrderDetailActivity.this, "Opening full image...", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalFullPhotoUrl));
                startActivity(intent);
            });
        } else {
            proofSeparator.setVisibility(View.GONE);
            tvProofLabel.setVisibility(View.GONE);
            ivDeliveryProof.setVisibility(View.GONE);
            tvDeliveredAt.setVisibility(View.GONE);
        }
    }

    private String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && !v.trim().isEmpty() && !v.equals("null")) return v.trim();
        return "";
    }

}
