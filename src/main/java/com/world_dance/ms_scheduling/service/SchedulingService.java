package com.world_dance.ms_scheduling.service;

import com.world_dance.ms_scheduling.client.EnrollmentServiceClient;
import com.world_dance.ms_scheduling.client.EventServiceClient;
import com.world_dance.ms_scheduling.dto.EnrollmentDTO;
import com.world_dance.ms_scheduling.dto.EventDTO;
import com.world_dance.ms_scheduling.dto.ParticipantDTO;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationRequestDto;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationResponseDto;
import com.world_dance.wd_lib_common.dto.ScheduleSlotDto;
import com.world_dance.wd_lib_common.dto.ModalityResponseDto;
import com.world_dance.wd_lib_common.dto.UserEventRoleResponseDto;
import com.world_dance.wd_lib_common.entity.Event;
import com.world_dance.wd_lib_common.entity.PresentationSlot;
import com.world_dance.wd_lib_common.entity.Schedule;
import com.world_dance.wd_lib_common.enums.Division;
import com.world_dance.wd_lib_common.enums.EventRole;
import com.world_dance.wd_lib_common.enums.ScheduleStatus;
import com.world_dance.wd_lib_common.enums.SlotStatus;
import com.world_dance.wd_lib_common.exception.BadRequestException;
import com.world_dance.wd_lib_common.exception.ResourceNotFoundException;
import com.world_dance.wd_lib_common.repository.EventRepository;
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

        List<ModalityResponseDto> modalities = fetchModalities(eventId);
        if (modalities.isEmpty()) {
            throw new BadRequestException("El evento no tiene modalidades configuradas.");
        }
        validateModalityOrder(request.getModalityOrder(), modalities);

        List<EnrollmentDTO> enrollments = null;
        try {
            enrollments = enrollmentServiceClient.getEnrollmentsByEvent(eventId);
        } catch (Exception e) {
            log.warn("Error al obtener inscripciones para el evento {}: {}", eventId, e.getMessage());
        }

        if (enrollments == null || enrollments.isEmpty()) {
            throw new BadRequestException("El evento no tiene inscripciones registradas para generar el cronograma.");
        }

        int durationMinutes = request.getDefaultDurationMinutes() != null ? request.getDefaultDurationMinutes() : 5;
        int transitionMinutes = request.getTransitionMinutes() != null ? request.getTransitionMinutes() : 2;

        List<String> stageNames = request.getStageNames() != null && !request.getStageNames().isEmpty()
                ? request.getStageNames()
                : List.of("Escenario Principal");

        List<ModalityResponseDto> sortedModalities = modalities.stream()
                .sorted(buildModalityComparator(request.getModalityOrder()))
                .collect(Collectors.toList());

        Comparator<EnrollmentDTO> enrollmentComparator = buildEnrollmentComparator(request.getSortingStrategy());

        Map<Long, List<EnrollmentDTO>> enrollmentsByModality = enrollments.stream()
                .filter(e -> e.getModalityId() != null)
                .collect(Collectors.groupingBy(EnrollmentDTO::getModalityId));

        List<ScheduleSlotDto> slots = new ArrayList<>();
        LocalDateTime currentTime = eventDto.getStartDate() != null ? eventDto.getStartDate() : LocalDateTime.now();
        int orderCounter = 1;
        int stageIndex = 0;

        for (ModalityResponseDto modality : sortedModalities) {
            List<EnrollmentDTO> modalityEnrollments = enrollmentsByModality.getOrDefault(modality.getId(), new ArrayList<>());

            if (modalityEnrollments.isEmpty()) {
                continue;
            }

            modalityEnrollments.sort(enrollmentComparator);

            for (EnrollmentDTO enrollment : modalityEnrollments) {
                String participantName = participantDisplayName(enrollment);

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

        scheduleRepository.findByEventId(eventId).ifPresent(existingSchedule -> {
            presentationSlotRepository.deleteByScheduleId(existingSchedule.getId());
            scheduleRepository.delete(existingSchedule);
        });

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
                    .stage(slot.getStage())
                    .durationMinutes(durationMinutes)
                    .notes(slot.getNotes())
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
        List<ModalityResponseDto> modalities = fetchModalities(eventId);
        Map<Long, ModalityResponseDto> modalityMap = modalities.stream().collect(Collectors.toMap(ModalityResponseDto::getId, m -> m, (m1, m2) -> m1));

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
            ModalityResponseDto modality = enrollment != null && enrollment.getModalityId() != null
                    ? modalityMap.get(enrollment.getModalityId())
                    : null;

            String participantName = "Inscripción #" + slot.getEnrollmentId();
            if (enrollment != null && enrollment.getParticipant() != null) {
                ParticipantDTO p = enrollment.getParticipant();
                participantName = p.getName() + (p.getLastName() != null ? " " + p.getLastName() : "");
            }

            LocalTime estimatedTime = slot.getEstimatedTime() != null ? slot.getEstimatedTime() : baseDate.toLocalTime();
            LocalDateTime slotStart = baseDate.with(estimatedTime);
            int slotDuration = slot.getDurationMinutes() != null ? slot.getDurationMinutes() : 5;

            ScheduleSlotDto slotDto = ScheduleSlotDto.builder()
                    .id(slot.getId())
                    .enrollmentId(slot.getEnrollmentId())
                    .participantName(participantName)
                    .division(modality != null ? modality.getDivision() : null)
                    .category(modality != null ? modality.getCategory() : null)
                    .style(modality != null ? modality.getStyle() : null)
                    .startTime(slotStart)
                    .endTime(slotStart.plusMinutes(slotDuration))
                    .stage(slot.getStage() != null ? slot.getStage() : "Escenario Principal")
                    .order(slot.getPresentationOrder())
                    .status(schedule.getStatus())
                    .notes(slot.getNotes())
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

        scheduleRepository.findByEventId(eventId).ifPresent(schedule -> {
            presentationSlotRepository.deleteByScheduleId(schedule.getId());
            scheduleRepository.delete(schedule);
        });
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

    private List<ModalityResponseDto> fetchModalities(Long eventId) {
        try {
            com.world_dance.wd_lib_common.dto.HttpGlobalResponse<List<ModalityResponseDto>> response =
                    eventServiceClient.getModalitiesByEvent(eventId);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Fallo al obtener modalidades vía Feign para el evento {}: {}", eventId, e.getMessage());
        }
        return Collections.emptyList();
    }

    private void validateOrganizerRole(Long userId, EventDTO eventDto) {
        if (!isOrganizer(userId, eventDto) && !hasAdminRole(userId, eventDto)) {
            throw new BadRequestException("Acceso denegado: Esta acción requiere ser el organizador del evento o tener rol ADMIN en el mismo.");
        }
    }

    private boolean isOrganizer(Long userId, EventDTO eventDto) {
        return userId != null && eventDto != null && userId.equals(eventDto.getOwnerId());
    }

    private boolean hasAdminRole(Long userId, EventDTO eventDto) {
        if (userId == null || eventDto == null) {
            return false;
        }
        try {
            UserEventRoleResponseDto roleResp = enrollmentServiceClient.getUserEventRole(eventDto.getId(), userId);
            return roleResp != null && roleResp.getRoleInEvent() == EventRole.ADMIN;
        } catch (Exception e) {
            log.warn("No se pudo verificar rol ADMIN para userId {} en evento {}: {}", userId, eventDto.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Orden de las modalidades. Si la request especifica modalityOrder, las modalidades
     * se ordenan según la posición de su id en esa lista (las no listadas van al final,
     * en el orden en que llegaron). Si no, se usa el orden por defecto: división
     * (SOLO/DUET/GROUP) y luego categoría.
     */
    private void validateModalityOrder(List<Long> modalityOrder, List<ModalityResponseDto> modalities) {
        if (modalityOrder == null || modalityOrder.isEmpty()) {
            return;
        }
        Set<Long> validIds = modalities.stream().map(ModalityResponseDto::getId).collect(Collectors.toSet());
        List<Long> invalidIds = modalityOrder.stream()
                .filter(id -> !validIds.contains(id))
                .distinct()
                .toList();
        if (!invalidIds.isEmpty()) {
            throw new BadRequestException("modalityOrder contiene ids de modalidad que no pertenecen al evento: " + invalidIds);
        }
    }

    private Comparator<ModalityResponseDto> buildModalityComparator(List<Long> modalityOrder) {
        if (modalityOrder != null && !modalityOrder.isEmpty()) {
            return Comparator.comparingInt((ModalityResponseDto m) -> {
                int idx = modalityOrder.indexOf(m.getId());
                return idx == -1 ? Integer.MAX_VALUE : idx;
            });
        }
        return Comparator.comparingInt((ModalityResponseDto m) -> DIVISION_ORDER.indexOf(m.getDivision()))
                .thenComparing(ModalityResponseDto::getCategory);
    }

    /**
     * Orden de las inscripciones dentro de cada modalidad, según sortingStrategy:
     * NEWEST_FIRST (por defecto), OLDEST_FIRST o ALPHABETICAL (por nombre del participante).
     */
    private Comparator<EnrollmentDTO> buildEnrollmentComparator(String sortingStrategy) {
        String strategy = sortingStrategy != null ? sortingStrategy.trim().toUpperCase() : "NEWEST_FIRST";

        Comparator<EnrollmentDTO> byCreatedAtAsc = Comparator.comparing(
                EnrollmentDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));

        return switch (strategy) {
            case "OLDEST_FIRST" -> byCreatedAtAsc;
            case "ALPHABETICAL" -> Comparator.comparing(
                    this::participantDisplayName,
                    String.CASE_INSENSITIVE_ORDER);
            default -> byCreatedAtAsc.reversed();
        };
    }

    private String participantDisplayName(EnrollmentDTO enrollment) {
        ParticipantDTO participant = enrollment.getParticipant();
        return participant != null && participant.getName() != null
                ? participant.getName() + (participant.getLastName() != null ? " " + participant.getLastName() : "")
                : "Participante " + enrollment.getId();
    }
}