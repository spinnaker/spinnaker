/*
 * Copyright 2019 Pivotal, Inc.
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
 */

package com.netflix.spinnaker.kork.configserver.autoconfig;

import static org.springframework.cloud.aws.context.config.support.ContextConfigurationUtils.REGION_PROVIDER_BEAN_NAME;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.aws.autoconfigure.context.ContextRegionProviderAutoConfiguration;
import org.springframework.cloud.aws.autoconfigure.context.ContextStackAutoConfiguration;
import org.springframework.cloud.aws.core.env.stack.StackResourceRegistry;
import org.springframework.cloud.aws.core.env.stack.config.StackResourceRegistryFactoryBean;
import org.springframework.cloud.aws.core.region.RegionProvider;
import org.springframework.cloud.aws.core.region.StaticRegionProvider;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

@Configuration
@AutoConfigureBefore({
  ContextRegionProviderAutoConfiguration.class,
  ContextStackAutoConfiguration.class
})
class SpringCloudAwsConfiguration {
  @Bean(REGION_PROVIDER_BEAN_NAME)
  @ConditionalOnMissingBean({RegionProvider.class, AwsS3EnvironmentProperties.class})
  public StaticRegionProvider defaultAwsS3RegionProvider() {
    // provide a default to prevent Spring Cloud AWS auto-configuration failures
    return new StaticRegionProvider("us-east-1");
  }

  @Bean(REGION_PROVIDER_BEAN_NAME)
  @ConditionalOnMissingBean(RegionProvider.class)
  @ConditionalOnBean(AwsS3EnvironmentProperties.class)
  public StaticRegionProvider environmentPropertiesAwsS3RegionProvider(
      AwsS3EnvironmentProperties s3EnvironmentProperties) {
    // use the same region as configured for Spring Cloud Config
    return new StaticRegionProvider(s3EnvironmentProperties.getRegion());
  }

  /**
   * Spring Cloud AWS auto-configuration defaults to auto-detecting a stack, which only works when
   * running on EC2. Disable stack auto-detection by default.
   *
   * @param environment the Spring environment
   * @return {@code null} to allow auto-configuration to do further configuration
   */
  @Bean
  @ConditionalOnMissingBean(StackResourceRegistry.class)
  public StackResourceRegistryFactoryBean stackResourceRegistryFactoryBean(
      ConfigurableEnvironment environment) {
    Map<String, Object> properties = new HashMap<>();
    properties.put("cloud.aws.stack.auto", "false");
    PropertySource<?> propertySource =
        new MapPropertySource("springCloudAwsConfiguration", properties);
    environment.getPropertySources().addLast(propertySource);
    return null;
  }
}
