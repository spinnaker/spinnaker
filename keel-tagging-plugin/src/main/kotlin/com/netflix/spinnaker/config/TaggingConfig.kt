/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */
package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.tagging.KeelTagHandler
import com.netflix.spinnaker.keel.tagging.ResourceTagger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

@Configuration
@ComponentScan("com.netflix.spinnaker.keel.tagging")
@EnableScheduling
@ConditionalOnProperty("clouddriver.enabled", "orca.enabled")
class TaggingConfig {

  @Bean
  fun resourceTagger(
    resourceRepository: ResourceRepository,
    resourcePersister: ResourcePersister,
    cloudDriverService: CloudDriverService,
    @Value("\${keel.resource-tagger.removed-tag-retention-hours:24}") removedTagRetentionHours: Long,
    clock: Clock
  ) = ResourceTagger(
    resourceRepository,
    resourcePersister,
    cloudDriverService,
    removedTagRetentionHours,
    clock
  )

  @Bean
  fun keelTagHandler(
    cloudDriverService: CloudDriverService,
    orcaService: OrcaService,
    objectMapper: ObjectMapper,
    normalizers: List<Resolver<*>>
  ) = KeelTagHandler(
    cloudDriverService,
    orcaService,
    objectMapper,
    normalizers
  )
}
