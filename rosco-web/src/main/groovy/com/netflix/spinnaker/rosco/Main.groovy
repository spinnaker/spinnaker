/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco

import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties
import com.netflix.spinnaker.rosco.jobs.config.LocalJobConfig
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration
import com.netflix.spinnaker.rosco.providers.azure.config.RoscoAzureConfiguration
import com.netflix.spinnaker.rosco.providers.docker.config.RoscoDockerConfiguration
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration
import com.netflix.spinnaker.rosco.providers.oracle.config.RoscoOracleConfiguration
import com.netflix.spinnaker.rosco.services.ServiceConfig
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.support.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.filter.ShallowEtagHeaderFilter

import javax.servlet.Filter

@Configuration
@ComponentScan([
  "com.netflix.spinnaker.rosco.config",
  "com.netflix.spinnaker.rosco.controllers",
  "com.netflix.spinnaker.rosco.endpoints",
  "com.netflix.spinnaker.rosco.executor",
  "com.netflix.spinnaker.rosco.filters",
  "com.netflix.spinnaker.rosco.jobs",
  "com.netflix.spinnaker.rosco.manifests",
  "com.netflix.spinnaker.rosco.persistence",
  "com.netflix.spinnaker.config"
])
@Import([
  WebConfig,
  ServiceConfig,
  RoscoAWSConfiguration,
  RoscoAzureConfiguration,
  RoscoDockerConfiguration,
  RoscoGoogleConfiguration,
  RoscoOracleConfiguration,
  RoscoPackerConfigurationProperties,
  LocalJobConfig
])
@EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration])
@EnableScheduling
class Main extends SpringBootServletInitializer {

  static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment': 'test',
    'netflix.account': '${netflix.environment}',
    'netflix.stack': 'test',
    'spring.config.location': '${user.home}/.spinnaker/',
    'spring.application.name': 'rosco',
    'spring.config.name': 'spinnaker,${spring.application.name}',
    'spring.profiles.active': '${netflix.environment},local'
  ]

  static void main(String... args) {
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main).run(args)
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    builder.properties(DEFAULT_PROPS).sources(Main)
  }

  @Bean
  Filter eTagFilter() {
    new ShallowEtagHeaderFilter()
  }
}
