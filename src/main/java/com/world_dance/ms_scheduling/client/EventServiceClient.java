package com.world_dance.ms_scheduling.client;

import com.world_dance.ms_scheduling.dto.EventDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ms-events", url = "${services.events.url}")
public interface EventServiceClient {

    @GetMapping("/api/v1/events/{eventId}")
    EventDTO getEvent(@PathVariable("eventId") Long eventId);
}