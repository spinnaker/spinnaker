/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.gate.converters;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Verifies the converters Gate's MVC stack actually ends up with now that it registers none of its
 * own: YAML in and out as {@code application/yaml}, and Strings handled by the String converter
 * ahead of Jackson so they are never JSON-quoted.
 */
@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
class MessageConvertersWiringTest {

  @MockitoBean ApplicationService applicationService;
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;
  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  @Autowired private RequestMappingHandlerAdapter adapter;

  @Autowired
  private java.util.Optional<com.netflix.spinnaker.gate.filters.LegacyYamlMediaTypeFilter>
      legacyYamlFilter;

  @Test
  void legacyYamlFilterIsRegistered() {
    assertThat(legacyYamlFilter).isPresent();
  }

  @Test
  void readsAndWritesYaml() {
    List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();

    assertThat(converters)
        .anyMatch(c -> c.canRead(java.util.Map.class, MediaType.APPLICATION_YAML));
    assertThat(converters)
        .anyMatch(c -> c.canWrite(java.util.Map.class, MediaType.APPLICATION_YAML));
  }

  @Test
  void stringConverterIsAheadOfJsonForStrings() {
    List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();

    HttpMessageConverter<?> first =
        converters.stream()
            .filter(c -> c.canWrite(String.class, MediaType.APPLICATION_JSON))
            .findFirst()
            .orElseThrow();
    assertThat(first).isInstanceOf(StringHttpMessageConverter.class);
  }
}
