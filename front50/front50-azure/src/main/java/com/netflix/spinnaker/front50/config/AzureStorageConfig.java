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

import com.netflix.spinnaker.front50.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Azure Blob metadata storage configuration.
 *
 * @deprecated Front50 is moving to SQL-only persistence. Azure metadata storage remains available
 *     through Spinnaker 2027.0.0 and is scheduled for removal afterward. Prefer SQL; see {@link
 *     DeprecatedStorageBackend}.
 */
@Deprecated
@Configuration
@ConditionalOnExpression("${spinnaker.azs.enabled:false}")
@EnableConfigurationProperties(AzureStorageProperties.class)
public class AzureStorageConfig {

  private static final Logger log = LoggerFactory.getLogger(AzureStorageConfig.class);

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  @SuppressWarnings("deprecation")
  public AzureStorageService azureStorageService(AzureStorageProperties azureStorageProperties) {
    DeprecatedStorageBackend.warn(log, "Azure");
    return new AzureStorageService(
        azureStorageProperties.getStorageConnectionString(),
        azureStorageProperties.getStorageContainerName());
  }
}
