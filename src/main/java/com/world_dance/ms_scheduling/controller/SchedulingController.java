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
            @RequestHeader(value = "X-User-Id", required = true) Long userId,
            @Valid @RequestBody ScheduleGenerationRequestDto request) {
        log.info("Solicitud de generación de cronograma para evento: {} por usuario con id: {}", request.getEventId(), userId);
        ScheduleGenerationResponseDto response = schedulingService.generateSchedule(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ScheduleGenerationResponseDto> getScheduleByEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-User-Id", required = true) Long userId) {
        log.info("Consulta de cronograma para evento: {} por usuario con id: {}", eventId, userId);
        ScheduleGenerationResponseDto response = schedulingService.getScheduleByEvent(eventId, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/event/{eventId}/status")
    public ResponseEntity<ScheduleGenerationResponseDto> updateScheduleStatus(
            @PathVariable Long eventId,
            @RequestParam ScheduleStatus status,
            @RequestHeader(value = "X-User-Id", required = true) Long userId) {
        log.info("Actualizando estado del cronograma del evento: {} a status: {} por id: {}", eventId, status, userId);
        ScheduleGenerationResponseDto response = schedulingService.updateScheduleStatus(eventId, status, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-User-Id", required = true) Long userId) {
        log.info("Solicitud de eliminación de cronograma para evento: {} por id: {}", eventId, userId);
        schedulingService.deleteSchedule(eventId, userId);
        return ResponseEntity.noContent().build();
    }
}
