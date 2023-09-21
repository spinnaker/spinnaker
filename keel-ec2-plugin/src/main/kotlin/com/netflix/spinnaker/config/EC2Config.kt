/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.resolvers.InstanceMetadataServiceResolver
import com.netflix.spinnaker.keel.ec2.resource.ApplicationLoadBalancerHandler
import com.netflix.spinnaker.keel.ec2.resource.BlockDeviceConfig
import com.netflix.spinnaker.keel.ec2.resource.ClassicLoadBalancerHandler
import com.netflix.spinnaker.keel.ec2.resource.ClusterHandler
import com.netflix.spinnaker.keel.ec2.resource.SecurityGroupHandler
import com.netflix.spinnaker.keel.environments.DependentEnvironmentFinder
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.orca.ClusterExportHelper
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.FeatureRolloutRepository
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@ConditionalOnProperty("keel.plugins.ec2.enabled")
class EC2Config {
  @Bean
  fun clusterHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    taskLauncher: TaskLauncher,
    clock: Clock,
    normalizers: List<Resolver<*>>,
    eventPublisher: EventPublisher,
    clusterExportHelper: ClusterExportHelper,
    blockDeviceConfig: BlockDeviceConfig,
    artifactService: ArtifactService
  ): ClusterHandler =
    ClusterHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      taskLauncher,
      clock,
      eventPublisher,
      normalizers,
      clusterExportHelper,
      blockDeviceConfig,
      artifactService
    )

  @Bean
  @ConditionalOnMissingBean
  fun securityGroupHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    taskLauncher: TaskLauncher,
    normalizers: List<Resolver<*>>
  ): SecurityGroupHandler =
    SecurityGroupHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      taskLauncher,
      normalizers
    )

  @Bean
  fun classicLoadBalancerHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    taskLauncher: TaskLauncher,
    normalizers: List<Resolver<*>>
  ): ClassicLoadBalancerHandler =
    ClassicLoadBalancerHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      taskLauncher,
      normalizers
    )

  @Bean
  fun applicationLoadBalancerHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    taskLauncher: TaskLauncher,
    normalizers: List<Resolver<*>>
  ): ApplicationLoadBalancerHandler =
    ApplicationLoadBalancerHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      taskLauncher,
      normalizers
    )

  @Bean
  fun ec2InstanceMetadataServiceResolver(
    dependentEnvironmentFinder: DependentEnvironmentFinder,
    applicationContext: ApplicationContext,
    featureRolloutRepository: FeatureRolloutRepository,
    eventPublisher: EventPublisher
  ): InstanceMetadataServiceResolver {
    // This is necessary to avoid a circular bean dependency as Resolver instances (like we're creating here)
    // get wired into ResourceHandlers, but here the Resolver needs a capability provided by the ResourceHandler.
    val clusterHandler by lazy { applicationContext.getBean<ClusterHandler>() }

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
