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
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnExpression("${spinnaker.gcs.enabled:false}")
public class GcsConfig {
  // Refresh every 10 minutes. In practice this either doesnt matter because refreshes are fast enough,
  // or should be finer tuned. But it seems silly to refresh at a fast rate when changes are generally infrequent.
  // Actual queries always check to see if the cache is out of date anyway. So this is mostly for the benefit of
  // keeping other replicas up to date so that last-minute updates have fewer changes in them.
  private final Logger log = LoggerFactory.getLogger(getClass());
  private static int APPLICATION_REFRESH_MS = (int)TimeUnit.MINUTES.toMillis(1);
  private static int PROJECT_REFRESH_MS = (int)TimeUnit.MINUTES.toMillis(1);
  private static int NOTIFICATION_REFRESH_MS = (int)TimeUnit.MINUTES.toMillis(1);
  private static int PIPELINE_REFRESH_MS = (int)TimeUnit.MINUTES.toMillis(1);
  private static int PIPELINE_STRATEGY_REFRESH_MS = (int)TimeUnit.MINUTES.toMillis(1);
  private static int SERVICE_ACCOUNT_REFRESH_MS = (int)TimeUnit.MINUTES.toMillis(1);

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
  public ApplicationBucketDAO applicationDAO(GcsStorageService service) {
    return new ApplicationBucketDAO(rootFolder,
                                    service,
                                    Schedulers.from(Executors.newFixedThreadPool(5)),
                                    APPLICATION_REFRESH_MS);
  }

  @Bean
  public ApplicationPermissionBucketDAO applicationPermissionDAO() {
    GcsStorageService service = googleCloudStorageService(ApplicationPermissionDAO.DEFAULT_DATA_FILENAME);
    return new ApplicationPermissionBucketDAO(rootFolder,
                                              service,
                                              Schedulers.from(Executors.newFixedThreadPool(5)),
                                              APPLICATION_REFRESH_MS);
  }

  @Bean
  public ServiceAccountBucketDAO serviceAccountBucketDAO(GcsStorageService service) {
    return new ServiceAccountBucketDAO(rootFolder,
                                       service,
                                       Schedulers.from(Executors.newFixedThreadPool(5)),
                                       SERVICE_ACCOUNT_REFRESH_MS);
  }

  @Bean
  public ProjectBucketDAO projectDAO(GcsStorageService service) {
    return new ProjectBucketDAO(rootFolder,
                                service,
                                Schedulers.from(Executors.newFixedThreadPool(5)),
                                PROJECT_REFRESH_MS);
  }

  @Bean
  public NotificationBucketDAO notificationDAO(GcsStorageService service) {
    return new NotificationBucketDAO(rootFolder,
                                     service,
                                     Schedulers.from(Executors.newFixedThreadPool(5)),
                                     NOTIFICATION_REFRESH_MS);
  }

  @Bean
  public PipelineStrategyBucketDAO pipelineStrategyDAO(GcsStorageService service) {
    return new PipelineStrategyBucketDAO(rootFolder,
                                         service,
                                         Schedulers.from(Executors.newFixedThreadPool(5)),
                                         PIPELINE_STRATEGY_REFRESH_MS);
  }

  @Bean
  public PipelineBucketDAO pipelineDAO(GcsStorageService service) {
    return new PipelineBucketDAO(rootFolder,
                                 service,
                                 Schedulers.from(Executors.newFixedThreadPool(5)),
                                 PIPELINE_REFRESH_MS);
  }
}
