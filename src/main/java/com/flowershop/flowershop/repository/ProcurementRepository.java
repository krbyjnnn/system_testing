package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Procurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProcurementRepository extends JpaRepository<Procurement, Long> {

    Optional<Procurement> findFirstBySupplierSupplierIdAndDateReceivedAndReceiptNumber(
            Long supplierId, LocalDate dateReceived, String receiptNumber);
}