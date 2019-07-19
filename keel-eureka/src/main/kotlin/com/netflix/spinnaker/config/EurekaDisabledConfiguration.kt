package com.netflix.spinnaker.config

import com.netflix.spinnaker.keel.activation.ApplicationUp
import com.netflix.spinnaker.keel.eureka.LocalInstanceIdSupplier
import com.netflix.spinnaker.keel.info.InstanceIdSupplier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("eureka.enabled", havingValue = "false", matchIfMissing = true)
class EurekaDisabledConfiguration {
  @Bean
  fun instanceIdSupplier(): InstanceIdSupplier = LocalInstanceIdSupplier

  @Bean
  fun simpleActivator(
    publisher: ApplicationEventPublisher,
    @Value("\${eureka.default-to-up:true}") defaultToUp: Boolean
  ) =
    ApplicationListener<ApplicationReadyEvent> {
      if (defaultToUp) {
        log.warn("Eureka disabled, sending application up event")
        publisher.publishEvent(ApplicationUp)
      } else {
        log.warn("Eureka disabled, NOT sending application up event")
      }
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
