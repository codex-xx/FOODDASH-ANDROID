package com.example.fooddash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OrderFlowManager {

    public static final class AddOnSelection {
        private final String name;
        private final double price;

        public AddOnSelection(String name, double price) {
            this.name = name == null ? "" : name.trim();
            this.price = Math.max(0, price);
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }
    }

    public static final class CartItem {
        private final String name;
        private final double basePrice;
        private int quantity;
        private final List<AddOnSelection> addOns;
        private final String preference;

        public CartItem(String name, double basePrice, int quantity, List<AddOnSelection> addOns, String preference) {
            this.name = name == null ? "" : name.trim();
            this.basePrice = Math.max(0, basePrice);
            this.quantity = Math.max(1, quantity);
            this.addOns = addOns == null ? new ArrayList<>() : new ArrayList<>(addOns);
            this.preference = preference == null ? "" : preference.trim();
        }

        public String getName() {
            return name;
        }

        public double getBasePrice() {
            return basePrice;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = Math.max(0, quantity);
        }

        public List<AddOnSelection> getAddOns() {
            return addOns;
        }

        public String getPreference() {
            return preference;
        }

        public double getUnitPrice() {
            double addOnTotal = 0;
            for (AddOnSelection addOn : addOns) {
                addOnTotal += addOn.getPrice();
            }
            return basePrice + addOnTotal;
        }

        public double getLineTotal() {
            return getUnitPrice() * quantity;
        }

        public String getAddOnSummary() {
            if (addOns.isEmpty()) {
                return "No add-ons";
            }

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < addOns.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(addOns.get(i).getName());
            }
            return builder.toString();
        }
    }

    private static final List<CartItem> cartItems = new ArrayList<>();
    private static final double DEFAULT_DELIVERY_FEE = 39.0;

    private static String deliveryAddress = "";
    private static String deliveryNotes = "";
    private static String promoCode = "NONE";
    private static String paymentMethod = "Cash";
    private static String orderNumber = "";

    private OrderFlowManager() {
    }

    public static void addItem(String name, double basePrice, int quantity, List<AddOnSelection> addOns, String preference) {
        if (name == null || name.trim().isEmpty() || quantity <= 0) {
            return;
        }

        cartItems.add(new CartItem(name, basePrice, quantity, addOns, preference));
    }

    public static List<CartItem> getCartItems() {
        return cartItems;
    }

    public static boolean isCartEmpty() {
        return cartItems.isEmpty();
    }

    public static void removeItem(CartItem item) {
        cartItems.remove(item);
    }

    public static int getCartItemCount() {
        return cartItems.size();
    }

    public static double getSubtotal() {
        double total = 0;
        for (CartItem item : cartItems) {
            total += item.getLineTotal();
        }
        return total;
    }

    public static void setPromoCode(String promo) {
        promoCode = promo == null ? "NONE" : promo.trim().toUpperCase(Locale.getDefault());
    }

    public static String getPromoCode() {
        return promoCode;
    }

    public static double getDeliveryFee() {
        if ("FREEDEL".equals(promoCode)) {
            return 0;
        }
        return DEFAULT_DELIVERY_FEE;
    }

    public static double getDiscountAmount() {
        double subtotal = getSubtotal();
        if ("SAVE10".equals(promoCode)) {
            return subtotal * 0.10;
        }
        if ("LESS30".equals(promoCode)) {
            return Math.min(30.0, subtotal);
        }
        return 0;
    }

    public static double getTotalAmount() {
        return Math.max(0, getSubtotal() + getDeliveryFee() - getDiscountAmount());
    }

    public static void setDeliveryAddress(String address) {
        deliveryAddress = address == null ? "" : address.trim();
    }

    public static String getDeliveryAddress() {
        return deliveryAddress;
    }

    public static void setDeliveryNotes(String notes) {
        deliveryNotes = notes == null ? "" : notes.trim();
    }

    public static String getDeliveryNotes() {
        return deliveryNotes;
    }

    public static void setPaymentMethod(String method) {
        paymentMethod = method == null || method.trim().isEmpty() ? "Cash" : method.trim();
    }

    public static String getPaymentMethod() {
        return paymentMethod;
    }

    public static void generateOrderNumber() {
        long stamp = System.currentTimeMillis() % 1_000_000;
        orderNumber = String.format(Locale.getDefault(), "FD-%06d", stamp);
    }

    public static String getOrderNumber() {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            generateOrderNumber();
        }
        return orderNumber;
    }

    public static void clearFlow() {
        cartItems.clear();
        promoCode = "NONE";
        paymentMethod = "Cash";
        deliveryAddress = "";
        deliveryNotes = "";
        orderNumber = "";
    }
}
