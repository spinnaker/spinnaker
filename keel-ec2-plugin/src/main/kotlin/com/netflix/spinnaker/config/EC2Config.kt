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

import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.constraints.CanaryConstraintConfigurationProperties
import com.netflix.spinnaker.keel.constraints.CanaryConstraintDeployHandler
import com.netflix.spinnaker.keel.ec2.constraints.Ec2CanaryConstraintDeployHandler
import com.netflix.spinnaker.keel.ec2.resolvers.ImageResolver
import com.netflix.spinnaker.keel.ec2.resource.ApplicationLoadBalancerHandler
import com.netflix.spinnaker.keel.ec2.resource.ClassicLoadBalancerHandler
import com.netflix.spinnaker.keel.ec2.resource.ClusterHandler
import com.netflix.spinnaker.keel.ec2.resource.SecurityGroupHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import java.time.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CanaryConstraintConfigurationProperties::class)
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
    publisher: ApplicationEventPublisher
  ): ClusterHandler =
    ClusterHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      taskLauncher,
      clock,
      publisher,
      normalizers
    )

  @Bean
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
  fun ec2CanaryDeployHandler(
    defaults: CanaryConstraintConfigurationProperties,
    taskLauncher: TaskLauncher,
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    imageService: ImageService,
    imageResolver: ImageResolver
  ): CanaryConstraintDeployHandler =
    Ec2CanaryConstraintDeployHandler(
      defaults,
      taskLauncher,
      cloudDriverService,
      cloudDriverCache,
      imageService,
      imageResolver
    )
}
