package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    List<Payment> findByOrderOrderId(Long orderId);
    List<Payment> findByPaymentStatus(String paymentStatus);
}