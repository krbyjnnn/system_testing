package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Returns;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ReturnsRepository extends JpaRepository<Returns, Long> {

    List<Returns> findByOrderOrderId(Long orderId);
    List<Returns> findByStatus(String status);

    @Query(value = """
            SELECT COALESCE(SUM(refund_amount), 0)
            FROM returns
            WHERE return_date >= :startDate AND return_date < :endDate
            """, nativeQuery = true)
    BigDecimal sumRefundsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Refunds per day, keyed by the day the return was logged (period = yyyy-MM-dd)
    @Query(value = """
            SELECT CAST(DATE(return_date) AS CHAR) AS period, COALESCE(SUM(refund_amount), 0) AS sales
            FROM returns
            WHERE return_date >= :startDate AND return_date < :endDate
            GROUP BY period
            ORDER BY period
            """, nativeQuery = true)
    List<SalesPoint> findDailyRefunds(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}