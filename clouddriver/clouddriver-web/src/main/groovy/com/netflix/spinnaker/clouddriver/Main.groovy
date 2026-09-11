/*
 * Copyright 2014-2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.module.SimpleModule
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDeserializer
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfiguration
import com.netflix.spinnaker.kork.artifacts.artifactstore.entities.DeserializerHookRegistry
import com.netflix.spinnaker.kork.artifacts.artifactstore.entities.SerializerHookRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.boot.DefaultPropertiesBuilder
import com.netflix.spinnaker.kork.configserver.ConfigServerBootstrap
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling

import java.security.Security

@Configuration
@Import([
  WebConfig,
  SecurityConfig,
  ArtifactStoreConfiguration,
])
@ComponentScan([
  'com.netflix.spinnaker.config',
  'com.netflix.spinnaker.clouddriver.config'
])
@EnableAutoConfiguration(exclude = [
  DataSourceAutoConfiguration
],
// Excluded by name (not class) so contexts without spring-data-redis on the
// classpath don't fail annotation introspection with TypeNotPresentException,
// which discards the whole @EnableAutoConfiguration.
excludeName = [
  'org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration',
  'org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration'
])
@EnableScheduling
class Main extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS = new DefaultPropertiesBuilder().build()

  static {
    /**
     * We often operate in an environment where we expect resolution of DNS names for remote dependencies to change
     * frequently, so it's best to tell the JVM to avoid caching DNS results internally.
     */
    Security.setProperty('networkaddress.cache.ttl', '0')
    System.setProperty("spring.main.allow-bean-definition-overriding", "true")
  }

  static void main(String... args) {
    ConfigServerBootstrap.systemProperties("clouddriver")
    new SpringApplicationBuilder()
      .properties(DEFAULT_PROPS)
      .sources(Main)
      .run(args)
  }

  @Bean
  @Primary
  ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder, Optional<DeserializerHookRegistry> deserializerHook, Optional<SerializerHookRegistry> serializerHook) {
    builder = builder.createXmlMapper(false)
      .mixIn(Artifact.class, ArtifactMixin.class);

    SimpleModule module = new SimpleModule("registryHook")
    if (deserializerHook.isPresent()) {
      module.setDeserializerModifier(deserializerHook.get())
    }
    if (serializerHook.isPresent()) {
      module.setSerializerModifier(serializerHook.get());
    }
    builder.modules(l -> l.add(module))
    return builder.build();
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application
      .properties(DEFAULT_PROPS)
      .sources(Main)
  }

  /**
   * Used to deserialize artifacts utilizing an artifact store, and thus
   * bypassing the default deserializer on the artifact object itself.
   */
  @JsonDeserialize(using = ArtifactDeserializer.class)
  private static interface ArtifactMixin{}
}

