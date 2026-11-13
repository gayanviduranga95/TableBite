package com.tablebite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tablebite.database.CategoryRepository;
import com.tablebite.database.MenuRepository;
import com.tablebite.model.Category;
import com.tablebite.model.MenuItem;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/** Menu and category management with input validation. */
public class MenuService {
    private static final Pattern ICON = Pattern.compile("^[a-z0-9-]{1,40}$");

    private final MenuRepository menu;
    private final CategoryRepository categories;

    public MenuService() {
        this(new MenuRepository(), new CategoryRepository());
    }

    public MenuService(MenuRepository menu, CategoryRepository categories) {
        this.menu = menu;
        this.categories = categories;
    }

    public List<MenuItem> customerMenu() throws SQLException {
        return menu.findCustomerMenu();
    }

    public List<MenuItem> adminMenu() throws SQLException {
        return menu.findAll();
    }

    public MenuItem get(long id) throws SQLException {
        MenuItem item = menu.findById(id);
        if (item == null) {
            throw ServiceException.notFound("Menu item");
        }
        return item;
    }

    public MenuItem create(JsonNode body) throws SQLException {
        return menu.create(parse(body));
    }

    public MenuItem update(long id, JsonNode body) throws SQLException {
        MenuItem updated = menu.update(id, parse(body));
        if (updated == null) {
            throw ServiceException.notFound("Menu item");
        }
        return updated;
    }

    public MenuItem setAvailability(long id, boolean available) throws SQLException {
        MenuItem updated = menu.setAvailability(id, available);
        if (updated == null) {
            throw ServiceException.notFound("Menu item");
        }
        return updated;
    }

    public void delete(long id) throws SQLException {
        if (!menu.delete(id)) {
            throw ServiceException.notFound("Menu item");
        }
    }

    private MenuRepository.MenuItemInput parse(JsonNode body) throws SQLException {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        String name = Validation.requireText(text(body, "name"), "Name", 2, 150);
        String description = Validation.optionalText(text(body, "description"), "Description", 1000);
        JsonNode priceNode = body.get("price");
        if (priceNode == null || !priceNode.isNumber() && !priceNode.isTextual()) {
            throw new IllegalArgumentException("Price is required");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(priceNode.asText());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Price must be a number");
        }
        Long categoryId = body.hasNonNull("categoryId") ? body.get("categoryId").asLong() : null;
        if (categoryId != null && categories.findById(categoryId) == null) {
            throw new IllegalArgumentException("Category does not exist");
        }
        return new MenuRepository.MenuItemInput(
                name, description, Validation.price(price), categoryId,
                Validation.imageUrl(text(body, "imageUrl")),
                bool(body, "available", true), bool(body, "popular", false),
                bool(body, "recommended", false), bool(body, "vegetarian", false),
                bool(body, "spicy", false),
                Validation.range(body.hasNonNull("prepMinutes") ? body.get("prepMinutes").asInt() : null,
                        "Preparation time", 0, 240, 15));
    }

    // ------------------------------------------------------------------ categories

    public List<Category> categories(boolean includeInactive) throws SQLException {
        return categories.findAll(includeInactive);
    }

    public Category createCategory(JsonNode body) throws SQLException {
        try {
            return categories.create(categoryName(body), icon(body), sortOrder(body),
                    bool(body, "active", true));
        } catch (SQLException exception) {
            throw uniqueName(exception);
        }
    }

    public Category updateCategory(long id, JsonNode body) throws SQLException {
        Category updated;
        try {
            updated = categories.update(id, categoryName(body), icon(body), sortOrder(body),
                    bool(body, "active", true));
        } catch (SQLException exception) {
            throw uniqueName(exception);
        }
        if (updated == null) {
            throw ServiceException.notFound("Category");
        }
        return updated;
    }

    public void deleteCategory(long id) throws SQLException {
        Category category = categories.findById(id);
        if (category == null) {
            throw ServiceException.notFound("Category");
        }
        if (category.itemCount() > 0) {
            throw ServiceException.conflict("Move or delete the " + category.itemCount()
                    + " dish(es) in this category first");
        }
        categories.delete(id);
    }

    private SQLException uniqueName(SQLException exception) {
        if ("23505".equals(exception.getSQLState())) {
            throw ServiceException.conflict("A category with this name already exists");
        }
        return exception;
    }

    private String categoryName(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        return Validation.requireText(text(body, "name"), "Category name", 2, 100);
    }

    private String icon(JsonNode body) {
        String icon = Validation.optionalText(text(body, "icon"), "Icon", 40);
        if (icon != null && !ICON.matcher(icon).matches()) {
            throw new IllegalArgumentException("Icon must be a simple keyword like 'pizza'");
        }
        return icon;
    }

    private int sortOrder(JsonNode body) {
        return Validation.range(body.hasNonNull("sortOrder") ? body.get("sortOrder").asInt() : null,
                "Sort order", 0, 10_000, 100);
    }

    static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    static boolean bool(JsonNode body, String field, boolean fallback) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? fallback : node.asBoolean(fallback);
    }
}
