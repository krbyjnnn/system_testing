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

    // Cost of goods sold per day: quantity sold times the product's average batch cost.
    // order_items does not store a cost, so the unit_cost of the product's inventory batches is used.
    @Query(value = """
            SELECT CAST(DATE(o.order_date) AS CHAR) AS period,
                   COALESCE(SUM(oi.quantity * COALESCE(c.avg_cost, 0)), 0) AS sales
            FROM order_items oi
            JOIN orders o ON o.order_id = oi.order_id
            LEFT JOIN (SELECT product_id, AVG(unit_cost) AS avg_cost FROM product_inventory GROUP BY product_id) c
                   ON c.product_id = oi.product_id
            WHERE o.order_status = 'COMPLETED'
            AND o.order_date >= :startDate AND o.order_date < :endDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesPoint> findDailyCogs(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}