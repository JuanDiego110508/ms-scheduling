package com.world_dance.ms_scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentDTO {
    private Long id;
    private Long userId;
    private Long eventId;
    private Long modalityId;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
    private ParticipantDTO participant;
}