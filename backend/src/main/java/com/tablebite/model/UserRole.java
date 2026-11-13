package com.tablebite.model;

public enum UserRole {
    CUSTOMER,
    ADMIN,
    KITCHEN,
    WAITER,
    /** Synthetic role for the legacy shared STAFF_ACCESS_CODE (kitchen + floor rights). */
    STAFF;

    public boolean isStaff() {
        return this != CUSTOMER;
    }

    public boolean canUseKitchen() {
        return this == ADMIN || this == KITCHEN || this == STAFF;
    }

    public boolean canUseFloor() {
        return this == ADMIN || this == WAITER || this == STAFF;
    }

    /** Parses a role that may be stored on a user account (STAFF is not assignable). */
    public static UserRole parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        UserRole role;
        try {
            role = UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported role: " + value);
        }
        if (role == STAFF) {
            throw new IllegalArgumentException("Unsupported role: " + value);
        }
        return role;
    }
}
