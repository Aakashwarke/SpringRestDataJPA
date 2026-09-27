package com.springlaunch.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stripe guarantees at-least-once webhook delivery, so the same event can arrive twice.
 * Recording the event id under a unique constraint turns that into exactly-once handling:
 * the second insert loses the race and the handler short-circuits.
 */
@Entity
@Table(name = "processed_webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public static ProcessedWebhookEvent of(String eventId, String eventType) {
        ProcessedWebhookEvent event = new ProcessedWebhookEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.receivedAt = Instant.now();
        return event;
    }
}
