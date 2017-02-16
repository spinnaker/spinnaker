/*
 * Copyright 2017 Microsoft, Inc.
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
import com.netflix.spinnaker.front50.model.*;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.notification.DefaultNotificationDAO;
import com.netflix.spinnaker.front50.model.notification.NotificationDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.DefaultPipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO;
import com.netflix.spinnaker.front50.model.project.DefaultProjectDAO;
import com.netflix.spinnaker.front50.model.project.ProjectDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.DefaultServiceAccountDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.front50.model.snapshot.DefaultSnapshotDAO;
import com.netflix.spinnaker.front50.model.snapshot.SnapshotDAO;
import com.netflix.spinnaker.front50.model.tag.DefaultEntityTagsDAO;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executors;

@Configuration
@ConditionalOnExpression("${spinnaker.azs.enabled:false}")
@EnableConfigurationProperties(AzureStorageProperties.class)
public class AzureStorageConfig {

  @Autowired
  Registry registry;

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public AzureStorageService azureStorageService(AzureStorageProperties azureStorageProperties) {
    // This is where we create the service
    return new AzureStorageService(azureStorageProperties.getStorageConnectionString(), azureStorageProperties.getStorageContainerName());
  }

  @Bean
  public ApplicationDAO applicationDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultApplicationDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 15000, registry);
  }

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultApplicationPermissionDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 45000, registry);
  }

  @Bean
  public ServiceAccountDAO serviceAccountDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultServiceAccountDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 30000, registry);
  }

  @Bean
  public ProjectDAO projectDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultProjectDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 30000, registry);
  }

  @Bean
  public NotificationDAO notificationDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultNotificationDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 30000, registry);
  }

  @Bean
  public PipelineStrategyDAO pipelineStrategyDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultPipelineStrategyDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 20000, registry);
  }

  @Bean
  public PipelineDAO pipelineDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultPipelineDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(25)), 10000, registry);
  }

  @Bean
  public SnapshotDAO snapshotDAO(AzureStorageService storageService, Registry registry) {
    return new DefaultSnapshotDAO(storageService, Schedulers.from(Executors.newFixedThreadPool(20)), 60000, registry);
  }

  @Bean
  public EntityTagsDAO entityTagsDAO(AzureStorageService storageService) {
    return new DefaultEntityTagsDAO(storageService, null, -1);
  }
}
