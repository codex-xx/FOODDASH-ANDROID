package com.example.fooddash;

public class Constants {
    // IMPORTANT: Make sure this is the correct IP address for your XAMPP server.
    // Try using "10.0.2.2" if you are using the Android Emulator.
    // Use your actual IP (e.g., 192.168.1.7) if using a physical device.
    public static final String IP_ADDRESS = "192.168.1.7"; 

    public static final String BASE_URL = "http://" + IP_ADDRESS + "/FoodDash/api/";
    public static final String URL_SEND_NOTIFICATION_EMAIL = BASE_URL + "send-notification-email";
    
    // Auth
    public static final String URL_LOGIN = BASE_URL + "login";

    // Core order-flow endpoints (REST style)
    public static final String URL_RESTAURANTS = BASE_URL + "restaurants";
    public static final String URL_MENU = BASE_URL + "menu";
    public static final String URL_ORDERS = BASE_URL + "orders";
    public static final String URL_DRIVER_ACCEPT_ORDER = BASE_URL + "driver/accept_order";
    public static final String URL_UPDATE_STATUS = BASE_URL + "update_status";

    // Backward-compatible fallback endpoints used by older backends
    public static final String URL_GET_ALL_RESTAURANTS = BASE_URL + "get_all_restaurants.php";
    public static final String URL_GET_MENU_BY_RESTAURANT = BASE_URL + "get_menus_by_restaurant.php";
    public static final String URL_GET_MENU_LEGACY = BASE_URL + "get_menus.php";
    public static final String URL_PLACE_ORDER_LEGACY = BASE_URL + "place_order.php";
    public static final String URL_GET_ORDERS_LEGACY = BASE_URL + "get_orders.php";
    public static final String URL_GET_DRIVER_ORDERS_LEGACY = BASE_URL + "get_driver_orders.php";
    public static final String URL_UPDATE_ORDER_STATUS_LEGACY = BASE_URL + "update_order_status.php";

    // Canonical order statuses (Lowercased for database compatibility)
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_PREPARING = "preparing";
    public static final String STATUS_READY = "ready";
    public static final String STATUS_ASSIGNED = "assigned";
    public static final String STATUS_ARRIVED_RESTAURANT = "arrived";
    public static final String STATUS_PICKED_UP = "picked_up";
    public static final String STATUS_ON_THE_WAY = "on_the_way";
    public static final String STATUS_DELIVERED = "delivered";

    // Delivery type keys and fixed fees.
    public static final String DELIVERY_MOTORCYCLE = "motorcycle";
    public static final String DELIVERY_TRICYCLE = "tricycle";
    public static final String DELIVERY_CAB = "cab";

    public static final double FEE_MOTORCYCLE = 39.00;
    public static final double FEE_TRICYCLE = 69.00;
    public static final double FEE_CAB = 109.00;
}
