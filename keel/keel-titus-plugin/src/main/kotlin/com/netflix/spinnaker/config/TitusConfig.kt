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

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.environments.DependentEnvironmentFinder
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import com.netflix.spinnaker.keel.titus.InstanceMetadataServiceResolver
import com.netflix.spinnaker.keel.titus.TitusClusterHandler
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@ConditionalOnProperty("keel.plugins.titus.enabled")
class TitusConfig {
  @Bean
  fun titusClusterHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    clock: Clock,
    taskLauncher: TaskLauncher,
    eventPublisher: EventPublisher,
    resolvers: List<Resolver<*>>,
    clusterExportHelper: ClusterExportHelper
  ): TitusClusterHandler = TitusClusterHandler(
    cloudDriverService,
    cloudDriverCache,
    orcaService,
    clock,
    taskLauncher,
    eventPublisher,
    resolvers,
    clusterExportHelper
  )

  @Bean
  fun titusInstanceMetadataServiceResolver(
    dependentEnvironmentFinder: DependentEnvironmentFinder,
    applicationContext: ApplicationContext,
    featureRolloutRepository: FeatureRolloutRepository,
    eventPublisher: EventPublisher
  ): InstanceMetadataServiceResolver {
    // This is necessary to avoid a circular bean dependency as Resolver instances (like we're creating here)
    // get wired into ResourceHandlers, but here the Resolver needs a capability provided by the ResourceHandler.
    val clusterHandler by lazy { applicationContext.getBean<TitusClusterHandler>() }

    return InstanceMetadataServiceResolver(
      dependentEnvironmentFinder,
      // although it looks like this could be optimized to clusterHandler::current that will cause the bean to get
      // created right away, which will blow up with a circular dependency
      { clusterHandler.current(it) },
      featureRolloutRepository,
      eventPublisher
    )
  }
}
