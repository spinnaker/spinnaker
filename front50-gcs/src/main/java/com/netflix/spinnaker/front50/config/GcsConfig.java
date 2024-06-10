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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.storage.StorageScopes;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.front50.api.model.Timestamped;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.jackson.mixins.PipelineMixins;
import com.netflix.spinnaker.front50.jackson.mixins.TimestampedMixins;
import com.netflix.spinnaker.front50.model.DefaultObjectKeyLoader;
import com.netflix.spinnaker.front50.model.GcsStorageService;
import com.netflix.spinnaker.front50.model.ObjectKeyLoader;
import com.netflix.spinnaker.front50.model.ObjectType;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.DefaultApplicationPermissionDAO;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import rx.schedulers.Schedulers;

@Configuration
@ConditionalOnExpression("${spinnaker.gcs.enabled:false}")
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

  private static final Logger log = LoggerFactory.getLogger(GcsConfig.class);

  private static final String DATA_FILENAME =
      ObjectType.APPLICATION.getDefaultMetadataFilename(true);
  private static final String APPLICATION_PERMISSION_DATA_FILENAME =
      ObjectType.APPLICATION_PERMISSION.getDefaultMetadataFilename(true);

  @Bean
  public GcsStorageService defaultGoogleCloudStorageService(
      Storage storage, GcsProperties gcsProperties) {
    return googleCloudStorageService(storage, DATA_FILENAME, gcsProperties);
  }

  private GcsStorageService googleCloudStorageService(
      Storage storage, String dataFilename, GcsProperties gcsProperties) {
    var executor =
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat(GcsStorageService.class.getName() + "-%s")
                .build());
    GcsStorageService service =
        new GcsStorageService(
            storage,
            gcsProperties.getBucket(),
            gcsProperties.getBucketLocation(),
            gcsProperties.getRootFolder(),
            dataFilename,
            new ObjectMapper()
                .addMixIn(Timestamped.class, TimestampedMixins.class)
                .addMixIn(Pipeline.class, PipelineMixins.class),
            executor);
    log.info(
        "Using Google Cloud Storage bucket={} in project={}",
        value("bucket", gcsProperties.getBucket()),
        value("project", gcsProperties.getProject()));

    return service;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  @Qualifier("gcsCredentials")
  public Credentials gcsCredentials(GcsProperties gcsProperties) throws IOException {

    String jsonPath = gcsProperties.getJsonPath();

    GoogleCredentials credentials;
    if (!jsonPath.isEmpty()) {
      try (FileInputStream fis = new FileInputStream(jsonPath)) {
        credentials = GoogleCredentials.fromStream(fis);
      }
      log.info("Loaded GCS credentials from {}", value("jsonPath", jsonPath));
    } else {
      log.info(
          "spinnaker.gcs.enabled without spinnaker.gcs.jsonPath. "
              + "Using default application credentials. Using default credentials.");
      credentials = GoogleCredentials.getApplicationDefault();
    }

    return credentials.createScopedRequired()
        ? credentials.createScoped(Collections.singleton(StorageScopes.DEVSTORAGE_FULL_CONTROL))
        : credentials;
  }

  @Bean
  public Storage googleCloudStorage(
      @Qualifier("gcsCredentials") Credentials credentials, GcsProperties properties) {
    return StorageOptions.newBuilder()
        .setCredentials(credentials)
        .setProjectId(properties.getProject())
        .build()
        .getService();
  }

  @Bean
  public ApplicationPermissionDAO applicationPermissionDAO(
      Storage storage,
      StorageServiceConfigurationProperties storageServiceConfigurationProperties,
      Registry registry,
      CircuitBreakerRegistry circuitBreakerRegistry,
      GcsProperties gcsProperties) {

    GcsStorageService service =
        googleCloudStorageService(storage, APPLICATION_PERMISSION_DATA_FILENAME, gcsProperties);
    ObjectKeyLoader keyLoader = new DefaultObjectKeyLoader(service);
    return new DefaultApplicationPermissionDAO(
        service,
        Schedulers.from(
            Executors.newFixedThreadPool(
                storageServiceConfigurationProperties.getApplicationPermission().getThreadPool())),
        keyLoader,
        storageServiceConfigurationProperties.getApplicationPermission(),
        registry,
        circuitBreakerRegistry);
  }
}
