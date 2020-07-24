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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.Optional;
import java.util.concurrent.Executors;
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

@Configuration
@ConditionalOnExpression("${spinnaker.gcs.enabled:false}")
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

  @Value("${spinnaker.gcs.safe-retry.max-wait-interval-ms:60000}")
  int maxWaitInterval;

  @Value("${spinnaker.gcs.safe-retry.retry-interval-base-sec:2}")
  int retryIntervalBase;

  @Value("${spinnaker.gcs.safe-retry.jitter-multiplier:1000}")
  int jitterMultiplier;

  @Value("${spinnaker.gcs.safe-retry.max-retries:10}")
  int maxRetries;

  @Value("${spinnaker.gcs.connect-timeout-sec:45}")
  Integer connectTimeoutSec;

  @Value("${spinnaker.gcs.read-timeout-sec:45}")
  Integer readTimeoutSec;

  @Autowired Registry registry;

  @Autowired GcsProperties gcsProperties;

  @Autowired TaskScheduler taskScheduler;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Bean
  public GcsStorageService defaultGoogleCloudStorageService(GcsProperties gcsProperties) {
    return googleCloudStorageService(null /*dataFilename*/, gcsProperties);
  }

  private GcsStorageService googleCloudStorageService(
      String dataFilename, GcsProperties gcsProperties) {
    String applicationVersion =
        Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("Unknown");
    GcsStorageService service;
    if (dataFilename == null || dataFilename.isEmpty()) {
      service =
          new GcsStorageService(
              gcsProperties.getBucket(),
              gcsProperties.getBucketLocation(),
              gcsProperties.getRootFolder(),
              gcsProperties.getProject(),
              gcsProperties.getJsonPath(),
              applicationVersion,
              connectTimeoutSec,
              readTimeoutSec,
              maxWaitInterval,
              retryIntervalBase,
              jitterMultiplier,
              maxRetries,
              taskScheduler,
              registry);
    } else {
      service =
          new GcsStorageService(
              gcsProperties.getBucket(),
              gcsProperties.getBucketLocation(),
              gcsProperties.getRootFolder(),
              gcsProperties.getProject(),
              gcsProperties.getJsonPath(),
              applicationVersion,
              dataFilename,
              connectTimeoutSec,
              readTimeoutSec,
              maxWaitInterval,
              retryIntervalBase,
              jitterMultiplier,
              maxRetries,
              taskScheduler,
              registry);
    }
    service.ensureBucketExists();
    log.info(
        "Using Google Cloud Storage bucket={} in project={}",
        value("bucket", gcsProperties.getBucket()),
        value("project", gcsProperties.getProject()));
    log.info(
        "Bucket versioning is {}.",
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

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO(
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    GcsStorageService service =
        googleCloudStorageService(ApplicationPermissionDAO.DEFAULT_DATA_FILENAME, gcsProperties);
    ObjectKeyLoader keyLoader = new DefaultObjectKeyLoader(service);
    return new DefaultApplicationPermissionDAO(
        service,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getApplicationPermission().getThreadPool())),
        keyLoader,
        storageServiceConfigurationProperties.getApplicationPermission().getRefreshMs(),
        storageServiceConfigurationProperties.getApplicationPermission().getShouldWarmCache(),
        registry,
        circuitBreakerRegistry);
  }
}
