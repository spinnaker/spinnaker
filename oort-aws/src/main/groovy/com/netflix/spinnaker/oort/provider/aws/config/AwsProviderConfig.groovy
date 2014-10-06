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

package com.netflix.spinnaker.oort.provider.aws.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.oort.config.AwsConfigurationProperties
import com.netflix.spinnaker.oort.config.CredentialsInitializer
import com.netflix.spinnaker.oort.provider.aws.AwsProvider
import com.netflix.spinnaker.oort.provider.aws.agent.ClusterCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.ImageCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.InstanceCachingAgent
import com.netflix.spinnaker.oort.provider.aws.agent.LaunchConfigCachingAgent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AwsProviderConfig {

  // This is just so Spring gets the dependency graph right
  @Autowired
  CredentialsInitializer credentialsInitializer

  @Bean
  AwsProvider awsProvider(AmazonClientProvider amazonClientProvider, AwsConfigurationProperties awsConfigurationProperties, ObjectMapper objectMapper) {
    List<CachingAgent> agents = []
    for (NetflixAmazonCredentials credentials : awsConfigurationProperties.accounts) {
      for (AmazonCredentials.AWSRegion region : credentials.regions) {
        agents << new ClusterCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new LaunchConfigCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new ImageCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
        agents << new InstanceCachingAgent(amazonClientProvider, credentials, region.name, objectMapper)
      }
    }
    new AwsProvider(agents)
  }

}
