package com.creditx.posting.repository;

import com.creditx.posting.model.OutboxEvent;
import com.creditx.posting.model.OutboxEventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

  @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
  List<OutboxEvent> findPendingEvents(OutboxEventStatus status);
}
