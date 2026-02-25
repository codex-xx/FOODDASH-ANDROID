package com.example.fooddash;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

public class CustomerDashboard extends AppCompatActivity {

    Button btnOrder, btnLogout;
    String URL_PLACE_ORDER = "http://192.168.1.10/FoodDash/public/api/orders"; // Replace with your IP

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_dashboard);

        btnOrder = findViewById(R.id.btnOrder);
        btnLogout = findViewById(R.id.btnLogout);

        btnOrder.setOnClickListener(v -> placeOrder());

        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void placeOrder() {
        StringRequest request = new StringRequest(Request.Method.POST, URL_PLACE_ORDER,
                response -> {
                    Toast.makeText(this, "Order Placed Successfully", Toast.LENGTH_SHORT).show();
                },
                error -> Toast.makeText(this, "Failed to place order", Toast.LENGTH_SHORT).show()
        ){
            @Override
            protected Map<String,String> getParams(){
                Map<String,String> params = new HashMap<>();
                // These are dummy parameters, replace with actual order details from your UI
                params.put("item_id", "1");
                params.put("quantity", "1");
                params.put("delivery_address", "123 Main St");
                return params;
            }
        };

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }
}