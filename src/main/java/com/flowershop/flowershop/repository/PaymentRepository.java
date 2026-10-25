package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderOrderId(Long orderId);
    List<Payment> findByPaymentStatus(String paymentStatus);

    @Query(value = "SELECT COALESCE(SUM(amount_paid), 0) FROM payment WHERE order_id = :orderId AND payment_status = 'PAID'", nativeQuery = true)
    BigDecimal sumPaidByOrder(@Param("orderId") Long orderId);

    // Money received per payment method for the printed report: method, number of payments, total.
    // Counted by the day the payment was received, like the net flow "money in".
    @Query(value = """
            SELECT payment_method, CAST(COUNT(*) AS SIGNED), COALESCE(SUM(amount_paid), 0)
            FROM payment
            WHERE payment_status = 'PAID' AND payment_date >= :startDate AND payment_date < :endDate
            GROUP BY payment_method
            ORDER BY SUM(amount_paid) DESC
            """, nativeQuery = true)
    List<Object[]> sumPaidByMethod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}