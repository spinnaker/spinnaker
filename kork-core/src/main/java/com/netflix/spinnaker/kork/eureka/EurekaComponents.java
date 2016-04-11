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
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.eventbus.impl.EventBusImpl;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.InvalidSubscriberException;
import com.netflix.eventbus.spi.Subscribe;
import com.netflix.spinnaker.kork.internal.Precondition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;

import javax.annotation.PreDestroy;
import java.util.Map;

@Configuration
@ConditionalOnProperty("eureka.enabled")
public class EurekaComponents {

  @Autowired
  HealthAggregator healthAggregator;

  @Autowired
  Map<String, HealthIndicator> healthIndicators;

  @Autowired
  ApplicationEventPublisher publisher;

  @Value("${eureka.instance.namespace:netflix.appinfo.}")
  String appInfoNamespace = "netflix.appinfo.";

  @Value("${eureka.instance.namespace:netflix.discovery.}")
  String clientConfigNamespace = "netflix.discovery.";

  @Bean
  public EventBus eventBus() {
    return new EventBusImpl();
  }

  @Bean
  @Deprecated //prefer to use EurekaClient interface rather than directly depending on DiscoveryClient
  public DiscoveryClient discoveryClient(ApplicationInfoManager applicationInfoManager, EurekaClientConfig eurekaClientConfig, DiscoveryClient.DiscoveryClientOptionalArgs optionalArgs) {
    return new DiscoveryClient(applicationInfoManager, eurekaClientConfig, optionalArgs);
  }

  @Bean
  public EurekaClient eurekaClient(DiscoveryClient discoveryClient) {
    return discoveryClient;
  }

  @Bean
  public ApplicationInfoManager applicationInfoManager(EurekaInstanceConfig eurekaInstanceConfig) {
    return new ApplicationInfoManager(eurekaInstanceConfig);
  }

  @Bean
  public InstanceInfo instanceInfo(ApplicationInfoManager applicationInfoManager) {
    return applicationInfoManager.getInfo();
  }

  @Bean
  @DependsOn("environmentBackedConfig")
  EurekaInstanceConfig eurekaInstanceConfig() {
    return new CloudInstanceConfig(fixNamespace(appInfoNamespace));
  }

  @Bean
  @DependsOn("environmentBackedConfig")
  EurekaClientConfig eurekaClientConfig() {
    return new DefaultEurekaClientConfig(fixNamespace(clientConfigNamespace));
  }

  @Bean
  DiscoveryClient.DiscoveryClientOptionalArgs optionalArgs(EventBus eventBus, HealthCheckHandler healthCheckHandler) {
    DiscoveryClient.DiscoveryClientOptionalArgs args = new DiscoveryClient.DiscoveryClientOptionalArgs();
    args.setEventBus(eventBus);
    args.setHealthCheckHandlerProvider(new StaticProvider<>(healthCheckHandler));
    return args;
  }

  @Bean
  EurekaStatusSubscriber eurekaStatusSubscriber(EventBus eventBus, DiscoveryClient discoveryClient) {
    return new EurekaStatusSubscriber(publisher, eventBus, discoveryClient);
  }

  @Bean
  HealthCheckHandler healthCheckHandler(ApplicationInfoManager applicationInfoManager) {
    return new BootHealthCheckHandler(applicationInfoManager, healthAggregator, healthIndicators);
  }

  private static class StaticProvider<T> implements com.google.inject.Provider<T> {
    private final T instance;

    public StaticProvider(T instance) {
      this.instance = Precondition.notNull(instance, "instance");
    }

    @Override
    public T get() {
      return instance;
    }
  }

  private static class EurekaStatusSubscriber {
    private final ApplicationEventPublisher publisher;
    private final EventBus eventBus;

    public EurekaStatusSubscriber(ApplicationEventPublisher publisher, EventBus eventBus, DiscoveryClient discoveryClient) {
      this.publisher = Precondition.notNull(publisher, "publisher");
      this.eventBus = Precondition.notNull(eventBus, "eventBus");
      publish(new StatusChangeEvent(
        InstanceInfo.InstanceStatus.UNKNOWN,
        discoveryClient.getInstanceRemoteStatus()));
      try {
        eventBus.registerSubscriber(this);
      } catch (InvalidSubscriberException ise) {
        throw new RuntimeException(ise);
      }
    }

    @PreDestroy
    public void shutdown() {
      eventBus.unregisterSubscriber(this);
    }

    private void publish(StatusChangeEvent event) {
      publisher.publishEvent(new RemoteStatusChangedEvent(event));
    }

    @Subscribe(name = "eurekaStatusSubscriber")
    public void onStatusChange(StatusChangeEvent event) {
      publish(event);
    }
  }

  private static String fixNamespace(String namespace) {
    return namespace.endsWith(".") ? namespace : namespace + ".";
  }
}
