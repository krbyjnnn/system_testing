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

    @Query(value = """
            SELECT CAST(DATE(o.order_date) AS CHAR) AS period,
                   CAST(COUNT(*) AS SIGNED) AS orders,
                   COALESCE(SUM(o.total_amount), 0) AS revenue,
                   CAST(COALESCE(SUM(i.qty), 0) AS SIGNED) AS itemsSold
            FROM orders o
            LEFT JOIN (SELECT order_id, SUM(quantity) AS qty FROM order_items GROUP BY order_id) i ON i.order_id = o.order_id
            WHERE o.order_status = 'COMPLETED'
            AND o.order_date >= :startDate AND o.order_date < :endDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesReportRow> findDailyReport(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT CAST(DATE_SUB(DATE(o.order_date), INTERVAL WEEKDAY(o.order_date) DAY) AS CHAR) AS period,
                   CAST(COUNT(*) AS SIGNED) AS orders,
                   COALESCE(SUM(o.total_amount), 0) AS revenue,
                   CAST(COALESCE(SUM(i.qty), 0) AS SIGNED) AS itemsSold
            FROM orders o
            LEFT JOIN (SELECT order_id, SUM(quantity) AS qty FROM order_items GROUP BY order_id) i ON i.order_id = o.order_id
            WHERE o.order_status = 'COMPLETED'
            AND o.order_date >= :startDate AND o.order_date < :endDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesReportRow> findWeeklyReport(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT DATE_FORMAT(o.order_date, '%Y-%m') AS period,
                   CAST(COUNT(*) AS SIGNED) AS orders,
                   COALESCE(SUM(o.total_amount), 0) AS revenue,
                   CAST(COALESCE(SUM(i.qty), 0) AS SIGNED) AS itemsSold
            FROM orders o
            LEFT JOIN (SELECT order_id, SUM(quantity) AS qty FROM order_items GROUP BY order_id) i ON i.order_id = o.order_id
            WHERE o.order_status = 'COMPLETED'
            AND o.order_date >= :startDate AND o.order_date < :endDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesReportRow> findMonthlyReport(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Completed orders of a period for the printed report:
    // transaction no., date, cashier, order type, items, payment method(s), total
    @Query(value = """
            SELECT o.transaction_number, o.order_date, u.full_name, o.order_type,
                   CAST(COALESCE((SELECT SUM(oi.quantity) FROM order_items oi WHERE oi.order_id = o.order_id), 0) AS SIGNED),
                   COALESCE((SELECT GROUP_CONCAT(DISTINCT p.payment_method SEPARATOR ', ') FROM payment p
                             WHERE p.order_id = o.order_id AND p.payment_status = 'PAID'), '-'),
                   o.total_amount
            FROM orders o
            JOIN users u ON u.user_id = o.user_id
            WHERE o.order_status = 'COMPLETED'
            AND o.order_date >= :startDate AND o.order_date < :endDate
            ORDER BY o.order_date
            """, nativeQuery = true)
    List<Object[]> findCompletedOrdersBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}