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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnExpression("${spinnaker.swift.enabled:false}")
@EnableConfigurationProperties(SwiftProperties.class)
public class SwiftConfig extends CommonStorageServiceDAOConfig {

  @Autowired Registry registry;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Bean
  public SwiftStorageService swiftService(SwiftProperties properties) {
    return new SwiftStorageService(
        properties.getContainerName(),
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
}
