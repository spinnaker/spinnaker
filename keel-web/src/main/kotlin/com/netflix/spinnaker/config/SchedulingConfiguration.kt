package com.netflix.spinnaker.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@ConditionalOnProperty("scheduling.enabled", matchIfMissing = true)
@EnableScheduling
class SchedulingConfiguration
