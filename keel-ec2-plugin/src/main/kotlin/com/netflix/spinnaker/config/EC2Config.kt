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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.ec2.resource.ApplicationLoadBalancerHandler
import com.netflix.spinnaker.keel.ec2.resource.ClassicLoadBalancerHandler
import com.netflix.spinnaker.keel.ec2.resource.ClusterHandler
import com.netflix.spinnaker.keel.ec2.resource.EnvironmentResolver
import com.netflix.spinnaker.keel.ec2.resource.ImageResolver
import com.netflix.spinnaker.keel.ec2.resource.SecurityGroupHandler
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@ConditionalOnProperty("keel.plugins.ec2.enabled")
class EC2Config {

  @Bean
  fun imageResolver(
    dynamicConfigService: DynamicConfigService,
    cloudDriverService: CloudDriverService,
    deliveryConfigRepository: DeliveryConfigRepository,
    artifactRepository: ArtifactRepository,
    imageService: ImageService
  ): ImageResolver =
    ImageResolver(
      dynamicConfigService,
      cloudDriverService,
      deliveryConfigRepository,
      artifactRepository,
      imageService
    )

  @Bean
  fun environmentResolver(
    deliveryConfigRepository: DeliveryConfigRepository
  ): EnvironmentResolver =
    EnvironmentResolver(
      deliveryConfigRepository
    )

  @Bean
  fun clusterHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    imageResolver: ImageResolver,
    environmentResolver: EnvironmentResolver,
    clock: Clock,
    objectMapper: ObjectMapper,
    normalizers: List<Resolver<*>>,
    publisher: ApplicationEventPublisher
  ): ClusterHandler =
    ClusterHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      environmentResolver,
      clock,
      publisher,
      objectMapper,
      normalizers
    )

  @Bean
  fun securityGroupHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    environmentResolver: EnvironmentResolver,
    objectMapper: ObjectMapper,
    normalizers: List<Resolver<*>>
  ): SecurityGroupHandler =
    SecurityGroupHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      environmentResolver,
      objectMapper,
      normalizers
    )

  @Bean
  fun classicLoadBalancerHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    environmentResolver: EnvironmentResolver,
    objectMapper: ObjectMapper,
    normalizers: List<Resolver<*>>
  ): ClassicLoadBalancerHandler =
    ClassicLoadBalancerHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      environmentResolver,
      objectMapper,
      normalizers
    )

  @Bean
  fun applicationLoadBalancerHandler(
    cloudDriverService: CloudDriverService,
    cloudDriverCache: CloudDriverCache,
    orcaService: OrcaService,
    environmentResolver: EnvironmentResolver,
    objectMapper: ObjectMapper,
    normalizers: List<Resolver<*>>
  ): ApplicationLoadBalancerHandler =
    ApplicationLoadBalancerHandler(
      cloudDriverService,
      cloudDriverCache,
      orcaService,
      environmentResolver,
      objectMapper,
      normalizers
    )
}
