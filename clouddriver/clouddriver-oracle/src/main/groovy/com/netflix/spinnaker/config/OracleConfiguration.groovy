/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.config

import com.netflix.spinnaker.clouddriver.oracle.config.OracleConfigurationProperties
import com.netflix.spinnaker.clouddriver.oracle.health.OracleHealthIndicator
import com.netflix.spinnaker.clouddriver.oracle.security.OracleCredentialsInitializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('oracle.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.oracle"])
@Import([ OracleCredentialsInitializer ])
class OracleConfiguration {

  @Bean
  @ConfigurationProperties("oracle")
  OracleConfigurationProperties oracleConfigurationProperties() {
    new OracleConfigurationProperties()
  }

  @Bean
  OracleHealthIndicator OracleHealthIndicator() {
    new OracleHealthIndicator()
  }

}
