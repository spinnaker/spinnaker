/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.gcs.config;

import com.netflix.kayenta.gcs.storage.GcsStorageService;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
@EnableConfigurationProperties
@ConditionalOnProperty("kayenta.gcs.enabled")
@ComponentScan({"com.netflix.kayenta.gcs"})
@Slf4j
public class GcsConfiguration {

  @Bean
  @DependsOn({"registerGoogleCredentials"})
  public StorageService storageService(AccountCredentialsRepository accountCredentialsRepository) {
    GcsStorageService.GcsStorageServiceBuilder gcsStorageServiceBuilder = GcsStorageService.builder();

    accountCredentialsRepository
      .getAll()
      .stream()
      .filter(c -> c instanceof GoogleNamedAccountCredentials)
      .filter(c -> c.getSupportedTypes().contains(AccountCredentials.Type.OBJECT_STORE))
      .map(c -> c.getName())
      .forEach(gcsStorageServiceBuilder::accountName);

    GcsStorageService gcsStorageService = gcsStorageServiceBuilder.build();

    log.info("Populated GcsStorageService with {} Google accounts.", gcsStorageService.getAccountNames().size());

    return gcsStorageService;
  }
}
