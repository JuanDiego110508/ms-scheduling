package com.world_dance.ms_scheduling.service;

import com.world_dance.ms_scheduling.client.EnrollmentServiceClient;
import com.world_dance.ms_scheduling.client.EventServiceClient;
import com.world_dance.ms_scheduling.dto.EnrollmentDTO;
import com.world_dance.ms_scheduling.dto.EventDTO;
import com.world_dance.ms_scheduling.dto.ParticipantDTO;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationRequestDto;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationResponseDto;
import com.world_dance.wd_lib_common.dto.ScheduleSlotDto;
import com.world_dance.wd_lib_common.entity.Event;
import com.world_dance.wd_lib_common.entity.Modality;
import com.world_dance.wd_lib_common.entity.PresentationSlot;
import com.world_dance.wd_lib_common.entity.Schedule;
import com.world_dance.wd_lib_common.enums.Division;
import com.world_dance.wd_lib_common.enums.ScheduleStatus;
import com.world_dance.wd_lib_common.enums.SlotStatus;
import com.world_dance.wd_lib_common.exception.BadRequestException;
import com.world_dance.wd_lib_common.exception.ResourceNotFoundException;
import com.world_dance.wd_lib_common.repository.EventRepository;
import com.world_dance.wd_lib_common.repository.ModalityRepository;
import com.world_dance.wd_lib_common.repository.PresentationSlotRepository;
import com.world_dance.wd_lib_common.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {

    private final EventServiceClient eventServiceClient;
    private final EventRepository eventRepository;
    private final ModalityRepository modalityRepository;
    private final ScheduleRepository scheduleRepository;
    private final PresentationSlotRepository presentationSlotRepository;
    private final EnrollmentServiceClient enrollmentServiceClient;

    private static final List<Division> DIVISION_ORDER = Arrays.asList(
            Division.SOLO,
            Division.DUET,
            Division.GROUP
    );

    @Transactional
    public ScheduleGenerationResponseDto generateSchedule(ScheduleGenerationRequestDto request, Long userId) {
        Long eventId = request.getEventId();
        EventDTO eventDto = fetchEventInfo(eventId);
        validateOrganizerRole(userId, eventDto);
        log.info("Generando cronograma para evento: {}", eventId);

        List<Modality> modalities = modalityRepository.findByEventId(eventId);
        if (modalities.isEmpty()) {
            throw new BadRequestException("El evento no tiene modalidades configuradas.");
        }

        List<EnrollmentDTO> enrollments = enrollmentServiceClient.getEnrollmentsByEvent(eventId);
        if (enrollments == null || enrollments.isEmpty()) {
            throw new BadRequestException("El evento no tiene inscripciones registradas para generar el cronograma.");
        }

        int durationMinutes = request.getDefaultDurationMinutes() != null ? request.getDefaultDurationMinutes() : 5;
        int transitionMinutes = request.getTransitionMinutes() != null ? request.getTransitionMinutes() : 2;

        List<String> stageNames = request.getStageNames() != null && !request.getStageNames().isEmpty()
                ? request.getStageNames()
                : List.of("Escenario Principal");

        List<Modality> sortedModalities = modalities.stream()
                .sorted(Comparator.comparingInt((Modality m) -> DIVISION_ORDER.indexOf(m.getDivision()))
                        .thenComparing(Modality::getCategory))
                .collect(Collectors.toList());

        Map<Long, List<EnrollmentDTO>> enrollmentsByModality = enrollments.stream()
                .filter(e -> e.getModalityId() != null)
                .collect(Collectors.groupingBy(EnrollmentDTO::getModalityId));

        List<ScheduleSlotDto> slots = new ArrayList<>();
        LocalDateTime currentTime = eventDto.getStartDate() != null ? eventDto.getStartDate() : LocalDateTime.now();
        int orderCounter = 1;
        int stageIndex = 0;

        for (Modality modality : sortedModalities) {
            List<EnrollmentDTO> modalityEnrollments = enrollmentsByModality.getOrDefault(modality.getId(), new ArrayList<>());

            if (modalityEnrollments.isEmpty()) {
                continue;
            }

            modalityEnrollments.sort((e1, e2) -> {
                if (e1.getCreatedAt() == null && e2.getCreatedAt() == null) return 0;
                if (e1.getCreatedAt() == null) return 1;
                if (e2.getCreatedAt() == null) return -1;
                return e2.getCreatedAt().compareTo(e1.getCreatedAt());
            });

            for (EnrollmentDTO enrollment : modalityEnrollments) {
                ParticipantDTO participant = enrollment.getParticipant();
                String participantName = participant != null && participant.getName() != null
                        ? participant.getName() + (participant.getLastName() != null ? " " + participant.getLastName() : "")
                        : "Participante " + enrollment.getId();

                ScheduleSlotDto slot = ScheduleSlotDto.builder()
                        .enrollmentId(enrollment.getId())
                        .participantName(participantName)
                        .division(modality.getDivision())
                        .category(modality.getCategory())
                        .style(modality.getStyle())
                        .startTime(currentTime)
                        .endTime(currentTime.plusMinutes(durationMinutes))
                        .stage(stageNames.get(stageIndex % stageNames.size()))
                        .order(orderCounter)
                        .status(ScheduleStatus.DRAFT)
                        .notes(request.getNotes())
                        .build();

                slots.add(slot);

                currentTime = currentTime.plusMinutes(durationMinutes + transitionMinutes);
                orderCounter++;
                stageIndex++;

                if (eventDto.getEndDate() != null && currentTime.isAfter(eventDto.getEndDate())) {
                    throw new BadRequestException("No hay suficiente tiempo disponible en el evento para todas las presentaciones.");
                }
            }
        }

        scheduleRepository.deleteByEventId(eventId);

        Schedule schedule = Schedule.builder()
                .eventId(eventId)
                .status(ScheduleStatus.DRAFT)
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);

        List<PresentationSlot> presentationSlots = new ArrayList<>();
        for (ScheduleSlotDto slot : slots) {
            PresentationSlot presentationSlot = PresentationSlot.builder()
                    .scheduleId(savedSchedule.getId())
                    .enrollmentId(slot.getEnrollmentId())
                    .presentationOrder(slot.getOrder())
                    .estimatedTime(slot.getStartTime().toLocalTime())
                    .status(SlotStatus.PENDING)
                    .build();
            presentationSlots.add(presentationSlot);
        }

        presentationSlotRepository.saveAll(presentationSlots);

        return ScheduleGenerationResponseDto.builder()
                .eventId(eventId)
                .eventName(eventDto.getName())
                .eventStartDate(eventDto.getStartDate())
                .eventEndDate(eventDto.getEndDate())
                .totalSlots(slots.size())
                .generatedAt(LocalDateTime.now())
                .schedules(slots)
                .build();
    }

    @Transactional(readOnly = true)
    public ScheduleGenerationResponseDto getScheduleByEvent(Long eventId) {
        return getScheduleByEvent(eventId, null);
    }

    @Transactional(readOnly = true)
    public ScheduleGenerationResponseDto getScheduleByEvent(Long eventId, Long userId) {
        log.info("Obteniendo cronograma del evento: {} (Solicitado por userId: {})", eventId, userId);

        Schedule schedule = scheduleRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró un cronograma configurado para el evento con ID: " + eventId));

        EventDTO eventDto = fetchEventInfo(eventId);
        boolean isOrganizer = isOrganizer(userId, eventDto);
        boolean canViewDraft = isOrganizer;
        
        if (!isOrganizer && userId != null) {
            try {
                com.world_dance.wd_lib_common.dto.UserEventRoleResponseDto roleResp = enrollmentServiceClient.getUserEventRole(eventId, userId);
                if (roleResp != null && roleResp.getRoleInEvent() != null) {
                    com.world_dance.wd_lib_common.enums.EventRole role = roleResp.getRoleInEvent();
                    if (role == com.world_dance.wd_lib_common.enums.EventRole.STAFF || role == com.world_dance.wd_lib_common.enums.EventRole.JURY) {
                        canViewDraft = true;
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener el rol del usuario {} en el evento {}: {}", userId, eventId, e.getMessage());
            }
        }

        if (schedule.getStatus() == ScheduleStatus.DRAFT && !canViewDraft) {
            throw new BadRequestException("El cronograma para este evento aún se encuentra en borrador y no ha sido publicado.");
        }
        List<PresentationSlot> slots = presentationSlotRepository.findByScheduleIdOrdered(schedule.getId());
        List<Modality> modalities = modalityRepository.findByEventId(eventId);
        Map<Long, Modality> modalityMap = modalities.stream().collect(Collectors.toMap(Modality::getId, m -> m, (m1, m2) -> m1));

        List<EnrollmentDTO> enrollments = new ArrayList<>();
        try {
            enrollments = enrollmentServiceClient.getEnrollmentsByEvent(eventId);
        } catch (Exception e) {
            log.warn("No se pudieron consultar las inscripciones detalladas desde ms-enrollment: {}", e.getMessage());
        }
        Map<Long, EnrollmentDTO> enrollmentMap = enrollments != null
                ? enrollments.stream().collect(Collectors.toMap(EnrollmentDTO::getId, e -> e, (e1, e2) -> e1))
                : Collections.emptyMap();

        LocalDateTime baseDate = eventDto.getStartDate() != null ? eventDto.getStartDate() : LocalDateTime.now();

        List<ScheduleSlotDto> slotDtos = new ArrayList<>();
        for (PresentationSlot slot : slots) {
            EnrollmentDTO enrollment = enrollmentMap.get(slot.getEnrollmentId());
            Modality modality = enrollment != null && enrollment.getModalityId() != null
                    ? modalityMap.get(enrollment.getModalityId())
                    : null;

            String participantName = "Inscripción #" + slot.getEnrollmentId();
            if (enrollment != null && enrollment.getParticipant() != null) {
                ParticipantDTO p = enrollment.getParticipant();
                participantName = p.getName() + (p.getLastName() != null ? " " + p.getLastName() : "");
            }

            LocalTime estimatedTime = slot.getEstimatedTime() != null ? slot.getEstimatedTime() : baseDate.toLocalTime();
            LocalDateTime slotStart = baseDate.with(estimatedTime);

            ScheduleSlotDto slotDto = ScheduleSlotDto.builder()
                    .id(slot.getId())
                    .enrollmentId(slot.getEnrollmentId())
                    .participantName(participantName)
                    .division(modality != null ? modality.getDivision() : null)
                    .category(modality != null ? modality.getCategory() : null)
                    .style(modality != null ? modality.getStyle() : null)
                    .startTime(slotStart)
                    .endTime(slotStart.plusMinutes(5))
                    .stage("Escenario Principal")
                    .order(slot.getPresentationOrder())
                    .status(schedule.getStatus())
                    .build();

            slotDtos.add(slotDto);
        }

        return ScheduleGenerationResponseDto.builder()
                .eventId(eventId)
                .eventName(eventDto.getName())
                .eventStartDate(eventDto.getStartDate())
                .eventEndDate(eventDto.getEndDate())
                .totalSlots(slotDtos.size())
                .generatedAt(schedule.getCreatedAt() != null ? schedule.getCreatedAt() : LocalDateTime.now())
                .schedules(slotDtos)
                .build();
    }

    @Transactional
    public ScheduleGenerationResponseDto updateScheduleStatus(Long eventId, ScheduleStatus status, Long userId) {
        EventDTO eventDto = fetchEventInfo(eventId);
        validateOrganizerRole(userId, eventDto);
        log.info("Actualizando estado del cronograma del evento {} a {}", eventId, status);

        Schedule schedule = scheduleRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe cronograma para el evento con ID: " + eventId));

        schedule.setStatus(status);
        scheduleRepository.save(schedule);

        return getScheduleByEvent(eventId, userId);
    }

    @Transactional
    public void deleteSchedule(Long eventId, Long userId) {
        EventDTO eventDto = fetchEventInfo(eventId);
        validateOrganizerRole(userId, eventDto);
        log.info("Eliminando cronograma del evento: {}", eventId);

        if (!scheduleRepository.existsByEventId(eventId)) {
            throw new ResourceNotFoundException("No se encontró cronograma para eliminar en el evento con ID: " + eventId);
        }

        scheduleRepository.deleteByEventId(eventId);
    }

    private EventDTO fetchEventInfo(Long eventId) {
        try {
            com.world_dance.wd_lib_common.dto.HttpGlobalResponse<com.world_dance.wd_lib_common.dto.EventResponseDto> response = eventServiceClient.getEvent(eventId);
            if (response != null && response.getData() != null) {
                com.world_dance.wd_lib_common.dto.EventResponseDto dto = response.getData();
                return EventDTO.builder()
                        .id(dto.getIdEvent())
                        .ownerId(dto.getOwnerId())
                        .name(dto.getName())
                        .description(dto.getDescription())
                        // Note: EventResponseDto dates are Strings. Parse them or fallback if needed.
                        .startDate(dto.getStartDate() != null ? LocalDateTime.parse(dto.getStartDate()) : null)
                        .endDate(dto.getEndDate() != null ? LocalDateTime.parse(dto.getEndDate()) : null)
                        .location(dto.getLocation())
                        .status(dto.getStatus() != null ? dto.getStatus().name() : null)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Fallo al obtener evento vía Feign: {}. Intentando vía EventRepository.", e.getMessage());
        }

        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            return EventDTO.builder()
                    .id(event.getId())
                    .name(event.getName())
                    .description(event.getDescription())
                    .startDate(event.getStartDate())
                    .endDate(event.getEndDate())
                    .location(event.getLocation())
                    .status(event.getStatus() != null ? event.getStatus().name() : null)
                    .ownerId(event.getOwnerId())
                    .build();
        }

        throw new ResourceNotFoundException("No se encontró el evento con ID: " + eventId);
    }

    private void validateOrganizerRole(Long userId, EventDTO eventDto) {
        if (!isOrganizer(userId, eventDto)) {
            throw new BadRequestException("Acceso denegado: Esta acción requiere ser el organizador del evento.");
        }
    }

    private boolean isOrganizer(Long userId, EventDTO eventDto) {
        return userId != null && eventDto != null && userId.equals(eventDto.getOwnerId());
    }
}