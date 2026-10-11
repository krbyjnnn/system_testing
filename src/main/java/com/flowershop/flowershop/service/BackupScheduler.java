package com.flowershop.flowershop.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class BackupScheduler {

    private final BackupService backupService;
    private final AuditService auditService;

    public BackupScheduler(BackupService backupService, AuditService auditService) {
        this.backupService = backupService;
        this.auditService = auditService;
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 3600000)
    public void runDueBackups() {
        try {
            backupService.runDueWeeklyBackups();
        } catch (Exception e) {
            try {
                auditService.log(null, "Automatic backup failed", "BACKUPS", String.valueOf(e.getMessage()));
            } catch (Exception ignored) {
            }
        }
    }
}