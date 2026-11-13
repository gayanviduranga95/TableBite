package com.tablebite.model;

public record Category(long id, String name, String icon, int sortOrder,
                       boolean active, int itemCount) {
}
