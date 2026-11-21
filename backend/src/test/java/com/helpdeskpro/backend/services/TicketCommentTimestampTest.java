package com.helpdeskpro.backend.services;

import com.helpdeskpro.backend.domain.entities.TicketComment;
import com.helpdeskpro.backend.dto.TicketCommentResponseDTO;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.TimeZone;
import static org.junit.jupiter.api.Assertions.*;

class TicketCommentTimestampTest {
    @Test
    void recordsUtcIndependentlyOfTheServerTimezone() {
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
            Instant before=Instant.now();
            TicketComment comment=new TicketComment("Test",null,null);
            Instant recorded=comment.getCreatedAt().toInstant(ZoneOffset.UTC);
            assertFalse(recorded.isBefore(before));
            assertFalse(recorded.isAfter(Instant.now()));
        } finally {TimeZone.setDefault(previous);}
    }
    @Test
    void responseMakesTheStoredUtcOffsetExplicitWithoutChangingTheRecord() {
        TicketComment comment=new TicketComment("Test",null,null);
        LocalDateTime stored=LocalDateTime.of(2026,11,19,0,25,43);
        comment.setCreatedAt(stored);
        var response=TicketCommentResponseDTO.fromEntity(comment);
        assertEquals(ZoneOffset.UTC,response.getCreatedAt().getOffset());
        assertEquals(Instant.parse("2026-11-19T00:25:43Z"),response.getCreatedAt().toInstant());
        assertEquals(stored,comment.getCreatedAt());
    }
}
