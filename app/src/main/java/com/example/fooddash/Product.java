package com.example.fooddash;

public class Product {
    public int id;
    public String name;
    public String description;
    public double price;
    public String imageUrl;
    public boolean isAvailable;
    public int quantity = 0;
    public int restaurantId;
    public int restaurantID;
    public String restaurantName;

    public Product(int id, String name, String description, double price, String imageUrl, boolean available, int restaurantId, String restaurantName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.imageUrl = imageUrl;
        this.isAvailable = available;
        this.restaurantId = restaurantId;
        this.restaurantID = restaurantId;
        this.restaurantName = restaurantName;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public boolean isAvailable() { return isAvailable; }
}


