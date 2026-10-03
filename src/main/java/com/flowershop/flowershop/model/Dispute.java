package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dispute")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dispute_id")
    private Long disputeId;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "complaint", nullable = false, length = 500)
    private String complaint;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "date_filed", nullable = false)
    private LocalDateTime dateFiled;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Dispute() {
    }

    public Dispute(Order order, String customerName, String complaint, String status, LocalDateTime dateFiled, User user) {
        this.order = order;
        this.customerName = customerName;
        this.complaint = complaint;
        this.status = status;
        this.dateFiled = dateFiled;
        this.user = user;
    }

    public Long getDisputeId() {
        return disputeId;
    }

    public void setDisputeId(Long disputeId) {
        this.disputeId = disputeId;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getComplaint() {
        return complaint;
    }

    public void setComplaint(String complaint) {
        this.complaint = complaint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getDateFiled() {
        return dateFiled;
    }

    public void setDateFiled(LocalDateTime dateFiled) {
        this.dateFiled = dateFiled;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}