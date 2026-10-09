package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderOrderId(Long orderId);

    @Query(value = """
            SELECT CAST(COALESCE(SUM(oi.quantity), 0) AS SIGNED)
            FROM order_items oi
            JOIN orders o ON o.order_id = oi.order_id
            WHERE DATE(o.order_date) = CURDATE()
            AND o.order_status = 'COMPLETED'
            """, nativeQuery = true)
    Long countItemsSoldToday();
}