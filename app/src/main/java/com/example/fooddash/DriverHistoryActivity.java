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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class DriverHistoryActivity extends AppCompatActivity {

    private static final String TAG = "DriverHistoryActivity";
    private static final String DRIVER_HISTORY_PREFS_NAME = "fooddash_driver_history_cache";
    private static final String KEY_DRIVER_DELIVERY_HISTORY = "driver_delivery_history_json";
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

        // Fetch all orders for this driver and filter delivered locally.
        // Some API variants ignore combined driver/status filters, which would hide valid history items.
        String url = Constants.URL_ORDERS + "?driver_id=" + driverId;
        
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
        String url = Constants.URL_GET_DRIVER_ORDERS_LEGACY + "?driver_id=" + driverId;
        
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
        Map<Integer, JSONObject> uniqueOrders = new HashMap<>();
        addOrdersFromResponse(uniqueOrders, response);
        addOrdersFromLocalCache(uniqueOrders);

        historyList.addAll(uniqueOrders.values());
        Collections.sort(historyList, (left, right) -> {
            int rightId = right.optInt("id", right.optInt("order_id", -1));
            int leftId = left.optInt("id", left.optInt("order_id", -1));
            return Integer.compare(rightId, leftId);
        });

        noHistoryTextView.setVisibility(historyList.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void addOrdersFromResponse(Map<Integer, JSONObject> uniqueOrders, JSONObject response) {
        JSONArray orders = response.optJSONArray("orders");
        if (orders == null) orders = response.optJSONArray("data");
        if (orders == null) {
            JSONObject data = response.optJSONObject("data");
            if (data != null) {
                orders = data.optJSONArray("orders");
            }
        }
        if (orders == null && (response.has("id") || response.has("order_id") || response.has("status"))) {
            orders = new JSONArray();
            orders.put(response);
        }

        if (orders == null) {
            return;
        }

        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null) {
                continue;
            }

            String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
            if (!Constants.STATUS_DELIVERED.equals(status)) {
                continue;
            }

            int id = order.optInt("id", order.optInt("order_id", -1));
            if (id > 0) {
                uniqueOrders.put(id, order);
            }
        }
    }

    private void addOrdersFromLocalCache(Map<Integer, JSONObject> uniqueOrders) {
        try {
            SharedPreferences prefs = getSharedPreferences(DRIVER_HISTORY_PREFS_NAME, MODE_PRIVATE);
            JSONArray localHistory = new JSONArray(prefs.getString(KEY_DRIVER_DELIVERY_HISTORY, "[]"));
            for (int i = 0; i < localHistory.length(); i++) {
                JSONObject order = localHistory.optJSONObject(i);
                if (order == null) {
                    continue;
                }

                String status = ActiveOrderActivity.normalizeStatus(order.optString("status", ""));
                if (!Constants.STATUS_DELIVERED.equals(status)) {
                    continue;
                }

                int id = order.optInt("id", order.optInt("order_id", -1));
                if (id > 0 && !uniqueOrders.containsKey(id)) {
                    uniqueOrders.put(id, order);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read local driver delivery history", e);
        }
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
            holder.viewDetailsButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(v.getContext(), OrderDetailActivity.class);
                    intent.putExtra("order_json", order.toString());
                    intent.putExtra("show_driver_details", false);
                    v.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(v.getContext(), "Unable to open order details", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView orderId;
            Button viewDetailsButton;

            ViewHolder(View v) {
                super(v);
                orderId = v.findViewById(R.id.historyOrderId);
                viewDetailsButton = v.findViewById(R.id.historyViewDetailsButton);
            }
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value) && !"undefined".equalsIgnoreCase(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
