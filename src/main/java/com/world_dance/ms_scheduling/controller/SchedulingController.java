package com.world_dance.ms_scheduling.controller;

import com.world_dance.ms_scheduling.service.SchedulingService;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationRequestDto;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationResponseDto;
import com.world_dance.wd_lib_common.dto.HttpGlobalResponse;
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
    public ResponseEntity<HttpGlobalResponse<ScheduleGenerationResponseDto>> generateSchedule(
            @RequestHeader(value = "X-User-Id", required = true) Long userId,
            @Valid @RequestBody ScheduleGenerationRequestDto request) {
        log.info("Solicitud de generación de cronograma para evento: {} por usuario con id: {}", request.getEventId(), userId);
        ScheduleGenerationResponseDto response = schedulingService.generateSchedule(request, userId);
        
        HttpGlobalResponse<ScheduleGenerationResponseDto> globalResponse = new HttpGlobalResponse<>();
        globalResponse.setData(response);
        globalResponse.setMessage("Cronograma generado exitosamente.");
        return ResponseEntity.status(HttpStatus.CREATED).body(globalResponse);
    }

    @GetMapping("/event/{eventId}")
    public ResponseEntity<HttpGlobalResponse<ScheduleGenerationResponseDto>> getScheduleByEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-User-Id", required = true) Long userId) {
        log.info("Consulta de cronograma para evento: {} por usuario con id: {}", eventId, userId);
        ScheduleGenerationResponseDto response = schedulingService.getScheduleByEvent(eventId, userId);
        
        HttpGlobalResponse<ScheduleGenerationResponseDto> globalResponse = new HttpGlobalResponse<>();
        globalResponse.setData(response);
        globalResponse.setMessage("Cronograma obtenido con éxito.");
        return ResponseEntity.ok(globalResponse);
    }

    @PatchMapping("/event/{eventId}/status")
    public ResponseEntity<HttpGlobalResponse<ScheduleGenerationResponseDto>> updateScheduleStatus(
            @PathVariable Long eventId,
            @RequestParam ScheduleStatus status,
            @RequestHeader(value = "X-User-Id", required = true) Long userId) {
        log.info("Actualizando estado del cronograma del evento: {} a status: {} por id: {}", eventId, status, userId);
        ScheduleGenerationResponseDto response = schedulingService.updateScheduleStatus(eventId, status, userId);
        
        HttpGlobalResponse<ScheduleGenerationResponseDto> globalResponse = new HttpGlobalResponse<>();
        globalResponse.setData(response);
        globalResponse.setMessage("Estado del cronograma actualizado con éxito.");
        return ResponseEntity.ok(globalResponse);
    }

    @DeleteMapping("/event/{eventId}")
    public ResponseEntity<HttpGlobalResponse<Void>> deleteSchedule(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-User-Id", required = true) Long userId) {
        log.info("Solicitud de eliminación de cronograma para evento: {} por id: {}", eventId, userId);
        schedulingService.deleteSchedule(eventId, userId);
        
        HttpGlobalResponse<Void> globalResponse = new HttpGlobalResponse<>();
        globalResponse.setMessage("Cronograma eliminado exitosamente.");
        return ResponseEntity.ok(globalResponse);
    }
}
