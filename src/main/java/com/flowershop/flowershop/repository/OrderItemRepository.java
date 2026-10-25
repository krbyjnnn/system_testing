package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Query(value = """
            SELECT CAST(COALESCE(SUM(oi.quantity), 0) AS SIGNED)
            FROM order_items oi
            JOIN orders o ON o.order_id = oi.order_id
            WHERE o.order_date >= :startDate AND o.order_date <= :endDate
            AND o.order_status = 'COMPLETED'
            """, nativeQuery = true)
    Long countItemsSoldBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT p.product_name, CAST(SUM(oi.quantity) AS SIGNED)
            FROM order_items oi
            JOIN orders o ON o.order_id = oi.order_id
            JOIN product p ON p.product_id = oi.product_id
            WHERE o.order_date >= :startDate AND o.order_date <= :endDate
            AND o.order_status = 'COMPLETED'
            GROUP BY p.product_id, p.product_name
            ORDER BY SUM(oi.quantity) DESC
            LIMIT 8
            """, nativeQuery = true)
    List<Object[]> findTopSelling(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}