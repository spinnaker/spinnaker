/*
 * Copyright 2017 Veritas Technologies, LLC.
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
import com.netflix.spinnaker.front50.model.SwiftStorageService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnExpression("${spinnaker.swift.enabled:false}")
@EnableConfigurationProperties(SwiftProperties.class)
public class SwiftConfig {

  @Autowired
  Registry registry;

  private final Logger log = LoggerFactory.getLogger(getClass());
  private static int APPLICATION_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(15);
  private static int APPLICATION_PERMISSIONS_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(45);
  private static int PROJECT_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(30);
  private static int NOTIFICATION_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(30);
  private static int PIPELINE_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(10);
  private static int PIPELINE_STRATEGY_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(20);
  private static int SERVICE_ACCOUNT_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(30);
  private static int SNAPSHOT_REFRESH_MS = (int) TimeUnit.SECONDS.toMillis(60);

  @Bean
  public SwiftStorageService swiftService(SwiftProperties properties) {
    return new SwiftStorageService(properties.getContainerName(),
                                   properties.getIdentityEndpoint(),
                                   properties.getUsername(),
                                   properties.getPassword(),
                                   properties.getProjectName(),
                                   properties.getDomainName());
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public ApplicationDAO applicationDAO(SwiftStorageService service, Registry registry) {
    return new DefaultApplicationDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), APPLICATION_REFRESH_MS, registry);
  }

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO(SwiftStorageService service, Registry registry) {
    return new DefaultApplicationPermissionDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), APPLICATION_PERMISSIONS_REFRESH_MS, registry);
  }

  @Bean
  public ServiceAccountDAO serviceAccountDAO(SwiftStorageService service, Registry registry) {
    return new DefaultServiceAccountDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), SERVICE_ACCOUNT_REFRESH_MS, registry);
  }

  @Bean
  public ProjectDAO projectDAO(SwiftStorageService service, Registry registry) {
    return new DefaultProjectDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PROJECT_REFRESH_MS, registry);
  }

  @Bean
  public NotificationDAO notificationDAO(SwiftStorageService service, Registry registry) {
    return new DefaultNotificationDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), NOTIFICATION_REFRESH_MS, registry);
  }

  @Bean
  public PipelineStrategyDAO pipelineStrategyDAO(SwiftStorageService service, Registry registry) {
    return new DefaultPipelineStrategyDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PIPELINE_STRATEGY_REFRESH_MS, registry);
  }

  @Bean
  public PipelineDAO pipelineDAO(SwiftStorageService service, Registry registry) {
    return new DefaultPipelineDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PIPELINE_REFRESH_MS, registry);
  }

  @Bean
  public SnapshotDAO snapshotDAO(SwiftStorageService service, Registry registry) {
    return new DefaultSnapshotDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), SNAPSHOT_REFRESH_MS, registry);
  }
}
