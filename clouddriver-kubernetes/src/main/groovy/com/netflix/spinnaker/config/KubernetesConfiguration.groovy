/*
 * Copyright 2015 Google, Inc.
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
 */

package com.netflix.spinnaker.config

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties
import com.netflix.spinnaker.clouddriver.kubernetes.health.KubernetesHealthIndicator
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentialsInitializer
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('kubernetes.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.kubernetes"])
@Import([ KubernetesNamedAccountCredentialsInitializer ])
class KubernetesConfiguration {
  @Bean
  @ConfigurationProperties("kubernetes")
  KubernetesConfigurationProperties kubernetesConfigurationProperties() {
    new KubernetesConfigurationProperties()
  }

  @Bean
  KubernetesHealthIndicator kubernetesHealthIndicator() {
    new KubernetesHealthIndicator()
  }

  @Bean
  KubernetesUtil kubernetesUtil() {
    new KubernetesUtil()
  }
}
