package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "procurement")
public class Procurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sdelivery_id")
    private Long sdeliveryId;

    @ManyToOne
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    // Defaults match the database defaults, because Hibernate inserts null instead of letting the database default apply
    @Column(name = "transaction_type", length = 20)
    private String transactionType = "delivery";

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @Column(name = "date_received", nullable = false)
    private LocalDate dateReceived;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "PAID";

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @ManyToOne
    @JoinColumn(name = "received_by", nullable = false)
    private User receivedBy;

    @Column(name = "notes", length = 255)
    private String notes;

    // Filled in by the database (current_timestamp), so it is read only here
    @Column(name = "date_created", insertable = false, updatable = false)
    private LocalDateTime dateCreated;

    public Procurement() {

    }

    public Procurement(Supplier supplier, String receiptNumber, LocalDate dateReceived,
                            String paymentMethod, User receivedBy) {
        this.supplier = supplier;
        this.receiptNumber = receiptNumber;
        this.dateReceived = dateReceived;
        this.paymentMethod = paymentMethod;
        this.receivedBy = receivedBy;
    }

    public Long getSdeliveryId() { 
        return sdeliveryId; 
    }

    public void setSdeliveryId(Long sdeliveryId) { 
        this.sdeliveryId = sdeliveryId; 
    }

    public Supplier getSupplier() { 
        return supplier; 

    }
    public void setSupplier(Supplier supplier) { 
        this.supplier = supplier; 
    }

    public String getTransactionType() { 
        return transactionType; 
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType; 
    }

    public String getReceiptNumber() {
        return receiptNumber; 
    }
    public void setReceiptNumber(String receiptNumber) { 
        this.receiptNumber = receiptNumber; 
    }

    public LocalDate getDateReceived() { 
        return dateReceived; 
    }

    public void setDateReceived(LocalDate dateReceived) { 
        this.dateReceived = dateReceived; 
    }

    public String getPaymentStatus() { 
        return paymentStatus; 
    }

    public void setPaymentStatus(String paymentStatus) { 
        this.paymentStatus = paymentStatus; 
    }

    public BigDecimal getTotalAmount() { 
        return totalAmount; 
    }

    public void setTotalAmount(BigDecimal totalAmount) { 
        this.totalAmount = totalAmount; 
    }

    public String getPaymentMethod() { 
        return paymentMethod; 
    }

    public void setPaymentMethod(String paymentMethod) { 
        this.paymentMethod = paymentMethod; 
    }

    public User getReceivedBy() { 
        return receivedBy; 
    }

    public void setReceivedBy(User receivedBy) { 
        this.receivedBy = receivedBy; 
    }

    public String getNotes() { 
        return notes; 
    }

    public void setNotes(String notes) { 
        this.notes = notes; 
    }

    public LocalDateTime getDateCreated() { 
        return dateCreated; 
    }
}