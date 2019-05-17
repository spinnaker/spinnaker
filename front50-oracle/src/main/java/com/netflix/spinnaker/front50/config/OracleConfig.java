/*
 * Copyright (c) 2017, 2018 Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.model.OracleStorageService;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnExpression("${spinnaker.oracle.enabled:false}")
@EnableConfigurationProperties(OracleProperties.class)
public class OracleConfig extends CommonStorageServiceDAOConfig {

  @Bean
  public OracleStorageService oracleStorageService(OracleProperties oracleProperties)
      throws IOException {
    OracleStorageService oracleStorageService = new OracleStorageService(oracleProperties);
    oracleStorageService.ensureBucketExists();
    return oracleStorageService;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
