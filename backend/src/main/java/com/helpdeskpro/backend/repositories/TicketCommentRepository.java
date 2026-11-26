package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, Long> {

    @org.springframework.data.jpa.repository.Query("select c from TicketComment c where c.ticket.id = :ticketId order by c.createdAt asc, c.id asc")
    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
