/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy;
import tools.jackson.databind.json.JsonMapper;

class Jackson3PropertyNamingConfigurationTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyNamesAreIndependentOfPropertySorting(boolean sort) {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(
            Jackson3PropertyOrderConfiguration.class, Jackson3PropertyNamingConfiguration.class)
        .withPropertyValues("spring.jackson.mapper.SORT_PROPERTIES_ALPHABETICALLY=" + sort)
        .run(
            context -> {
              JsonMapper mapper = context.getBean(JsonMapper.class);
              assertThat(mapper.writeValueAsString(new AcronymBean()))
                  .contains("\"oauthScopes\":\"scope\"");
              assertThat(
                      mapper
                          .readValue("{\"oauthScopes\":\"legacy\"}", AcronymBean.class)
                          .getOAuthScopes())
                  .isEqualTo("legacy");
            });
  }

  @Test
  void legacyNamingCanBeDisabled() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(Jackson3PropertyNamingConfiguration.class)
        .withPropertyValues("spinnaker.jackson.legacy-bean-naming=false")
        .run(
            context ->
                assertThat(context.getBean(JsonMapper.class).writeValueAsString(new AcronymBean()))
                    .contains("\"oAuthScopes\":\"scope\""));
  }

  @Test
  void serviceNamingStrategyIsRespected() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(Jackson3PropertyNamingConfiguration.class)
        .withPropertyValues("spring.jackson.property-naming-strategy=SNAKE_CASE")
        .run(
            context ->
                assertThat(context.getBean(JsonMapper.class).writeValueAsString(new AcronymBean()))
                    .contains("\"oauth_scopes\":\"scope\""));
  }

  @Test
  void serviceCanOverrideAccessorNaming() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(
            Jackson3PropertyNamingConfiguration.class, StandardNamingConfiguration.class)
        .run(
            context ->
                assertThat(context.getBean(JsonMapper.class).writeValueAsString(new AcronymBean()))
                    .contains("\"oAuthScopes\":\"scope\""));
  }

  @Configuration
  static class StandardNamingConfiguration {
    @Bean
    @Order(1)
    JsonMapperBuilderCustomizer standardAccessorNaming() {
      return builder ->
          builder
              .accessorNaming(new DefaultAccessorNamingStrategy.Provider())
              .enable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX);
    }
  }

  static class AcronymBean {
    private String oAuthScopes = "scope";

    public String getOAuthScopes() {
      return oAuthScopes;
    }

    public void setOAuthScopes(String value) {
      oAuthScopes = value;
    }
  }
}
