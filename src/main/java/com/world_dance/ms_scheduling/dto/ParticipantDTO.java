package com.world_dance.ms_scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {
    private Long id;
    private String name;
    private String lastName;
    private String email;
    private String documentNumber;
}