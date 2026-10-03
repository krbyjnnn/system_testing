package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    
    List<Recipe> findByProductProductId(Long productId);
    List<Recipe> findByComponentComponentId(Long componentId);
}