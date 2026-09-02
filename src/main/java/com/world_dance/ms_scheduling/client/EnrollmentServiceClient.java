package com.world_dance.ms_scheduling.client;

import com.world_dance.ms_scheduling.dto.EnrollmentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@FeignClient(name = "ms-enrollment", url = "${services.enrollment.url}")
public interface EnrollmentServiceClient {

    @GetMapping("/api/v1/enrollments/event/{eventId}")
    List<EnrollmentDTO> getEnrollmentsByEvent(@PathVariable("eventId") Long eventId);
};