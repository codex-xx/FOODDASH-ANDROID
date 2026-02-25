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

public class DriverDashboard extends AppCompatActivity {

    Button btnViewOrders, btnLogout;
    String URL_VIEW_ORDERS = "http://192.168.1.10/FoodDash/public/api/orders"; // Replace with your IP


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_dashboard);

        btnViewOrders = findViewById(R.id.btnViewOrders);
        btnLogout = findViewById(R.id.btnLogout);

        btnViewOrders.setOnClickListener(v -> viewOrders());

        btnLogout.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void viewOrders() {
        StringRequest request = new StringRequest(Request.Method.GET, URL_VIEW_ORDERS,
                response -> {
                    // In a real app, you would parse this JSON and display it in a list
                    Toast.makeText(this, response, Toast.LENGTH_LONG).show();
                },
                error -> Toast.makeText(this, "Failed to fetch orders", Toast.LENGTH_SHORT).show()
        );

        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(request);
    }
}