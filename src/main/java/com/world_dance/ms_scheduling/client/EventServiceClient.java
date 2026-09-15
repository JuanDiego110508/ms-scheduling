package com.world_dance.ms_scheduling.client;

import com.world_dance.wd_lib_common.dto.EventResponseDto;
import com.world_dance.wd_lib_common.dto.HttpGlobalResponse;
import com.world_dance.wd_lib_common.dto.ModalityResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "ms-events", url = "${services.events.url}")
public interface EventServiceClient {

    @GetMapping("/api/v1/events/{eventId}")
    HttpGlobalResponse<EventResponseDto> getEvent(@PathVariable("eventId") Long eventId);

    @GetMapping("/api/v1/modality/getModalitiesByEventId/{eventId}")
    HttpGlobalResponse<List<ModalityResponseDto>> getModalitiesByEvent(@PathVariable("eventId") Long eventId);
}