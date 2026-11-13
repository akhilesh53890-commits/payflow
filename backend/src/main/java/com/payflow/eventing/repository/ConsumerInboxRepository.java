package com.payflow.eventing.repository;

import com.payflow.eventing.domain.ConsumerInbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerInboxRepository extends JpaRepository<ConsumerInbox, String> {
    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);
}

