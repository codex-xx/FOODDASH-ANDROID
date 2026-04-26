package com.example.fooddash;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class AccessControlManager {

    private static final String PREFS_NAME = "fooddash_prefs";
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_DRIVER = "driver";
    public static final String ROLE_RESTAURANT = "restaurant";

    public enum Action {
        READ,
        WRITE,
        UPDATE,
        DELETE
    }

    public enum Resource {
        CUSTOMER_DASHBOARD,
        DRIVER_DASHBOARD,
        RESTAURANT_DASHBOARD,
        CART,
        CHECKOUT,
        ORDER_TRACKING,
        ACTIVE_ORDER,
        DRIVER_HISTORY,
        ORDERS
    }

    private static final Map<String, EnumMap<Resource, EnumSet<Action>>> POLICY = new java.util.HashMap<>();

    static {
        EnumMap<Resource, EnumSet<Action>> customerRules = new EnumMap<>(Resource.class);
        customerRules.put(Resource.CUSTOMER_DASHBOARD, EnumSet.of(Action.READ));
        customerRules.put(Resource.CART, EnumSet.of(Action.READ, Action.WRITE, Action.UPDATE, Action.DELETE));
        customerRules.put(Resource.CHECKOUT, EnumSet.of(Action.READ, Action.WRITE));
        customerRules.put(Resource.ORDER_TRACKING, EnumSet.of(Action.READ));
        customerRules.put(Resource.ORDERS, EnumSet.of(Action.READ, Action.WRITE));
        POLICY.put(ROLE_CUSTOMER, customerRules);

        EnumMap<Resource, EnumSet<Action>> driverRules = new EnumMap<>(Resource.class);
        driverRules.put(Resource.DRIVER_DASHBOARD, EnumSet.of(Action.READ));
        driverRules.put(Resource.ACTIVE_ORDER, EnumSet.of(Action.READ, Action.UPDATE));
        driverRules.put(Resource.DRIVER_HISTORY, EnumSet.of(Action.READ));
        driverRules.put(Resource.ORDERS, EnumSet.of(Action.READ, Action.UPDATE));
        POLICY.put(ROLE_DRIVER, driverRules);

        EnumMap<Resource, EnumSet<Action>> restaurantRules = new EnumMap<>(Resource.class);
        restaurantRules.put(Resource.RESTAURANT_DASHBOARD, EnumSet.of(Action.READ));
        restaurantRules.put(Resource.ACTIVE_ORDER, EnumSet.of(Action.READ, Action.UPDATE));
        restaurantRules.put(Resource.ORDERS, EnumSet.of(Action.READ, Action.UPDATE));
        POLICY.put(ROLE_RESTAURANT, restaurantRules);
    }

    private AccessControlManager() {
    }

    public static boolean requireAccess(AppCompatActivity activity, Resource resource, Action action) {
        if (activity == null) {
            return false;
        }

        if (!hasValidSession(activity)) {
            Toast.makeText(activity, "Please login again", Toast.LENGTH_SHORT).show();
            forceLogin(activity);
            return false;
        }

        if (!isUserContextValid(activity)) {
            Toast.makeText(activity, "Session data is incomplete. Please login again", Toast.LENGTH_LONG).show();
            AuthSessionManager.clearSession(activity);
            forceLogin(activity);
            return false;
        }

        String role = getCurrentRole(activity);
        if (!canPerform(role, resource, action)) {
            Toast.makeText(activity, "Access denied for this action", Toast.LENGTH_LONG).show();
            redirectToAllowedHome(activity, role);
            return false;
        }

        return true;
    }

    public static boolean canPerform(Context context, Resource resource, Action action) {
        if (context == null) {
            return false;
        }
        return canPerform(getCurrentRole(context), resource, action);
    }

    public static String getCurrentRole(Context context) {
        if (context == null) {
            return "";
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return normalizeRole(prefs.getString("user_role", ""));
    }

    public static boolean isUserContextValid(Context context) {
        if (context == null) {
            return false;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);
        String role = normalizeRole(prefs.getString("user_role", ""));
        return userId > 0 && !TextUtils.isEmpty(role);
    }

    private static boolean hasValidSession(Context context) {
        String token = AuthSessionManager.getValidAccessTokenOrNull(context);
        return !TextUtils.isEmpty(token);
    }

    private static boolean canPerform(String role, Resource resource, Action action) {
        EnumMap<Resource, EnumSet<Action>> roleRules = POLICY.get(normalizeRole(role));
        if (roleRules == null) {
            return false;
        }

        EnumSet<Action> actions = roleRules.get(resource);
        return actions != null && actions.contains(action);
    }

    public static String normalizeRole(String role) {
        if (role == null) {
            return "";
        }

        String normalized = role.trim().toLowerCase();
        if (normalized.contains(ROLE_DRIVER)) {
            return ROLE_DRIVER;
        }
        if (normalized.contains(ROLE_CUSTOMER)) {
            return ROLE_CUSTOMER;
        }
        if (normalized.contains(ROLE_RESTAURANT)) {
            return ROLE_RESTAURANT;
        }
        return normalized;
    }

    private static void redirectToAllowedHome(AppCompatActivity activity, String role) {
        Intent destination;
        String nRole = normalizeRole(role);
        if (ROLE_DRIVER.equals(nRole)) {
            destination = new Intent(activity, DriverDashboard.class);
        } else if (ROLE_CUSTOMER.equals(nRole)) {
            destination = new Intent(activity, CustomerDashboard.class);
        } else if (ROLE_RESTAURANT.equals(nRole)) {
            // Placeholder for RestaurantDashboard
            // destination = new Intent(activity, RestaurantDashboard.class);
            forceLogin(activity);
            return;
        } else {
            forceLogin(activity);
            return;
        }

        destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(destination);
        activity.finish();
    }

    private static void forceLogin(AppCompatActivity activity) {
        Intent loginIntent = new Intent(activity, LoginActivity.class);
        loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(loginIntent);
        activity.finish();
    }
}
