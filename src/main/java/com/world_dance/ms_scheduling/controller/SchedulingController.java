package com.world_dance.ms_scheduling.controller;

import com.world_dance.ms_scheduling.service.SchedulingService;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationRequestDto;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationResponseDto;
import com.world_dance.wd_lib_common.enums.ScheduleStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scheduling")
@RequiredArgsConstructor
@Slf4j
public class SchedulingController {

    private final SchedulingService schedulingService;

    @PostMapping("/generate")
    public ResponseEntity<ScheduleGenerationResponseDto> generateSchedule(
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "ORGANIZER") String userRole,
            @Valid @RequestBody ScheduleGenerationRequestDto request) {
        log.info("Solicitud de generación de cronograma para evento: {} por usuario con rol: {}", request.getEventId(), userRole);
        ScheduleGenerationResponseDto response = schedulingService.generateSchedule(request, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ScheduleGenerationResponseDto> getScheduleByEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        log.info("Consulta de cronograma para evento: {} por usuario con rol: {}", eventId, userRole);
        ScheduleGenerationResponseDto response = schedulingService.getScheduleByEvent(eventId, userRole);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/event/{eventId}/status")
    public ResponseEntity<ScheduleGenerationResponseDto> updateScheduleStatus(
            @PathVariable Long eventId,
            @RequestParam ScheduleStatus status,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "ORGANIZER") String userRole) {
        log.info("Actualizando estado del cronograma del evento: {} a status: {} por rol: {}", eventId, status, userRole);
        ScheduleGenerationResponseDto response = schedulingService.updateScheduleStatus(eventId, status, userRole);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-User-Role", required = false, defaultValue = "ORGANIZER") String userRole) {
        log.info("Solicitud de eliminación de cronograma para evento: {} por rol: {}", eventId, userRole);
        schedulingService.deleteSchedule(eventId, userRole);
        return ResponseEntity.noContent().build();
    }
}
