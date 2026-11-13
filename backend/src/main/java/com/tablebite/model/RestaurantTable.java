package com.tablebite.model;

public record RestaurantTable(long id, String tableNumber, String qrToken, int seats,
                              String status, String location, int activeOrders,
                              String createdAt) {
}
