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
import com.netflix.spinnaker.keel.memory.MemoryAssetActivityRepository
import com.netflix.spinnaker.keel.memory.MemoryAssetRepository
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
  ApplicationAssetGuardProperties::class,
  KindAssetGuardProperties::class
)
@ComponentScan(basePackages = [
  "com.netflix.spinnaker.keel.dryrun",
  "com.netflix.spinnaker.keel.filter"
])
open class KeelConfiguration {

  @Autowired lateinit var properties: KeelProperties

  @Bean open fun assetSubTypeLocator() =
    ClassSubtypeLocator(Asset::class.java, properties.assetPackages)

  @Bean open fun assetSpecSubTypeLocator() =
    ClassSubtypeLocator(AssetSpec::class.java, properties.assetSpecPackages)

  @Bean open fun attributeSubTypeLocator() =
    ClassSubtypeLocator(Attribute::class.java, properties.attributePackages)

  @Bean
  @ConditionalOnMissingBean(AssetRepository::class)
  open fun memoryAssetRepository(applicationEventPublisher: ApplicationEventPublisher): AssetRepository =
    MemoryAssetRepository(applicationEventPublisher)

  @Bean
  @ConditionalOnMissingBean(AssetActivityRepository::class)
  open fun memoryAssetActivityRepository(): AssetActivityRepository = MemoryAssetActivityRepository()

  @Bean open fun clock(): Clock = Clock.systemDefaultZone()

  @Bean open fun applicationAssetGuard(registry: Registry, properties: ApplicationAssetGuardProperties) =
    ApplicationAssetGuard(registry, properties)

  @Bean open fun kindAssetGuard(registry: Registry, properties: KindAssetGuardProperties) =
    KindAssetGuard(registry, properties)
}
