package com.netflix.spinnaker.config

import com.netflix.spinnaker.keel.annealing.ResourceActuator
import com.netflix.spinnaker.keel.annealing.ResourceCheckQueue
import com.netflix.spinnaker.keel.annealing.spring.ApplicationEventResourceCheckListener
import com.netflix.spinnaker.keel.annealing.spring.ApplicationEventResourceCheckQueue
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AnnealingConfiguration {

  @Bean
  @ConditionalOnMissingBean(ResourceCheckQueue::class)
  fun commandQueue(publisher: ApplicationEventPublisher) =
    ApplicationEventResourceCheckQueue(publisher)

  @Bean
  @ConditionalOnBean(ApplicationEventResourceCheckQueue::class)
  fun commandListener(resourceActuator: ResourceActuator) =
    ApplicationEventResourceCheckListener(resourceActuator)
}
