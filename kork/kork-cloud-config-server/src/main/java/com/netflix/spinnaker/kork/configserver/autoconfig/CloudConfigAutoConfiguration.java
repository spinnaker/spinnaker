/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.kork.configserver.autoconfig;

import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.configserver.ConfigFileService;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.config.server.bootstrap.ConfigServerBootstrapConfiguration;
import org.springframework.cloud.config.server.config.CompositeConfiguration;
import org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration;
import org.springframework.cloud.config.server.config.ConfigServerEncryptionConfiguration;
import org.springframework.cloud.config.server.config.ResourceRepositoryConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@AutoConfigureAfter({ConfigServerAutoConfiguration.class, ConfigServerBootstrapConfiguration.class})
public class CloudConfigAutoConfiguration {
  @Bean
  ConfigFileService configFileService(CloudConfigResourceService cloudConfigResourceService) {
    return new ConfigFileService(cloudConfigResourceService);
  }

  @Configuration
  @Conditional(RemoteConfigSourceConfigured.class)
  @Import({
    CompositeConfiguration.class,
    ResourceRepositoryConfiguration.class,
    ConfigServerEncryptionConfiguration.class
  })
  static class RemoteConfigSourceConfiguration {
    @Bean
    CloudConfigResourceService cloudConfigResourceService(
        ResourceRepository resourceRepository, EnvironmentRepository environmentRepository) {
      return new CloudConfigResourceService(resourceRepository, environmentRepository);
    }
  }

  @Configuration
  static class DefaultConfiguration {
    @Bean
    @ConditionalOnMissingBean(CloudConfigResourceService.class)
    CloudConfigResourceService cloudConfigResourceService() {
      return new CloudConfigResourceService();
    }
  }
}
