package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "spoilage_log")
public class SpoilageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "spoilage_id")
    private Long spoilageId;

    @ManyToOne
    @JoinColumn(name = "cbatch_id", nullable = false)
    private ComponentInventory batch;

    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    private Component component;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason;

    @ManyToOne
    @JoinColumn(name = "logged_by")
    private User loggedBy;

    @Column(name = "logged_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime loggedAt;

    public SpoilageLog() {
    }

    public SpoilageLog(ComponentInventory batch, Integer quantity, BigDecimal unitCost, String reason, User loggedBy) {
        this.batch = batch;
        this.component = batch.getComponent();
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.reason = reason;
        this.loggedBy = loggedBy;
    }

    public Long getSpoilageId() {
        return spoilageId;
    }

    public ComponentInventory getBatch() {
        return batch;
    }

    public Component getComponent() {
        return component;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public String getReason() {
        return reason;
    }

    public User getLoggedBy() {
        return loggedBy;
    }

    public LocalDateTime getLoggedAt() {
        return loggedAt;
    }
}