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
import com.netflix.spinnaker.kork.web.context.RequestContextProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
@EnableConfigurationProperties(StorageServiceMigratorConfigurationProperties::class)
class CompositeStorageServiceConfiguration(
  private val storageServices: List<StorageService>,
  private val applicationContext: ApplicationContext,
  private val properties: StorageServiceMigratorConfigurationProperties,
  private val dynamicConfigService: DynamicConfigService,
  private val registry: Registry
) {
  @Bean
  @Primary
  @ConditionalOnProperty("spinnaker.migration.compositeStorageService.enabled")
  fun compositeStorageService() =
    CompositeStorageService(
      dynamicConfigService,
      registry,
      findStorageService(properties.primaryClass, properties.primaryName),
      findStorageService(properties.previousClass, properties.previousName)
    )

  @Bean
  @ConditionalOnProperty("spinnaker.migration.compositeStorageService.enabled")
  fun storageServiceMigrator(
    entityTagsDAO: EntityTagsDAO,
    contextProvider: RequestContextProvider
  ) =
    StorageServiceMigrator(
      dynamicConfigService,
      registry,
      findStorageService(properties.primaryClass, properties.primaryName),
      findStorageService(properties.previousClass, properties.previousName),
      entityTagsDAO,
      contextProvider
    )

  private fun findStorageService(
    className: String?,
    beanName: String?
  ): StorageService {
    return if (className != null && className.isNotBlank()) {
      storageServices.first { it.javaClass.canonicalName == className }
    } else {
      applicationContext.getBean(beanName) as StorageService
    }
  }
}

@ConfigurationProperties("spinnaker.migration")
data class StorageServiceMigratorConfigurationProperties(
  var primaryClass: String? = null,
  var previousClass: String? = null,
  var primaryName: String? = null,
  var previousName: String? = null,
  var writeOnly: Boolean = false
)
