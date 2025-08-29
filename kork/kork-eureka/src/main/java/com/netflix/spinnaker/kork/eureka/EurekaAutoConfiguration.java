/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.kork.eureka;

import com.netflix.appinfo.*;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eventbus.impl.EventBusImpl;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.spinnaker.kork.discovery.DiscoveryAutoConfiguration;
import java.util.Map;
import java.util.Objects;
import javax.inject.Provider;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.StatusAggregator;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty("eureka.enabled")
@EnableConfigurationProperties(EurekaConfigurationProperties.class)
@AutoConfigureBefore(DiscoveryAutoConfiguration.class)
public class EurekaAutoConfiguration {

  @Bean
  public EventBus eventBus() {
    return new EventBusImpl();
  }

  /**
   * @deprecated use EurekaClient rather than DiscoveryClient
   */
  @Bean
  @Deprecated
  public DiscoveryClient discoveryClient(
      ApplicationInfoManager applicationInfoManager,
      EurekaClientConfig eurekaClientConfig,
      DiscoveryClient.DiscoveryClientOptionalArgs optionalArgs) {
    return new DiscoveryClient(applicationInfoManager, eurekaClientConfig, optionalArgs);
  }

  @Bean
  @Primary
  public EurekaClient eurekaClient(DiscoveryClient discoveryClient) {
    return discoveryClient;
  }

  @Bean
  public ApplicationInfoManager applicationInfoManager(EurekaInstanceConfig eurekaInstanceConfig) {
    return new ApplicationInfoManager(
        eurekaInstanceConfig, (ApplicationInfoManager.OptionalArgs) null);
  }

  @Bean
  public InstanceInfo instanceInfo(ApplicationInfoManager applicationInfoManager) {
    return applicationInfoManager.getInfo();
  }

  @Bean
  EurekaInstanceConfig eurekaInstanceConfig(
      EurekaConfigurationProperties eurekaConfigurationProperties) {
    return new CloudInstanceConfig(eurekaConfigurationProperties.getInstance().getNamespace());
  }

  @Bean
  EurekaClientConfig eurekaClientConfig(
      EurekaConfigurationProperties eurekaConfigurationProperties) {
    return new DefaultEurekaClientConfig(eurekaConfigurationProperties.getClient().getNamespace());
  }

  @Bean
  DiscoveryClient.DiscoveryClientOptionalArgs optionalArgs(
      EventBus eventBus, HealthCheckHandler healthCheckHandler) {
    DiscoveryClient.DiscoveryClientOptionalArgs args =
        new DiscoveryClient.DiscoveryClientOptionalArgs();
    args.setEventBus(eventBus);
    args.setHealthCheckHandlerProvider(new StaticProvider<>(healthCheckHandler));
    return args;
  }

  @Bean
  EurekaStatusSubscriber eurekaStatusSubscriber(
      EventBus eventBus, DiscoveryClient discoveryClient, ApplicationEventPublisher publisher) {
    return new EurekaStatusSubscriber(publisher, eventBus, discoveryClient);
  }

  @Bean
  HealthCheckHandler healthCheckHandler(
      ApplicationInfoManager applicationInfoManager,
      StatusAggregator statusAggregator,
      Map<String, HealthIndicator> healthIndicators) {
    return new BootHealthCheckHandler(applicationInfoManager, statusAggregator, healthIndicators);
  }

  private static class StaticProvider<T> implements Provider<T> {
    private final T instance;

    public StaticProvider(T instance) {
      this.instance = Objects.requireNonNull(instance, "instance");
    }

    @Override
    public T get() {
      return instance;
    }
  }
}
