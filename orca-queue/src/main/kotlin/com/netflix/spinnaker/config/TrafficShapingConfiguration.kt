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
import com.netflix.spinnaker.orca.q.NoopTrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.TrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.interceptor.ApplicationRateLimitQueueInterceptor
import com.netflix.spinnaker.orca.q.interceptor.GlobalRateLimitQueueInterceptor
import com.netflix.spinnaker.orca.q.ratelimit.NoopRateLimitBackend
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitBackend
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("queue.trafficShaping.enabled")
@EnableConfigurationProperties(
  TrafficShapingProperties::class,
  TrafficShapingProperties.GlobalRateLimitingProperties::class,
  TrafficShapingProperties.ApplicationRateLimitingProperties::class
)
open class TrafficShapingConfiguration {

  @Bean
  @ConditionalOnMissingBean(RateLimitBackend::class)
  @ConditionalOnProperty("queue.trafficShaping.applicationRateLimiting.enabled")
  open fun noopRateLimitBackend(): RateLimitBackend = NoopRateLimitBackend()

  @Bean @ConditionalOnProperty("queue.trafficShaping.globalRateLimiting.enabled")
  open fun globalRateLimitQueueInterceptor(rateLimitBackend: RateLimitBackend,
                                           registry: Registry,
                                           globalRateLimitingProperties: TrafficShapingProperties.GlobalRateLimitingProperties
  ): TrafficShapingInterceptor
    = GlobalRateLimitQueueInterceptor(rateLimitBackend, registry, globalRateLimitingProperties)

  @Bean @ConditionalOnProperty("queue.trafficShaping.applicationRateLimiting.enabled")
  open fun applicationRateLimitQueueInterceptor(rateLimitBackend: RateLimitBackend,
                                                registry: Registry,
                                                applicationRateLimitingProperties: TrafficShapingProperties.ApplicationRateLimitingProperties
  ): TrafficShapingInterceptor
    = ApplicationRateLimitQueueInterceptor(rateLimitBackend, registry, applicationRateLimitingProperties)

  @Bean @ConditionalOnMissingBean(TrafficShapingInterceptor::class)
  open fun noopTrafficShapingInterceptor() = NoopTrafficShapingInterceptor()
}
