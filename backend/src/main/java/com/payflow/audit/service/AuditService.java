package com.payflow.audit.service;

import com.payflow.audit.domain.AuditLog;
import com.payflow.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorId, String actorRole, String action, String entityType,
                       String entityId, String outcome, String ipAddress, String correlationId, String metadata) {
        AuditLog audit = new AuditLog(actorId, actorRole, action, entityType, entityId,
                outcome, ipAddress, correlationId, metadata);
        auditLogRepository.save(audit);
        log.info("Audit log recorded: actor={} action={} entityType={} entityId={} outcome={}",
                actorId, action, entityType, entityId, outcome);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> listLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}

