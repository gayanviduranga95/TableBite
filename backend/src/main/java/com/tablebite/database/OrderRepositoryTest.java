package com.tablebite.database;

import java.sql.SQLException;
import java.util.List;

public final class OrderRepositoryTest {

    private OrderRepositoryTest() {
    }

    public static void main(String[] args) {
        OrderRepository repository = new OrderRepository();

        try {
            long orderId = repository.createOrder(
                    1,
                    List.of(
                            new OrderRepository.OrderItemInput(1, 2),
                            new OrderRepository.OrderItemInput(4, 1)
                    )
            );

            System.out.println("SUCCESS: Order saved with ID " + orderId);
        } catch (SQLException | IllegalArgumentException exception) {
            System.err.println(
                    "Order creation failed: " + exception.getMessage()
            );
            System.exit(1);
        }
    }
}