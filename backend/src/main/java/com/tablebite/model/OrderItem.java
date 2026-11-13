package com.tablebite.model;

import java.math.BigDecimal;

public record OrderItem(long menuItemId, String name, int quantity, BigDecimal unitPrice,
                        BigDecimal lineTotal, String notes, String imageUrl) {
}
