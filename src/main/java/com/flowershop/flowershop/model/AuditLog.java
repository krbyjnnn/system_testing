package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "timestamp", nullable = false, insertable = false, updatable = false)
    private LocalDateTime timestamp;

    public AuditLog() {    
    }

    public AuditLog(User user, String action, String module, String details) {
        this.user = user;
        this.action = action;
        this.module = module;
        this.details = details;
    }

    public Long getAuditId() { 
        return auditId; 
    }

    public void setAuditId(Long auditId) { 
        this.auditId = auditId; 
    }

    public User getUser() { 
        return user; 
    }

    public void setUser(User user) { 
        this.user = user; 
    }

    public String getAction() { 
        return action; 
    }

    public void setAction(String action) { 
        this.action = action; 
    }

    public String getModule() { 
        return module; 
    }

    public void setModule(String module) { 
        this.module = module; 
    }

    public String getDetails() { 
        return details; 
    }

    public void setDetails(String details) { 
        this.details = details; 
    }

    public LocalDateTime getTimestamp() {
        return timestamp; 
    }
}