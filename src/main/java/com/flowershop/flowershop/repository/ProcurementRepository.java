package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Procurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ProcurementRepository extends JpaRepository<Procurement, Long> {

    Optional<Procurement> findFirstBySupplierSupplierIdAndDateReceivedAndReceiptNumber(
            Long supplierId, LocalDate dateReceived, String receiptNumber);

    long countByPaymentStatus(String paymentStatus);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Procurement p WHERE p.dateReceived >= :startDate AND p.dateReceived <= :endDate")
    BigDecimal sumPurchasesBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}