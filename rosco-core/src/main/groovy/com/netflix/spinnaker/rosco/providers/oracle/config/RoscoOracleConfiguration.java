/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.rosco.providers.oracle.config;

import com.netflix.spinnaker.rosco.api.BakeRequest;
import com.netflix.spinnaker.rosco.providers.oracle.OCIBakeHandler;
import com.netflix.spinnaker.rosco.providers.registry.CloudProviderBakeHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty("oracle.enabled")
@ComponentScan("com.netflix.spinnaker.rosco.providers.oracle")
public class RoscoOracleConfiguration {

  final private CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry;

  final private OCIBakeHandler ociBakeHandler;

  @Autowired
  public RoscoOracleConfiguration(CloudProviderBakeHandlerRegistry cloudProviderBakeHandlerRegistry, OCIBakeHandler ociBakeHandler) {
    this.cloudProviderBakeHandlerRegistry = cloudProviderBakeHandlerRegistry;
    this.ociBakeHandler = ociBakeHandler;
  }

  @PostConstruct
  void init() {
    cloudProviderBakeHandlerRegistry.register(BakeRequest.CloudProviderType.oracle, ociBakeHandler);
  }

  @Bean
  @ConfigurationProperties("oracle.bakery-defaults")
  public OracleBakeryDefaults oracleBakeryDefaults() {
    return new OracleBakeryDefaults();
  }

  @Bean
  @ConfigurationProperties("oracle")
  public OracleConfigurationProperties oracleConfigurationProperties() {
    return new OracleConfigurationProperties();
  }

}
