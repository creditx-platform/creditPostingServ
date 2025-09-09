package com.creditx.posting.service;

import com.creditx.posting.model.OutboxEvent;
import java.util.List;

public interface OutboxEventService {

  OutboxEvent saveEvent(String eventType, Long aggregateId, String payload);

  List<OutboxEvent> fetchPendingEvents(int limit);

  void markAsPublished(OutboxEvent event);

  void markAsFailed(OutboxEvent event);
}