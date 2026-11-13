package com.tablebite.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Input validation helpers. All failures throw IllegalArgumentException (HTTP 400). */
public final class Validation {
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()-]{7,20}$");
    private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");

    private Validation() {
    }

    public static String requireText(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min) {
            throw new IllegalArgumentException(min <= 1 ? field + " is required"
                    : field + " must be at least " + min + " characters");
        }
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    public static String optionalText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    public static String email(String value) {
        String email = requireText(value, "Email", 3, 160).toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        return email;
    }

    public static String optionalPhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String phone = value.trim();
        if (!PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("Enter a valid phone number");
        }
        return phone;
    }

    public static String password(String value) {
        if (value == null || value.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (value.length() > 128) {
            throw new IllegalArgumentException("Password is too long");
        }
        if (!value.chars().anyMatch(Character::isLetter) || !value.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password must contain letters and numbers");
        }
        return value;
    }

    public static String idempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!IDEMPOTENCY.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid idempotency key");
        }
        return value;
    }

    public static BigDecimal price(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Price is required");
        }
        if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000")) >= 0) {
            throw new IllegalArgumentException("Price must be between 0 and 999,999.99");
        }
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Price can have at most 2 decimals");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public static int range(Integer value, String field, int min, int max, int fallback) {
        int result = value == null ? fallback : value;
        if (result < min || result > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
        return result;
    }

    /** Image paths must be our own static/uploaded files or an https URL. */
    public static String imageUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String url = value.trim();
        if (url.length() > 500) {
            throw new IllegalArgumentException("Image URL is too long");
        }
        if (!(url.startsWith("/images/") || url.startsWith("/uploads/") || url.startsWith("https://"))
                || url.contains("..") || url.contains("\"") || url.contains("<")) {
            throw new IllegalArgumentException("Image must be an uploaded file or an https URL");
        }
        return url;
    }
}
