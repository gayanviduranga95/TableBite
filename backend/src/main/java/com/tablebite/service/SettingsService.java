package com.tablebite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tablebite.database.SettingsRepository;
import com.tablebite.payment.PaymentGateways;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Restaurant settings with validation. */
public class SettingsService {
    private static final Set<String> TEXT_KEYS = Set.of(
            "restaurant_name", "tagline", "address", "phone", "opening_hours", "currency");
    private static final Set<String> RATE_KEYS = Set.of("tax_rate", "service_charge_rate");
    private static final Set<String> BOOLEAN_KEYS = Set.of("allow_manual_table_selection", "allow_takeaway");

    private final SettingsRepository settings;

    public SettingsService() {
        this(new SettingsRepository());
    }

    public SettingsService(SettingsRepository settings) {
        this.settings = settings;
    }

    /** Settings exposed to customers (plus whether online payment is available). */
    public Map<String, Object> publicSettings() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>(settings.findAll());
        result.put("online_payment_enabled", PaymentGateways.onlineEnabled());
        return result;
    }

    public Map<String, String> all() throws SQLException {
        return settings.findAll();
    }

    public Map<String, String> update(JsonNode body) throws SQLException {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        Map<String, String> values = new LinkedHashMap<>();
        var fields = body.properties();
        for (Map.Entry<String, JsonNode> field : fields) {
            String key = field.getKey();
            String value = field.getValue().isNull() ? "" : field.getValue().asText().trim();
            if (TEXT_KEYS.contains(key)) {
                values.put(key, Validation.requireText(value, label(key), 1, 200));
            } else if (RATE_KEYS.contains(key)) {
                values.put(key, rate(value, label(key)));
            } else if (BOOLEAN_KEYS.contains(key)) {
                values.put(key, String.valueOf(Boolean.parseBoolean(value)));
            } else if ("public_base_url".equals(key)) {
                values.put(key, baseUrl(value));
            } else {
                throw new IllegalArgumentException("Unknown setting: " + key);
            }
        }
        settings.saveAll(values);
        return settings.findAll();
    }

    private static String rate(String value, String label) {
        try {
            BigDecimal rate = new BigDecimal(value);
            if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(label + " must be between 0 and 100");
            }
            return rate.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a number");
        }
    }

    private static String baseUrl(String value) {
        if (value.isEmpty()) {
            return "";
        }
        String url = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        if (!url.matches("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?(/[A-Za-z0-9._~/-]*)?$")) {
            throw new IllegalArgumentException(
                    "Public URL must look like http://192.168.1.20:5173 or https://order.example.com");
        }
        return url;
    }

    private static String label(String key) {
        String words = key.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
