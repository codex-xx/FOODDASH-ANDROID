package com.example.fooddash;

import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Url;
import retrofit2.http.Query;

public interface ApiService {

    @FormUrlEncoded
    @POST
    Call<ResponseBody> login(@Url String url, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST
    Call<ResponseBody> register(@Url String url, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST
    Call<ResponseBody> sendOtp(@Url String url, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST
    Call<ResponseBody> verifyOtp(@Url String url, @FieldMap Map<String, String> fields);

    @GET("restaurants")
    Call<ResponseBody> getRestaurants();

    @GET("get_all_restaurants.php")
    Call<ResponseBody> getAllRestaurants();

    @GET("menu")
    Call<ResponseBody> getMenu(@Query("restaurant_id") int restaurantId);

    @GET("get_menus_by_restaurant.php")
    Call<ResponseBody> getMenusByRestaurant(@Query("restaurant_id") int restaurantId);

    @GET("get_menus.php")
    Call<ResponseBody> getMenusLegacy(@Query("restaurant_id") int restaurantId);

    @GET("search.php")
    Call<ResponseBody> search(@Query("query") String query);

    @GET("orders/{userId}")
    Call<ResponseBody> getOrders(@Path("userId") int userId);

    @GET("orders/{orderId}")
    Call<ResponseBody> getOrderDetails(@Path("orderId") int orderId);

    @GET("get_orders.php")
    Call<ResponseBody> getOrdersLegacy(@Query("user_id") int userId);

    @GET("get_order_status.php")
    Call<ResponseBody> getOrderStatusLegacy(@Query("order_id") int orderId);

    @FormUrlEncoded
    @POST("orders")
    Call<ResponseBody> placeOrder(@FieldMap Map<String, Object> fields);

    @GET("driver/orders")
    Call<ResponseBody> getDriverOrders();

    @GET("get_driver_orders.php")
    Call<ResponseBody> getDriverOrdersLegacy();

    @FormUrlEncoded
    @POST
    Call<ResponseBody> updateOrderStatus(@Url String url, @FieldMap Map<String, String> fields);

    @retrofit2.http.Multipart
    @POST
    Call<ResponseBody> updateOrderStatusMultipart(
        @Url String url,
        @retrofit2.http.PartMap Map<String, okhttp3.RequestBody> fields,
        @retrofit2.http.Part okhttp3.MultipartBody.Part image
    );

    @FormUrlEncoded
    @POST("orders/{orderId}/status")
    Call<ResponseBody> updateOrderStatusWithId(@Path("orderId") int orderId, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST
    Call<ResponseBody> updateOrderStatusLegacy(@Url String url, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST("place_order.php")
    Call<ResponseBody> placeOrderLegacy(@FieldMap Map<String, String> fields);

    @GET("get-profile")
    Call<ResponseBody> getProfile();

    @FormUrlEncoded
    @POST
    Call<ResponseBody> acceptOrder(@Url String url, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST
    Call<ResponseBody> acceptOrderLegacy(@Url String url, @FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST("simulate-payment.php")
    Call<ResponseBody> simulatePayment(@FieldMap Map<String, Object> fields);

    @GET("test")
    Call<ResponseBody> testConnection();

    @FormUrlEncoded
    @POST("forgot-password")
    Call<ResponseBody> forgotPassword(@FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST("reset-password")
    Call<ResponseBody> resetPassword(@FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST("verify-code")
    Call<ResponseBody> verifyCode(@FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST("mfa/verify")
    Call<ResponseBody> verifyMfa(@FieldMap Map<String, String> fields);

    @FormUrlEncoded
    @POST("verify-mfa")
    Call<ResponseBody> verifyMfaFallback(@FieldMap Map<String, String> fields);
    @FormUrlEncoded
    @POST("send-notification-email")
    Call<ResponseBody> sendNotificationEmail(@FieldMap Map<String, String> fields);

    @retrofit2.http.Multipart
    @POST
    Call<ResponseBody> uploadDeliveryProof(
        @Url String url,
        @retrofit2.http.Part okhttp3.MultipartBody.Part image,
        @retrofit2.http.PartMap Map<String, okhttp3.RequestBody> fields,
        @Query("api_token") String apiToken
    );

    @GET("orders")
    Call<ResponseBody> getOrdersByDriver(@Query("driver_id") int driverId);

    @GET("get_driver_orders.php")
    Call<ResponseBody> getDriverOrdersLegacyWithDriverId(@Query("driver_id") int driverId);
}
