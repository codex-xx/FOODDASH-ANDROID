# FoodDash Notification API Integration Guide

## Overview

This document explains how your Android app should call the FoodDash notifications API endpoint. The API uses **Bearer token authentication** to ensure secure communication.

## API Endpoints

### Modern Endpoint
```
POST /FoodDash/api/notifications
```

### Legacy Endpoint (Fallback)
```
POST /FoodDash/api/notifications.php
```

## Authentication

All requests to the notifications API **must include a Bearer token** in the Authorization header:

```
Authorization: Bearer {access_token}
```

The access token is automatically retrieved from `AuthSessionManager.getValidAccessTokenOrNull()`.

## Request Payload Format

```json
{
  "title": "Order Update",
  "message": "Your order is out for delivery",
  "type": "order_status",
  "data": {
    "order_id": 123,
    "status": "out_for_delivery"
  }
}
```

### Payload Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Notification title |
| `message` | string | Yes | Notification message content |
| `type` | string | Yes | Notification type (e.g., "order_status") |
| `data` | object | No | Additional data containing order details |
| `data.order_id` | integer | No | The order ID being notified about |
| `data.status` | string | No | The current status of the order |

## Usage in Android Code

### Simple Example: Send Order Status Notification

```java
import com.example.fooddash.NotificationService;

// In your Activity or Fragment
NotificationService.sendOrderStatusNotification(
    context,
    "Your order is out for delivery",
    123,              // order_id
    "out_for_delivery", // status
    null              // callback (optional)
);
```

### Example with Callback for Handling Response

```java
NotificationService.sendOrderStatusNotification(
    this,  // context
    "Your order has been accepted by the restaurant",
    456,   // order_id
    "accepted",
    new NotificationService.Callback() {
        @Override
        public void onSuccess(String response) {
            Log.d("NotificationService", "Notification sent successfully: " + response);
            Toast.makeText(context, "Order notification sent", Toast.LENGTH_SHORT).show();
        }
        
        @Override
        public void onError(String error) {
            Log.e("NotificationService", "Failed to send notification: " + error);
            Toast.makeText(context, "Failed to send notification", Toast.LENGTH_SHORT).show();
        }
    }
);
```

### Example: Custom Notification (Advanced)

If you need to send a notification with custom data, you can use the underlying sendNotification method:

```java
// This example demonstrates the structure for advanced usage
// The NotificationService handles all the Bearer token authentication automatically
```

## Common Order Status Values

Use these status values when sending order status notifications:

- `pending` - Order placed but not yet accepted
- `accepted` - Restaurant accepted the order
- `preparing` - Gift being prepared
- `ready` - Food ready for pickup
- `picked_up` - Driver picked up the order
- `arrived_at_restaurant` - Driver arrived at restaurant
- `out_for_delivery` - Order is out for delivery
- `delivered` - Order successfully delivered
- `cancelled` - Order was cancelled

## Response Handling

The API returns a JSON response. The `NotificationService` automatically checks for success by looking for:

1. `"success": true` in the response, OR
2. `"status": "success"` or `"status": "ok"` in the response

## Error Handling

The service includes built-in error handling:

- **Network errors** are logged and passed to the callback
- **Missing authentication token** - The notification will not be sent if no valid token is available
- **Invalid context** - The method will validate the context before making the request
- **JSON parsing errors** - Payload build errors are caught and logged

All errors include detailed logging via Android's Log class with the tag `"NotificationService"`.

## Required Imports in Constants.java

The following constants are already defined for you:

```java
// Modern API endpoint
public static final String URL_SEND_NOTIFICATION = BASE_URL + "notifications";

// Legacy endpoint (fallback)
public static final String URL_SEND_NOTIFICATION_LEGACY = BASE_URL + "notifications.php";
```

## Integration Points in Your App

### When to Send Notifications

1. **Order Accepted** - When restaurant accepts the order
   ```java
   NotificationService.sendOrderStatusNotification(
       context, 
       "Your order has been accepted", 
       orderId, 
       "accepted", 
       null
   );
   ```

2. **Order Ready for Pickup** - When food is ready
   ```java
   NotificationService.sendOrderStatusNotification(
       context, 
       "Your order is ready for pickup", 
       orderId, 
       "ready", 
       null
   );
   ```

3. **Out for Delivery** - When driver is heading to customer
   ```java
   NotificationService.sendOrderStatusNotification(
       context, 
       "Your order is on its way", 
       orderId, 
       "out_for_delivery", 
       null
   );
   ```

4. **Delivered** - When order is successfully delivered
   ```java
   NotificationService.sendOrderStatusNotification(
       context, 
       "Your order has been delivered", 
       orderId, 
       "delivered", 
       null
   );
   ```

## HTTP Headers

The `NotificationService` automatically adds these headers:

```
Authorization: Bearer {access_token}
Content-Type: application/json
```

## Retry Policy

Requests are automatically retried with the following policy:

- **Timeout**: 10 seconds
- **Max Retries**: Default maximum retries
- **Backoff Multiplier**: Default exponential backoff

## Logging

All notification requests are logged using Android's Log class:

```java
// Tag
"NotificationService"

// Log levels:
// INFO: Successful notifications
// WARN: Non-success API responses
// ERROR: Network errors and exceptions
```

To view logs in Android Studio:
```
adb logcat | grep NotificationService
```

## Requirements

- **AuthSessionManager** must be available in your app
- **Valid access token** from the backend API
- **Internet connectivity** on the device
- **Volley library** (already included in dependencies)

## File References

- **Service Class**: `com.example.fooddash.NotificationService`
- **Constants**: `com.example.fooddash.Constants`
- **Authentication**: `com.example.fooddash.AuthSessionManager`

## Example Full Implementation

```java
// In your Activity when order status changes
private void handleOrderStatusChange(int orderId, String newStatus, String message) {
    // Send notification via API
    NotificationService.sendOrderStatusNotification(
        this,
        message,
        orderId,
        newStatus,
        new NotificationService.Callback() {
            @Override
            public void onSuccess(String response) {
                Log.d("MainActivity", "Notification sent: " + response);
                // Update UI or continue with other operations
            }

            @Override
            public void onError(String error) {
                Log.e("MainActivity", "Notification failed: " + error);
                // Handle error - maybe retry or show user message
            }
        }
    );
}

// Call this when you detect an order status update
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // Example: Send notification when order is accepted
    handleOrderStatusChange(
        123,
        "accepted",
        "Restaurant has accepted your order!"
    );
}
```

## Troubleshooting

### Notification Not Sent
- **Check**: Is there a valid authentication token?
- **Check**: Is the app connected to the internet?
- **Check**: Are the order ID and status values correct?
- **Check**: Review logs in Android Studio for error messages

### "No authentication token" Error
- Ensure user is logged in
- Verify `AuthSessionManager.getValidAccessTokenOrNull()` returns a token
- Check token expiration and refresh if needed

### "Failed to build notification payload" Error
- Verify all required fields (title, message, type) are provided
- Check for any null values being passed

## API Backend Expectations

The backend API should:

1. Validate the Bearer token
2. Extract user information from the token
3. Accept the notification payload
4. Store or process the notification
5. Return a success response with `"success": true` or `"status": "success"`

## Additional Questions?

For more information about:
- **Authentication**: See `AuthSessionManager.java`
- **Similar Services**: See `EmailNotificationService.java`
- **Network Configuration**: See `Constants.java`

