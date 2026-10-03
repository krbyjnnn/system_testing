package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    List<AuditLog> findByUserUserId(Long userId);
    List<AuditLog> findByModule(String module);
}