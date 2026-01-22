/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.delivery.DefaultDeliveryRepository;
import com.netflix.spinnaker.front50.model.delivery.DeliveryRepository;
import com.netflix.spinnaker.front50.model.notification.DefaultNotificationDAO;
import com.netflix.spinnaker.front50.model.notification.NotificationDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineTemplateDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO;
import com.netflix.spinnaker.front50.model.plugins.DefaultPluginInfoRepository;
import com.netflix.spinnaker.front50.model.plugins.DefaultPluginVersionPinningRepository;
import com.netflix.spinnaker.front50.model.plugins.PluginInfoRepository;
import com.netflix.spinnaker.front50.model.plugins.PluginVersionPinningRepository;
import com.netflix.spinnaker.front50.model.project.DefaultProjectDAO;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.DefaultServiceAccountDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.front50.model.snapshot.DefaultSnapshotDAO;
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO;
import com.netflix.spinnaker.front50.model.tag.DefaultEntityTagsDAO;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    value = "spinnaker.redis.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class CommonStorageServiceDAOConfig {
  @Bean
  @ConditionalOnMissingBean(ObjectKeyLoader.class)
  ObjectKeyLoader defaultObjectKeyLoader(StorageService storageService) {
    return new DefaultObjectKeyLoader(storageService);
  }

  @Bean
  ApplicationDAO applicationDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultApplicationDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getApplication().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getApplication(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  @ConditionalOnMissingBean // GcsConfig overrides this
  ApplicationPermissionDAO applicationPermissionDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultApplicationPermissionDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getApplicationPermission().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getApplicationPermission(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  ServiceAccountDAO serviceAccountDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultServiceAccountDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getServiceAccount().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getServiceAccount(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  ProjectDAO projectDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultProjectDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getProject().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getProject(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  NotificationDAO notificationDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultNotificationDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getNotification().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getNotification(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  PipelineStrategyDAO pipelineStrategyDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultPipelineStrategyDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPipelineStrategy().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPipelineStrategy(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  PipelineDAO pipelineDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultPipelineDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPipeline().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPipeline(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  PipelineTemplateDAO pipelineTemplateDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultPipelineTemplateDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPipelineTemplate().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPipelineTemplate(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  SnapshotDAO snapshotDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultSnapshotDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getSnapshot().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getSnapshot(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  EntityTagsDAO entityTagsDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultEntityTagsDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getEntityTags().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getEntityTags(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  DeliveryRepository deliveryRepository(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultDeliveryRepository(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getDeliveryConfig().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getDeliveryConfig(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  PluginInfoRepository pluginInfoRepository(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultPluginInfoRepository(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPluginInfo().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPluginInfo(),
        registry,
        circuitBreakerRegistry);
  }

  @Bean
  PluginVersionPinningRepository pluginVersionPinningRepository(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    return new DefaultPluginVersionPinningRepository(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPluginInfo().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPluginInfo(),
        registry,
        circuitBreakerRegistry);
  }
}
