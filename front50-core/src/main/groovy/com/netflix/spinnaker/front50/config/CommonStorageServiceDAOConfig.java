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
import com.netflix.spinnaker.front50.model.StorageService;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
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
import org.springframework.context.annotation.Bean;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executors;

public class CommonStorageServiceDAOConfig {
  @Bean
  ApplicationDAO applicationDAO(StorageService storageService,
                                StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                Registry registry) {
    return new DefaultApplicationDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getApplication().getThreadPool())),
      storageServiceConfigurationProperties.getApplication().getRefreshMs(),
      registry
    );
  }

  @Bean
  ApplicationPermissionDAO applicationPermissionDAO(StorageService storageService,
                                                    StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                                    Registry registry) {
    return new DefaultApplicationPermissionDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getApplicationPermission().getThreadPool())),
      storageServiceConfigurationProperties.getApplicationPermission().getRefreshMs(),
      registry
    );
  }

  @Bean
  ServiceAccountDAO serviceAccountDAO(StorageService storageService,
                                      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                      Registry registry) {
    return new DefaultServiceAccountDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getServiceAccount().getThreadPool())),
      storageServiceConfigurationProperties.getServiceAccount().getRefreshMs(),
      registry
    );
  }

  @Bean
  ProjectDAO projectDAO(StorageService storageService,
                        StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                        Registry registry) {
    return new DefaultProjectDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getProject().getThreadPool())),
      storageServiceConfigurationProperties.getProject().getRefreshMs(),
      registry
    );
  }

  @Bean
  NotificationDAO notificationDAO(StorageService storageService,
                                  StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                  Registry registry) {
    return new DefaultNotificationDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getNotification().getThreadPool())),
      storageServiceConfigurationProperties.getNotification().getRefreshMs(),
      registry
    );
  }

  @Bean
  PipelineStrategyDAO pipelineStrategyDAO(StorageService storageService,
                                          StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                          Registry registry) {
    return new DefaultPipelineStrategyDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getPipelineStrategy().getThreadPool())),
      storageServiceConfigurationProperties.getPipelineStrategy().getRefreshMs(),
      registry
    );
  }

  @Bean
  PipelineDAO pipelineDAO(StorageService storageService,
                          StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                          Registry registry) {
    return new DefaultPipelineDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getPipeline().getThreadPool())),
      storageServiceConfigurationProperties.getPipeline().getRefreshMs(),
      registry
    );
  }

  @Bean
  PipelineTemplateDAO pipelineTemplateDAO(StorageService storageService,
                                          StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                          Registry registry) {
    return new DefaultPipelineTemplateDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getPipelineTemplate().getThreadPool())),
      storageServiceConfigurationProperties.getPipelineTemplate().getRefreshMs(),
      registry
    );
  }

  @Bean
  SnapshotDAO snapshotDAO(StorageService storageService,
                          StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                          Registry registry) {
    return new DefaultSnapshotDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getSnapshot().getThreadPool())),
      storageServiceConfigurationProperties.getSnapshot().getRefreshMs(),
      registry
    );
  }

  @Bean
  EntityTagsDAO entityTagsDAO(StorageService storageService,
                              StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                              Registry registry) {
    return new DefaultEntityTagsDAO(
      storageService,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getEntityTags().getThreadPool())),
      storageServiceConfigurationProperties.getEntityTags().getRefreshMs(),
      registry
    );
  }
}
