/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.model.OracleBMCSStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Configuration
@ConditionalOnExpression("${spinnaker.oraclebmcs.enabled:false}")
@EnableConfigurationProperties(OracleBMCSProperties.class)
public class OracleBMCSConfig extends CommonStorageServiceDAOConfig {

  @Bean
  public OracleBMCSStorageService oracleBMCSStorageService(OracleBMCSProperties oracleBMCSProperties) throws IOException {
    OracleBMCSStorageService oracleBMCSStorageService = new OracleBMCSStorageService(oracleBMCSProperties);
    oracleBMCSStorageService.ensureBucketExists();
    return oracleBMCSStorageService;
  }

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

}
