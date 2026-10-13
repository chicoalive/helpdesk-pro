package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.dto.TechnicianTicketCountDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByRequesterId(Long requesterId);

    @Query("select t.status, count(t) from Ticket t group by t.status")
    List<Object[]> countGroupedByStatus();

    @Query("select t.priority, count(t) from Ticket t group by t.priority")
    List<Object[]> countGroupedByPriority();

    @Query("select new com.helpdeskpro.backend.dto.TechnicianTicketCountDTO(t.technician.id, t.technician.name, count(t)) "
            + "from Ticket t where t.technician is not null "
            + "group by t.technician.id, t.technician.name "
            + "order by count(t) desc, t.technician.name asc")
    List<TechnicianTicketCountDTO> countGroupedByTechnician();

    long countByTechnicianIsNull();
}
