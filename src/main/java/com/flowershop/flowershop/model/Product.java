package com.flowershop.flowershop.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "selling_price", nullable = false)
    private BigDecimal sellingPrice;

    @Column(name = "is_composite", nullable = false)
    private Boolean isComposite;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    public Product() {
    }
    
    public Product(Category category, String productCode, String productName, BigDecimal sellingPrice, Boolean isComposite, String status) {
        this.category = category;
        this.productCode = productCode;
        this.productName = productName;
        this.sellingPrice = sellingPrice;
        this.isComposite = isComposite;
        this.status = status;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public Boolean getIsComposite() {
        return isComposite;
    }

    public void setIsComposite(Boolean isComposite) {
        this.isComposite = isComposite;
    }

        public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
