/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs

import com.netflix.spinnaker.clouddriver.oraclebmcs.config.OracleBMCSConfigurationProperties
import com.netflix.spinnaker.clouddriver.oraclebmcs.health.OracleBMCSHealthIndicator
import com.netflix.spinnaker.clouddriver.oraclebmcs.security.OracleBMCSCredentialsInitializer
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.*
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableConfigurationProperties
@EnableScheduling
@ConditionalOnProperty('oraclebmcs.enabled')
@ComponentScan(["com.netflix.spinnaker.clouddriver.oraclebmcs"])
@Import([ OracleBMCSCredentialsInitializer ])
class OracleBMCSConfiguration {

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  @ConfigurationProperties("oraclebmcs")
  OracleBMCSConfigurationProperties oracleBMCSConfigurationProperties() {
    new OracleBMCSConfigurationProperties()
  }

  @Bean
  OracleBMCSHealthIndicator OracleBMCSHealthIndicator() {
    new OracleBMCSHealthIndicator()
  }

}
