package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Returns;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReturnsRepository extends JpaRepository<Returns, Long> {
    
    List<Returns> findByOrderOrderId(Long orderId);
    List<Returns> findByStatus(String status);
}