package com.evse.simulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration pour le module Scheduler.
 * Active la planification via @Scheduled.
 *
 * Note: RestTemplate est déjà défini dans TTEConfig.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
