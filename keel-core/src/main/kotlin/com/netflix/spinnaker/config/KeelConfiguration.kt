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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.*
import com.netflix.spinnaker.keel.attribute.Attribute
import com.netflix.spinnaker.keel.memory.MemoryIntentActivityRepository
import com.netflix.spinnaker.keel.memory.MemoryIntentRepository
import com.netflix.spinnaker.keel.memory.MemoryTraceRepository
import com.netflix.spinnaker.keel.policy.Policy
import com.netflix.spinnaker.keel.tracing.TraceRepository
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
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

  @Autowired lateinit var properties: KeelProperties

  @Bean open fun intentSubTypeLocator() =
    ClassSubtypeLocator(Intent::class.java, properties.intentPackages)

  @Bean open fun intentSpecSubTypeLocator() =
    ClassSubtypeLocator(IntentSpec::class.java, properties.intentSpecPackages)

  @Bean open fun policySubTypeLocator() =
    ClassSubtypeLocator(Policy::class.java, properties.policyPackages)

  @Bean open fun attributeSubTypeLocator() =
    ClassSubtypeLocator(Attribute::class.java, properties.attributePackages)

  @Bean
  @ConditionalOnMissingBean(IntentRepository::class)
  open fun memoryIntentRepository(applicationEventPublisher: ApplicationEventPublisher): IntentRepository =
    MemoryIntentRepository(applicationEventPublisher)

  @Bean
  @ConditionalOnMissingBean(IntentActivityRepository::class)
  open fun memoryIntentActivityRepository(): IntentActivityRepository = MemoryIntentActivityRepository(properties)

  @Bean
  @ConditionalOnMissingBean(TraceRepository::class)
  open fun memoryTraceRepository(): TraceRepository = MemoryTraceRepository()

  @Bean open fun clock(): Clock = Clock.systemDefaultZone()

  @Bean open fun applicationIntentGuard(registry: Registry, properties: ApplicationIntentGuardProperties) =
    ApplicationIntentGuard(registry, properties)

  @Bean open fun kindIntentGuard(registry: Registry, properties: KindIntentGuardProperties) =
    KindIntentGuard(registry, properties)
}
