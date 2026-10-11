package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.AuditLog;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User user, String action, String module, String details) {
        auditLogRepository.save(new AuditLog(user, action, module, details));
    }
}