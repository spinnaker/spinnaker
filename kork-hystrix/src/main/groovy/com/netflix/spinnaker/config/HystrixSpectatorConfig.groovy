/*
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.hystrix.strategy.HystrixPlugins
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.hystrix.spectator.HystrixSpectatorPublisher
import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Slf4j
@Configuration
class HystrixSpectatorConfig {
  @Bean
  HystrixSpectatorPublisher hystrixSpectatorPublisher(Registry registry) {
    def publisher = new HystrixSpectatorPublisher(registry)
    log.info("Enabling HystrixSpectatorPublisher")
    HystrixPlugins.getInstance().registerMetricsPublisher(publisher)
    return publisher;
  }
}
