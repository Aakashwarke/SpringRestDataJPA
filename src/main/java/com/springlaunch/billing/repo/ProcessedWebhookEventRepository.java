package com.springlaunch.billing.repo;

import com.springlaunch.billing.domain.ProcessedWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository extends JpaRepository<ProcessedWebhookEvent, Long> {

    boolean existsByEventId(String eventId);
}
