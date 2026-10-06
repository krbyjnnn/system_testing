package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "component_inventory")
public class ComponentInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cbatch_id")
    private Long cbatchId;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    private Component component;

    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand;

    @Column(name = "reorder_level", nullable = false)
    private Integer reorderLevel;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "date_received", nullable = false)
    private LocalDate dateReceived;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    public ComponentInventory() {
    }

    public ComponentInventory(Supplier supplier, Component component, Integer quantityOnHand, Integer reorderLevel, BigDecimal unitCost, LocalDate dateReceived, LocalDate expiryDate) {
        this.supplier = supplier;
        this.component = component;
        this.quantityOnHand = quantityOnHand;
        this.reorderLevel = reorderLevel;
        this.unitCost = unitCost;
        this.dateReceived = dateReceived;
        this.expiryDate = expiryDate;
    }

    public Long getCbatchId() { 
        return cbatchId; 
    }

    public void setCbatchId(Long cbatchId) { 
        this.cbatchId = cbatchId; 
    }

    public Supplier getSupplier() { 
        return supplier; 
    }

    public void setSupplier(Supplier supplier) { 
        this.supplier = supplier; 
    }

    public Component getComponent() { 
        return component; 
    }

    public void setComponent(Component component) { 
        this.component = component; 
    }

    public Integer getQuantityOnHand() { 
        return quantityOnHand; 
    }

    public void setQuantityOnHand(Integer quantityOnHand) { 
        this.quantityOnHand = quantityOnHand; 
    }

    public Integer getReorderLevel() { 
        return reorderLevel; 
    }

    public void setReorderLevel(Integer reorderLevel) { 
        this.reorderLevel = reorderLevel; 
    }

    public BigDecimal getUnitCost() { 
        return unitCost; 
    }

    public void setUnitCost(BigDecimal unitCost) { 
        this.unitCost = unitCost; 
    }

    public LocalDate getDateReceived() { 
        return dateReceived; 
    }

    public void setDateReceived(LocalDate dateReceived) { 
        this.dateReceived = dateReceived; 
    }

    public LocalDate getExpiryDate() { 
        return expiryDate; 
    }

    public void setExpiryDate(LocalDate expiryDate) { 
        this.expiryDate = expiryDate; 
    }
}