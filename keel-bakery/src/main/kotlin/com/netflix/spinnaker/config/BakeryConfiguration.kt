package com.netflix.spinnaker.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("bakery.enabled")
class BakeryConfiguration
