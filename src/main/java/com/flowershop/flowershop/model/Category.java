package com.flowershop.flowershop.model;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 255) // Column is 'name'
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    private String type = "Non-perishable";

    public Category() {
    }

    public Category(String name) {
        this.name = name;
    }

    public Category(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Long getCategoryId() { 
        return categoryId; 
    }
    
    public void setCategoryId(Long categoryId) { 
        this.categoryId = categoryId; 
    }

    public String getName() { 
        return name; 
    }

    public void setName(String name) { 
        this.name = name; 
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isPerishable() {
        return "Perishable".equals(type);
    }
}