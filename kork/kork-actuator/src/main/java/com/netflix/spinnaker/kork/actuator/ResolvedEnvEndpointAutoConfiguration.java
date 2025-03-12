/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.actuator;

import com.netflix.spinnaker.kork.actuator.endpoint.ResolvedEnvironmentConfigurationProperties;
import com.netflix.spinnaker.kork.actuator.endpoint.ResolvedEnvironmentEndpoint;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the {@link ResolvedEnvironmentEndpoint}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ResolvedEnvironmentConfigurationProperties.class})
@ConditionalOnAvailableEndpoint(endpoint = ResolvedEnvironmentEndpoint.class)
public class ResolvedEnvEndpointAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ResolvedEnvironmentEndpoint resolvedEnvironmentEndpoint(
      Environment environment,
      ResolvedEnvironmentConfigurationProperties resolvedEnvironmentConfigurationProperties) {
    return new ResolvedEnvironmentEndpoint(environment, resolvedEnvironmentConfigurationProperties);
  }
}
