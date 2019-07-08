/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spinnaker.front50.migrations.StorageServiceMigrator
import com.netflix.spinnaker.front50.model.CompositeStorageService
import com.netflix.spinnaker.front50.model.StorageService
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(StorageServiceMigratorConfigurationProperties::class)
class CompositeStorageServiceConfiguration() {
  @Bean
  @Primary
  @ConditionalOnProperty("spinnaker.migration.compositeStorageService.enabled")
  fun compositeStorageService(
    dynamicConfigService: DynamicConfigService,
    registry: Registry,
    properties: StorageServiceMigratorConfigurationProperties,
    storageServices: List<StorageService>
  ) =
    CompositeStorageService(
      dynamicConfigService,
      registry,
      storageServices.first { it.javaClass.canonicalName.equals(properties.primaryClass) },
      storageServices.first { it.javaClass.canonicalName.equals(properties.previousClass) }
    )

  @Bean
  @ConditionalOnProperty("spinnaker.migration.compositeStorageService.enabled")
  fun storageServiceMigrator(
    dynamicConfigService: DynamicConfigService,
    registry: Registry,
    properties: StorageServiceMigratorConfigurationProperties,
    storageServices: List<StorageService>,
    entityTagsDAO: EntityTagsDAO
  ) =
    StorageServiceMigrator(
      dynamicConfigService,
      registry,
      storageServices.first { it.javaClass.canonicalName.equals(properties.primaryClass) },
      storageServices.first { it.javaClass.canonicalName.equals(properties.previousClass) },
      entityTagsDAO
    )
}

@ConfigurationProperties("spinnaker.migration")
data class StorageServiceMigratorConfigurationProperties(
  var primaryClass: String? = null,
  var previousClass: String? = null,
  var writeOnly: Boolean = false
)
