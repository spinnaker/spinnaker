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
import com.netflix.spinnaker.front50.model.project.DefaultProjectDAO;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.DefaultServiceAccountDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.front50.model.snapshot.DefaultSnapshotDAO;
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO;
import com.netflix.spinnaker.front50.model.tag.DefaultEntityTagsDAO;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import rx.schedulers.Schedulers;

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
      Registry registry) {
    return new DefaultApplicationDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getApplication().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getApplication().getRefreshMs(),
        storageServiceConfigurationProperties.getApplication().getShouldWarmCache(),
        registry);
  }

  @Bean
  ApplicationPermissionDAO applicationPermissionDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultApplicationPermissionDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getApplicationPermission().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getApplicationPermission().getRefreshMs(),
        storageServiceConfigurationProperties.getApplicationPermission().getShouldWarmCache(),
        registry);
  }

  @Bean
  ServiceAccountDAO serviceAccountDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultServiceAccountDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getServiceAccount().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getServiceAccount().getRefreshMs(),
        storageServiceConfigurationProperties.getServiceAccount().getShouldWarmCache(),
        registry);
  }

  @Bean
  ProjectDAO projectDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultProjectDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getProject().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getProject().getRefreshMs(),
        storageServiceConfigurationProperties.getProject().getShouldWarmCache(),
        registry);
  }

  @Bean
  NotificationDAO notificationDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultNotificationDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getNotification().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getNotification().getRefreshMs(),
        storageServiceConfigurationProperties.getNotification().getShouldWarmCache(),
        registry);
  }

  @Bean
  PipelineStrategyDAO pipelineStrategyDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultPipelineStrategyDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPipelineStrategy().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPipelineStrategy().getRefreshMs(),
        storageServiceConfigurationProperties.getPipelineStrategy().getShouldWarmCache(),
        registry);
  }

  @Bean
  PipelineDAO pipelineDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultPipelineDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPipeline().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPipeline().getRefreshMs(),
        storageServiceConfigurationProperties.getPipeline().getShouldWarmCache(),
        registry);
  }

  @Bean
  PipelineTemplateDAO pipelineTemplateDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultPipelineTemplateDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getPipelineTemplate().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getPipelineTemplate().getRefreshMs(),
        storageServiceConfigurationProperties.getPipelineTemplate().getShouldWarmCache(),
        registry);
  }

  @Bean
  SnapshotDAO snapshotDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultSnapshotDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getSnapshot().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getSnapshot().getRefreshMs(),
        storageServiceConfigurationProperties.getSnapshot().getShouldWarmCache(),
        registry);
  }

  @Bean
  EntityTagsDAO entityTagsDAO(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultEntityTagsDAO(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getEntityTags().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getEntityTags().getRefreshMs(),
        storageServiceConfigurationProperties.getEntityTags().getShouldWarmCache(),
        registry);
  }

  @Bean
  DeliveryRepository deliveryRepository(
      StorageService storageService,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      ObjectKeyLoader objectKeyLoader,
      Registry registry) {
    return new DefaultDeliveryRepository(
        storageService,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getDeliveryConfig().getThreadPool())),
        objectKeyLoader,
        storageServiceConfigurationProperties.getDeliveryConfig().getRefreshMs(),
        storageServiceConfigurationProperties.getDeliveryConfig().getShouldWarmCache(),
        registry);
  }
}
