package com.world_dance.ms_scheduling.controller;

import com.world_dance.wd_lib_common.dto.ScheduleGenerationRequestDto;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationResponseDto;
import com.world_dance.ms_scheduling.service.SchedulingService;
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
            @Valid @RequestBody ScheduleGenerationRequestDto request) {
        log.info("Solicitud de generacion de cronograma para evento: {}", request.getEventId());
        ScheduleGenerationResponseDto response = schedulingService.generateSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<ScheduleGenerationResponseDto> getScheduleByEvent(
            @PathVariable Long eventId) {
        log.info("Obteniendo cronograma del evento: {}", eventId);
        ScheduleGenerationResponseDto response = schedulingService.getScheduleByEvent(eventId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long eventId) {
        log.info("Eliminando cronograma del evento: {}", eventId);
        schedulingService.deleteSchedule(eventId);
        return ResponseEntity.noContent().build();
    }
}
