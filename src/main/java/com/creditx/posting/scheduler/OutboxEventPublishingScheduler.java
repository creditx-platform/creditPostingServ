package com.creditx.posting.scheduler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.creditx.posting.messaging.OutboxStreamPublisher;
import com.creditx.posting.model.OutboxEvent;
import com.creditx.posting.service.OutboxEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxEventPublishingScheduler {
    private final OutboxEventService outboxEventService;
    private final OutboxStreamPublisher outboxStreamPublisher;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventService.fetchPendingEvents(batchSize);

        for (OutboxEvent event : events) {
            try {
                outboxStreamPublisher.publish(event.getAggregateId().toString(), event.getPayload());
                outboxEventService.markAsPublished(event);
            } catch (Exception e) {
                outboxEventService.markAsFailed(event);
            }
        }
    }
}
