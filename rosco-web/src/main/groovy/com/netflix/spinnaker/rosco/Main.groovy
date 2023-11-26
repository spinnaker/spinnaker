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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfiguration
import com.netflix.spinnaker.kork.artifacts.artifactstore.EmbeddedArtifactSerializer
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties
import com.netflix.spinnaker.rosco.jobs.config.LocalJobConfig
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmConfigurationProperties
import com.netflix.spinnaker.rosco.manifests.config.RoscoHelmfileConfigurationProperties
import com.netflix.spinnaker.rosco.manifests.config.RoscoKustomizeConfigurationProperties
import com.netflix.spinnaker.rosco.providers.alicloud.config.RoscoAliCloudConfiguration
import com.netflix.spinnaker.rosco.providers.aws.config.RoscoAWSConfiguration
import com.netflix.spinnaker.rosco.providers.azure.config.RoscoAzureConfiguration
import com.netflix.spinnaker.rosco.providers.docker.config.RoscoDockerConfiguration
import com.netflix.spinnaker.rosco.providers.google.config.RoscoGoogleConfiguration
import com.netflix.spinnaker.rosco.providers.huaweicloud.config.RoscoHuaweiCloudConfiguration
import com.netflix.spinnaker.rosco.providers.oracle.config.RoscoOracleConfiguration
import com.netflix.spinnaker.rosco.providers.tencentcloud.config.RoscoTencentCloudConfiguration
import com.netflix.spinnaker.rosco.services.ServiceConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.filter.ShallowEtagHeaderFilter
import com.netflix.spinnaker.kork.boot.DefaultPropertiesBuilder

import javax.servlet.Filter

@Configuration
@ComponentScan([
  "com.netflix.spinnaker.rosco.config",
  "com.netflix.spinnaker.rosco.controllers",
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
  RoscoAliCloudConfiguration,
  RoscoAWSConfiguration,
  RoscoAzureConfiguration,
  RoscoDockerConfiguration,
  RoscoGoogleConfiguration,
  RoscoHuaweiCloudConfiguration,
  RoscoOracleConfiguration,
  RoscoTencentCloudConfiguration,
  RoscoPackerConfigurationProperties,
  RoscoHelmConfigurationProperties,
  RoscoHelmfileConfigurationProperties,
  RoscoKustomizeConfigurationProperties,
  LocalJobConfig,
  ArtifactStoreConfiguration
])
@EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration])
@EnableScheduling
class Main extends SpringBootServletInitializer {

  static final Map<String, String> DEFAULT_PROPS = new DefaultPropertiesBuilder().property("spring.application.name", "rosco").property('bakeAccount','${netflix.account}').build()

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

  @Bean
  EmbeddedArtifactSerializer artifactSerializer(ArtifactStore store, @Qualifier("artifactObjectMapper") ObjectMapper objectMapper) {
    return new EmbeddedArtifactSerializer(objectMapper, store);
  }

  @Bean
  ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder, EmbeddedArtifactSerializer serializer) {
    return builder.createXmlMapper(false)
            .serializerByType(Artifact.class, serializer)
            .build();
  }
}
