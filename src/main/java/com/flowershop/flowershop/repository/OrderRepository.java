package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserUserId(Long userId);
    List<Order> findByOrderStatus(String orderStatus);
    Page<Order> findAllByOrderByOrderDateDesc(Pageable pageable);

    // Sales cards: only COMPLETED (paid) orders, so unpaid pay-later orders are not counted yet
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus = 'COMPLETED' AND o.orderDate >= :startDate AND o.orderDate <= :endDate")
    BigDecimal calculateRevenueBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Net flow "money in": counted by the day the payment was received, not the day the order was placed
    @Query(value = """
            SELECT COALESCE(SUM(amount_paid), 0)
            FROM payment
            WHERE payment_status = 'PAID'
            AND payment_date >= :startDate AND payment_date < :endDate
            """, nativeQuery = true)
    BigDecimal calculateMoneyInBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT CAST(DATE(order_date) AS CHAR) AS period, COALESCE(SUM(total_amount), 0) AS sales
            FROM orders
            WHERE order_status = 'COMPLETED'
            AND order_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesPoint> findDailySales();

    @Query(value = """
            SELECT CAST(DATE_SUB(DATE(order_date), INTERVAL WEEKDAY(order_date) DAY) AS CHAR) AS period, COALESCE(SUM(total_amount), 0) AS sales
            FROM orders
            WHERE order_status = 'COMPLETED'
            AND order_date >= DATE_SUB(CURDATE(), INTERVAL 8 WEEK)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesPoint> findWeeklySales();

    @Query(value = """
            SELECT DATE_FORMAT(order_date, '%Y-%m') AS period, COALESCE(SUM(total_amount), 0) AS sales
            FROM orders
            WHERE order_status = 'COMPLETED'
            AND order_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesPoint> findMonthlySales();
}