/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider;
import com.netflix.spinnaker.clouddriver.refresh.CloudConfigRefreshScheduler;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Create a {@link CloudConfigRefreshScheduler} to refresh the Spring Cloud Config Server from an
 * environment repository backend on a schedule that matches the cache refresh schedule.
 */
@Configuration
@AutoConfigureAfter({RedisCacheConfig.class, DynomiteCacheConfig.class})
@Conditional(RemoteConfigSourceConfigured.class)
public class CloudConfigRefreshConfig {

  @Bean
  @ConditionalOnBean(DefaultAgentIntervalProvider.class)
  public CloudConfigRefreshScheduler intervalProviderConfigRefreshScheduler(
      ContextRefresher contextRefresher, DefaultAgentIntervalProvider agentIntervalProvider) {
    return new CloudConfigRefreshScheduler(contextRefresher, agentIntervalProvider.getInterval());
  }

  @Bean
  @ConditionalOnMissingBean(DefaultAgentIntervalProvider.class)
  public CloudConfigRefreshScheduler defaultIntervalConfigRefreshScheduler(
      ContextRefresher contextRefresher) {
    return new CloudConfigRefreshScheduler(contextRefresher, 60);
  }
}

class RemoteConfigSourceConfigured implements Condition {
  @Override
  public boolean matches(ConditionContext context, @NotNull AnnotatedTypeMetadata metadata) {
    ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
    if (beanFactory != null) {
      return
      // beans added via Spring Cloud Config profile activation
      beanFactory.containsBean("defaultEnvironmentRepository")
          || beanFactory.containsBean("vaultEnvironmentRepository")
          || beanFactory.containsBean("jdbcEnvironmentRepository")
          || beanFactory.containsBean("credhubEnvironmentRepository")
          // beans added via composite Spring Cloud Config profile
          || beanFactory.containsBean("git-env-repo0")
          || beanFactory.containsBean("vault-env-repo0")
          || beanFactory.containsBean("jdbc-env-repo0")
          || beanFactory.containsBean("credhub-env-repo0");
    }
    return false;
  }
}
