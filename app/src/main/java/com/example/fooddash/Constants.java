package com.example.fooddash;

public class Constants {
    // IMPORTANT: Make sure this is the correct IP address for your XAMPP server.
    public static final String IP_ADDRESS = "192.168.100.142";

    public static final String BASE_URL = "http://" + IP_ADDRESS + "/FoodDash/api/";
    public static final String URL_SEND_NOTIFICATION_EMAIL = BASE_URL + "send-notification-email";
    
    // Auth
    public static final String URL_LOGIN = BASE_URL + "login";
    public static final String URL_REGISTER = BASE_URL + "register";
    public static final String URL_REGISTER_SEND_OTP = BASE_URL + "register/send-otp";
    public static final String URL_REGISTER_SEND_OTP_FALLBACK = BASE_URL + "send-register-otp";
    
    // Verification endpoints
    public static final String URL_REGISTER_VERIFY_OTP = BASE_URL + "verify-register-otp"; 
    public static final String URL_VERIFY_CODE = BASE_URL + "verify-code";
    
    public static final String URL_MFA_VERIFY = BASE_URL + "mfa/verify";
    public static final String URL_MFA_VERIFY_FALLBACK = BASE_URL + "verify-mfa";
    public static final String PASSWORD_HASH_ALGORITHM = "argon2id";

    // Core order-flow endpoints
    public static final String URL_RESTAURANTS = BASE_URL + "restaurants";
    public static final String URL_MENU = BASE_URL + "menu";
    public static final String URL_ORDERS = BASE_URL + "orders";
    public static final String URL_DRIVER_ACCEPT_ORDER = BASE_URL + "driver/accept_order";
    public static final String URL_UPDATE_STATUS = BASE_URL + "update_status";

    // Backward-compatible fallback endpoints
    public static final String URL_GET_ALL_RESTAURANTS = BASE_URL + "get_all_restaurants.php";
    public static final String URL_GET_MENU_BY_RESTAURANT = BASE_URL + "get_menus_by_restaurant.php";
    public static final String URL_GET_MENU_LEGACY = BASE_URL + "get_menus.php";
    public static final String URL_PLACE_ORDER_LEGACY = BASE_URL + "place_order.php";
    public static final String URL_GET_ORDERS_LEGACY = BASE_URL + "get_orders.php";
    public static final String URL_GET_DRIVER_ORDERS_LEGACY = BASE_URL + "get_driver_orders.php";
    public static final String URL_UPDATE_ORDER_STATUS_LEGACY = BASE_URL + "update_status.php";
    public static final String URL_DRIVER_ACCEPT_ORDER_LEGACY = BASE_URL + "driver/accept_order.php";

    // Canonical order statuses (Source of Truth)
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_PREPARING = "preparing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_PICKED_UP = "picked_up";
    public static final String STATUS_ARRIVED_RESTAURANT = "arrived_at_restaurant";
    public static final String STATUS_OUT_FOR_DELIVERY = "out_for_delivery";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_CANCELLED = "cancelled";

    // Delivery type keys and fixed fees
    public static final String DELIVERY_MOTORCYCLE = "motorcycle";
    public static final String DELIVERY_TRICYCLE = "tricycle";
    public static final String DELIVERY_CAB = "cab";

    public static final double FEE_MOTORCYCLE = 39.00;
    public static final double FEE_TRICYCLE = 69.00;
    public static final double FEE_CAB = 109.00;
}
