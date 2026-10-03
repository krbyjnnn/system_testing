package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.ProductInventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {

    @Query("SELECT pi FROM ProductInventory pi WHERE pi.quantityOnHand <= :threshold")
    List<ProductInventory> findLowStockItems(@Param("threshold") Integer threshold);
}