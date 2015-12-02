/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.kato.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import groovy.util.logging.Slf4j

@Slf4j
@ConditionalOnProperty('azure.enabled')
@Configuration
@ComponentScan('com.netflix.spinnaker.kato.azure')
class AzureConfig {

  public AzureConfig() {
  }

  @Bean
  @ConfigurationProperties('azure.defaults')
  DeployDefaults azureDeployDefaults() {
    new DeployDefaults()
  }

  static class DeployDefaults {
  }
}
