package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.SpoilageLog;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.SpoilageLogRepository;
import com.flowershop.flowershop.util.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpoilageService {

    public static final String[] REASONS = {"Expired", "Wilted", "Damaged", "Other"};

    private final ComponentInventoryRepository componentInventoryRepository;
    private final SpoilageLogRepository spoilageLogRepository;
    private final AuditService auditService;

    public SpoilageService(
            ComponentInventoryRepository componentInventoryRepository,
            SpoilageLogRepository spoilageLogRepository,
            AuditService auditService
    ) {
        this.componentInventoryRepository = componentInventoryRepository;
        this.spoilageLogRepository = spoilageLogRepository;
        this.auditService = auditService;
    }

    // Takes the spoiled quantity out of the batch and records it, all in one transaction
    @Transactional
    public SpoilageLog logSpoilage(User user, Long batchId, int quantity, String reason) {
        Permissions.require(user, Permissions.Feature.INVENTORY_EDIT);

        ComponentInventory batch = componentInventoryRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Batch not found."));

        if (quantity < 1) {
            throw new IllegalStateException("Quantity must be at least 1.");
        }
        if (quantity > batch.getQuantityOnHand()) {
            throw new IllegalStateException("Only " + batch.getQuantityOnHand() + " left in this batch.");
        }

        batch.setQuantityOnHand(batch.getQuantityOnHand() - quantity);
        componentInventoryRepository.save(batch);

        SpoilageLog log = spoilageLogRepository.save(
                new SpoilageLog(batch, quantity, batch.getUnitCost(), reason, user));

        auditService.log(user, "Spoilage logged", "INVENTORY",
                batch.getComponent().getName() + ": " + quantity + " " + batch.getComponent().getUnitOfMeasure()
                        + " (" + reason + "), batch #" + batch.getCbatchId());
        return log;
    }
}