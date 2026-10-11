package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Delivery or pick-up details of an order (for pick-ups the address is "Store pick-up"). */
@Entity
@Table(name = "delivery")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long deliveryId;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 50)
    private String recipientPhone;

    @Column(name = "delivery_address", nullable = false, length = 255)
    private String deliveryAddress;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "delivery_status", nullable = false, length = 50)
    private String deliveryStatus;

    // Already included in the order's total_amount; stored separately so it can be reported on its own
    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "notes", length = 255)
    private String notes;

    public Delivery() {
    }

    public Delivery(Order order, String recipientName, String recipientPhone, String deliveryAddress, LocalDate deliveryDate, String deliveryStatus) {
        this.order = order;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.deliveryDate = deliveryDate;
        this.deliveryStatus = deliveryStatus;
    }

    public Delivery(Order order, String recipientName, String recipientPhone, String deliveryAddress, LocalDate deliveryDate, String deliveryStatus, BigDecimal deliveryFee, String notes) {
        this(order, recipientName, recipientPhone, deliveryAddress, deliveryDate, deliveryStatus);
        this.deliveryFee = deliveryFee;
        this.notes = notes;
    }

    public Long getDeliveryId() { 
        return deliveryId; 
    }

    public void setDeliveryId(Long deliveryId) { 
        this.deliveryId = deliveryId; 
    }

    public Order getOrder() {
        return order; 
    }

    public void setOrder(Order order) { 
        this.order = order; 
    }

    public String getRecipientName() { 
        return recipientName; 
    }

    public void setRecipientName(String recipientName) { 
        this.recipientName = recipientName; 
    }

    public String getRecipientPhone() { 
        return recipientPhone; 
    }

    public void setRecipientPhone(String recipientPhone) { 
        this.recipientPhone = recipientPhone; 
    }

    public String getDeliveryAddress() { 
        return deliveryAddress; 
    }

    public void setDeliveryAddress(String deliveryAddress) { 
        this.deliveryAddress = deliveryAddress; 
    }

    public LocalDate getDeliveryDate() { 
        return deliveryDate; 
    }

    public void setDeliveryDate(LocalDate deliveryDate) { 
        this.deliveryDate = deliveryDate; 
    }

    public String getDeliveryStatus() { 
        return deliveryStatus; 
    }
    
    public void setDeliveryStatus(String deliveryStatus) { 
        this.deliveryStatus = deliveryStatus; 
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}