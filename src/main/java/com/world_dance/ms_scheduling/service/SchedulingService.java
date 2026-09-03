package com.world_dance.ms_scheduling.service;

import com.world_dance.ms_scheduling.client.EnrollmentServiceClient;
import com.world_dance.ms_scheduling.client.EventServiceClient;
import com.world_dance.ms_scheduling.dto.EnrollmentDTO;
import com.world_dance.ms_scheduling.dto.EventDTO;
import com.world_dance.ms_scheduling.dto.ParticipantDTO;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationRequestDto;
import com.world_dance.wd_lib_common.dto.ScheduleGenerationResponseDto;
import com.world_dance.wd_lib_common.dto.ScheduleSlotDto;
import com.world_dance.wd_lib_common.entity.Modality;
import com.world_dance.wd_lib_common.entity.Schedule;
import com.world_dance.wd_lib_common.entity.PresentationSlot;
import com.world_dance.wd_lib_common.enums.Division;
import com.world_dance.wd_lib_common.enums.ScheduleStatus;
import com.world_dance.wd_lib_common.enums.SlotStatus;
import com.world_dance.wd_lib_common.exception.BadRequestException;
import com.world_dance.wd_lib_common.repository.ModalityRepository;
import com.world_dance.wd_lib_common.repository.ScheduleRepository;
import com.world_dance.wd_lib_common.repository.PresentationSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingService {

    private final EventServiceClient eventServiceClient;
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
    public ScheduleGenerationResponseDto generateSchedule(ScheduleGenerationRequestDto request) {
        Long eventId = request.getEventId();
        log.info("Generando cronograma para evento: {}", eventId);

        EventDTO eventDto = eventServiceClient.getEvent(eventId);
        if (eventDto == null) {
            throw new BadRequestException("Evento no encontrado con ID: " + eventId);
        }

        List<Modality> modalities = modalityRepository.findByEventId(eventId);
        if (modalities.isEmpty()) {
            throw new BadRequestException("El evento no tiene modalidades configuradas");
        }

        List<EnrollmentDTO> enrollments = enrollmentServiceClient.getEnrollmentsByEvent(eventId);
        if (enrollments == null || enrollments.isEmpty()) {
            throw new BadRequestException("El evento no tiene inscripciones para generar cronograma");
        }

        int durationMinutes = request.getDefaultDurationMinutes() != null
                ? request.getDefaultDurationMinutes() : 5;

        int transitionMinutes = request.getTransitionMinutes() != null
                ? request.getTransitionMinutes() : 2;

        List<String> stageNames = request.getStageNames() != null && !request.getStageNames().isEmpty()
                ? request.getStageNames()
                : List.of("Stage 1");

        List<Modality> sortedModalities = modalities.stream()
                .sorted(Comparator.comparingInt((Modality m) -> DIVISION_ORDER.indexOf(m.getDivision()))
                        .thenComparing(Modality::getCategory))
                .collect(Collectors.toList());

        Map<Long, List<EnrollmentDTO>> enrollmentsByModality = enrollments.stream()
                .filter(e -> e.getModalityId() != null)
                .collect(Collectors.groupingBy(EnrollmentDTO::getModalityId));

        List<ScheduleSlotDto> slots = new ArrayList<>();
        LocalDateTime currentTime = eventDto.getStartDate();
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
                String participantName = participant != null ? participant.getName() : "Usuario " + enrollment.getId();

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

                if (currentTime.isAfter(eventDto.getEndDate())) {
                    throw new BadRequestException("No hay suficiente tiempo en el evento para todas las presentaciones");
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

    public ScheduleGenerationResponseDto getScheduleByEvent(Long eventId) {
        log.info("Obteniendo cronograma del evento: {}", eventId);
        return null;
    }

    @Transactional
    public void deleteSchedule(Long eventId) {
        log.info("Eliminando cronograma del evento: {}", eventId);
        scheduleRepository.deleteByEventId(eventId);
    }
}