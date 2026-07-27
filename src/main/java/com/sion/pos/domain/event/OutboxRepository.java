package com.sion.pos.domain.event;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findByPublishedAtIsNullOrderByIdAsc(Pageable pageable);
}