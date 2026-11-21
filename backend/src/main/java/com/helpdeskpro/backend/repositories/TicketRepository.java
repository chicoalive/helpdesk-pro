package com.helpdeskpro.backend.repositories;

import com.helpdeskpro.backend.domain.entities.Ticket;
import com.helpdeskpro.backend.domain.enums.TicketCategory;
import com.helpdeskpro.backend.domain.enums.TicketStatus;
import com.helpdeskpro.backend.dto.TechnicianTicketCountDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    boolean existsByTitle(String title);

    Optional<Ticket> findFirstByTitleOrderByIdAsc(String title);

    @Override
    @Query("select new com.helpdeskpro.backend.domain.entities.Ticket("
            + "t.id, t.title, t.description, t.solution, t.status, t.category, t.priority, "
            + "r, tech, a, t.createdAt, t.updatedAt, t.attachmentName, t.attachmentContentType) "
            + "from Ticket t "
            + "left join t.requester r "
            + "left join t.technician tech "
            + "left join t.asset a")
    List<Ticket> findAll();

    @Query("select new com.helpdeskpro.backend.domain.entities.Ticket("
            + "t.id, t.title, t.description, t.solution, t.status, t.category, t.priority, "
            + "r, tech, a, t.createdAt, t.updatedAt, t.attachmentName, t.attachmentContentType) "
            + "from Ticket t "
            + "left join t.requester r "
            + "left join t.technician tech "
            + "left join t.asset a "
            + "where r.id = :requesterId")
    List<Ticket> findByRequesterId(@Param("requesterId") Long requesterId);

    Optional<Ticket> findFirstByRequesterIdAndCategoryAndStatusInOrderByIdAsc(
            Long requesterId,
            TicketCategory category,
            Collection<TicketStatus> statuses
    );

    Optional<Ticket> findFirstByRequesterIdAndCategoryAndAssetIdAndStatusInOrderByIdAsc(
            Long requesterId,
            TicketCategory category,
            Long assetId,
            Collection<TicketStatus> statuses
    );

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
