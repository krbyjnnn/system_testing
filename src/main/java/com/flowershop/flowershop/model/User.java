package com.flowershop.flowershop.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id") // Maps to user_id in MySQL
    private Long userId;      // Renamed from userid to userId

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "username", nullable = false, length = 255, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 255)
    private String role;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "ACTIVE";

    @Column(name = "void_passkey", length = 255)
    private String voidPasskey;

    public User() {

    }

    public User(String fullName, String username, String passwordHash, String role) {
        this.fullName = fullName;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getUserId() { 
        return userId; 
    }

    public void setUserId(Long userId) { 
        this.userId = userId; 
    }

    public String getFullName() { 
        return fullName; 
    }
    public void setFullName(String fullName) { 
        this.fullName = fullName; 
    }

    public String getUsername() { 
        return username; 
    }

    public void setUsername(String username) { 
        this.username = username; 
    }

    public String getPasswordHash() { 
        return passwordHash; 
    }

    public void setPasswordHash(String passwordHash) { 
        this.passwordHash = passwordHash; 
    }

    public String getRole() { 
        return role; 
    }

    public void setRole(String role) { 
        this.role = role; 
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVoidPasskey() {
        return voidPasskey;
    }

    public void setVoidPasskey(String voidPasskey) {
        this.voidPasskey = voidPasskey;
    }
}