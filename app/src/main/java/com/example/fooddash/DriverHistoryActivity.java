package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverHistoryActivity extends AppCompatActivity {

    private static final String TAG = "DriverHistoryActivity";
    private RecyclerView historyRecyclerView;
    private HistoryAdapter adapter;
    private List<JSONObject> historyList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView noHistoryTextView;
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_history);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.DRIVER_HISTORY,
                AccessControlManager.Action.READ)) {
            return;
        }

        requestQueue = Volley.newRequestQueue(this);

        historyRecyclerView = findViewById(R.id.historyRecyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        noHistoryTextView = findViewById(R.id.noHistoryTextView);
        Button btnBack = findViewById(R.id.btnBack);

        // Fix History Back Button Navigation
        btnBack.setOnClickListener(v -> onBackPressed());

        // Enable toolbar back button if present
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Delivery History");
        }

        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(historyList);
        historyRecyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(this::fetchHistory);

        fetchHistory();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        // Ensure we return to dashboard cleanly
        Intent intent = new Intent(this, DriverDashboard.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void fetchHistory() {
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(true);
        int driverId = getDriverId();
        String token = getApiToken();

        if (driverId <= 0) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        // Fetching delivered orders for this driver
        String url = Constants.URL_ORDERS + "?driver_id=" + driverId + "&status=" + Constants.STATUS_DELIVERED;
        
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    parseHistory(response);
                },
                error -> {
                    Log.e(TAG, "History fetch failed, trying legacy", error);
                    fetchHistoryLegacy(driverId, token);
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };

        requestQueue.add(request);
    }

    private void fetchHistoryLegacy(int driverId, String token) {
        String url = Constants.URL_GET_DRIVER_ORDERS_LEGACY + "?driver_id=" + driverId + "&status=" + Constants.STATUS_DELIVERED;
        
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    parseHistory(response);
                },
                error -> {
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Failed to load history.", Toast.LENGTH_SHORT).show();
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + token);
                return headers;
            }
        };
        requestQueue.add(request);
    }

    private void parseHistory(JSONObject response) {
        historyList.clear();
        JSONArray orders = response.optJSONArray("orders");
        if (orders == null) orders = response.optJSONArray("data");
        
        if (orders != null && orders.length() > 0) {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order != null) {
                    historyList.add(order);
                }
            }
            noHistoryTextView.setVisibility(View.GONE);
        } else {
            noHistoryTextView.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private int getDriverId() {
        return getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getInt("user_id", -1);
    }

    private String getApiToken() {
        return AuthSessionManager.getValidAccessTokenOrNull(this);
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<JSONObject> items;

        HistoryAdapter(List<JSONObject> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_driver_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject order = items.get(position);
            int id = order.optInt("id", order.optInt("order_id", -1));
            holder.orderId.setText("Order #" + id);
            holder.status.setText(order.optString("status", "DELIVERED").toUpperCase());
            holder.restaurantName.setText(order.optString("restaurant_name", "Restaurant"));
            holder.customerName.setText("Customer: " + order.optString("customer_name", "N/A"));
            holder.address.setText("Address: " + order.optString("delivery_address", order.optString("address", "N/A")));
            holder.date.setText("Date: " + order.optString("created_at", "Recent"));
            holder.amount.setText("Total: P" + String.format("%.2f", order.optDouble("total_amount", order.optDouble("total", 0.0))));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView orderId, status, restaurantName, customerName, address, date, amount;

            ViewHolder(View v) {
                super(v);
                orderId = v.findViewById(R.id.historyOrderId);
                status = v.findViewById(R.id.historyStatus);
                restaurantName = v.findViewById(R.id.historyRestaurantName);
                customerName = v.findViewById(R.id.historyCustomerName);
                address = v.findViewById(R.id.historyAddress);
                date = v.findViewById(R.id.historyDate);
                amount = v.findViewById(R.id.historyAmount);
            }
        }
    }
}
