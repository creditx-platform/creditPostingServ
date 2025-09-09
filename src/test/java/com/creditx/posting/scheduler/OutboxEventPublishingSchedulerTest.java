package com.creditx.posting.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.creditx.posting.messaging.OutboxStreamPublisher;
import com.creditx.posting.model.OutboxEvent;
import com.creditx.posting.service.OutboxEventService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublishingSchedulerTest {

  @Mock
  private OutboxEventService outboxEventService;

  @Mock
  private OutboxStreamPublisher outboxStreamPublisher;

  @InjectMocks
  private OutboxEventPublishingScheduler outboxEventPublishingScheduler;

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(outboxEventPublishingScheduler, "batchSize", 10);
  }

  @Test
  void shouldPublishPendingEvents() {
    // given
    UUID aggregateId1 = UUID.randomUUID();
    UUID aggregateId2 = UUID.randomUUID();

    OutboxEvent event1 = createOutboxEvent(aggregateId1, "{\"transactionId\":123}");
    OutboxEvent event2 = createOutboxEvent(aggregateId2, "{\"transactionId\":456}");

    List<OutboxEvent> events = Arrays.asList(event1, event2);

    when(outboxEventService.fetchPendingEvents(10)).thenReturn(events);

    // when
    outboxEventPublishingScheduler.publishPendingEvents();

    // then
    verify(outboxStreamPublisher, times(1)).publish(
        String.valueOf(aggregateId1.getMostSignificantBits()), "{\"transactionId\":123}",
        "transaction.posted");
    verify(outboxStreamPublisher, times(1)).publish(
        String.valueOf(aggregateId2.getMostSignificantBits()), "{\"transactionId\":456}",
        "transaction.posted");
    verify(outboxEventService, times(1)).markAsPublished(event1);
    verify(outboxEventService, times(1)).markAsPublished(event2);
  }

  @Test
  void shouldNotPublishWhenNoPendingEvents() {
    // given
    when(outboxEventService.fetchPendingEvents(10)).thenReturn(Collections.emptyList());

    // when
    outboxEventPublishingScheduler.publishPendingEvents();

    // then
    verify(outboxStreamPublisher, never()).publish(any(), any(), any());
    verify(outboxEventService, never()).markAsPublished(any());
    verify(outboxEventService, never()).markAsFailed(any());
  }

  @Test
  void shouldMarkAsFailedWhenPublishingFails() {
    // given
    UUID aggregateId = UUID.randomUUID();
    OutboxEvent event = createOutboxEvent(aggregateId, "{\"transactionId\":123}");

    when(outboxEventService.fetchPendingEvents(10)).thenReturn(List.of(event));
    doThrow(new RuntimeException("Publishing failed")).when(outboxStreamPublisher)
        .publish(String.valueOf(aggregateId.getMostSignificantBits()), "{\"transactionId\":123}",
            "transaction.posted");

    // when
    outboxEventPublishingScheduler.publishPendingEvents();

    // then
    verify(outboxStreamPublisher, times(1)).publish(
        String.valueOf(aggregateId.getMostSignificantBits()), "{\"transactionId\":123}",
        "transaction.posted");
    verify(outboxEventService, never()).markAsPublished(event);
    verify(outboxEventService, times(1)).markAsFailed(event);
  }

  private OutboxEvent createOutboxEvent(UUID aggregateId, String payload) {
    OutboxEvent event = new OutboxEvent();
    event.setAggregateId(aggregateId.getMostSignificantBits());
    event.setPayload(payload);
    event.setEventType("transaction.posted");
    return event;
  }
}