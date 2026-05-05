package com.example.fooddash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class ProfileActivity extends AppCompatActivity {

    private TextView profileNameTextView;
    private TextView profileEmailTextView;
    private TextView profilePhoneTextView;
    private TextView profileRoleTextView;
    private TextView profileAddressTextView;
    private TextView profilePaymentTextView;

    private Button tabHomeButton;
    private Button tabOrdersButton;
    private Button tabCartButton;
    private Button tabNotificationsButton;
    private Button tabProfileButton;
    private TextView tabCartBadgeTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        if (!AccessControlManager.requireAccess(this,
                AccessControlManager.Resource.CUSTOMER_DASHBOARD,
                AccessControlManager.Action.READ)) {
            return;
        }

        profileNameTextView = findViewById(R.id.profileNameTextView);
        profileEmailTextView = findViewById(R.id.profileEmailTextView);
        profilePhoneTextView = findViewById(R.id.profilePhoneTextView);
        profileRoleTextView = findViewById(R.id.profileRoleTextView);
        profileAddressTextView = findViewById(R.id.profileAddressTextView);
        profilePaymentTextView = findViewById(R.id.profilePaymentTextView);

        Button btnEditAddress = findViewById(R.id.btnEditAddress);
        Button btnChangePayment = findViewById(R.id.btnChangePayment);
        Button btnLogoutFromProfile = findViewById(R.id.btnLogoutFromProfile);

        tabHomeButton = findViewById(R.id.tabHomeButton);
        tabOrdersButton = findViewById(R.id.tabOrdersButton);
        tabCartButton = findViewById(R.id.tabCartButton);
        tabNotificationsButton = findViewById(R.id.tabNotificationsButton);
        tabProfileButton = findViewById(R.id.tabProfileButton);
        tabCartBadgeTextView = findViewById(R.id.tabCartBadgeTextView);

        setupBottomNavigation();
        bindProfileData();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();

        btnEditAddress.setOnClickListener(v -> showEditAddressDialog());
        btnChangePayment.setOnClickListener(v -> showPaymentDialog());
        // Order history access moved to Orders screen; removed from profile
        btnLogoutFromProfile.setOnClickListener(v -> performLogout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindProfileData();
        updateCartBadgeFromPrefs();
        updateNotificationsTabCount();
    }

    private void bindProfileData() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String name = prefs.getString("user_name", "Customer");
        String email = prefs.getString("user_email", "-");
        String phone = prefs.getString("contact_number", prefs.getString("phone", "-"));
        String role = prefs.getString("user_role", "customer");
        String address = prefs.getString("delivery_address", "No saved address");
        String payment = prefs.getString("preferred_payment", "cod");

        profileNameTextView.setText("Name: " + name);
        profileEmailTextView.setText("Email: " + email);
        profilePhoneTextView.setText("Phone: " + phone);
        profileRoleTextView.setText("Role: " + role);
        profileAddressTextView.setText("Saved Address: " + address);
        profilePaymentTextView.setText("Preferred Payment: " + payment.toUpperCase());
    }

    private void showEditAddressDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter new delivery address");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        input.setText(getSharedPreferences("fooddash_prefs", MODE_PRIVATE).getString("delivery_address", ""));
        input.setPadding(32, 24, 32, 24);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(32, 12, 32, 0);
        wrapper.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Edit Saved Address")
                .setView(wrapper)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (TextUtils.isEmpty(value)) {
                        Toast.makeText(this, "Address cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    getSharedPreferences("fooddash_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("delivery_address", value)
                            .apply();
                    bindProfileData();
                    Toast.makeText(this, "Address updated", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showPaymentDialog() {
        final String[] options = new String[]{"cod", "gcash", "maya"};
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String current = prefs.getString("preferred_payment", "cod");
        int selected = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equalsIgnoreCase(current)) {
                selected = i;
                break;
            }
        }

        final int[] choice = new int[]{selected};
        new AlertDialog.Builder(this)
                .setTitle("Preferred Payment")
                .setSingleChoiceItems(new CharSequence[]{"Cash on Delivery", "GCash", "Maya"}, selected,
                        (dialog, which) -> choice[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    prefs.edit().putString("preferred_payment", options[choice[0]]).apply();
                    bindProfileData();
                    Toast.makeText(this, "Preferred payment updated", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void setupBottomNavigation() {
        highlightBottomTab(tabProfileButton);

        if (tabHomeButton != null) {
            tabHomeButton.setOnClickListener(v -> {
                highlightBottomTab(tabHomeButton);
                Intent intent = new Intent(this, CustomerDashboard.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }

        if (tabOrdersButton != null) {
            tabOrdersButton.setOnClickListener(v -> {
                highlightBottomTab(tabOrdersButton);
                startActivity(new Intent(this, OrderTrackingActivity.class));
                finish();
            });
        }

        if (tabCartButton != null) {
            tabCartButton.setOnClickListener(v -> {
                highlightBottomTab(tabCartButton);
                openCartFromPrefs();
            });
        }

        if (tabNotificationsButton != null) {
            tabNotificationsButton.setOnClickListener(v -> {
                highlightBottomTab(tabNotificationsButton);
                startActivity(new Intent(this, NotificationActivity.class));
                finish();
            });
        }

        if (tabProfileButton != null) {
            tabProfileButton.setOnClickListener(v -> highlightBottomTab(tabProfileButton));
        }
    }

    private void highlightBottomTab(Button selected) {
        applyBottomTabStyle(tabHomeButton, selected == tabHomeButton);
        applyBottomTabStyle(tabOrdersButton, selected == tabOrdersButton);
        applyBottomTabStyle(tabCartButton, selected == tabCartButton);
        applyBottomTabStyle(tabNotificationsButton, selected == tabNotificationsButton);
        applyBottomTabStyle(tabProfileButton, selected == tabProfileButton);
    }

    private void applyBottomTabStyle(Button tab, boolean isSelected) {
        if (tab == null) return;
        if (isSelected) {
            tab.setBackgroundResource(R.drawable.bottom_nav_tab_selected);
            tab.setTextColor(getResources().getColor(R.color.white));
        } else {
            tab.setBackgroundResource(R.drawable.bottom_nav_tab_unselected);
            tab.setTextColor(getResources().getColor(R.color.black));
        }
    }

    private void updateCartBadgeFromPrefs() {
        if (tabCartBadgeTextView == null) return;
        int count = getCartItemCountFromPrefs();
        if (count <= 0) {
            tabCartBadgeTextView.setVisibility(android.view.View.GONE);
            return;
        }
        tabCartBadgeTextView.setVisibility(android.view.View.VISIBLE);
        tabCartBadgeTextView.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private void updateNotificationsTabCount() {
        if (tabNotificationsButton == null) return;
        int count = getNotificationCountFromPrefs();
        if (count <= 0) {
            tabNotificationsButton.setText("Notifications");
            return;
        }
        String badge = count > 99 ? "99+" : String.valueOf(count);
        tabNotificationsButton.setText("Notif (" + badge + ")");
    }

    private int getNotificationCountFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString("notification_history_json", "[]"));
            return array.length();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int getCartItemCountFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        int count = 0;
        try {
            JSONArray array = new JSONArray(prefs.getString("global_cart_json", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) count += obj.optInt("quantity", 0);
            }
        } catch (Exception ignored) {}
        return count;
    }

    private void openCartFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("fooddash_prefs", MODE_PRIVATE);
        String cartJson = prefs.getString("global_cart_json", "[]");
        try {
            JSONArray cart = new JSONArray(cartJson);
            if (cart.length() == 0) {
                Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception ignored) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CartActivity.class);
        intent.putExtra("cart_items_json", cartJson);
        startActivity(intent);
        finish();
    }

    private void performLogout() {
        AuthSessionManager.clearSession(this);
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("fooddash_prefs", MODE_PRIVATE);

        // Preserve notification history across customer logout.
        String notificationHistory = prefs.getString("notification_history_json", "[]");
        prefs.edit().clear().apply();
        prefs.edit().putString("notification_history_json", notificationHistory).apply();

        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
