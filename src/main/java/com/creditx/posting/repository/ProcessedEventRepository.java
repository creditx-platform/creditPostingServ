package com.creditx.posting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.creditx.posting.model.ProcessedEvent;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    
    boolean existsByEventId(String eventId);
    
    boolean existsByPayloadHash(String payloadHash);
}
