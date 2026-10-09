package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.ProductInventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {

    @Query("SELECT pi FROM ProductInventory pi WHERE pi.quantityOnHand <= :threshold")
    List<ProductInventory> findLowStockItems(@Param("threshold") Integer threshold);

    @Query(value = "SELECT pi FROM ProductInventory pi WHERE pi.quantityOnHand <= :threshold ORDER BY pi.quantityOnHand ASC, pi.pbatchId ASC",
            countQuery = "SELECT COUNT(pi) FROM ProductInventory pi WHERE pi.quantityOnHand <= :threshold")
    Page<ProductInventory> findLowStockPage(@Param("threshold") Integer threshold, Pageable pageable);
}