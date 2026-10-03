package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "returns")
public class Returns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    // NEW: the dispute this return came from
    @ManyToOne
    @JoinColumn(name = "dispute_id")
    private Dispute dispute;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "quantity_returned", nullable = false)
    private Integer quantityReturned;

    @Column(name = "refund_amount", nullable = false)
    private BigDecimal refundAmount;

    @Column(name = "return_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime returnDate;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    public Returns() {
    }

    public Returns(Order order, OrderItem orderItem, String reason, Integer quantityReturned, BigDecimal refundAmount, String status) {
        this.order = order;
        this.orderItem = orderItem;
        this.reason = reason;
        this.quantityReturned = quantityReturned;
        this.refundAmount = refundAmount;
        this.status = status;
    }

    public Long getReturnId() { 
        return returnId; 
    }

    public void setReturnId(Long returnId) { 
        this.returnId = returnId; 
    }

    public Order getOrder() { 
        return order; 
    }

    public void setOrder(Order order) { 
        this.order = order; 
    }

    public OrderItem getOrderItem() { 
        return orderItem; 
    }

    public void setOrderItem(OrderItem orderItem) { 
        this.orderItem = orderItem; 
    }

    // NEW
    public Dispute getDispute() { 
        return dispute; 
    }

    // NEW
    public void setDispute(Dispute dispute) { 
        this.dispute = dispute; 
    }

    public String getReason() { 
        return reason; 
    }
    
    public void setReason(String reason) { 
        this.reason = reason; 
    }

    public Integer getQuantityReturned() { 
        return quantityReturned; 
    }

    public void setQuantityReturned(Integer quantityReturned) { 
        this.quantityReturned = quantityReturned; 
    }

    public BigDecimal getRefundAmount() { 
        return refundAmount; 
    }

    public void setRefundAmount(BigDecimal refundAmount) { 
        this.refundAmount = refundAmount; 
    }

    public LocalDateTime getReturnDate() { 
        return returnDate; 
    }

    public String getStatus() { 
        return status; 
    }

    public void setStatus(String status) { 
        this.status = status; 
    }
}