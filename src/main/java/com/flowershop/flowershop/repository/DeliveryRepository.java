package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    
    List<Delivery> findByOrderOrderId(Long orderId);
    List<Delivery> findByDeliveryStatus(String deliveryStatus);
}