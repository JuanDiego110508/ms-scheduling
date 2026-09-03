package com.world_dance.ms_scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
    "com.world_dance.ms_scheduling",
    "com.world_dance.wd_lib_common"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {
    "com.world_dance.ms_scheduling.client"
})
@EnableJpaRepositories(basePackages = {
    "com.world_dance.wd_lib_common.repository"
})
@EntityScan(basePackages = {
    "com.world_dance.wd_lib_common.entity"
})
public class MsSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsSchedulingApplication.class, args);
    }
}