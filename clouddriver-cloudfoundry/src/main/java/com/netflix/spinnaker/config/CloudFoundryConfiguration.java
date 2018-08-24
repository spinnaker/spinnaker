/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.cats.provider.ProviderSynchronizerTypeWrapper;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentialsInitializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("cloudfoundry.enabled")
@ComponentScan("com.netflix.spinnaker.clouddriver.cloudfoundry")
public class CloudFoundryConfiguration {

  @Bean
  CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties() {
    return new CloudFoundryConfigurationProperties();
  }

  @Bean
  CloudFoundrySynchronizerTypeWrapper cloudFoundrySynchronizerTypeWrapper() {
    return new CloudFoundrySynchronizerTypeWrapper();
  }

  @Bean
  CloudFoundryCredentialsInitializer cloudFoundryCredentialsInitializer() {
    return new CloudFoundryCredentialsInitializer();
  }

  public static class CloudFoundryProviderSynchronizer {
  }

  class CloudFoundrySynchronizerTypeWrapper implements ProviderSynchronizerTypeWrapper {
    @Override
    public Class getSynchronizerType() {
      return CloudFoundryProviderSynchronizer.class;
    }
  }
}
