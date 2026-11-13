package com.payflow.eventing.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumer_inbox", uniqueConstraints = {
        @UniqueConstraint(name = "uq_consumer_event", columnNames = {"consumer_name", "event_id"})
})
public class ConsumerInbox {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "consumer_name", nullable = false, length = 64)
    private String consumerName;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ConsumerInbox() {}

    public ConsumerInbox(String consumerName, String eventId) {
        this.id = UUID.randomUUID().toString();
        this.consumerName = consumerName;
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getConsumerName() { return consumerName; }
    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
}

