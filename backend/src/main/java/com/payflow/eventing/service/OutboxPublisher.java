package com.payflow.eventing.service;

import com.payflow.eventing.domain.OutboxEvent;
import com.payflow.eventing.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final int maxRetries;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            @Value("${payflow.outbox.batch-size:50}") int batchSize,
            @Value("${payflow.outbox.max-retries:5}") int maxRetries
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    public record DomainEventNotification(String eventId, String aggregateType, String aggregateId, String eventType, String payload) {}

    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to publish", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // If Kafka template is configured and available, publish to Kafka topic
                if (kafkaTemplate != null) {
                    String topic = "payflow." + event.getAggregateType().toLowerCase() + ".events";
                    kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
                }

                // Also publish in-process domain event notification for local listeners
                applicationEventPublisher.publishEvent(new DomainEventNotification(
                        event.getId(), event.getAggregateType(), event.getAggregateId(), event.getEventType(), event.getPayload()));

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("Successfully published outbox event id={} type={}", event.getId(), event.getEventType());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                event.setLastError(ex.getMessage());
                if (event.getRetryCount() >= maxRetries) {
                    event.setStatus("FAILED");
                    log.error("Outbox event id={} reached max retries ({}), marked FAILED", event.getId(), maxRetries);
                } else {
                    log.warn("Transient error publishing outbox event id={}, will retry (attempt {}): {}",
                            event.getId(), event.getRetryCount(), ex.getMessage());
                }
                outboxEventRepository.save(event);
            }
        }
    }
}

