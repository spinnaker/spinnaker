package com.netflix.spinnaker.kato.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty('google.enabled')
@Configuration
@ComponentScan('com.netflix.spinnaker.kato.gce')
class GceConfig {
}
