package com.payflow.eventing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.eventing.domain.OutboxEvent;
import com.payflow.eventing.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            String payloadJson = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
            OutboxEvent event = new OutboxEvent(aggregateType, aggregateId, eventType, payloadJson);
            OutboxEvent saved = outboxEventRepository.save(event);
            log.info("Recorded transactional outbox event id={} type={} aggId={}",
                    saved.getId(), eventType, aggregateId);
            return saved;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to record outbox event", e);
        }
    }

    @Transactional(readOnly = true)
    public long getPendingOutboxCount() {
        return outboxEventRepository.countByStatus("PENDING");
    }
}

