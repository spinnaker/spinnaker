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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.*;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

import java.util.Optional;
import java.util.concurrent.Executors;

import static net.logstash.logback.argument.StructuredArguments.value;

@Configuration
@ConditionalOnExpression("${spinnaker.gcs.enabled:false}")
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig extends CommonStorageServiceDAOConfig {
  @Value("${spinnaker.gcs.safeRetry.maxWaitIntervalMs:60000}")
  Long maxWaitInterval;

  @Value("${spinnaker.gcs.safeRetry.retryIntervalBaseSec:2}")
  Long retryIntervalBase;

  @Value("${spinnaker.gcs.safeRetry.jitterMultiplier:1000}")
  Long jitterMultiplier;

  @Value("${spinnaker.gcs.safeRetry.maxRetries:10}")
  Long maxRetries;

  @Autowired
  Registry registry;

  @Autowired
  GcsProperties gcsProperties;

  @Autowired
  TaskScheduler taskScheduler;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Bean
  public GcsStorageService defaultGoogleCloudStorageService(GcsProperties gcsProperties) {
    return googleCloudStorageService(null /*dataFilename*/, gcsProperties);
  }

  private GcsStorageService googleCloudStorageService(String dataFilename, GcsProperties gcsProperties) {
    String applicationVersion = Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("Unknown");
    GcsStorageService service;
    if (dataFilename == null || dataFilename.isEmpty()) {
      service = new GcsStorageService(gcsProperties.getBucket(),
        gcsProperties.getBucketLocation(),
        gcsProperties.getRootFolder(),
        gcsProperties.getProject(),
        gcsProperties.getJsonPath(),
        applicationVersion,
        maxWaitInterval,
        retryIntervalBase,
        jitterMultiplier,
        maxRetries,
        taskScheduler,
        registry);
    } else {
      service = new GcsStorageService(gcsProperties.getBucket(),
        gcsProperties.getBucketLocation(),
        gcsProperties.getRootFolder(),
        gcsProperties.getProject(),
        gcsProperties.getJsonPath(),
        applicationVersion,
        dataFilename,
        maxWaitInterval,
        retryIntervalBase,
        jitterMultiplier,
        maxRetries,
        taskScheduler,
        registry);
    }
    service.ensureBucketExists();
    log.info("Using Google Cloud Storage bucket={} in project={}",
      value("bucket", gcsProperties.getBucket()),
      value("project", gcsProperties.getProject()));
    log.info("Bucket versioning is {}.",
      value("versioning", service.supportsVersioning() ? "enabled" : "DISABLED"));

    // Cleanup every 5 minutes to reduce rate limiting contention.
    long period_ms = 300 * 1000;
    taskScheduler.scheduleAtFixedRate(
      new Runnable() {
        public void run() {
          service.purgeBatchedVersionPaths();
        }
      },
      period_ms);

    return service;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Override
  public ApplicationPermissionDAO applicationPermissionDAO(StorageService storageService,
                                                           StorageServiceConfigurationProperties storageServiceConfigurationProperties,
                                                           ObjectKeyLoader objectKeyLoader,
                                                           Registry registry) {
    GcsStorageService service = googleCloudStorageService(ApplicationPermissionDAO.DEFAULT_DATA_FILENAME, gcsProperties);
    ObjectKeyLoader keyLoader = new DefaultObjectKeyLoader(service);
    return new DefaultApplicationPermissionDAO(
      service,
      Schedulers.from(Executors.newFixedThreadPool(storageServiceConfigurationProperties.getApplicationPermission().getThreadPool())),
      keyLoader,
      storageServiceConfigurationProperties.getApplicationPermission().getRefreshMs(),
      storageServiceConfigurationProperties.getApplicationPermission().getShouldWarmCache(),
      registry
    );
  }
}
