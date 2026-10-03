package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Component;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComponentRepository extends JpaRepository<Component, Long> {
    
    List<Component> findByCategoryCategoryId(Long categoryId);
    List<Component> findByNameContainingIgnoreCase(String name);
}