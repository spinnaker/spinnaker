/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.kayenta.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.kayenta.atlas.config.KayentaSerializationConfigurationProperties;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class KayentaJackson3SubtypeConfigurationTest {

  @JsonTypeName("fake-query")
  static class FakeQueryConfig implements CanaryMetricSetQueryConfig {
    @Override
    public String getServiceType() {
      return "fake";
    }
  }

  @Test
  void polymorphicQueryTypeResolvesOnJackson3Mapper() throws Exception {
    ObjectMapperSubtypeConfigurer.ClassSubtypeLocator locator =
        new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
            CanaryMetricSetQueryConfig.class, List.of("com.netflix.kayenta.config"));

    JsonMapper.Builder builder = JsonMapper.builder();
    new KayentaJackson3SubtypeConfiguration()
        .kayentaJackson3SubtypeRegistrar(List.of(locator))
        .customize(builder);
    JsonMapper mapper = builder.build();

    Object result =
        mapper.readValue(
            "{\"type\":\"fake-query\",\"serviceType\":\"fake\"}", CanaryMetricSetQueryConfig.class);

    assertTrue(result instanceof FakeQueryConfig);
    assertEquals("fake", ((CanaryMetricSetQueryConfig) result).getServiceType());
  }

  @Test
  void httpConverterUsesKayentaSubtypeAndDateSettings() throws Exception {
    ObjectMapperSubtypeConfigurer.ClassSubtypeLocator locator =
        new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
            CanaryMetricSetQueryConfig.class, List.of("com.netflix.kayenta.config"));
    KayentaSerializationConfigurationProperties properties =
        new KayentaSerializationConfigurationProperties();
    properties.setWriteDatesAsTimestamps(false);
    ObjectMapper mapper =
        new KayentaConfiguration()
            .kayentaObjectMapper(
                JsonMapper.builder().build(),
                new ObjectMapperSubtypeConfigurer(true),
                List.of(locator),
                properties);
    List<HttpMessageConverter<?>> converters = new ArrayList<>();
    new KayentaJacksonMessageConverterConfiguration(mapper).extendMessageConverters(converters);
    @SuppressWarnings("unchecked")
    HttpMessageConverter<Object> converter = (HttpMessageConverter<Object>) converters.getFirst();

    MockHttpInputMessage input =
        new MockHttpInputMessage("{\"type\":\"fake-query\"}".getBytes(StandardCharsets.UTF_8));
    input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    assertTrue(converter.canRead(CanaryMetricSetQueryConfig.class, MediaType.APPLICATION_JSON));
    assertTrue(converter.read(CanaryMetricSetQueryConfig.class, input) instanceof FakeQueryConfig);

    MockHttpOutputMessage output = new MockHttpOutputMessage();
    converter.write(Instant.EPOCH, MediaType.APPLICATION_JSON, output);
    assertEquals("\"1970-01-01T00:00:00Z\"", output.getBodyAsString());
  }
}
