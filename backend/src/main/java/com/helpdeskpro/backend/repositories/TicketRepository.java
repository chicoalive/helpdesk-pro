package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByRequesterId(Long requesterId);
}
