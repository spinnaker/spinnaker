/*
 * Copyright 2016 Google, Inc.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnExpression("${spinnaker.gcs.enabled:false}")
@PropertySource(value = "classpath:META-INF/front50-core.properties", ignoreResourceNotFound = true)
public class GcsConfig {
  // Refresh every 10 minutes. In practice this either doesnt matter because refreshes are fast enough,
  // or should be finer tuned. But it seems silly to refresh at a fast rate when changes are generally infrequent.
  // Actual queries always check to see if the cache is out of date anyway. So this is mostly for the benefit of
  // keeping other replicas up to date so that last-minute updates have fewer changes in them.
  private final Logger log = LoggerFactory.getLogger(getClass());
  private static int APPLICATION_REFRESH_MS = (int) TimeUnit.MINUTES.toMillis(1);
  private static int PROJECT_REFRESH_MS = (int) TimeUnit.MINUTES.toMillis(1);
  private static int NOTIFICATION_REFRESH_MS = (int) TimeUnit.MINUTES.toMillis(1);
  private static int PIPELINE_REFRESH_MS = (int) TimeUnit.MINUTES.toMillis(1);
  private static int PIPELINE_STRATEGY_REFRESH_MS = (int) TimeUnit.MINUTES.toMillis(1);
  private static int SERVICE_ACCOUNT_REFRESH_MS = (int) TimeUnit.MINUTES.toMillis(1);

  @Value("${spinnaker.gcs.bucket}")
  private String bucket;

  @Value("${spinnaker.gcs.bucketLocation}")
  private String bucketLocation;

  @Value("${spinnaker.gcs.rootFolder}")
  private String rootFolder;

  @Value("${spinnaker.gcs.jsonPath:}")
  private String jsonPath;

  @Value("${spinnaker.gcs.project:}")
  private String project;

  @Value("${Implementation-Version:Unknown}")
  private String applicationVersion;

  @Bean
  public GcsStorageService defaultGoogleCloudStorageService() {
    return googleCloudStorageService(null /*dataFilename*/);
  }

  private GcsStorageService googleCloudStorageService(String dataFilename) {
    GcsStorageService service;
    if (dataFilename == null || dataFilename.isEmpty()) {
      service = new GcsStorageService(bucket, bucketLocation, rootFolder, project, jsonPath, applicationVersion);
    } else {
      service = new GcsStorageService(bucket, bucketLocation, rootFolder, project, jsonPath, applicationVersion, dataFilename);
    }
    service.ensureBucketExists();
    log.info("Using Google Cloud Storage bucket={} in project={}",
      bucket, project);
    log.info("Bucket versioning is {}.",
      service.supportsVersioning() ? "enabled" : "DISABLED");
    return service;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public ApplicationDAO applicationDAO(GcsStorageService service) {
    return new DefaultApplicationDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), APPLICATION_REFRESH_MS);
  }

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO() {
    GcsStorageService service = googleCloudStorageService(ApplicationPermissionDAO.DEFAULT_DATA_FILENAME);
    return new DefaultApplicationPermissionDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), APPLICATION_REFRESH_MS);
  }

  @Bean
  public ServiceAccountDAO serviceAccountDAO(GcsStorageService service) {
    return new DefaultServiceAccountDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), SERVICE_ACCOUNT_REFRESH_MS);
  }

  @Bean
  public ProjectDAO projectDAO(GcsStorageService service) {
    return new DefaultProjectDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PROJECT_REFRESH_MS);
  }

  @Bean
  public NotificationDAO notificationDAO(GcsStorageService service) {
    return new DefaultNotificationDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), NOTIFICATION_REFRESH_MS);
  }

  @Bean
  public PipelineStrategyDAO pipelineStrategyDAO(GcsStorageService service) {
    return new DefaultPipelineStrategyDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PIPELINE_STRATEGY_REFRESH_MS);
  }

  @Bean
  public PipelineDAO pipelineDAO(GcsStorageService service) {
    return new DefaultPipelineDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PIPELINE_REFRESH_MS);
  }

  @Bean
  public SnapshotDAO snapshotDAO(GcsStorageService service) {
    return new DefaultSnapshotDAO(service, Schedulers.from(Executors.newFixedThreadPool(20)), PIPELINE_REFRESH_MS);
  }
}
