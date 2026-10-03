package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.ComponentInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComponentInventoryRepository extends JpaRepository<ComponentInventory, Long> {
    
    List<ComponentInventory> findByComponentComponentId(Long componentId);
    List<ComponentInventory> findBySupplierSupplierId(Long supplierId);
}