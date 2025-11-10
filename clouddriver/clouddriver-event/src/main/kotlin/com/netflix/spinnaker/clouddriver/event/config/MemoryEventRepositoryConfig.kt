/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.event.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.event.persistence.EventRepository
import com.netflix.spinnaker.clouddriver.event.persistence.InMemoryEventRepository
import java.time.Duration
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.constraints.Min
import kotlin.reflect.KClass
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@Configuration
@EnableConfigurationProperties(MemoryEventRepositoryConfigProperties::class)
open class MemoryEventRepositoryConfig {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  init {
    log.info("Configuring EventRepository: InMemoryEventRepository")
  }

  @Bean
  @ConditionalOnMissingBean(EventRepository::class)
  open fun eventRepository(
    properties: MemoryEventRepositoryConfigProperties,
    applicationEventPublisher: ApplicationEventPublisher,
    registry: Registry
  ): EventRepository =
    InMemoryEventRepository(properties, applicationEventPublisher, registry)
}

@MemoryEventRepositoryConfigProperties.SpinValidated
@ConfigurationProperties("spinnaker.clouddriver.eventing.memory-repository")
open class MemoryEventRepositoryConfigProperties {
  /**
   * The max age of an [Aggregate]. One of this and [maxAggregatesCount] must be set.
   */
  @Min(
    message = "Event repository aggregate age cannot be less than 24 hours.",
    value = 60 * 60 * 24 * 1000
  )
  var maxAggregateAgeMs: Long? = Duration.ofHours(24).toMillis()

  /**
   * The max number of [Aggregate] objects. One of this and [maxAggregateAgeMs] must be set.
   */
  var maxAggregatesCount: Int? = null

  @Validated
  @Constraint(validatedBy = [Validator::class])
  @Target(AnnotationTarget.CLASS)
  annotation class SpinValidated(
    val message: String = "Invalid event repository configuration",
    val groups: Array<KClass<out Any>> = [],
    val payload: Array<KClass<out Any>> = []
  )

  class Validator : ConstraintValidator<SpinValidated, MemoryEventRepositoryConfigProperties> {
    override fun isValid(
      value: MemoryEventRepositoryConfigProperties,
      context: ConstraintValidatorContext
    ): Boolean {
      if (value.maxAggregateAgeMs != null && value.maxAggregatesCount != null) {
        context.buildConstraintViolationWithTemplate("Only one of 'maxAggregateAgeMs' and 'maxAggregatesCount' can be defined")
          .addConstraintViolation()
        return false
      }
      if (value.maxAggregateAgeMs == null && value.maxAggregatesCount == null) {
        context.buildConstraintViolationWithTemplate("One of 'maxAggregateAgeMs' and 'maxAggregatesCount' must be set")
          .addConstraintViolation()
        return false
      }
      return true
    }
  }
}
