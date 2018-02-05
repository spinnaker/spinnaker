/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.jonpeterson.jackson.module.versioning.VersioningModule
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentActivityRepository
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.findAllSubtypes
import com.netflix.spinnaker.keel.ApplicationIntentGuard
import com.netflix.spinnaker.keel.KindIntentGuard
import com.netflix.spinnaker.keel.memory.MemoryIntentActivityRepository
import com.netflix.spinnaker.keel.memory.MemoryIntentRepository
import com.netflix.spinnaker.keel.memory.MemoryTraceRepository
import com.netflix.spinnaker.keel.policy.Policy
import com.netflix.spinnaker.keel.policy.PolicySpec
import com.netflix.spinnaker.keel.tracing.TraceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(
  KeelProperties::class,
  ApplicationIntentGuardProperties::class,
  KindIntentGuardProperties::class
)
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.keel.dryrun",
  "com.netflix.spinnaker.keel.filter"
])
open class KeelConfiguration {

  private val log = LoggerFactory.getLogger(javaClass)

  @Autowired lateinit var properties: KeelProperties

  // Autowired so that we use the Spring object mapper; otherwise not all instances of ObjectMapper will
  // have the subtypes registered.
  // TODO rz - Move keiko subtype configurer into kork so we can use it here instead
  @Autowired
  open fun objectMapper(objectMapper: ObjectMapper, subtypeLocators: List<KeelSubTypeLocator>) =
    objectMapper.apply {
      registerSubtypes(*findAllSubtypes(log, Intent::class.java, "com.netflix.spinnaker.keel.intent"))
      registerSubtypes(*findAllSubtypes(log, IntentSpec::class.java, "com.netflix.spinnaker.keel.intent"))
      registerSubtypes(*findAllSubtypes(log, Policy::class.java, "com.netflix.spinnaker.keel.policy"))
      registerSubtypes(*findAllSubtypes(log, PolicySpec::class.java, "com.netflix.spinnaker.keel.policy"))
      registerSubtypes(*findAllSubtypes(log, Attribute::class.java, "com.netflix.spinnaker.keel.attribute"))

      subtypeLocators.forEach { subtype ->
        subtype.packages.forEach { pkg ->
          registerSubtypes(*findAllSubtypes(log, subtype.cls, pkg))
        }
      }
    }
      .registerModule(KotlinModule())
      .registerModule(VersioningModule())
      .registerModule(JavaTimeModule())
      .disable(FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
      .apply {
        if (properties.prettyPrintJson) {
          enable(INDENT_OUTPUT)
        }
      }

  @Bean
  @ConditionalOnMissingBean(IntentRepository::class)
  open fun memoryIntentRepository(): IntentRepository = MemoryIntentRepository()

  @Bean
  @ConditionalOnMissingBean(IntentActivityRepository::class)
  open fun memoryIntentActivityRepository(): IntentActivityRepository = MemoryIntentActivityRepository(properties)

  @Bean
  @ConditionalOnMissingBean(TraceRepository::class)
  open fun memoryTraceRepository(): TraceRepository = MemoryTraceRepository()

  @Bean open fun clock(): Clock = Clock.systemDefaultZone()

  @Bean open fun intentSubTypeLocator() =
    KeelSubTypeLocator(Intent::class.java, properties.intentPackages)

  @Bean open fun intentSpecSubTypeLocator() =
    KeelSubTypeLocator(IntentSpec::class.java, properties.intentSpecPackages)

  @Bean open fun policySubTypeLocator() =
    KeelSubTypeLocator(Policy::class.java, properties.policyPackages)

  @Bean open fun attributeSubTypeLocator() =
    KeelSubTypeLocator(Attribute::class.java, properties.attributePackages)

  @Bean open fun applicationIntentGuard(properties: ApplicationIntentGuardProperties) =
    ApplicationIntentGuard(properties)

  @Bean open fun kindIntentGuard(properties: KindIntentGuardProperties) =
    KindIntentGuard(properties)
}
