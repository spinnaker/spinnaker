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

package com.netflix.spinnaker.clouddriver.appengine.storage;

import com.netflix.spinnaker.clouddriver.appengine.storage.config.StorageConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.security.GeneralSecurityException;


@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty("storage.gcs.enabled")
class StorageConfiguration {
  @Autowired
  StorageConfigurationProperties storageAccountInfo;

  @Bean
  @ConfigurationProperties("storage.gcs")
  StorageConfigurationProperties storageConfigurationProperties() {
    return new StorageConfigurationProperties();
  }

  @Bean
  GcsStorageService.Factory storageServiceFactory(String clouddriverUserAgentApplicationName) {
    try {
      return new GcsStorageService.Factory(clouddriverUserAgentApplicationName);
    } catch (IOException ioex) {
      throw new IllegalStateException(ioex);
    } catch (GeneralSecurityException secex) {
      throw new IllegalStateException(secex);
    }
  }
}
