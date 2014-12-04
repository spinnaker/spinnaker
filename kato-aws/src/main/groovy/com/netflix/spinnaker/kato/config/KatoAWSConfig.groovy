/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.deploy.userdata.LocalFileUserDataProvider
import com.netflix.spinnaker.kato.aws.deploy.userdata.UserDataProvider
import com.netflix.spinnaker.kato.aws.model.AmazonInstanceClassBlockDevice
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty('aws.enabled')
@ComponentScan('com.netflix.spinnaker.kato.aws')
@EnableConfigurationProperties
@Configuration
class KatoAWSConfig {

  @Bean
  @ConditionalOnMissingBean(UserDataProvider)
  UserDataProvider userDataProvider() {
    new LocalFileUserDataProvider()
  }

  @Bean
  AmazonClientProvider amazonClientProvider() {
    new AmazonClientProvider()
  }

  @Bean
  @ConfigurationProperties('aws.defaults')
  DeployDefaults deployDefaults() {
    new DeployDefaults()
  }

  static class DeployDefaults {
    String iamRole
    List<AmazonInstanceClassBlockDevice> instanceClassBlockDevices = []
  }
}
