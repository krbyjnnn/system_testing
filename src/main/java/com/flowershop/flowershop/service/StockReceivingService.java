package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.Supplier;
import com.flowershop.flowershop.model.Procurement;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.ProcurementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

// Records stock coming in. Money out = quantity received x unit cost of the batches of a delivery.
@Service
public class StockReceivingService {

    private final ProcurementRepository deliveryRepository;
    private final ComponentInventoryRepository componentInventoryRepository;

    public StockReceivingService(ProcurementRepository deliveryRepository,
                                 ComponentInventoryRepository componentInventoryRepository) {
        this.deliveryRepository = deliveryRepository;
        this.componentInventoryRepository = componentInventoryRepository;
    }

    // Transactional so a delivery is never saved without its batch (or the other way around)
    @Transactional
    public ComponentInventory receive(Supplier supplier, User receivedBy, LocalDate dateReceived,
                                      String receiptNumber, String paymentMethod,
                                      Component component, int quantity, int reorderLevel,
                                      BigDecimal unitCost, LocalDate expiryDate) {

        String receipt = (receiptNumber == null || receiptNumber.trim().isEmpty()) ? null : receiptNumber.trim();

        // Same supplier + date + receipt number = same delivery, so a receipt with many items stays together.
        // Without a receipt number every entry is its own delivery.
        Procurement delivery = null;
        if (receipt != null) {
            delivery = deliveryRepository
                    .findFirstBySupplierSupplierIdAndDateReceivedAndReceiptNumber(
                            supplier.getSupplierId(), dateReceived, receipt)
                    .orElse(null);
        }
        if (delivery == null) {
            delivery = deliveryRepository.save(
                    new Procurement(supplier, receipt, dateReceived, paymentMethod, receivedBy));
        }

        ComponentInventory batch = new ComponentInventory(
                supplier, component, quantity, reorderLevel, unitCost, dateReceived, expiryDate);
        batch.setSdelivery(delivery);
        batch.setQuantityReceived(quantity);
        return componentInventoryRepository.save(batch);
    }
}