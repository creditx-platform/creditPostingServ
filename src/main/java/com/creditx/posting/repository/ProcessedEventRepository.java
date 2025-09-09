package com.creditx.posting.repository;

import com.creditx.posting.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

  boolean existsByEventId(String eventId);

  boolean existsByPayloadHash(String payloadHash);
}
