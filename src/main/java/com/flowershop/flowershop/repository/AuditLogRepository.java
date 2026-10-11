package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    List<AuditLog> findByUserUserId(Long userId);
    List<AuditLog> findByModule(String module);
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
    Page<AuditLog> findByModuleOrderByTimestampDesc(String module, Pageable pageable);
    Page<AuditLog> findByUserUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
    Page<AuditLog> findByModuleAndUserUserIdOrderByTimestampDesc(String module, Long userId, Pageable pageable);
}