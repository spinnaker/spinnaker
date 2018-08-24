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

import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.health.GoogleHealthIndicator
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceTypeDisk
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentialsInitializer
import groovy.transform.ToString
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('google.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.google"])
@Import([ GoogleCredentialsInitializer ])
class GoogleConfiguration {

  private static final String DEFAULT_KEY = "default"

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("google")
  GoogleConfigurationProperties googleConfigurationProperties() {
    new GoogleConfigurationProperties()
  }

  @Bean
  GoogleHealthIndicator googleHealthIndicator() {
    new GoogleHealthIndicator()
  }

  @Bean
  GoogleOperationPoller googleOperationPoller() {
    new GoogleOperationPoller()
  }

  @Bean
  @ConfigurationProperties('google.defaults')
  DeployDefaults googleDeployDefaults() {
    new DeployDefaults()
  }

  @ToString(includeNames = true)
  static class DeployDefaults {
    List<GoogleDisk> fallbackInstanceTypeDisks = []
    List<GoogleInstanceTypeDisk> instanceTypeDisks = []

    GoogleInstanceTypeDisk determineInstanceTypeDisk(String instanceType) {
      GoogleInstanceTypeDisk instanceTypeDisk = instanceTypeDisks.find {
        it.instanceType == instanceType
      }

      if (!instanceTypeDisk) {
        instanceTypeDisk = instanceTypeDisks.find {
          it.instanceType == DEFAULT_KEY
        }
      }

      if (!instanceTypeDisk) {
        instanceTypeDisk = new GoogleInstanceTypeDisk(instanceType: DEFAULT_KEY,
                                                      disks: fallbackInstanceTypeDisks)
      }

      return instanceTypeDisk
    }
  }
}

