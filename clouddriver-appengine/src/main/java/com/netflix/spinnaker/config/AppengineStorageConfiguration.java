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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.clouddriver.appengine.artifacts.GcsStorageService.Factory;
import com.netflix.spinnaker.clouddriver.appengine.artifacts.config.StorageConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.security.GeneralSecurityException;


@Configuration
@ConditionalOnProperty("artifacts.gcs.enabled")
@EnableConfigurationProperties(StorageConfigurationProperties.class)
@EnableScheduling
public class AppengineStorageConfiguration {
  @Autowired
  StorageConfigurationProperties storageAccountInfo;

  @Bean
  Factory storageServiceFactory(String clouddriverUserAgentApplicationName) {
    try {
      return new Factory(clouddriverUserAgentApplicationName);
    } catch (IOException | GeneralSecurityException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
