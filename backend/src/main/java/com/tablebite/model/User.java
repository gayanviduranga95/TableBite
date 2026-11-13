package com.tablebite.model;

public record User(long id, String fullName, String email, String phone,
                   UserRole role, boolean active, String createdAt, String lastLoginAt) {
}
