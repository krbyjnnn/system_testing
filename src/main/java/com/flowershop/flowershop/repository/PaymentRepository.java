package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderOrderId(Long orderId);
    List<Payment> findByPaymentStatus(String paymentStatus);

    @Query(value = "SELECT COALESCE(SUM(amount_paid), 0) FROM payment WHERE order_id = :orderId AND payment_status = 'PAID'", nativeQuery = true)
    BigDecimal sumPaidByOrder(@Param("orderId") Long orderId);
}