package com.tablebite.model;

import java.math.BigDecimal;

public record MenuItem(long id, String name, String description, BigDecimal price,
                       Long categoryId, String category, String imageUrl,
                       boolean available, boolean popular, boolean recommended,
                       boolean vegetarian, boolean spicy, int prepMinutes,
                       String createdAt, boolean isNew, int orderCount) {
}
